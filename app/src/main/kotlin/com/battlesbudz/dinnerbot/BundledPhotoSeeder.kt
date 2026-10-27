package com.battlesbudz.dinnerbot

import android.content.Context
import com.battlesbudz.dinnerbot.core.data.DinnerBotDatabase
import com.battlesbudz.dinnerbot.core.data.SeedDataParser
import com.battlesbudz.dinnerbot.core.data.SeedMapper
import com.battlesbudz.dinnerbot.core.model.RecipePhoto
import com.battlesbudz.dinnerbot.core.model.RecipePhotos
import java.io.File
import java.io.InputStream

/**
 * Bundled starter photos (Phase C2). Every seed recipe ships with a
 * photo asset (seed-data/photos/, credited in
 * seed-data/photo-credits.json). On first install this copies each
 * bundled image into the recipe_photos folder and records it in the
 * database with its credit, so from then on the photo is
 * indistinguishable from a household photo: the ordinary display,
 * replace, and remove flows all apply to it.
 *
 * A marker file records that the one-time copy has run. A photo the
 * user later removes is therefore never restored, and a recipe that
 * already has any photo record (the user's own) is never overwritten.
 * The copy, like all photo storage here, is local only: nothing is
 * uploaded anywhere.
 */
object BundledPhotoSeeder {

    private const val MARKER_NAME = ".bundled-photos-seeded"
    private const val CREDITS_ASSET = "photo-credits.json"

    /**
     * Copy any bundled starter photos not yet recorded. Returns how
     * many photo records were created (0 once the marker exists).
     */
    suspend fun seedIfNeeded(
        context: Context,
        database: DinnerBotDatabase,
        openAsset: (String) -> InputStream,
        nowMillis: Long = System.currentTimeMillis(),
    ): Int {
        val directory = RecipePhotoStore.directory(context)
        val marker = File(directory, MARKER_NAME)
        if (marker.exists()) return 0

        val credits = runCatching {
            val text = openAsset(CREDITS_ASSET).bufferedReader().use { it.readText() }
            SeedDataParser.parsePhotoCredits(text)
        }.getOrDefault(emptyList())
        if (credits.isEmpty()) return 0

        val recipeIds = database.recipeDao().getAll().map { it.id }.toSet()
        val recordedIds = database.recipePhotoDao().getAll().map { it.recipeId }.toSet()
        var inserted = 0
        var failed = 0
        for (credit in credits) {
            if (credit.recipeId !in recipeIds || credit.recipeId in recordedIds) continue
            val fileName = RecipePhotos.fileNameFor(credit.recipeId)
            val target = File(directory, fileName)
            // A file with no record is treated as the user's own work
            // in progress; never overwrite it with the bundled image.
            if (target.exists()) continue
            val copied = runCatching {
                val temp = File(directory, "$fileName.bundled")
                openAsset(credit.file).use { input ->
                    temp.outputStream().use { output -> input.copyTo(output) }
                }
                check(temp.renameTo(target) || (target.delete() && temp.renameTo(target))) {
                    "bundled photo could not be moved into place"
                }
            }.isSuccess
            if (!copied) {
                failed++
                continue
            }
            val photo = RecipePhoto(
                recipeId = credit.recipeId,
                fileName = fileName,
                credit = credit.credit,
                updatedAtMillis = nowMillis,
            )
            database.recipePhotoDao().upsert(with(SeedMapper) { photo.toEntity() })
            inserted++
        }
        // The marker is written only when every bundled photo landed,
        // so a copy that failed is retried on the next start instead of
        // being stranded. User removals after this point are final:
        // the marker keeps the seeder from ever running again.
        if (failed == 0) {
            runCatching { marker.writeText("seeded") }
        }
        return inserted
    }
}
