package com.battlesbudz.dinnerbot

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.battlesbudz.dinnerbot.core.data.DinnerBotDatabase
import com.battlesbudz.dinnerbot.core.data.GroceryItemEntity
import com.battlesbudz.dinnerbot.core.data.KeepPromptEntity
import com.battlesbudz.dinnerbot.core.data.PlanMapper
import com.battlesbudz.dinnerbot.core.data.ReconciliationMapper
import com.battlesbudz.dinnerbot.core.data.SeedMapper
import com.battlesbudz.dinnerbot.core.model.RanOutState
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * v0.5.2 upgrade proof (Phase RI-3): a populated version-13 database
 * (the v0.5.1 install base: everything the v12 fixture carried plus one
 * of Andrea's imported recipes) opens under version 14 through the
 * real MIGRATION_13_14 with every row intact. Stored grocery rows read
 * as ordinary buy lines under the new columns' defaults; the
 * keep-prompt table starts empty; and both new stores then round-trip
 * through their DAOs — a keep-prompt record and a covered grocery row
 * with its covering name, uncross flag, and placeholder guess.
 */
@RunWith(AndroidJUnit4::class)
class Migration1314Test {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val dbName = "migration-13-14-test.db"

    @Before fun setUp() {
        PopulatedV13Database.create(context, dbName)
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

    @Test fun upgradeFromV13KeepsEveryKindOfUserData() = runBlocking {
        val db = openMigrated()
        try {
            assertEquals(16, db.openHelper.readableDatabase.version)

            // Prices, pantry, stock, settings, ratings — the whole v13 base.
            val breast = db.priceItemDao().getById("chicken-breast")!!
            assertEquals(799, breast.packPriceCents)
            assertTrue(breast.userCorrected)
            assertTrue(db.priceItemDao().getById("salt")!!.staple)
            assertEquals(499, db.priceItemDao().getById("user-chicken-drumsticks")!!.packPriceCents)
            val saltPantry = db.pantryDao().getAll().single()
            assertEquals("Salt", saltPantry.name)
            assertTrue(saltPantry.owned)
            assertEquals(4.5, db.bulkStockDao().getAll().single().amount, 0.001)
            val settings = with(SeedMapper) { db.settingsDao().get()!!.toModel() }
            assertEquals(4, settings.householdSize)
            assertEquals(listOf("plan-1"), settings.reconciledPlanIds)
            assertEquals("UP", db.recipeRatingDao().getAll().single().rating)
            assertEquals(
                "chicken-breast",
                db.barcodeLinkDao().getByBarcode("012345678905")!!.itemId,
            )
            assertEquals(189544L, db.ingredientNutritionDao().getAll().single().fdcId)
            assertEquals("r1.jpg", db.recipePhotoDao().getAll().single().fileName)
            val event = with(ReconciliationMapper) { db.ranOutDao().getAll().single().toModel() }
            assertEquals(RanOutState.PENDING, event.state)
            assertTrue(db.cookCheckoffDao().getForPlan("plan-1").single().checked)
            assertEquals(
                "chicken-breast",
                db.scanSubstitutionDao().getAll().single().originalItemId,
            )
            assertEquals(1, db.recipeOverrideDao().getAll().size)

            // Her imported recipe survived, priced line and as-written
            // line alike.
            val hers = db.importedRecipeDao().getAll().single()
            assertEquals("imported-1", hers.id)
            assertEquals("Her Imported Stew", hers.title)
            assertTrue(hers.ingredientsJson.contains("Saffron"))

            // The stored plan and its rows: the RI-3 columns read as an
            // ordinary buy line under their defaults.
            val plan = with(PlanMapper) { db.planDao().getLatest()!!.toStoredPlan() }
            assertEquals("plan-1", plan.id)
            val rows = db.groceryDao().getForPlan("plan-1")
            assertEquals(2, rows.size)
            rows.forEach { row ->
                assertNull(row.coveredByPantryName)
                assertFalse(row.uncrossed)
                assertNull(row.priceGuessCents)
            }
            assertEquals(
                "chicken-breast",
                rows.single { it.itemId == "user-chicken-drumsticks" }.substitutedForItemId,
            )

            // The keep-prompt table starts empty: nothing has been asked.
            assertTrue(db.keepPromptDao().getAll().isEmpty())
        } finally {
            db.close()
        }
    }

    @Test fun newStoresRoundTripAfterMigration() = runBlocking {
        val db = openMigrated()
        try {
            db.keepPromptDao().upsert(KeepPromptEntity("imported-1", 1234L))
            assertEquals(
                listOf(KeepPromptEntity("imported-1", 1234L)),
                db.keepPromptDao().getAll(),
            )

            db.groceryDao().insertAll(
                listOf(
                    GroceryItemEntity(
                        planId = "plan-1", itemId = "russet-potatoes",
                        displayName = "Russet potatoes", store = "ALDI",
                        neededAmount = 2.0, neededUnit = "POUND", packsToBuy = 0,
                        packPriceCents = 349, portionCostCents = 140,
                        checkedOff = false, coveredByPantryName = "Potatoes",
                    ),
                    GroceryItemEntity(
                        planId = "plan-1", itemId = "aswritten:saffron",
                        displayName = "Saffron", store = "ALDI",
                        neededAmount = 0.5, neededUnit = "OUNCE", packsToBuy = 1,
                        packPriceCents = 0, portionCostCents = null,
                        checkedOff = false, priceGuessCents = 167,
                    ),
                ),
            )
            val rows = db.groceryDao().getForPlan("plan-1")
            val covered = rows.single { it.itemId == "russet-potatoes" }
            assertEquals("Potatoes", covered.coveredByPantryName)
            assertFalse(covered.uncrossed)
            val guessRow = rows.single { it.itemId == "aswritten:saffron" }
            assertEquals(167, guessRow.priceGuessCents)
            val coveredModel = with(PlanMapper) { covered.toModel() }
            assertEquals("Potatoes", coveredModel.coveredByPantryName)
            assertEquals(167, with(PlanMapper) { guessRow.toModel() }.priceGuessCents)
        } finally {
            db.close()
        }
    }
}
