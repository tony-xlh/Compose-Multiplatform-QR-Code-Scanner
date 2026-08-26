package org.example.project

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp

/**
 * A decoded barcode together with its bounding quadrilateral.
 * The corners are normalized to 0..1 in the rotated (display) coordinate space,
 * so they can be mapped directly onto the camera preview. [aspectRatio] is the
 * width/height ratio of the rotated camera frame and is required to undo the
 * normalization when drawing on the preview.
 */
data class BarcodeAnnotation(
    val text: String,
    val corners: List<Offset>,
    val aspectRatio: Float,
)

/**
 * Draws a bounding quadrilateral and the decoded text of each detected barcode
 * on top of the camera preview.
 *
 * The corners are normalized (0..1) in the display coordinate space and mapped
 * onto the preview with the same center-crop scaling that both the CameraX
 * PreviewView (FILL_CENTER) and the AVFoundation preview layer
 * (AVLayerVideoGravityResizeAspectFill) apply.
 */
@Composable
fun BarcodeOverlay(
    annotations: List<BarcodeAnnotation>,
    modifier: Modifier = Modifier,
) {
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = remember {
        TextStyle(color = Color.Green, fontSize = 14.sp)
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        annotations.forEach { annotation ->
            // Both previews center-crop the camera image to fill the view. The
            // normalized corners span an image with the given aspect ratio, so
            // width and height must be scaled separately to avoid distortion.
            val aspectRatio = annotation.aspectRatio
            val scale = maxOf(size.width / aspectRatio, size.height)
            val imageWidth = aspectRatio * scale
            val offsetX = (size.width - imageWidth) / 2f
            val offsetY = (size.height - scale) / 2f
            val corners = annotation.corners.map {
                Offset(offsetX + it.x * imageWidth, offsetY + it.y * scale)
            }
            if (corners.size < 3) return@forEach

            val contour = Path().apply {
                moveTo(corners.first().x, corners.first().y)
                corners.drop(1).forEach { lineTo(it.x, it.y) }
                close()
            }
            drawPath(contour, color = Color.Green.copy(alpha = 0.2f))
            drawPath(contour, color = Color.Green, style = Stroke(width = 4f))

            // Show the decoded text above the top-left corner of the quadrilateral.
            val label = textMeasurer.measure(annotation.text, labelStyle)
            val topLeft = corners.first()
            drawText(
                textLayoutResult = label,
                topLeft = Offset(
                    topLeft.x.coerceIn(0f, size.width - label.size.width),
                    (topLeft.y - label.size.height).coerceAtLeast(0f)
                )
            )
        }
    }
}
