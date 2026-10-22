package com.battlesbudz.dinnerbot.dataseed

import com.battlesbudz.dinnerbot.core.model.PriceBookFile
import com.battlesbudz.dinnerbot.core.model.RecipesFile
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File

/**
 * Validates the REAL canonical seed files in repo-root seed-data/ — the same
 * files the app bundles as assets. This is the data-seed validator run as a test.
 */
class SeedDataFilesTest {
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

    @Test fun canonicalSeedDataIsValid() {
        val priceBook = json.decodeFromString<PriceBookFile>(seedFile("pricebook.json").readText())
        val recipes = json.decodeFromString<RecipesFile>(seedFile("recipes.json").readText())

        val errors = SeedValidator.validate(priceBook.items, recipes.recipes)
        assertTrue("seed data errors: $errors", errors.isEmpty())

        // Phase 1 seed set expectations: the priced household anchors must be present.
        val ids = priceBook.items.map { it.id }.toSet()
        assertTrue("ground-turkey" in ids)
        assertTrue("pork-tenderloin" in ids)
        assertEquals(12, recipes.recipes.size)
        assertTrue(recipes.recipes.none { it.title.contains("beef", ignoreCase = true) })
    }
}
