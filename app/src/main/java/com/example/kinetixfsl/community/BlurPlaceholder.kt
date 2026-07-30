package com.example.kinetixfsl.community

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale

/**
 * The instant-paint layer: decodes the ~1 KB base64 JPEG stored on the post and
 * draws it stretched to fill.
 *
 * It's about 24px on its longest edge, so scaling it up to a full-width media
 * slot produces exactly the soft blur you want behind a loading photo — no blur
 * filter needed, and none is used (RenderEffect needs API 31 and this app
 * supports 29).
 *
 * Draw this *behind* the real image. AsyncImage is transparent until it
 * resolves, so the blur shows through and is covered the moment the real file
 * arrives.
 */
@Composable
internal fun BlurPlaceholder(
    data: String?,
    modifier: Modifier = Modifier,
) {
    if (data.isNullOrBlank()) return

    val bitmap: ImageBitmap? = remember(data) {
        try {
            val bytes = Base64.decode(data, Base64.NO_WRAP)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
        } catch (_: Exception) {
            // A malformed placeholder just means no placeholder.
            null
        }
    }

    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier,
        )
    }
}
