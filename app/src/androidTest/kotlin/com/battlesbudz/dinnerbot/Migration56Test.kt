package com.battlesbudz.dinnerbot

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.battlesbudz.dinnerbot.core.data.BarcodeLinkEntity
import com.battlesbudz.dinnerbot.core.data.DinnerBotDatabase
import com.battlesbudz.dinnerbot.core.data.PlanMapper
import com.battlesbudz.dinnerbot.core.data.SeedMapper
import com.battlesbudz.dinnerbot.core.model.BarcodeLink
import com.battlesbudz.dinnerbot.core.model.ReminderSettings
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Phase V2-5 upgrade proof: a populated version-5 database (the v0.2.4
 * install base: Andrea's scan-era predecessor data — a corrected price
 * with its freeze note, settings with a diet preset and a reminder
 * switched on, pantry, bulk stock, a thumbs rating, and a saved plan
 * with a night marked off) opens under version 6 through the real
 * MIGRATION_5_6 with every row intact. The migration adds only the
 * barcode_links table, created empty; links learned afterward persist
 * and read back through the mapper.
 */
@RunWith(AndroidJUnit4::class)
class Migration56Test {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val dbName = "migration-5-6-test.db"

    // The version-5 schema: the v4 tables plus settings.remindersJson,
    // appended the way MIGRATION_4_5's ALTER TABLE appended it.
    private val v5Ddl = listOf(
        "CREATE TABLE IF NOT EXISTS `price_items` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, " +
            "`category` TEXT NOT NULL, `homeStore` TEXT NOT NULL, `packDescription` TEXT NOT NULL, " +
            "`packAmount` REAL NOT NULL, `packUnit` TEXT NOT NULL, `packPriceCents` INTEGER NOT NULL, " +
            "`pricePerUnitCents` REAL, `checkedDate` TEXT NOT NULL, `source` TEXT NOT NULL, " +
            "`userCorrected` INTEGER NOT NULL, `alternatesJson` TEXT NOT NULL, `freezeNote` TEXT, " +
            "PRIMARY KEY(`id`))",
        "CREATE TABLE IF NOT EXISTS `recipes` (`id` TEXT NOT NULL, `title` TEXT NOT NULL, " +
            "`servings` INTEGER NOT NULL, `activeMinutes` INTEGER NOT NULL, `cuisine` TEXT NOT NULL, " +
            "`mainProtein` TEXT NOT NULL, `dietTagsJson` TEXT NOT NULL, `ingredientsJson` TEXT NOT NULL, " +
            "`stepsJson` TEXT NOT NULL, PRIMARY KEY(`id`))",
        "CREATE TABLE IF NOT EXISTS `pantry_items` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, " +
            "`owned` INTEGER NOT NULL, PRIMARY KEY(`id`))",
        "CREATE TABLE IF NOT EXISTS `bulk_stock` (`itemId` TEXT NOT NULL, `amount` REAL NOT NULL, " +
            "`unit` TEXT NOT NULL, PRIMARY KEY(`itemId`))",
        "CREATE TABLE IF NOT EXISTS `plans` (`id` TEXT NOT NULL, `weekStart` TEXT NOT NULL, " +
            "`budgetCents` INTEGER NOT NULL, `cashNeededCents` INTEGER NOT NULL, " +
            "`foodCostCents` INTEGER NOT NULL, `samsMonthlySavingsCents` INTEGER NOT NULL, " +
            "`nightsJson` TEXT NOT NULL, `notesJson` TEXT NOT NULL, `createdAtMillis` INTEGER NOT NULL, " +
            "PRIMARY KEY(`id`))",
        "CREATE TABLE IF NOT EXISTS `grocery_items` (`rowId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
            "`planId` TEXT NOT NULL, `itemId` TEXT NOT NULL, `displayName` TEXT NOT NULL, " +
            "`store` TEXT NOT NULL, `neededAmount` REAL NOT NULL, `neededUnit` TEXT NOT NULL, " +
            "`packsToBuy` INTEGER NOT NULL, `packPriceCents` INTEGER NOT NULL, " +
            "`portionCostCents` INTEGER, `checkedOff` INTEGER NOT NULL)",
        "CREATE TABLE IF NOT EXISTS `settings` (`id` INTEGER NOT NULL, `householdSize` INTEGER NOT NULL, " +
            "`cookNightsJson` TEXT NOT NULL, `weeklyBudgetCents` INTEGER NOT NULL, " +
            "`wontEatJson` TEXT NOT NULL, `dietNotes` TEXT NOT NULL, `pantryStaplesJson` TEXT NOT NULL, " +
            "`onboardingComplete` INTEGER NOT NULL, `dietPresetsJson` TEXT NOT NULL DEFAULT '[]', " +
            "`remindersJson` TEXT NOT NULL DEFAULT '{}', PRIMARY KEY(`id`))",
        "CREATE TABLE IF NOT EXISTS `recipe_ratings` (`recipeId` TEXT NOT NULL, " +
            "`rating` TEXT NOT NULL, `updatedAtMillis` INTEGER NOT NULL, PRIMARY KEY(`recipeId`))",
    )

    @Before fun setUp() {
        context.deleteDatabase(dbName)
        createPopulatedV5Database()
    }

    @After fun tearDown() {
        context.deleteDatabase(dbName)
    }

    private fun createPopulatedV5Database() {
        val factory = FrameworkSQLiteOpenHelperFactory()
        val config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(dbName)
            .callback(object : SupportSQLiteOpenHelper.Callback(5) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    v5Ddl.forEach { db.execSQL(it) }
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            })
            .build()
        val helper = factory.create(config)
        val db = helper.writableDatabase
        // Andrea's corrected price, with a seed-refreshed freeze note on the Sam's item.
        db.execSQL(
            "INSERT INTO price_items (id, name, category, homeStore, packDescription, packAmount, " +
                "packUnit, packPriceCents, pricePerUnitCents, checkedDate, source, userCorrected, " +
                "alternatesJson, freezeNote) VALUES ('pork-tenderloin', 'Pork tenderloin', 'Meat', " +
                "'SAMS_CLUB', 'about 9 lb', 9.0, 'POUND', 2099, 233.2, '2026-10-22', " +
                "'Corrected in app', 1, '[]', 'Freeze what this week will not use.')",
        )
        db.execSQL(
            "INSERT INTO recipes (id, title, servings, activeMinutes, cuisine, mainProtein, " +
                "dietTagsJson, ingredientsJson, stepsJson) VALUES ('r1', 'Test Dinner', 4, 10, " +
                "'Test', 'Turkey', '[]', '[]', '[\"Cook.\"]')",
        )
        db.execSQL("INSERT INTO pantry_items (id, name, owned) VALUES ('salt', 'Salt', 1)")
        db.execSQL(
            "INSERT INTO bulk_stock (itemId, amount, unit) VALUES ('pork-tenderloin', 4.5, 'POUND')",
        )
        // Settings with a diet preset picked and the dinner reminder on.
        db.execSQL(
            "INSERT INTO settings (id, householdSize, cookNightsJson, weeklyBudgetCents, wontEatJson, " +
                "dietNotes, pantryStaplesJson, onboardingComplete, dietPresetsJson, remindersJson) " +
                "VALUES (1, 5, '[\"MON\",\"WED\"]', 9000, '[\"mushrooms\"]', '', '[\"Salt\"]', 1, " +
                "'[\"dairy-free\"]', '{\"dinnerEnabled\":true,\"dinnerTime\":\"17:15\"}')",
        )
        db.execSQL(
            "INSERT INTO recipe_ratings (recipeId, rating, updatedAtMillis) VALUES ('r1', 'DOWN', 7)",
        )
        // A saved plan carrying a Phase V2-2 night-off mark.
        db.execSQL(
            "INSERT INTO plans (id, weekStart, budgetCents, cashNeededCents, foodCostCents, " +
                "samsMonthlySavingsCents, nightsJson, notesJson, createdAtMillis) VALUES ('plan-1', " +
                "'2026-10-19', 9000, 396, 396, 0, " +
                "'[{\"day\":\"MON\",\"recipeId\":\"r1\",\"notCooking\":true,\"notCookingReason\":\"leftovers\"}]', " +
                "'[]', 1)",
        )
        helper.close()
    }

    @Test fun upgradeFromV5KeepsUserDataAndAddsEmptyBarcodeLinks() = runBlocking {
        val db = Room.databaseBuilder(context, DinnerBotDatabase::class.java, dbName)
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
        try {
            assertEquals(18, db.openHelper.readableDatabase.version)

            // Corrected price, its flag, and its freeze note survive.
            val pork = db.priceItemDao().getById("pork-tenderloin")!!
            assertEquals(2099, pork.packPriceCents)
            assertTrue(pork.userCorrected)
            assertEquals("Freeze what this week will not use.", pork.freezeNote)

            // Settings survive whole: presets and the reminder switch included.
            val settings = with(SeedMapper) { db.settingsDao().get()!!.toModel() }
            assertEquals(5, settings.householdSize)
            assertEquals(listOf("dairy-free"), settings.dietPresets)
            assertEquals(
                ReminderSettings(dinnerEnabled = true, dinnerTime = "17:15"),
                settings.reminders,
            )
            assertEquals(listOf("mushrooms"), settings.wontEat)

            // Ratings, pantry, stock, and the night-off plan all survive.
            assertEquals("DOWN", db.recipeRatingDao().getAll().single().rating)
            assertEquals("Salt", db.pantryDao().getAll().single().name)
            assertEquals(4.5, db.bulkStockDao().getAll().single().amount, 0.001)
            val plan = with(PlanMapper) { db.planDao().getLatest()!!.toStoredPlan() }
            assertEquals("plan-1", plan.id)
            assertTrue(plan.nights.single().notCooking)

            // The new table starts empty, then a learned link persists
            // and reads back through the mapper unchanged.
            assertTrue(db.barcodeLinkDao().getAll().isEmpty())
            val learned = BarcodeLink("0012345678901", "pork-tenderloin", 99L)
            db.barcodeLinkDao().upsert(with(SeedMapper) { learned.toEntity() })
            val stored: BarcodeLinkEntity? = db.barcodeLinkDao().getByBarcode("0012345678901")
            assertEquals(learned, stored?.let { with(SeedMapper) { it.toModel() } })
        } finally {
            db.close()
        }
    }
}
