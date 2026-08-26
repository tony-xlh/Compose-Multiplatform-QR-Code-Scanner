package org.example.project

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitInteropProperties
import androidx.compose.ui.viewinterop.UIKitView
import dynamsoft.DSBarcodeResultItem
import dynamsoft.DSCaptureVisionRouter
import dynamsoft.DSLicenseManager
import dynamsoft.DSLicenseVerificationListenerProtocol
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.CValue
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.cValue
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import platform.AVFoundation.AVCaptureConnection
import platform.AVFoundation.AVCaptureDevice
import platform.AVFoundation.AVCaptureDeviceInput
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
import platform.CoreGraphics.CGRect
import platform.CoreGraphics.CGRectZero
import platform.CoreImage.CIContext
import platform.CoreImage.CIImage
import platform.CoreImage.createCGImage
import platform.CoreMedia.CMSampleBufferGetImageBuffer
import platform.CoreMedia.CMSampleBufferRef
import platform.CoreVideo.CVImageBufferRef
import platform.Foundation.NSDate
import platform.Foundation.NSError
import platform.Foundation.timeIntervalSince1970
import platform.QuartzCore.CALayer
import platform.QuartzCore.CATransaction
import platform.QuartzCore.kCATransactionDisableActions
import platform.UIKit.UIDevice
import platform.UIKit.UIDeviceOrientation
import platform.UIKit.UIImage
import platform.UIKit.UIView
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import platform.darwin.dispatch_queue_create


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
            coordinator.stop()
        }
    }

    UIKitView<UIView>(
        modifier = modifier.fillMaxSize(),
        factory = {
            val previewContainer = ScannerPreviewView(coordinator)
            coordinator.prepare(previewContainer.layer)
            previewContainer
        },
        properties = UIKitInteropProperties(
            isInteractive = true,
            isNativeAccessibilityEnabled = true,
        )
    )
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
): AVCaptureVideoDataOutputSampleBufferDelegateProtocol, DSLicenseVerificationListenerProtocol, NSObject() {

    private var previewLayer: AVCaptureVideoPreviewLayer? = null
    lateinit var captureSession: AVCaptureSession
    // CaptureVisionRouter is the entry point of the Dynamsoft Capture Vision core API.
    // It hosts the barcode engine of Dynamsoft Barcode Reader.
    lateinit var router: DSCaptureVisionRouter
    var lastTime: Long = 0

    // Decode frames on a dedicated serial queue to keep the main thread responsive.
    private val decodeQueue = dispatch_queue_create("org.example.project.barcodeDecode", null)

    @OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
    fun prepare(layer: CALayer) {
        // Public trial license. A network connection is required for the first online verification.
        // Request a longer trial key at https://www.dynamsoft.com/customer/license/trialLicense/?product=dbr
        DSLicenseManager.initLicense(
            "DLS2eyJoYW5kc2hha2VDb2RlIjoiMjAwMDAxLTE2NDk4Mjk3OTI2MzUiLCJvcmdhbml6YXRpb25JRCI6IjIwMDAwMSIsInNlc3Npb25QYXNzd29yZCI6IndTcGR6Vm05WDJrcEQ5YUoifQ==",
            this
        )
        router = DSCaptureVisionRouter()
        captureSession = AVCaptureSession()
        val device = AVCaptureDevice.defaultDeviceWithMediaType(AVMediaTypeVideo)
        if (device == null) {
            println("Device has no camera")
            return
        }

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

        if (videoInput != null && captureSession.canAddInput(videoInput)) {
            captureSession.addInput(videoInput)
        } else {
            println("Could not add input")
            return
        }

        val videoDataOutput = AVCaptureVideoDataOutput()

        if (captureSession.canAddOutput(videoDataOutput)) {
            captureSession.addOutput(videoDataOutput)
            videoDataOutput.alwaysDiscardsLateVideoFrames = true
            val map = HashMap<Any?, Any>()
            map.put(
                platform.CoreVideo.kCVPixelBufferPixelFormatTypeKey,
                platform.CoreVideo.kCVPixelFormatType_32BGRA
            )
            videoDataOutput.videoSettings = map
            videoDataOutput.setSampleBufferDelegate(this, queue = decodeQueue)
            // Deliver portrait frames so that results match the portrait preview.
            videoDataOutput.connectionWithMediaType(AVMediaTypeVideo)?.videoOrientation = AVCaptureVideoOrientationPortrait
        } else {
            println("Could not add output")
            return
        }

        previewLayer = AVCaptureVideoPreviewLayer(session = captureSession).also {
            it.frame = layer.bounds
            it.videoGravity = AVLayerVideoGravityResizeAspectFill
            setCurrentOrientation(newOrientation = UIDevice.currentDevice.orientation)
            layer.addSublayer(it)
        }

        GlobalScope.launch(Dispatchers.Default) {
            captureSession.startRunning()
        }
    }

    fun stop() {
        if (::captureSession.isInitialized && captureSession.isRunning()) {
            GlobalScope.launch(Dispatchers.Default) {
                captureSession.stopRunning()
            }
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
        // Decode at most one frame per second.
        val now = (NSDate().timeIntervalSince1970 * 1000).toLong()
        if (now - lastTime < 1000) return
        lastTime = now

        val imageBuffer: CVImageBufferRef = CMSampleBufferGetImageBuffer(didOutputSampleBuffer) ?: return
        val ciImage = CIImage(cVPixelBuffer = imageBuffer)
        val cgImage = CIContext().createCGImage(ciImage, ciImage.extent) ?: return
        val image = UIImage(cgImage)

        // Read barcodes with the built-in "ReadBarcodes_Default" template.
        val capturedResult = router.captureFromImage(image, "ReadBarcodes_Default") ?: return
        if (capturedResult.errorCode != 0L) {
            println("Decode failed: ${capturedResult.errorMessage}")
            return
        }
        val text = (capturedResult.decodedBarcodesResult?.items?.firstOrNull() as? DSBarcodeResultItem)?.text
        if (text != null) {
            // Report results on the main thread because they update Compose state.
            dispatch_async(dispatch_get_main_queue()) {
                onScanned(text)
            }
        }
    }

    fun setFrame(rect: CValue<CGRect>) {
        previewLayer?.setFrame(rect)
    }

    override fun onLicenseVerified(isSuccess: Boolean, error: NSError?) {
        println("License verified: $isSuccess")
    }
}
