package com.battlesbudz.dinnerbot.feature.planner

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * A night card's thumbnail of the household's photo for that dinner
 * (Epic issue #10). Decoded off the main thread, downsampled for the
 * thumbnail size; a missing or unreadable file renders nothing, so a
 * night whose dinner has no photo looks exactly as it always has.
 * This mirrors the loader in feature-recipes: the two surfaces each
 * stay inside their own package, per the project's package SOP.
 */
@Composable
internal fun RecipePhotoThumb(
    file: File,
    contentDescription: String?,
    modifier: Modifier = Modifier,
) {
    val bitmap by produceState<ImageBitmap?>(null, file) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(file.path, bounds)
                val options = BitmapFactory.Options().apply {
                    var sample = 1
                    while (bounds.outWidth / (sample * 2) >= 128 &&
                        bounds.outHeight / (sample * 2) >= 128
                    ) {
                        sample *= 2
                    }
                    inSampleSize = sample
                }
                BitmapFactory.decodeFile(file.path, options)?.asImageBitmap()
            }.getOrNull()
        }
    }
    val image = bitmap
    if (image != null) {
        Image(
            bitmap = image,
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = ContentScale.Crop,
        )
    }
}
