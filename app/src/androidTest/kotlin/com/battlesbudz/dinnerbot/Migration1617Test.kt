package com.battlesbudz.dinnerbot

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.battlesbudz.dinnerbot.core.data.DinnerBotDatabase
import com.battlesbudz.dinnerbot.core.data.PlanMapper
import com.battlesbudz.dinnerbot.core.data.SeedMapper
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The Phase C1 upgrade proof: a populated version-16 database (the
 * v0.6.3 install base: a user-corrected price book written with the old
 * enum store names, an imported recipe, an override, a saved plan and
 * list, label nutrition) opens under version 17 through the real
 * MIGRATION_16_17. NOTHING resets: every user row survives; the only
 * changes are store values rewritten from enum names to store ids
 * (including inside the alternates JSON) and the new stores table,
 * seeded with the three legacy chains so existing data has names.
 */
@RunWith(AndroidJUnit4::class)
class Migration1617Test {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val dbName = "migration-16-17-test.db"

    @Before fun setUp() {
        PopulatedV16Database.create(context, dbName)
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

    @Test fun populatedV16OpensAtV17WithEveryRowIntact() {
        val db = openMigrated()
        try {
            assertEquals(17, db.openHelper.writableDatabase.version)
            runBlocking {
                // The price book survives with user data untouched; only
                // the store values changed shape.
                val items = db.priceItemDao().getAll()
                    .map { with(SeedMapper) { it.toModel() } }
                assertEquals(4, items.size)
                val breast = items.first { it.id == "chicken-breast" }
                assertEquals("aldi", breast.homeStore)
                assertEquals(799, breast.packPriceCents)
                assertTrue(breast.userCorrected)
                assertTrue(items.first { it.id == "salt" }.staple)
                assertTrue(items.first { it.id == "user-chicken-drumsticks" }.guessPriced)
                val tenderloin = items.first { it.id == "pork-tenderloin" }
                assertEquals("sams-club", tenderloin.homeStore)
                assertEquals(2168, tenderloin.packPriceCents)
                assertEquals(
                    listOf("aldi", "walmart"),
                    tenderloin.alternates.map { it.store },
                )

                // The stores table: exactly the legacy chains, in order.
                val stores = db.storeDao().getAll()
                    .map { with(SeedMapper) { it.toModel() } }
                assertEquals(
                    listOf("aldi" to "Aldi", "walmart" to "Walmart", "sams-club" to "Sam's Club"),
                    stores.map { it.id to it.name },
                )

                // Settings, recipes, and the imported recipe survive.
                val settings = with(SeedMapper) { db.settingsDao().get()!!.toModel() }
                assertEquals(4, settings.householdSize)
                assertEquals(8000, settings.weeklyBudgetCents)
                val recipes = db.recipeDao().getAll()
                    .map { with(SeedMapper) { it.toModel() } }
                assertEquals(1, recipes.size)
                assertEquals("Test Dinner", recipes.single().title)
                assertEquals(1, db.importedRecipeDao().getAll().size)
                assertEquals(1, db.labelNutritionDao().getAll().size)

                // The plan survives, legacy savings column included (the
                // value is preserved as stored data; nothing reads it
                // since the gate was deleted).
                val plan = db.planDao().getById("plan-1")!!
                assertEquals(1600, plan.samsMonthlySavingsCents)
                assertEquals(799, plan.cashNeededCents)
                val stored = with(PlanMapper) { plan.toStoredPlan() }
                val rows = db.groceryDao().getForPlan("plan-1")
                    .map { with(PlanMapper) { it.toModel() } }
                assertEquals(3, rows.size)
                assertEquals("aldi", rows.first { it.itemId == "chicken-breast" }.store)
                assertEquals("sams-club", rows.first { it.itemId == "pork-tenderloin" }.store)
                assertEquals(stored.id, "plan-1")
            }
        } finally {
            db.close()
        }
    }
}
