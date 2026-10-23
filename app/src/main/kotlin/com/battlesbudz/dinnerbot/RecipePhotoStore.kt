package com.battlesbudz.dinnerbot

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.battlesbudz.dinnerbot.core.model.RecipePhoto
import com.battlesbudz.dinnerbot.core.model.RecipePhotos
import java.io.File
import java.io.FileOutputStream

/**
 * Recipe photo files (Epic issue #10): the household's photos live in
 * app-local storage under files/recipe_photos, one JPEG per recipe,
 * named by [RecipePhotos.fileNameFor]. The database holds only the
 * reference and the credit; this object owns the bytes. Nothing here
 * touches the network, no photo is bundled with the app, and importing
 * a backup neither creates nor deletes photo files.
 */
object RecipePhotoStore {

    /** The folder holding every recipe photo file. */
    fun directory(context: Context): File =
        File(context.filesDir, RecipePhotos.DIRECTORY_NAME).apply { mkdirs() }

    /** The file a photo record points at (it may not exist). */
    fun fileFor(context: Context, photo: RecipePhoto): File =
        File(directory(context), photo.fileName)

    /**
     * Copy a picked photo into app-local storage as this recipe's
     * photo, downscaled to a sane display size and stored as JPEG.
     * Returns the record to persist, or null when the picked image
     * could not be read or written. Attaching again replaces the old
     * photo: the file name is the same, so nothing orphans.
     */
    fun savePickedPhoto(
        context: Context,
        recipeId: String,
        picked: Uri,
        credit: String,
        nowMillis: Long,
    ): RecipePhoto? {
        val bitmap = decodeDownscaled(context, picked) ?: return null
        val fileName = RecipePhotos.fileNameFor(recipeId)
        val target = File(directory(context), fileName)
        val written = runCatching {
            FileOutputStream(target).use { out ->
                check(bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)) {
                    "photo could not be written"
                }
            }
        }.isSuccess
        if (!written) return null
        return RecipePhoto(
            recipeId = recipeId,
            fileName = fileName,
            credit = RecipePhotos.creditOrDefault(credit),
            updatedAtMillis = nowMillis,
        )
    }

    /** Delete a recipe's photo file, if present. */
    fun deleteFile(context: Context, photo: RecipePhoto) {
        runCatching { fileFor(context, photo).delete() }
    }

    private fun decodeDownscaled(context: Context, picked: Uri): Bitmap? {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(picked)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= MAX_DIMENSION ||
            bounds.outHeight / (sample * 2) >= MAX_DIMENSION
        ) {
            sample *= 2
        }
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        val decoded = resolver.openInputStream(picked)?.use {
            BitmapFactory.decodeStream(it, null, options)
        } ?: return null
        val largest = maxOf(decoded.width, decoded.height)
        if (largest <= MAX_DIMENSION) return decoded
        val scale = MAX_DIMENSION.toDouble() / largest
        return Bitmap.createScaledBitmap(
            decoded,
            (decoded.width * scale).toInt().coerceAtLeast(1),
            (decoded.height * scale).toInt().coerceAtLeast(1),
            true,
        )
    }

    /** Longest edge a stored photo keeps: plenty for a phone screen, kind to storage. */
    private const val MAX_DIMENSION = 1600
    private const val JPEG_QUALITY = 85
}
