package com.battlesbudz.dinnerbot

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.battlesbudz.dinnerbot.core.data.DinnerBotDatabase
import com.battlesbudz.dinnerbot.core.data.SeedMapper
import com.battlesbudz.dinnerbot.core.model.LabelNutrition
import com.battlesbudz.dinnerbot.core.model.NutritionValues
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The v0.6.3 upgrade proof: a populated version-15 database (the
 * v0.6.2 install base — her price book with a guess-marked item, her
 * imported recipe, her library override, her plan and list state)
 * opens under version 16 through the real MIGRATION_15_16 with every
 * row intact. The override gains no steps (null: the seed steps
 * stand), and the new label_nutrition table starts empty and takes
 * her first scanned facts.
 */
@RunWith(AndroidJUnit4::class)
class Migration1516Test {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val dbName = "migration-15-16-test.db"

    @Before fun setUp() {
        PopulatedV15Database.create(context, dbName)
    }

    @After fun tearDown() {
        context.deleteDatabase(dbName)
    }

    private fun openMigrated(): DinnerBotDatabase =
        Room.databaseBuilder(context, DinnerBotDatabase::class.java, dbName)
            .addMigrations(
                DinnerBotDatabase.MIGRATION_2_3,
                DinnerBotDatabase.MIGRATION_3_4,
                DinnerBotDatabase.MIGRATION_4_5,
                DinnerBotDatabase.MIGRATION_5_6,
                DinnerBotDatabase.MIGRATION_6_7,
                DinnerBotDatabase.MIGRATION_7_8,
                DinnerBotDatabase.MIGRATION_8_9,
                DinnerBotDatabase.MIGRATION_9_10,
                DinnerBotDatabase.MIGRATION_10_11,
                DinnerBotDatabase.MIGRATION_11_12,
                DinnerBotDatabase.MIGRATION_12_13,
                DinnerBotDatabase.MIGRATION_13_14,
                DinnerBotDatabase.MIGRATION_14_15,
                DinnerBotDatabase.MIGRATION_15_16,
                DinnerBotDatabase.MIGRATION_16_17,
            )
            .build()

    @Test fun populatedV15OpensAtV16WithEveryRowIntact() {
        val db = openMigrated()
        try {
            assertEquals(17, db.openHelper.writableDatabase.version)
            runBlocking {
                // The price book, including the guess mark v15 added.
                val items = db.priceItemDao().getAll()
                    .map { with(SeedMapper) { it.toModel() } }
                assertEquals(3, items.size)
                assertEquals(799, items.first { it.id == "chicken-breast" }.packPriceCents)
                assertTrue(items.first { it.id == "salt" }.staple)
                assertTrue(items.first { it.id == "user-chicken-drumsticks" }.guessPriced)

                // Settings, plan, and list state.
                val settings = with(SeedMapper) { db.settingsDao().get()!!.toModel() }
                assertEquals(4, settings.householdSize)
                assertEquals(8000, settings.weeklyBudgetCents)
                val rows = db.groceryDao().getForPlan("plan-1")
                assertEquals(2, rows.size)
                assertEquals(
                    "Potatoes",
                    rows.single { it.coveredByPantryName != null }.coveredByPantryName,
                )
                assertEquals(
                    "chicken-breast",
                    db.barcodeLinkDao().getByBarcode("012345678905")!!.itemId,
                )
                assertEquals(
                    setOf("her-chicken-soup"),
                    db.keepPromptDao().getAll().map { it.recipeId }.toSet(),
                )

                // Her override survives, its steps unset: null means
                // the seed steps stand, NOT that they were wiped.
                val override = db.recipeOverrideDao().getAll().single()
                    .let { with(SeedMapper) { it.toModel() } }
                assertEquals("r1", override.recipeId)
                assertEquals(1, override.ingredients.size)
                assertNull(override.steps)

                // Her own imported recipe, steps and all.
                val hers = db.importedRecipeDao().getAll().single()
                assertEquals("imported-1", hers.id)
                assertEquals("Her Imported Stew", hers.title)
                assertEquals(
                    listOf("Stew it."),
                    with(SeedMapper) { hers.toModel() }.recipe.steps,
                )

                // The label table starts empty...
                assertTrue(db.labelNutritionDao().getAll().isEmpty())
            }
        } finally {
            db.close()
        }
    }

    @Test fun labelFactsRoundTripThroughTheNewDao() {
        val db = openMigrated()
        try {
            runBlocking {
                val facts = LabelNutrition(
                    itemId = "chicken-breast",
                    barcode = "012345678905",
                    per100g = NutritionValues(
                        caloriesKcal = 120.0, proteinG = 23.0, sodiumMg = 74.0,
                    ),
                    perServing = NutritionValues(caloriesKcal = 136.0, proteinG = 26.0),
                    servingSizeText = "4 oz (113 g)",
                    servingGrams = 113.0,
                    updatedAtMillis = 42L,
                )
                db.labelNutritionDao().upsert(with(SeedMapper) { facts.toEntity() })
                val stored = db.labelNutritionDao().getByItemId("chicken-breast")
                    ?.let { with(SeedMapper) { it.toModel() } }
                assertEquals(facts, stored)
            }
        } finally {
            db.close()
        }
    }
}
