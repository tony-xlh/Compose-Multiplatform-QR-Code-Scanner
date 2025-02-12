package org.example.project

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitInteropProperties
import androidx.compose.ui.viewinterop.UIKitView
import cocoapods.DynamsoftBarcodeReader.DBRLicenseVerificationListenerProtocol
import cocoapods.DynamsoftBarcodeReader.DynamsoftBarcodeReader
import cocoapods.DynamsoftBarcodeReader.EnumImagePixelFormat
import cocoapods.DynamsoftBarcodeReader.EnumImagePixelFormatABGR_8888
import cocoapods.DynamsoftBarcodeReader.iImageData
import cocoapods.DynamsoftBarcodeReader.iTextResult
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.CValue
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.cValue
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.useContents
import kotlinx.cinterop.value
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import platform.AVFoundation.AVCaptureConnection
import platform.AVFoundation.AVCaptureDevice
import platform.AVFoundation.AVCaptureDeviceInput
import platform.AVFoundation.AVCaptureMetadataOutput
import platform.AVFoundation.AVCaptureMetadataOutputObjectsDelegateProtocol
import platform.AVFoundation.AVCaptureOutput
import platform.AVFoundation.AVCaptureSession
import platform.AVFoundation.AVCaptureVideoDataOutput
import platform.AVFoundation.AVCaptureVideoDataOutputSampleBufferDelegateProtocol
import platform.AVFoundation.AVCaptureVideoOrientationLandscapeLeft
import platform.AVFoundation.AVCaptureVideoOrientationLandscapeRight
import platform.AVFoundation.AVCaptureVideoOrientationPortrait
import platform.AVFoundation.AVCaptureVideoOrientationPortraitUpsideDown
import platform.AVFoundation.AVCaptureVideoPreviewLayer
import platform.AVFoundation.AVLayerVideoGravityResizeAspectFill
import platform.AVFoundation.AVMediaTypeVideo
import platform.AVFoundation.AVMetadataMachineReadableCodeObject
import platform.AVFoundation.AVMetadataObjectType
import platform.AudioToolbox.AudioServicesPlaySystemSound
import platform.CoreGraphics.CGRect
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGRectZero
import platform.CoreImage.CIContext
import platform.CoreImage.CIImage
import platform.CoreImage.createCGImage
import platform.CoreMedia.CMSampleBufferGetImageBuffer
import platform.CoreMedia.CMSampleBufferRef
import platform.CoreVideo.CVImageBufferRef
import platform.CoreVideo.CVPixelBufferGetBaseAddress
import platform.CoreVideo.CVPixelBufferGetBytesPerRow
import platform.CoreVideo.CVPixelBufferGetDataSize
import platform.CoreVideo.CVPixelBufferGetHeight
import platform.CoreVideo.CVPixelBufferGetWidth
import platform.CoreVideo.CVPixelBufferLockBaseAddress
import platform.CoreVideo.CVPixelBufferLockFlags
import platform.CoreVideo.CVPixelBufferRef
import platform.CoreVideo.CVPixelBufferUnlockBaseAddress
import platform.Foundation.NSData
import platform.Foundation.NSDate
import platform.Foundation.NSError
import platform.Foundation.NSTimeInterval
import platform.Foundation.create
import platform.Foundation.timeIntervalSince1970
import platform.QuartzCore.CALayer
import platform.QuartzCore.CATransaction
import platform.QuartzCore.kCATransactionDisableActions
import platform.UIKit.UIDevice
import platform.UIKit.UIDeviceOrientation
import platform.UIKit.UIImage
import platform.UIKit.UIView
import platform.darwin.NSInteger
import platform.darwin.NSObject
import platform.darwin.dispatch_get_main_queue


@Composable
fun UiScannerView(
    modifier: Modifier = Modifier,
    onScanned: (String) -> Unit
) {
    val coordinator = remember {
        ScannerCameraCoordinator(
            onScanned = onScanned
        )
    }

    DisposableEffect(Unit) {
        val listener = OrientationListener { orientation ->
            coordinator.setCurrentOrientation(orientation)
        }

        listener.register()

        onDispose {
            listener.unregister()
        }
    }

    UIKitView<UIView>(
        modifier = modifier.fillMaxSize(),
        factory = {
            val previewContainer = ScannerPreviewView(coordinator)
            println("Calling prepare")
            coordinator.prepare(previewContainer.layer)
            previewContainer
        },
        properties = UIKitInteropProperties(
            isInteractive = true,
            isNativeAccessibilityEnabled = true,
        )
    )

//    DisposableEffect(Unit) {
//        onDispose {
//            // stop capture
//            coordinator.
//        }
//    }

}

@OptIn(ExperimentalForeignApi::class)
class ScannerPreviewView(private val coordinator: ScannerCameraCoordinator): UIView(frame = cValue { CGRectZero }) {
    @OptIn(ExperimentalForeignApi::class)
    override fun layoutSubviews() {
        super.layoutSubviews()
        CATransaction.begin()
        CATransaction.setValue(true, kCATransactionDisableActions)

        layer.setFrame(frame)
        coordinator.setFrame(frame)
        CATransaction.commit()
    }
}

@OptIn(ExperimentalForeignApi::class)
class ScannerCameraCoordinator(
    val onScanned: (String) -> Unit
): AVCaptureVideoDataOutputSampleBufferDelegateProtocol, DBRLicenseVerificationListenerProtocol, NSObject() {

    private var previewLayer: AVCaptureVideoPreviewLayer? = null
    lateinit var captureSession: AVCaptureSession
    lateinit var barcodeReader: DynamsoftBarcodeReader
    var lastTime: Long = 0

    @OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
    fun prepare(layer: CALayer) {
        DynamsoftBarcodeReader.initLicense("DLS2eyJvcmdhbml6YXRpb25JRCI6IjIwMDAwMSJ9", this)
        barcodeReader = DynamsoftBarcodeReader()
        captureSession = AVCaptureSession()
        val device = AVCaptureDevice.defaultDeviceWithMediaType(AVMediaTypeVideo)
        if (device == null) {
            println("Device has no camera")
            return
        }

        println("Initializing video input")
        val videoInput = memScoped {
            val error: ObjCObjectVar<NSError?> = alloc<ObjCObjectVar<NSError?>>()
            val videoInput = AVCaptureDeviceInput(device = device, error = error.ptr)
            if (error.value != null) {
                println(error.value)
                null
            } else {
                videoInput
            }
        }

        println("Adding video input")
        if (videoInput != null && captureSession.canAddInput(videoInput)) {
            captureSession.addInput(videoInput)
        } else {
            println("Could not add input")
            return
        }

        val videoDataOutput = AVCaptureVideoDataOutput()

        println("Adding video output")
        if (captureSession.canAddOutput(videoDataOutput)) {
            captureSession.addOutput(videoDataOutput)
            val map = HashMap<Any?, Any>()
            map.put(
                platform.CoreVideo.kCVPixelBufferPixelFormatTypeKey,
                platform.CoreVideo.kCVPixelFormatType_32BGRA
            )
            videoDataOutput.videoSettings = map
            videoDataOutput.setSampleBufferDelegate(this, queue = dispatch_get_main_queue())
            //metadataOutput.setMetadataObjectsDelegate(this, queue = dispatch_get_main_queue())
        } else {
            println("Could not add output")
            return
        }

        println("Adding preview layer")
        previewLayer = AVCaptureVideoPreviewLayer(session = captureSession).also {
            it.frame = layer.bounds
            it.videoGravity = AVLayerVideoGravityResizeAspectFill
            println("Set orientation")
            setCurrentOrientation(newOrientation = UIDevice.currentDevice.orientation)
            println("Adding sublayer")
            layer.bounds.useContents {
                println("Bounds: ${this.size.width}x${this.size.height}")

            }
            layer.frame.useContents {
                println("Frame: ${this.size.width}x${this.size.height}")
            }
            layer.addSublayer(it)
        }

        println("Launching capture session")
        GlobalScope.launch(Dispatchers.Default) {
            captureSession.startRunning()
        }
    }


    fun setCurrentOrientation(newOrientation: UIDeviceOrientation) {
        when (newOrientation) {
            UIDeviceOrientation.UIDeviceOrientationLandscapeLeft ->
                previewLayer?.connection?.videoOrientation = AVCaptureVideoOrientationLandscapeRight

            UIDeviceOrientation.UIDeviceOrientationLandscapeRight ->
                previewLayer?.connection?.videoOrientation = AVCaptureVideoOrientationLandscapeLeft

            UIDeviceOrientation.UIDeviceOrientationPortrait ->
                previewLayer?.connection?.videoOrientation = AVCaptureVideoOrientationPortrait

            UIDeviceOrientation.UIDeviceOrientationPortraitUpsideDown ->
                previewLayer?.connection?.videoOrientation =
                    AVCaptureVideoOrientationPortraitUpsideDown

            else ->
                previewLayer?.connection?.videoOrientation = AVCaptureVideoOrientationPortrait
        }
    }

    override fun captureOutput(
        output: AVCaptureOutput,
        didOutputSampleBuffer: CMSampleBufferRef?,
        fromConnection: AVCaptureConnection
    ) {
        println("delegate")
        println("interval")
        val interval = NSDate().timeIntervalSince1970*1000 - lastTime
        println(interval)
        if (interval > 1000) {
            println("decode")
            val imageBuffer: CVImageBufferRef? = CMSampleBufferGetImageBuffer(didOutputSampleBuffer)
            val ciImage = platform.CoreImage.CIImage(cVPixelBuffer = imageBuffer)
            val cgImage = CIContext().createCGImage(ciImage, ciImage.extent)
            var image = UIImage(cgImage)

            val result = barcodeReader.decodeImage(image, null)
            if (result != null) {
                println("result length: ")
                println(result.size)
                if (result.isNotEmpty()) {
                    val textResult: iTextResult = result[0] as iTextResult
                    textResult.barcodeText?.let { onFound(it) }
                }
            } else {
                println("result is null")
            }
            lastTime = (NSDate().timeIntervalSince1970*1000).toLong()
        }
    }

    fun onFound(code: String) {
        println(code)
        onScanned(code)
    }

    fun setFrame(rect: CValue<CGRect>) {
        previewLayer?.setFrame(rect)
    }

    override fun DBRLicenseVerificationCallback(isSuccess: Boolean, error: NSError?) {
        println("LicenseVerificationCallback")
        println(isSuccess)
    }
}
   