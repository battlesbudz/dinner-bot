package com.battlesbudz.dinnerbot

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.battlesbudz.dinnerbot.core.data.DinnerBotDatabase
import com.battlesbudz.dinnerbot.core.data.RecipeRatingEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Phase V2-1 upgrade proof: a populated version-2 database (Andrea's price
 * correction, pantry, settings, a saved plan) opens under version 3 through
 * the real MIGRATION_2_3 with every row intact, and the new recipe_ratings
 * table works. This is the v0.1.1 rule: real user data gets a real
 * migration, never the destructive fallback.
 */
@RunWith(AndroidJUnit4::class)
class Migration23Test {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val dbName = "migration-2-3-test.db"

    // The version-2 schema, exactly as the v2 entities define it.
    private val v2Ddl = listOf(
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
    )

    @Before fun setUp() {
        context.deleteDatabase(dbName)
        createPopulatedV2Database()
    }

    @After fun tearDown() {
        context.deleteDatabase(dbName)
    }

    private fun createPopulatedV2Database() {
        val factory = FrameworkSQLiteOpenHelperFactory()
        val config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(dbName)
            .callback(object : SupportSQLiteOpenHelper.Callback(2) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    v2Ddl.forEach { db.execSQL(it) }
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
        db.execSQL(
            "INSERT INTO recipes (id, title, servings, activeMinutes, cuisine, mainProtein, " +
                "dietTagsJson, ingredientsJson, stepsJson) VALUES ('r1', 'Test Dinner', 4, 10, " +
                "'Test', 'Turkey', '[]', '[]', '[\"Cook.\"]')",
        )
        db.execSQL("INSERT INTO pantry_items (id, name, owned) VALUES ('salt', 'Salt', 1)")
        db.execSQL(
            "INSERT INTO settings (id, householdSize, cookNightsJson, weeklyBudgetCents, wontEatJson, " +
                "dietNotes, pantryStaplesJson, onboardingComplete) VALUES (1, 5, '[\"MON\",\"WED\"]', " +
                "9000, '[]', '', '[\"Salt\"]', 1)",
        )
        db.execSQL(
            "INSERT INTO plans (id, weekStart, budgetCents, cashNeededCents, foodCostCents, " +
                "samsMonthlySavingsCents, nightsJson, notesJson, createdAtMillis) VALUES ('plan-1', " +
                "'2026-10-19', 9000, 396, 396, 0, '[{\"day\":\"MON\",\"recipeId\":\"r1\"}]', '[]', 1)",
        )
        helper.close()
    }

    @Test fun upgradeFromV2KeepsUserDataAndAddsRatings() = runBlocking {
        // Since Phase V2-4 the database version is 5, so the v2 database rides
        // the full registered chain (2 to 3 to 4 to 5) to open; the data-survival
        // assertions below are the original V2-1 proof, unchanged.
        val db = Room.databaseBuilder(context, DinnerBotDatabase::class.java, dbName)
            .addMigrations(
                DinnerBotDatabase.MIGRATION_2_3,
                DinnerBotDatabase.MIGRATION_3_4,
                DinnerBotDatabase.MIGRATION_4_5,
                DinnerBotDatabase.MIGRATION_5_6,
                DinnerBotDatabase.MIGRATION_6_7,
            )
            .build()
        try {
            val item = db.priceItemDao().getById("yellow-onions")!!
            assertEquals(249, item.packPriceCents)
            assertTrue(item.userCorrected)
            assertEquals("Salt", db.pantryDao().getAll().single().name)
            assertEquals(5, db.settingsDao().get()!!.householdSize)
            assertEquals("plan-1", db.planDao().getLatest()!!.id)
            assertEquals("Test Dinner", db.recipeDao().getById("r1")!!.title)

            assertTrue(db.recipeRatingDao().getAll().isEmpty())
            db.recipeRatingDao().upsert(RecipeRatingEntity("r1", "DOWN", 42L))
            val stored = db.recipeRatingDao().getAll().single()
            assertEquals("r1", stored.recipeId)
            assertEquals("DOWN", stored.rating)
            assertEquals(6, db.openHelper.readableDatabase.version)
        } finally {
            db.close()
        }
    }
}
