package com.example.kinetixfsl.community.home

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * A full-screen photo cropper — pinch to zoom, drag to reposition, and the part
 * inside the frame is what gets saved, the way Facebook lets you place a photo.
 *
 * [aspectRatio] is width/height of the crop frame (a banner is wide, a profile
 * picture is 1:1). [outputWidth] caps the exported image so uploads stay small.
 */
@Composable
fun ImageCropScreen(
    imageUri: Uri,
    aspectRatio: Float,
    title: String,
    outputWidth: Int,
    circle: Boolean,
    onCancel: () -> Unit,
    onDone: (Bitmap) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var source by remember(imageUri) { mutableStateOf<Bitmap?>(null) }
    var areaSize by remember { mutableStateOf(IntSize.Zero) }
    var scale by remember(imageUri) { mutableStateOf(1f) }
    var offset by remember(imageUri) { mutableStateOf(Offset.Zero) }

    BackHandler(onBack = onCancel)

    LaunchedEffect(imageUri) {
        source = withContext(Dispatchers.IO) { loadOrientedBitmap(context, imageUri) }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .statusBarsPadding(),
    ) {
        val src = source
        if (src == null) {
            CircularProgressIndicator(
                color = Color.White,
                modifier = Modifier.align(Alignment.Center),
            )
        } else {
            val image = remember(src) { src.asImageBitmap() }

            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .onSizeChanged { areaSize = it }
                    .pointerInput(src) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(1f, 6f)
                            offset += pan
                        }
                    },
            ) {
                val g = cropGeometry(
                    areaW = size.width,
                    areaH = size.height,
                    srcW = src.width,
                    srcH = src.height,
                    aspect = aspectRatio,
                    scale = scale,
                    offset = offset,
                )

                drawImage(
                    image = image,
                    srcOffset = IntOffset.Zero,
                    srcSize = IntSize(src.width, src.height),
                    dstOffset = IntOffset(g.imgLeft.roundToInt(), g.imgTop.roundToInt()),
                    dstSize = IntSize(
                        (src.width * g.effScale).roundToInt(),
                        (src.height * g.effScale).roundToInt(),
                    ),
                )

                // Dim everything outside the crop frame.
                val dim = Color.Black.copy(alpha = 0.55f)
                drawRect(dim, topLeft = Offset(0f, 0f), size = Size(size.width, g.fy))
                drawRect(dim, topLeft = Offset(0f, g.fy + g.fh), size = Size(size.width, size.height - (g.fy + g.fh)))
                drawRect(dim, topLeft = Offset(0f, g.fy), size = Size(g.fx, g.fh))
                drawRect(dim, topLeft = Offset(g.fx + g.fw, g.fy), size = Size(size.width - (g.fx + g.fw), g.fh))

                // Frame outline (a circle guide for a round avatar).
                if (circle) {
                    drawCircle(
                        color = Color.White,
                        radius = g.fw / 2f,
                        center = Offset(g.fx + g.fw / 2f, g.fy + g.fh / 2f),
                        style = Stroke(width = 2.dp.toPx()),
                    )
                } else {
                    drawRect(
                        color = Color.White,
                        topLeft = Offset(g.fx, g.fy),
                        size = Size(g.fw, g.fh),
                        style = Stroke(width = 2.dp.toPx()),
                    )
                }
            }
        }

        // ---- Top bar: Cancel / title / Save ----
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "Cancel",
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clickable(onClick = onCancel),
            )
            Text(text = title, color = Color.White, fontWeight = FontWeight.Bold)
            Text(
                text = "Save",
                color = if (src != null) Color.White else Color.White.copy(alpha = 0.4f),
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable(enabled = src != null) {
                    val bmp = src ?: return@clickable
                    val out = cropToBitmap(
                        source = bmp,
                        areaW = areaSize.width.toFloat(),
                        areaH = areaSize.height.toFloat(),
                        aspect = aspectRatio,
                        scale = scale,
                        offset = offset,
                        outputWidth = outputWidth,
                    )
                    onDone(out)
                },
            )
        }

        Text(
            text = "Pinch to zoom · drag to reposition",
            color = Color.White.copy(alpha = 0.8f),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 28.dp),
        )
    }
}

/** Frame + placement geometry, shared by the on-screen draw and the export. */
private class Geom(
    val fx: Float, val fy: Float, val fw: Float, val fh: Float,
    val imgLeft: Float, val imgTop: Float, val effScale: Float,
)

private fun cropGeometry(
    areaW: Float,
    areaH: Float,
    srcW: Int,
    srcH: Int,
    aspect: Float,
    scale: Float,
    offset: Offset,
): Geom {
    if (areaW <= 0f || areaH <= 0f) return Geom(0f, 0f, 0f, 0f, 0f, 0f, 1f)

    var fw = areaW * 0.92f
    var fh = fw / aspect
    if (fh > areaH * 0.92f) {
        fh = areaH * 0.92f
        fw = fh * aspect
    }
    val fx = (areaW - fw) / 2f
    val fy = (areaH - fh) / 2f

    // Cover-fit the frame, then apply the user's zoom.
    val base = max(fw / srcW, fh / srcH)
    val eff = base * scale
    val dw = srcW * eff
    val dh = srcH * eff

    val frameCx = fx + fw / 2f
    val frameCy = fy + fh / 2f
    var imgLeft = frameCx + offset.x - dw / 2f
    var imgTop = frameCy + offset.y - dh / 2f

    // Keep the image covering the frame — no empty gaps at the edges. When the
    // image is exactly the frame's size (a square photo in a square frame),
    // floating-point rounding can make the clamp's min exceed its max, which
    // coerceIn rejects — so only clamp when there's real slack, else center.
    imgLeft = if (dw > fw) imgLeft.coerceIn(fx + fw - dw, fx) else fx + (fw - dw) / 2f
    imgTop = if (dh > fh) imgTop.coerceIn(fy + fh - dh, fy) else fy + (fh - dh) / 2f

    return Geom(fx, fy, fw, fh, imgLeft, imgTop, eff)
}

private fun cropToBitmap(
    source: Bitmap,
    areaW: Float,
    areaH: Float,
    aspect: Float,
    scale: Float,
    offset: Offset,
    outputWidth: Int,
): Bitmap {
    val g = cropGeometry(areaW, areaH, source.width, source.height, aspect, scale, offset)

    // The frame, expressed in the source image's own pixels.
    val left = ((g.fx - g.imgLeft) / g.effScale).roundToInt().coerceIn(0, source.width - 1)
    val top = ((g.fy - g.imgTop) / g.effScale).roundToInt().coerceIn(0, source.height - 1)
    val w = (g.fw / g.effScale).roundToInt().coerceIn(1, source.width - left)
    val h = (g.fh / g.effScale).roundToInt().coerceIn(1, source.height - top)

    val cropped = Bitmap.createBitmap(source, left, top, w, h)

    // Scale down to the output cap so uploads stay small.
    if (cropped.width <= outputWidth) return cropped
    val outH = (outputWidth / aspect).roundToInt().coerceAtLeast(1)
    return Bitmap.createScaledBitmap(cropped, outputWidth, outH, true)
}

/** Decodes [uri] downscaled to a sane size, applying its EXIF rotation. */
private fun loadOrientedBitmap(context: android.content.Context, uri: Uri): Bitmap? {
    return try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, bounds)
        }
        val maxDim = 1600
        var sample = 1
        while (bounds.outWidth / sample > maxDim || bounds.outHeight / sample > maxDim) {
            sample *= 2
        }

        val decoded = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: return null

        val rotation = context.contentResolver.openInputStream(uri)?.use { stream ->
            when (ExifInterface(stream).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL,
            )) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        } ?: 0f

        if (rotation == 0f) {
            decoded
        } else {
            val m = Matrix().apply { postRotate(rotation) }
            Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, m, true)
        }
    } catch (_: Exception) {
        null
    }
}
