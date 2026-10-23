package com.battlesbudz.dinnerbot.feature.recipes

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
 * Shows one recipe photo file from app-local storage (Epic issue #10).
 * The bitmap is decoded off the main thread and downsampled toward a
 * display size; a missing or unreadable file renders nothing, so a
 * recipe whose photo file is gone looks exactly like a recipe that
 * never had one.
 */
@Composable
fun RecipePhotoImage(
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
                    while (bounds.outWidth / (sample * 2) >= 512 &&
                        bounds.outHeight / (sample * 2) >= 512
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
