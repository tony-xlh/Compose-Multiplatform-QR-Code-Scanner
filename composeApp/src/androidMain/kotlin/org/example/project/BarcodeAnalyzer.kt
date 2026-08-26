package org.example.project

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.compose.ui.geometry.Offset
import com.dynamsoft.core.basic_structures.EnumImagePixelFormat
import com.dynamsoft.core.basic_structures.ImageData
import com.dynamsoft.cvr.CaptureVisionRouter
import com.dynamsoft.cvr.EnumPresetTemplate
import com.dynamsoft.dbr.BarcodeResultItem

class BarcodeAnalyzer(
    private val onScanned: (String) -> Unit,
    private val onBarcodesUpdated: (List<BarcodeAnnotation>) -> Unit,
    private val context: Context,
) : ImageAnalysis.Analyzer {

    // CaptureVisionRouter is the entry point of the Dynamsoft Capture Vision core API.
    // It hosts the barcode engine of Dynamsoft Barcode Reader.
    private val router = CaptureVisionRouter(context)

    @SuppressLint("UnsafeOptInUsageError")
    override fun analyze(imageProxy: ImageProxy) {
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
            // Read barcodes with the built-in "ReadBarcodes" preset template.
            val capturedResult = router.capture(imageData, EnumPresetTemplate.PT_READ_BARCODES)
            if (capturedResult.errorCode != 0) {
                Log.e("DBR", capturedResult.errorMessage)
            } else {
                val items = capturedResult.decodedBarcodesResult?.items.orEmpty()
                items.firstOrNull()?.let {
                    onScanned(it.text)
                }
                // Report every barcode of the current frame, so the overlay can
                // draw a bounding quadrilateral for each of them.
                val rotatedWidth = if (imageProxy.imageInfo.rotationDegrees % 180 == 0) image.width else image.height
                val rotatedHeight = if (imageProxy.imageInfo.rotationDegrees % 180 == 0) image.height else image.width
                onBarcodesUpdated(
                    items.mapNotNull { item ->
                        normalizedCorners(
                            item,
                            rotatedWidth,
                            rotatedHeight,
                            imageProxy.imageInfo.rotationDegrees
                        )?.let { corners ->
                            BarcodeAnnotation(
                                item.text,
                                corners,
                                rotatedWidth.toFloat() / rotatedHeight
                            )
                        }
                    }
                )
            }
        }
        imageProxy.close()
    }

    /**
     * Converts the location of a barcode (reported in the unrotated buffer space)
     * into normalized corners in the display coordinate space.
     */
    private fun normalizedCorners(
        item: BarcodeResultItem,
        rotatedWidth: Int,
        rotatedHeight: Int,
        rotationDegrees: Int,
    ): List<Offset>? {
        val points = item.location?.points ?: return null
        val bufferWidth = if (rotationDegrees % 180 == 0) rotatedWidth else rotatedHeight
        val bufferHeight = if (rotationDegrees % 180 == 0) rotatedHeight else rotatedWidth
        return points.map { point ->
            // Normalize in the unrotated buffer space first, then apply the
            // rotation so the corner lands in the display coordinate space.
            val u = point.x.toFloat() / bufferWidth
            val v = point.y.toFloat() / bufferHeight
            when (rotationDegrees) {
                90 -> Offset(1f - v, u)
                180 -> Offset(1f - u, 1f - v)
                270 -> Offset(v, 1f - u)
                else -> Offset(u, v)
            }
        }
    }
}