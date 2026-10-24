package com.battlesbudz.dinnerbot

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.battlesbudz.dinnerbot.core.data.DinnerBotDatabase
import com.battlesbudz.dinnerbot.core.data.PlanMapper
import com.battlesbudz.dinnerbot.core.data.SeedMapper
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Phase V2-2 upgrade proof: a populated version-3 database (the v0.2.0
 * install base: Andrea's corrected price, settings, pantry, bulk stock, a
 * thumbs rating, and a saved plan whose nightsJson predates the
 * not-cooking fields) opens under version 4 through the real
 * MIGRATION_3_4 with every row intact. The migration adds only the two
 * new columns; the legacy plan JSON must decode with every night cooking.
 */
@RunWith(AndroidJUnit4::class)
class Migration34Test {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val dbName = "migration-3-4-test.db"

    // The version-3 schema: the v2 tables plus recipe_ratings from V2-1.
    private val v3Ddl = listOf(
        "CREATE TABLE IF NOT EXISTS `price_items` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, " +
            "`category` TEXT NOT NULL, `homeStore` TEXT NOT NULL, `packDescription` TEXT NOT NULL, " +
            "`packAmount` REAL NOT NULL, `packUnit` TEXT NOT NULL, `packPriceCents` INTEGER NOT NULL, " +
            "`pricePerUnitCents` REAL, `checkedDate` TEXT NOT NULL, `source` TEXT NOT NULL, " +
            "`userCorrected` INTEGER NOT NULL, `alternatesJson` TEXT NOT NULL, PRIMARY KEY(`id`))",
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
            "`onboardingComplete` INTEGER NOT NULL, PRIMARY KEY(`id`))",
        "CREATE TABLE IF NOT EXISTS `recipe_ratings` (`recipeId` TEXT NOT NULL, " +
            "`rating` TEXT NOT NULL, `updatedAtMillis` INTEGER NOT NULL, PRIMARY KEY(`recipeId`))",
    )

    @Before fun setUp() {
        context.deleteDatabase(dbName)
        createPopulatedV3Database()
    }

    @After fun tearDown() {
        context.deleteDatabase(dbName)
    }

    private fun createPopulatedV3Database() {
        val factory = FrameworkSQLiteOpenHelperFactory()
        val config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(dbName)
            .callback(object : SupportSQLiteOpenHelper.Callback(3) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    v3Ddl.forEach { db.execSQL(it) }
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            })
            .build()
        val helper = factory.create(config)
        val db = helper.writableDatabase
        // Andrea's corrected price: 249, flagged user-corrected.
        db.execSQL(
            "INSERT INTO price_items (id, name, category, homeStore, packDescription, packAmount, " +
                "packUnit, packPriceCents, pricePerUnitCents, checkedDate, source, userCorrected, " +
                "alternatesJson) VALUES ('yellow-onions', 'Yellow onions', 'Produce', 'ALDI', " +
                "'3 lb bag', 3.0, 'POUND', 249, 83.0, '2026-10-22', 'Corrected in app', 1, '[]')",
        )
        // The Sam's bulk item, priced as shipped in v0.2.0 (no freezeNote yet).
        db.execSQL(
            "INSERT INTO price_items (id, name, category, homeStore, packDescription, packAmount, " +
                "packUnit, packPriceCents, pricePerUnitCents, checkedDate, source, userCorrected, " +
                "alternatesJson) VALUES ('pork-tenderloin', 'Pork tenderloin', 'Meat', 'SAMS_CLUB', " +
                "'about 9 lb', 9.0, 'POUND', 2168, 240.9, '2026-10-22', 'Battles', 0, '[]')",
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
        db.execSQL(
            "INSERT INTO settings (id, householdSize, cookNightsJson, weeklyBudgetCents, wontEatJson, " +
                "dietNotes, pantryStaplesJson, onboardingComplete) VALUES (1, 5, '[\"MON\",\"WED\"]', " +
                "9000, '[\"mushrooms\"]', '', '[\"Salt\"]', 1)",
        )
        db.execSQL(
            "INSERT INTO recipe_ratings (recipeId, rating, updatedAtMillis) VALUES ('r1', 'UP', 7)",
        )
        // A v0.2.0 plan: nightsJson in the legacy shape, no notCooking fields.
        db.execSQL(
            "INSERT INTO plans (id, weekStart, budgetCents, cashNeededCents, foodCostCents, " +
                "samsMonthlySavingsCents, nightsJson, notesJson, createdAtMillis) VALUES ('plan-1', " +
                "'2026-10-19', 9000, 396, 396, 0, '[{\"day\":\"MON\",\"recipeId\":\"r1\"}]', '[]', 1)",
        )
        helper.close()
    }

    @Test fun upgradeFromV3KeepsUserDataAndAddsV22Columns() = runBlocking {
        // Since Phase V2-4 the database version is 5, so the v3 database rides
        // the chain one step further (4 to 5) to open; the assertions below
        // are the original V2-2 proof, unchanged.
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
            )
            .build()
        try {
            assertEquals(11, db.openHelper.readableDatabase.version)

            // Corrected price and its flag survive; the new column starts empty.
            val onions = db.priceItemDao().getById("yellow-onions")!!
            assertEquals(249, onions.packPriceCents)
            assertTrue(onions.userCorrected)
            assertNull(onions.freezeNote)

            // The freeze-note refresh path writes ONLY the note.
            db.priceItemDao().updateFreezeNote("pork-tenderloin", "Bag flat and freeze.")
            val pork = db.priceItemDao().getById("pork-tenderloin")!!
            assertEquals("Bag flat and freeze.", pork.freezeNote)
            assertEquals(2168, pork.packPriceCents)

            // Settings survive; preset selection starts empty and round-trips.
            val settingsEntity = db.settingsDao().get()!!
            assertEquals(5, settingsEntity.householdSize)
            assertEquals("[\"mushrooms\"]", settingsEntity.wontEatJson)
            assertEquals("[]", settingsEntity.dietPresetsJson)
            val settingsModel = with(SeedMapper) { settingsEntity.toModel() }
            assertTrue(settingsModel.dietPresets.isEmpty())
            db.settingsDao().upsert(
                with(SeedMapper) { settingsModel.copy(dietPresets = listOf("dairy-free")).toEntity() },
            )
            val reread = with(SeedMapper) { db.settingsDao().get()!!.toModel() }
            assertEquals(listOf("dairy-free"), reread.dietPresets)
            assertEquals(listOf("mushrooms"), reread.wontEat)

            // Ratings, pantry, stock, and the legacy plan all survive.
            assertEquals("UP", db.recipeRatingDao().getAll().single().rating)
            assertEquals("Salt", db.pantryDao().getAll().single().name)
            assertEquals(4.5, db.bulkStockDao().getAll().single().amount, 0.001)
            val plan = with(PlanMapper) { db.planDao().getLatest()!!.toStoredPlan() }
            assertEquals("plan-1", plan.id)
            assertEquals("r1", plan.nights.single().recipeId)
            assertTrue(!plan.nights.single().notCooking)
            assertNull(plan.nights.single().notCookingReason)
        } finally {
            db.close()
        }
    }
}
