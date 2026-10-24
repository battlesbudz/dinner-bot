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
            )
            .build()

    @Test fun populatedV14OpensAtV15WithEveryRowIntact() {
        val db = openMigrated()
        try {
            assertEquals(15, db.openHelper.writableDatabase.version)
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

                // Her import and the rest of the household's data.
                val imported = db.importedRecipeDao().getAll()
                    .map { with(SeedMapper) { it.toModel() } }
                assertEquals(1, imported.size)
                assertEquals("Her Chicken Soup", imported.single().recipe.title)
                assertEquals(5, db.recipeDao().getAll().size)
                assertEquals(2, db.pantryDao().getAll().size)
                assertEquals(1, db.bulkStockDao().getAll().size)
                assertEquals("THUMBS_UP", db.recipeRatingDao().getAll().single().rating)
                assertEquals("0012345678905", db.barcodeLinkDao().getAll().single().barcode)
                assertEquals(1, db.ingredientNutritionDao().getAll().size)
                assertEquals(1, db.recipePhotoDao().getAll().size)
                val ranOut = db.ranOutDao().getAll()
                    .map { with(ReconciliationMapper) { it.toModel() } }
                assertEquals(RanOutState.PENDING, ranOut.single().state)
                assertEquals(1, db.cookCheckoffDao().getAll().size)
                assertEquals(1, db.scanSubstitutionDao().getAll().size)
                assertEquals(1, db.recipeOverrideDao().getAll().size)
                val settings = db.settingsDao().get()
                assertEquals(8000, settings?.weeklyBudgetCents)

                // v0.5.2's own rows: the covered grocery line and the
                // keep-prompt record survive the step untouched.
                val plan = db.planDao().getRecent(1).single()
                val rows = db.groceryDao().getForPlan(plan.id)
                    .map { with(PlanMapper) { it.toModel() } }
                assertEquals(2, rows.size)
                val covered = rows.first { it.coveredByPantryName != null }
                assertEquals("Potatoes", covered.coveredByPantryName)
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
