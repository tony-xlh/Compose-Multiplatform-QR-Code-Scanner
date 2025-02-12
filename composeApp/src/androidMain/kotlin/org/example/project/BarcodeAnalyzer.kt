package org.example.project

import android.annotation.SuppressLint
import android.content.Context
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.dynamsoft.core.basic_structures.EnumImagePixelFormat
import com.dynamsoft.core.basic_structures.ImageData
import com.dynamsoft.cvr.CaptureVisionRouter
import com.dynamsoft.cvr.EnumPresetTemplate


class BarcodeAnalyzer(
    private val onScanned: (String) -> Unit,
    private val context: Context,
) : ImageAnalysis.Analyzer {

    private val router = CaptureVisionRouter(context)

    @SuppressLint("UnsafeOptInUsageError")
    override fun analyze(imageProxy: ImageProxy) {
        println("analyse")
        imageProxy.image?.let { image ->
            val buffer = image.planes[0].buffer
            val nRowStride = image.planes[0].rowStride
            val nPixelStride = image.planes[0].pixelStride
            val length = buffer.remaining()
            val bytes = ByteArray(length)
            buffer[bytes]
            val imageData = ImageData()
            imageData.bytes = bytes
            imageData.width = image.width
            imageData.height = image.height
            imageData.stride = nRowStride * nPixelStride
            imageData.format = EnumImagePixelFormat.IPF_NV21
            val capturedResult = router.capture(imageData,EnumPresetTemplate.PT_READ_SINGLE_BARCODE)
            if (capturedResult.decodedBarcodesResult != null) {
                if (capturedResult.decodedBarcodesResult!!.items.isNotEmpty()) {
                    val result = capturedResult.decodedBarcodesResult!!.items[0]
                    onScanned(result.text)
                }
            }
        }
        imageProxy.close()
    }
}