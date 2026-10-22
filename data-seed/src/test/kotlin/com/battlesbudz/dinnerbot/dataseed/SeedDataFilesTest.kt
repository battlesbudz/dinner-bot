package com.battlesbudz.dinnerbot.dataseed

import com.battlesbudz.dinnerbot.core.model.DietPresetsFile
import com.battlesbudz.dinnerbot.core.model.PriceBookFile
import com.battlesbudz.dinnerbot.core.model.RecipesFile
import com.battlesbudz.dinnerbot.core.model.Store
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
        assertEquals(36, recipes.recipes.size)
        assertTrue(recipes.recipes.none { it.title.contains("beef", ignoreCase = true) })
    }

    @Test fun everySamsBulkItemCarriesAFreezeNote() {
        val priceBook = json.decodeFromString<PriceBookFile>(seedFile("pricebook.json").readText())
        val samsItems = priceBook.items.filter { it.homeStore == Store.SAMS_CLUB }
        assertTrue("expected at least one Sam's Club item", samsItems.isNotEmpty())
        for (item in samsItems) {
            assertTrue(
                "Sam's item ${item.id} must carry freeze guidance (Phase V2-2)",
                !item.freezeNote.isNullOrBlank(),
            )
        }
        // No other store's items carry freeze notes: the guidance exists for
        // oversize bulk packs only.
        assertTrue(priceBook.items.filter { it.homeStore != Store.SAMS_CLUB }.all { it.freezeNote == null })
    }

    @Test fun dietPresetsAreValidAgainstTheLibrary() {
        val recipes = json.decodeFromString<RecipesFile>(seedFile("recipes.json").readText())
        val presets = json.decodeFromString<DietPresetsFile>(seedFile("diet-presets.json").readText())
        val errors = SeedValidator.validatePresets(presets.presets, recipes.recipes)
        assertTrue("diet preset errors: $errors", errors.isEmpty())
        assertEquals(
            setOf("dairy-free", "gluten-free", "low-carb", "no-pork", "vegetarian"),
            presets.presets.map { it.id }.toSet(),
        )
    }

    /** The Phase V2-2 diet-tag audit (seed-data/DIET_TAG_AUDIT.md), pinned as assertions. */
    @Test fun dietTagsMatchTheAudit() {
        val recipes = json.decodeFromString<RecipesFile>(seedFile("recipes.json").readText())
        val byId = recipes.recipes.associateBy { it.id }

        // Vegetarian: exactly the ten egg and cheese dinners, nothing with meat.
        val vegetarian = recipes.recipes.filter { "Vegetarian" in it.dietTags }.map { it.id }.toSet()
        assertEquals(
            setOf(
                "egg-fried-rice", "tomato-soup-grilled-cheese", "breakfast-burritos",
                "veggie-frittata", "cheese-omelets-home-fries", "rotini-mac-and-cheese",
                "cheese-quesadillas", "veggie-pasta-bake", "cheesy-potato-skillet",
                "bean-cheese-burritos",
            ),
            vegetarian,
        )

        // Gluten-free: the audit's eight, including the three added in V2-2.
        val glutenFree = recipes.recipes.filter { "Gluten-free" in it.dietTags }.map { it.id }.toSet()
        assertEquals(
            setOf(
                "turkey-chili", "turkey-bean-chili", "veggie-frittata",
                "cheese-omelets-home-fries", "cheesy-potato-skillet",
                "sheet-pan-pork-potatoes", "turkey-shepherds-pie", "potato-egg-skillet",
            ),
            glutenFree,
        )
        // Named judgment calls from the audit: removable flour and soy sauce
        // dishes are NOT tagged; the won't-eat machinery handles them.
        assertTrue("Gluten-free" !in (byId["cubed-pork-potato-skillet"]?.dietTags ?: emptyList()))
        assertTrue("Gluten-free" !in (byId["pork-green-bean-skillet"]?.dietTags ?: emptyList()))
    }

    /**
     * Phase V2-6: the bundled nutrition file covers the shipped ingredient
     * set exactly. Every priced item and every pantry staple the recipes
     * use has a sourced entry, and the file's known-incomplete list
     * matches the seed (it is empty while coverage is complete, and the
     * validator fails the moment the two drift apart).
     */
    @Test fun nutritionSeedCoversTheShippedIngredientsExactly() {
        val priceBook = json.decodeFromString<PriceBookFile>(seedFile("pricebook.json").readText())
        val recipes = json.decodeFromString<RecipesFile>(seedFile("recipes.json").readText())
        val nutrition = json.decodeFromString<com.battlesbudz.dinnerbot.core.model.NutritionFile>(
            seedFile("nutrition.json").readText(),
        )

        val errors = SeedValidator.validateNutrition(
            nutrition.entries, priceBook.items, recipes.recipes, nutrition.incomplete,
        )
        assertTrue("nutrition seed errors: $errors", errors.isEmpty())
        assertEquals(42, nutrition.entries.size)
        assertEquals(31, nutrition.entries.count { !it.pantryItem })
        assertEquals(11, nutrition.entries.count { it.pantryItem })
        assertTrue(nutrition.incomplete.isEmpty())
    }
}
