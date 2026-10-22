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
            assertEquals(12, refreshed)

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
}
