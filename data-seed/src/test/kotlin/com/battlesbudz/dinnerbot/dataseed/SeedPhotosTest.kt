package com.battlesbudz.dinnerbot.dataseed

import com.battlesbudz.dinnerbot.core.model.RecipesFile
import com.battlesbudz.dinnerbot.core.model.SeedPhotosFile
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File

/**
 * Phase C2: every starter recipe ships with a bundled photo, checked
 * against the REAL canonical files in repo-root seed-data/ — the same
 * files the app bundles as assets. The manifest (photo-credits.json)
 * must cover the library exactly, every named asset must exist on
 * disk as a real WebP image of a sane size, and every photo must
 * carry the credit it will show in the app (stock photos openly
 * licensed with their source page recorded; the rest AI generated,
 * and the credit says so).
 */
class SeedPhotosTest {
    private val json = Json { ignoreUnknownKeys = true }

    private fun seedFile(name: String): File {
        var dir: File? = File(System.getProperty("user.dir")).absoluteFile
        while (dir != null) {
            val candidate = File(dir, "seed-data/$name")
            if (candidate.isFile) return candidate
            dir = dir.parentFile
        }
        fail("could not locate seed-data/$name from ${System.getProperty("user.dir")}")
        error("unreachable")
    }

    private fun seedDir(): File = seedFile("recipes.json").parentFile

    @Test fun everyStarterRecipeHasOneBundledPhoto() {
        val recipes = json.decodeFromString<RecipesFile>(seedFile("recipes.json").readText())
        val credits = json.decodeFromString<SeedPhotosFile>(seedFile("photo-credits.json").readText())

        val recipeIds = recipes.recipes.map { it.id }.toSet()
        val creditedIds = credits.photos.map { it.recipeId }
        assertEquals("photo manifest must not repeat a recipe", creditedIds.size, creditedIds.toSet().size)
        assertEquals("photo manifest must cover the library exactly", recipeIds, creditedIds.toSet())
    }

    @Test fun bundledPhotoFilesExistAndAreSaneWebpImages() {
        val credits = json.decodeFromString<SeedPhotosFile>(seedFile("photo-credits.json").readText())
        var totalBytes = 0L
        for (photo in credits.photos) {
            val file = File(seedDir(), photo.file)
            assertTrue("missing bundled photo ${photo.file} for ${photo.recipeId}", file.isFile)
            val bytes = file.readBytes()
            assertTrue(
                "bundled photo for ${photo.recipeId} is suspiciously small (${bytes.size} bytes)",
                bytes.size > 5_000,
            )
            assertTrue(
                "bundled photo for ${photo.recipeId} is too large (${bytes.size} bytes)",
                bytes.size < 400_000,
            )
            // WebP container: RIFF header with the WEBP fourcc.
            assertTrue(
                "bundled photo for ${photo.recipeId} is not a WebP image",
                bytes.size > 12 &&
                    bytes.copyOfRange(0, 4).decodeToString() == "RIFF" &&
                    bytes.copyOfRange(8, 12).decodeToString() == "WEBP",
            )
            totalBytes += bytes.size
        }
        // The whole library's photos stay a modest part of the APK.
        assertTrue("photo payload grew past 15 MB: $totalBytes", totalBytes < 15_000_000)
    }

    @Test fun photoCreditsAreCompleteAndHonest() {
        val credits = json.decodeFromString<SeedPhotosFile>(seedFile("photo-credits.json").readText())
        for (photo in credits.photos) {
            assertTrue("photo for ${photo.recipeId} has no credit", photo.credit.isNotBlank())
            when (photo.source) {
                "stock" -> {
                    assertTrue(
                        "stock photo for ${photo.recipeId} must record its page",
                        !photo.sourceUrl.isNullOrBlank(),
                    )
                    assertTrue(
                        "stock photo for ${photo.recipeId} must record its license",
                        !photo.license.isNullOrBlank(),
                    )
                }
                "ai" -> assertTrue(
                    "generated photo for ${photo.recipeId} must say so in its credit",
                    photo.credit.contains("AI", ignoreCase = true) ||
                        photo.credit.contains("generated", ignoreCase = true),
                )
                else -> fail("photo for ${photo.recipeId} has unknown source '${photo.source}'")
            }
        }
    }
}
