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
        assertEquals(100, recipes.recipes.size)
        // Phase C1: beef content is legal seed content now (see the
        // national-average test below). Phase C2 grew the library from
        // the household's 36 recipes to a 100-recipe public starter
        // library with beef, seafood, and vegetarian variety.
    }

    @Test fun bulkPackItemsCarryFreezeNotes() {
        // The seed's one oversize bulk pack carries the freeze guidance
        // that marks it stock-tracked (Phase C1: the note, not the store,
        // is the bulk marker).
        val priceBook = json.decodeFromString<PriceBookFile>(seedFile("pricebook.json").readText())
        val tenderloin = priceBook.items.single { it.id == "pork-tenderloin" }
        assertTrue(!tenderloin.freezeNote.isNullOrBlank())
    }

    @Test fun seedPricesAreNationalAveragesWithNoPersonalProvenance() {
        // Phase C1 item 4: the shipped price book is national averages.
        // Every provenance string reads as the national average, no
        // personal or store-specific note survives anywhere in the file,
        // and the in-app disclosure copy says exactly that.
        val raw = seedFile("pricebook.json").readText()
        val priceBook = json.decodeFromString<PriceBookFile>(raw)
        assertTrue(priceBook.items.all { it.source == "National average" })
        assertTrue(priceBook.items.none { it.packDescription.contains("6539") })
        assertTrue(!raw.contains("Battles, household"))
        assertTrue(!raw.contains("Instacart"))
        assertTrue("ground-beef" in priceBook.items.map { it.id })
        assertTrue(
            com.battlesbudz.dinnerbot.core.model.SeedPrices.DISCLOSURE
                .contains("national average", ignoreCase = true),
        )
        assertTrue(
            com.battlesbudz.dinnerbot.core.model.SeedPrices.DISCLOSURE
                .contains("first time you scan"),
        )
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

    /**
     * The diet-tag audit (seed-data/DIET_TAG_AUDIT.md: the Phase V2-2
     * audit plus the Phase C2 addendum), pinned as assertions.
     */
    @Test fun dietTagsMatchTheAudit() {
        val recipes = json.decodeFromString<RecipesFile>(seedFile("recipes.json").readText())
        val byId = recipes.recipes.associateBy { it.id }

        // Vegetarian: the audit's ten egg and cheese dinners plus the
        // C2 meatless additions, nothing with meat or seafood.
        val vegetarian = recipes.recipes.filter { "Vegetarian" in it.dietTags }.map { it.id }.toSet()
        assertEquals(
            setOf(
                "egg-fried-rice", "tomato-soup-grilled-cheese", "breakfast-burritos",
                "veggie-frittata", "cheese-omelets-home-fries", "rotini-mac-and-cheese",
                "cheese-quesadillas", "veggie-pasta-bake", "cheesy-potato-skillet",
                "bean-cheese-burritos",
                "veggie-lasagna", "lentil-vegetable-soup", "chickpea-tacos",
                "black-bean-burgers", "tofu-stir-fry", "lasagna-roll-ups",
                "mushroom-stroganoff", "sweet-potato-chili", "veggie-lo-mein",
                "potato-corn-chowder", "broccoli-cheddar-soup", "rice-bean-bowls",
                "broccoli-cheddar-quiche", "shakshuka", "breakfast-hash-eggs",
                "cheese-enchiladas",
            ),
            vegetarian,
        )

        // Gluten-free: the audit's eight plus the C2 additions. Sausage
        // and bacon dishes are never tagged (the addendum's judgment
        // call), and neither is anything carrying a wheat carrier.
        val glutenFree = recipes.recipes.filter { "Gluten-free" in it.dietTags }.map { it.id }.toSet()
        assertEquals(
            setOf(
                "turkey-chili", "turkey-bean-chili", "veggie-frittata",
                "cheese-omelets-home-fries", "cheesy-potato-skillet",
                "sheet-pan-pork-potatoes", "turkey-shepherds-pie", "potato-egg-skillet",
                "beef-pot-roast", "beef-chili", "beef-cabbage-skillet",
                "oven-bbq-chicken", "stuffed-bell-peppers", "turkey-sweet-potato-skillet",
                "garlic-butter-salmon", "cajun-shrimp-rice", "lentil-vegetable-soup",
                "sweet-potato-chili", "rice-bean-bowls", "breakfast-hash-eggs",
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
     * set exactly. Every priced item the recipes use has a sourced entry,
     * and the file's known-incomplete list matches the seed (it is empty
     * while coverage is complete, and the validator fails the moment the
     * two drift apart). Since v0.4.3 the eleven staples are priced items,
     * so all entries key by price item id and none carries the old
     * pantry flag. Phase C2 added 35 items and their entries alongside
     * the C1 set: nutrition resolution covers all 78 items.
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
        assertEquals(78, nutrition.entries.size)
        assertEquals(78, nutrition.entries.count { !it.pantryItem })
        assertEquals(0, nutrition.entries.count { it.pantryItem })
        // Every price book item resolves a nutrition entry by its id.
        val entryIds = nutrition.entries.map { it.itemId }.toSet()
        for (item in priceBook.items) {
            assertTrue("no nutrition entry for price item ${item.id}", item.id in entryIds)
        }
        assertTrue(nutrition.incomplete.isEmpty())
    }
}
