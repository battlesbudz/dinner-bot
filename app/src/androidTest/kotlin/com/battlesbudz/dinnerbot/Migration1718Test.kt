package com.battlesbudz.dinnerbot

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.battlesbudz.dinnerbot.core.data.DinnerBotDatabase
import com.battlesbudz.dinnerbot.core.data.PlanMapper
import com.battlesbudz.dinnerbot.core.data.SeedMapper
import com.battlesbudz.dinnerbot.core.model.PremiumLocalState
import com.battlesbudz.dinnerbot.core.model.SavingsWeek
import com.battlesbudz.dinnerbot.core.model.UsageCounters
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The Phase C3 upgrade proof: a populated version-17 database (the
 * Phase C1 install base: a user-corrected price book, an imported
 * recipe, an override, a saved plan and list, label nutrition, the
 * stores table) opens under version 18 through the real
 * MIGRATION_17_18. NOTHING resets: every user row survives untouched,
 * and the two new tables (premium_state, savings_records) start
 * empty -- no premium state row exists and there are no savings
 * records until the app writes them.
 */
@RunWith(AndroidJUnit4::class)
class Migration1718Test {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val dbName = "migration-17-18-test.db"

    @Before fun setUp() {
        PopulatedV17Database.create(context, dbName)
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
                DinnerBotDatabase.MIGRATION_17_18,
            )
            .build()

    @Test fun populatedV17OpensAtV18WithEveryRowIntact() {
        val db = openMigrated()
        try {
            assertEquals(18, db.openHelper.writableDatabase.version)
            runBlocking {
                // The price book survives with user data untouched,
                // store values already store ids.
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

                // The two new tables start empty: no premium state row
                // yet, and no savings records.
                assertNull(db.premiumStateDao().get())
                assertTrue(db.savingsRecordDao().getAll().isEmpty())

                // Once written, a premium state and a savings record
                // read back intact.
                val premium = PremiumLocalState(
                    cachedPremium = true,
                    premiumCheckedAtMillis = 1234L,
                    counters = UsageCounters(
                        starterScansUsed = 2,
                        scanMonth = "2026-10",
                        scansUsedInMonth = 3,
                        importMonth = "2026-10",
                        importsUsedInMonth = 1,
                    ),
                    lastPromptDismissedAtMillis = 5678L,
                    firstSeenAtMillis = 999L,
                )
                db.premiumStateDao().upsert(with(SeedMapper) { premium.toEntity() })
                val storedPremium = with(SeedMapper) { db.premiumStateDao().get()!!.toModel() }
                assertEquals(premium, storedPremium)

                val week = SavingsWeek(
                    weekStart = "2026-10-19",
                    budgetCents = 8000,
                    cashNeededCents = 799,
                )
                db.savingsRecordDao().upsert(with(SeedMapper) { week.toEntity(4242L) })
                val records = db.savingsRecordDao().getAll()
                assertEquals(1, records.size)
                assertEquals(4242L, records.single().updatedAtMillis)
                assertEquals(week, with(SeedMapper) { records.single().toModel() })
            }
        } finally {
            db.close()
        }
    }

    /**
     * The production upgrade path, which the test above does not
     * exercise: the fixture pre-creates the new tables at their v18
     * shapes (the standing fixture pattern), so its migration run
     * never executes MIGRATION_17_18's CREATE TABLE statements
     * against an empty slot — but a real v17 install has no such
     * tables. Drop them first, so here the migration creates both
     * tables itself and Room validates what it made.
     */
    @Test fun migrationCreatesTheNewTablesWhenTheyAreAbsent() {
        val path = context.getDatabasePath(dbName).absolutePath
        val raw = SQLiteDatabase.openDatabase(path, null, SQLiteDatabase.OPEN_READWRITE)
        raw.execSQL("DROP TABLE IF EXISTS premium_state")
        raw.execSQL("DROP TABLE IF EXISTS savings_records")
        raw.close()

        val db = openMigrated()
        try {
            assertEquals(18, db.openHelper.writableDatabase.version)
            runBlocking {
                assertNull(db.premiumStateDao().get())
                assertTrue(db.savingsRecordDao().getAll().isEmpty())
                val week = SavingsWeek(
                    weekStart = "2026-10-19",
                    budgetCents = 8000,
                    cashNeededCents = 799,
                )
                db.savingsRecordDao().upsert(with(SeedMapper) { week.toEntity(4242L) })
                assertEquals(week, with(SeedMapper) { db.savingsRecordDao().getAll().single().toModel() })

                // User data still intact after the real DDL ran.
                val settings = with(SeedMapper) { db.settingsDao().get()!!.toModel() }
                assertEquals(4, settings.householdSize)
                assertEquals(8000, settings.weeklyBudgetCents)
                assertEquals(4, db.priceItemDao().getAll().size)
            }
        } finally {
            db.close()
        }
    }
}
