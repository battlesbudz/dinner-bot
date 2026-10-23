package com.battlesbudz.dinnerbot

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import com.battlesbudz.dinnerbot.core.model.RecipePhoto
import com.battlesbudz.dinnerbot.core.model.RecipePhotos
import java.io.File
import java.io.FileOutputStream

/**
 * Recipe photo files (Epic issue #10): the household's photos live in
 * app-local storage under recipe_photos, one JPEG per recipe, named by
 * [RecipePhotos.fileNameFor]. The database holds only the reference
 * and the credit; this object owns the bytes.
 *
 * The folder sits in the app's no-backup files directory on purpose:
 * Android Auto Backup never uploads it, so a photo can never leave
 * this phone through a backup or reappear on another device — the
 * recipe screens say the photo is stored on this phone only, and this
 * is what makes that true. Nothing here touches the network, no photo
 * is bundled with the app, and importing a backup neither creates nor
 * deletes photo files.
 */
object RecipePhotoStore {

    /** The folder holding every recipe photo file. */
    fun directory(context: Context): File =
        File(context.noBackupFilesDir, RecipePhotos.DIRECTORY_NAME).apply { mkdirs() }

    /** The file a photo record points at (it may not exist). */
    fun fileFor(context: Context, photo: RecipePhoto): File =
        File(directory(context), photo.fileName)

    /**
     * Copy a picked photo into app-local storage as this recipe's
     * photo: EXIF orientation applied (a portrait shot stays portrait),
     * downscaled to a sane display size, stored as JPEG. Returns the
     * record to persist, or null when the picked image could not be
     * read or written.
     *
     * The new image is written to a temporary file first and moved
     * over the recipe's file only once it is complete, so a failed
     * replacement leaves the previous photo exactly as it was.
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
        val dir = directory(context)
        val target = File(dir, fileName)
        val temp = File(dir, "$fileName.tmp")
        val written = runCatching {
            FileOutputStream(temp).use { out ->
                check(bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)) {
                    "photo could not be written"
                }
            }
            check(temp.renameTo(target) || (target.delete() && temp.renameTo(target))) {
                "photo could not be moved into place"
            }
        }.isSuccess
        if (!written) {
            runCatching { temp.delete() }
            return null
        }
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
        // BitmapFactory ignores EXIF orientation and the JPEG we write
        // carries none, so apply the picked image's orientation to the
        // pixels themselves before anything is saved.
        val oriented = applyExifOrientation(context, picked, decoded)
        val largest = maxOf(oriented.width, oriented.height)
        if (largest <= MAX_DIMENSION) return oriented
        val scale = MAX_DIMENSION.toDouble() / largest
        return Bitmap.createScaledBitmap(
            oriented,
            (oriented.width * scale).toInt().coerceAtLeast(1),
            (oriented.height * scale).toInt().coerceAtLeast(1),
            true,
        )
    }

    /** Rotate/flip [bitmap] per the picked image's EXIF orientation; unchanged when there is none. */
    private fun applyExifOrientation(context: Context, picked: Uri, bitmap: Bitmap): Bitmap {
        val orientation = runCatching {
            context.contentResolver.openInputStream(picked)?.use { stream ->
                ExifInterface(stream).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL,
                )
            }
        }.getOrNull() ?: return bitmap
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postRotate(90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postRotate(270f)
                matrix.postScale(-1f, 1f)
            }
            else -> return bitmap
        }
        return runCatching {
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        }.getOrDefault(bitmap)
    }

    /** Longest edge a stored photo keeps: plenty for a phone screen, kind to storage. */
    private const val MAX_DIMENSION = 1600
    private const val JPEG_QUALITY = 85
}
