package com.battlesbudz.dinnerbot

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.battlesbudz.dinnerbot.core.data.DinnerBotDatabase
import com.battlesbudz.dinnerbot.core.data.PlanMapper
import com.battlesbudz.dinnerbot.core.data.ReconciliationMapper
import com.battlesbudz.dinnerbot.core.data.SeedMapper
import com.battlesbudz.dinnerbot.core.model.RanOutState
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The correction upgrade proof (Battles's correction, 2026-10-24): a
 * populated version-14 database (the v0.5.2 install base, including
 * its weekly-list line state and a keep-prompt record) opens under
 * version 15 through the real MIGRATION_14_15 with every row intact.
 * Every existing price item reads unmarked — its price is a real one —
 * and the guess mark then round-trips through the price DAO.
 */
@RunWith(AndroidJUnit4::class)
class Migration1415Test {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val dbName = "migration-14-15-test.db"

    @Before fun setUp() {
        PopulatedV14Database.create(context, dbName)
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
            )
            .build()

    @Test fun populatedV14OpensAtV15WithEveryRowIntact() {
        val db = openMigrated()
        try {
            assertEquals(16, db.openHelper.writableDatabase.version)
            runBlocking {
                // The book's three fixture items (a corrected seed
                // item, a flagged staple, her scan-added one), every
                // price a real one — nothing guess-marked.
                val items = db.priceItemDao().getAll()
                    .map { with(SeedMapper) { it.toModel() } }
                assertEquals(3, items.size)
                assertTrue(items.none { it.guessPriced })
                val breast = items.first { it.id == "chicken-breast" }
                assertEquals(799, breast.packPriceCents)
                assertTrue(breast.userCorrected)
                assertTrue(items.first { it.id == "salt" }.staple)
                assertEquals(
                    499,
                    items.first { it.id == "user-chicken-drumsticks" }.packPriceCents,
                )

                // The rest of the household's data, exactly as the
                // v13 -> v14 proof reads it.
                val saltPantry = db.pantryDao().getAll().single()
                assertEquals("Salt", saltPantry.name)
                assertTrue(saltPantry.owned)
                assertEquals(4.5, db.bulkStockDao().getAll().single().amount, 0.001)
                val settings = with(SeedMapper) { db.settingsDao().get()!!.toModel() }
                assertEquals(4, settings.householdSize)
                assertEquals(8000, settings.weeklyBudgetCents)
                assertEquals("UP", db.recipeRatingDao().getAll().single().rating)
                assertEquals(
                    "chicken-breast",
                    db.barcodeLinkDao().getByBarcode("012345678905")!!.itemId,
                )
                assertEquals(189544L, db.ingredientNutritionDao().getAll().single().fdcId)
                assertEquals("r1.jpg", db.recipePhotoDao().getAll().single().fileName)
                val event = with(ReconciliationMapper) {
                    db.ranOutDao().getAll().single().toModel()
                }
                assertEquals(RanOutState.PENDING, event.state)
                assertTrue(db.cookCheckoffDao().getForPlan("plan-1").single().checked)
                assertEquals(
                    "chicken-breast",
                    db.scanSubstitutionDao().getAll().single().originalItemId,
                )
                assertEquals(1, db.recipeOverrideDao().getAll().size)
                val hers = db.importedRecipeDao().getAll().single()
                assertEquals("imported-1", hers.id)
                assertEquals("Her Imported Stew", hers.title)

                // v0.5.2's own rows: the covered grocery line and the
                // keep-prompt record survive the step untouched.
                val rows = db.groceryDao().getForPlan("plan-1")
                assertEquals(2, rows.size)
                val covered = rows.filter { it.coveredByPantryName != null }
                assertEquals(1, covered.size)
                assertEquals("Potatoes", covered.single().coveredByPantryName)
                assertEquals(
                    "chicken-breast",
                    rows.single { it.itemId == "user-chicken-drumsticks" }
                        .substitutedForItemId,
                )
                assertEquals(
                    setOf("her-chicken-soup"),
                    db.keepPromptDao().getAll().map { it.recipeId }.toSet(),
                )
            }
        } finally {
            db.close()
        }
    }

    @Test fun guessMarkRoundTripsThroughThePriceDao() {
        val db = openMigrated()
        try {
            runBlocking {
                val entity = db.priceItemDao().getById("salt")!!
                assertFalse(entity.guessPriced)
                db.priceItemDao().upsert(entity.copy(guessPriced = true))
                assertTrue(db.priceItemDao().getById("salt")!!.guessPriced)
                val model = with(SeedMapper) { db.priceItemDao().getById("salt")!!.toModel() }
                assertTrue(model.guessPriced)
                // And clearing it (the first real price) sticks.
                db.priceItemDao().upsert(entity.copy(guessPriced = false, packPriceCents = 199))
                val cleared = db.priceItemDao().getById("salt")!!
                assertFalse(cleared.guessPriced)
                assertEquals(199, cleared.packPriceCents)
            }
        } finally {
            db.close()
        }
    }
}
