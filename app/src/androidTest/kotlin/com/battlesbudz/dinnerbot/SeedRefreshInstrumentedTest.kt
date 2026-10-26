package com.battlesbudz.dinnerbot

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.battlesbudz.dinnerbot.core.data.DinnerBotDatabase
import com.battlesbudz.dinnerbot.core.data.SeedLoader
import com.battlesbudz.dinnerbot.core.data.SeedMapper
import com.battlesbudz.dinnerbot.core.data.SettingsEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The v0.1.0 -> v0.1.1 upgrade path: stored seed recipes written before the
 * essential/removable marks existed must be refreshed from the bundled seed
 * file, while user data (settings, corrected prices) is left exactly as it
 * was. No schema change, no migration, nothing wiped.
 */
@RunWith(AndroidJUnit4::class)
class SeedRefreshInstrumentedTest {

    @Test fun refreshUpdatesSeedRecipesAndLeavesUserDataAlone() = runBlocking {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            DinnerBotDatabase::class.java,
        ).allowMainThreadQueries().build()
        try {
            val priceJson = appContext.assets.open("pricebook.json").bufferedReader().use { it.readText() }
            val recipesJson = appContext.assets.open("recipes.json").bufferedReader().use { it.readText() }
            val loader = SeedLoader(db)
            loader.seedIfEmpty(priceJson, recipesJson)

            // Simulate the v0.1.0 stored state: every ingredient unmarked,
            // which the new model reads as essential = true.
            val stored = db.recipeDao().getAll().map { with(SeedMapper) { it.toModel() } }
            val legacy = stored.map { recipe ->
                recipe.copy(ingredients = recipe.ingredients.map { it.copy(essential = true) })
            }
            db.recipeDao().deleteAll()
            db.recipeDao().insertAll(legacy.map { with(SeedMapper) { it.toEntity() } })

            // User data that must survive: completed settings + a corrected price.
            db.settingsDao().upsert(
                SettingsEntity(
                    id = 1, householdSize = 3, cookNightsJson = "[\"MON\"]",
                    weeklyBudgetCents = 5555, wontEatJson = "[\"onion\"]",
                    dietNotes = "", pantryStaplesJson = "[]", onboardingComplete = true,
                ),
            )
            db.priceItemDao().updatePrice("yellow-onions", 999, 333.0)

            val refreshed = loader.refreshSeedRecipes(recipesJson)
            assertEquals(36, refreshed)

            val after = db.recipeDao().getAll().map { with(SeedMapper) { it.toModel() } }
            val spaghetti = after.single { it.id == "turkey-spaghetti" }
            val onion = spaghetti.ingredients.single { it.priceItemId == "yellow-onions" }
            assertFalse("onion must now be marked removable", onion.essential)
            val pasta = spaghetti.ingredients.single { it.priceItemId == "spaghetti" }
            assertTrue("the pasta itself stays essential", pasta.essential)

            // User data untouched by the refresh.
            val settings = db.settingsDao().get()!!
            assertEquals(5555, settings.weeklyBudgetCents)
            assertEquals(3, settings.householdSize)
            assertEquals(999, db.priceItemDao().getById("yellow-onions")!!.packPriceCents)

            // Second run is a no-op: stored already matches the bundle.
            assertEquals(0, loader.refreshSeedRecipes(recipesJson))
        } finally {
            db.close()
        }
    }

    /**
     * The v0.4.3 upgrade: a book stored by v0.4.2 (31 items, no staples)
     * gains the eleven priced staples on the next start, flagged staple,
     * while a corrected price and an owned pantry mark are untouched.
     */
    @Test fun addMissingSeedPriceItemsUpgradesA31ItemBook() = runBlocking {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            DinnerBotDatabase::class.java,
        ).allowMainThreadQueries().build()
        try {
            val priceJson = appContext.assets.open("pricebook.json").bufferedReader().use { it.readText() }
            val recipesJson = appContext.assets.open("recipes.json").bufferedReader().use { it.readText() }

            // Rebuild the v0.4.2 book from the bundled file: same items
            // minus the eleven staples.
            val stapleIds = setOf(
                "long-grain-white-rice", "butter", "vegetable-oil", "flour", "salt",
                "black-pepper", "paprika", "chili-powder", "garlic-powder",
                "ground-cumin", "soy-sauce",
            )
            val root = org.json.JSONObject(priceJson)
            val items = root.getJSONArray("items")
            val kept = org.json.JSONArray()
            for (i in 0 until items.length()) {
                val obj = items.getJSONObject(i)
                if (obj.getString("id") !in stapleIds) kept.put(obj)
            }
            root.put("items", kept)

            val loader = SeedLoader(db)
            loader.seedIfEmpty(root.toString(), recipesJson)
            assertEquals(31, db.priceItemDao().getAll().size)

            // User data that must survive: a corrected price and an
            // owned staple mark in the pantry.
            db.priceItemDao().updatePrice("yellow-onions", 999, 333.0)
            db.pantryDao().insertAll(
                listOf(com.battlesbudz.dinnerbot.core.data.PantryItemEntity("salt", "Salt", owned = true)),
            )

            val added = loader.addMissingSeedPriceItems(priceJson)
            assertEquals(11, added)
            assertEquals(43, db.priceItemDao().getAll().size)

            val salt = db.priceItemDao().getById("salt")!!
            assertTrue("the inserted staple carries its flag", salt.staple)
            assertEquals(89, salt.packPriceCents)
            assertEquals("Salt", salt.name)
            assertEquals(999, db.priceItemDao().getById("yellow-onions")!!.packPriceCents)
            val pantry = db.pantryDao().getAll().single()
            assertEquals("Salt", pantry.name)
            assertTrue(pantry.owned)

            // Second run is a no-op.
            assertEquals(0, loader.addMissingSeedPriceItems(priceJson))
        } finally {
            db.close()
        }
    }

    /**
     * PR v0.4.4: an item Andrea added from a barcode scan (a "user-" id,
     * a typed price, a learned barcode link) is user data, exactly like
     * a corrected price: every seed refresh leaves it whole. The
     * refreshes only ever touch seed ids — addMissing inserts seed ids
     * that are absent, refreshSeedPriceNotes rewrites notes for seed
     * ids the bundle carries, refreshSeedRecipes replaces recipes only.
     */
    @Test fun userAddedItemAndItsLinkSurviveEverySeedRefresh() = runBlocking {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            DinnerBotDatabase::class.java,
        ).allowMainThreadQueries().build()
        try {
            val priceJson = appContext.assets.open("pricebook.json").bufferedReader().use { it.readText() }
            val recipesJson = appContext.assets.open("recipes.json").bufferedReader().use { it.readText() }
            val loader = SeedLoader(db)
            loader.seedIfEmpty(priceJson, recipesJson)
            assertEquals(43, db.priceItemDao().getAll().size)

            // The scan-created item and its link, as the write path saves them.
            db.priceItemDao().upsert(
                com.battlesbudz.dinnerbot.core.data.PriceItemEntity(
                    id = "user-granulated-sugar", name = "Granulated sugar",
                    category = "Dry and canned", homeStore = "WALMART",
                    packDescription = "Bag, 4 lb", packAmount = 4.0, packUnit = "POUND",
                    packPriceCents = 342, pricePerUnitCents = 85.5,
                    checkedDate = "2026-10-23", source = "Added by barcode scan",
                    userCorrected = true,
                ),
            )
            db.barcodeLinkDao().upsert(
                com.battlesbudz.dinnerbot.core.data.BarcodeLinkEntity(
                    "036800123456", "user-granulated-sugar", 123L,
                ),
            )

            assertEquals(0, loader.addMissingSeedPriceItems(priceJson))
            loader.refreshSeedPriceNotes(priceJson)
            loader.refreshSeedRecipes(recipesJson)

            val sugar = db.priceItemDao().getById("user-granulated-sugar")!!
            assertEquals("Granulated sugar", sugar.name)
            assertEquals(342, sugar.packPriceCents)
            assertEquals("walmart", sugar.homeStore)
            assertTrue(sugar.userCorrected)
            assertEquals(43, db.priceItemDao().getAll().size)
            val link = db.barcodeLinkDao().getByBarcode("036800123456")!!
            assertEquals("user-granulated-sugar", link.itemId)
        } finally {
            db.close()
        }
    }
}
