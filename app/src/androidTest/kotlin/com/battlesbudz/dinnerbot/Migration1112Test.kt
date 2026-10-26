package com.battlesbudz.dinnerbot

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.battlesbudz.dinnerbot.core.data.DinnerBotDatabase
import com.battlesbudz.dinnerbot.core.data.PlanMapper
import com.battlesbudz.dinnerbot.core.data.ReconciliationMapper
import com.battlesbudz.dinnerbot.core.data.SeedMapper
import com.battlesbudz.dinnerbot.core.model.EffectiveRecipes
import com.battlesbudz.dinnerbot.core.model.MeasureUnit
import com.battlesbudz.dinnerbot.core.model.Quantity
import com.battlesbudz.dinnerbot.core.model.RanOutState
import com.battlesbudz.dinnerbot.core.model.RecipeIngredient
import com.battlesbudz.dinnerbot.core.model.RecipeOverride
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * v0.5.0 upgrade proof (Phase RI-1): a populated version-11 database
 * (the v0.4.4 install base: corrected prices, a scan-added item, a
 * remembered scan substitution and a substitution-marked grocery row,
 * settings, pantry, stock, a rating, a barcode link, a photo, a plan,
 * and Sunday reconciliation state) opens under version 12 through the
 * real MIGRATION_11_12 with every row intact. The migration only
 * creates the recipe_overrides table, empty: no recipe has household
 * edits until Andrea makes one, and a v11 plan's nights JSON decodes
 * with no tonight-only edits on any night.
 */
@RunWith(AndroidJUnit4::class)
class Migration1112Test {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val dbName = "migration-11-12-test.db"

    @Before fun setUp() {
        PopulatedV11Database.create(context, dbName)
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

    @Test fun upgradeFromV11KeepsEveryKindOfUserData() = runBlocking {
        val db = openMigrated()
        try {
            assertEquals(16, db.openHelper.readableDatabase.version)

            // Prices: the corrected one, the staple flag, the scan-added item.
            val breast = db.priceItemDao().getById("chicken-breast")!!
            assertEquals(799, breast.packPriceCents)
            assertTrue(breast.userCorrected)
            assertTrue(db.priceItemDao().getById("salt")!!.staple)
            assertEquals(499, db.priceItemDao().getById("user-chicken-drumsticks")!!.packPriceCents)

            // The v0.4.4 barcode rework state, whole.
            val swap = db.scanSubstitutionDao().getAll().single()
            assertEquals("chicken-breast", swap.originalItemId)
            assertEquals("user-chicken-drumsticks", swap.substituteItemId)
            assertEquals("r1", swap.recipeId)
            assertEquals(499, swap.substitutePriceCents)
            val rows = db.groceryDao().getForPlan("plan-1")
            assertEquals(2, rows.size)
            assertEquals(
                "chicken-breast",
                rows.single { it.itemId == "user-chicken-drumsticks" }.substitutedForItemId,
            )
            assertNull(rows.single { it.itemId == "chicken-breast" }.substitutedForItemId)

            // Everything else, as in every migration test before this one.
            assertEquals("UP", db.recipeRatingDao().getAll().single().rating)
            assertEquals(
                "chicken-breast",
                db.barcodeLinkDao().getByBarcode("012345678905")!!.itemId,
            )
            assertEquals(189544L, db.ingredientNutritionDao().getAll().single().fdcId)
            val saltPantry = db.pantryDao().getAll().single()
            assertEquals("Salt", saltPantry.name)
            assertTrue(saltPantry.owned)
            assertEquals(4.5, db.bulkStockDao().getAll().single().amount, 0.001)
            val settings = with(SeedMapper) { db.settingsDao().get()!!.toModel() }
            assertEquals(4, settings.householdSize)
            assertEquals(listOf("plan-1"), settings.reconciledPlanIds)
            assertEquals("r1.jpg", db.recipePhotoDao().getAll().single().fileName)
            val event = with(ReconciliationMapper) { db.ranOutDao().getAll().single().toModel() }
            assertEquals(RanOutState.PENDING, event.state)
            assertTrue(db.cookCheckoffDao().getForPlan("plan-1").single().checked)

            // The stored plan decodes, its night carrying no tonight edits.
            val plan = with(PlanMapper) { db.planDao().getLatest()!!.toStoredPlan() }
            assertEquals("plan-1", plan.id)
            assertEquals("r1", plan.nights.single().recipeId)
            assertNull(plan.nights.single().tonightEdits)

            // The new table starts empty, and an override written through
            // it merges over the stored recipe on the load path.
            assertTrue(db.recipeOverrideDao().getAll().isEmpty())
            val herList = listOf(
                RecipeIngredient(
                    priceItemId = "chicken-breast",
                    quantity = Quantity(2.0, MeasureUnit.POUND),
                ),
            )
            db.recipeOverrideDao().upsert(
                with(SeedMapper) { RecipeOverride("r1", herList, 100L).toEntity() },
            )
            val stored = db.recipeDao().getAll().map { with(SeedMapper) { it.toModel() } }
            val overrides = db.recipeOverrideDao().getAll()
                .map { with(SeedMapper) { it.toModel() } }
                .associateBy { it.recipeId }
            val effective = EffectiveRecipes.applyOverrides(stored, overrides)
            assertEquals(herList, effective.single().ingredients)

            // Restore-original is the row's deletion, nothing else.
            db.recipeOverrideDao().delete("r1")
            assertTrue(db.recipeOverrideDao().getAll().isEmpty())
            assertEquals("UP", db.recipeRatingDao().getAll().single().rating)
        } finally {
            db.close()
        }
    }
}
