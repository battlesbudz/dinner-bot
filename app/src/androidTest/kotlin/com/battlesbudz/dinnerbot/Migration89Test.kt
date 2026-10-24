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
import com.battlesbudz.dinnerbot.core.data.RecipePhotoEntity
import com.battlesbudz.dinnerbot.core.data.ReconciliationMapper
import com.battlesbudz.dinnerbot.core.data.SeedMapper
import com.battlesbudz.dinnerbot.core.model.RanOutState
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Epic issue #10 upgrade proof: a populated version-8 database (the
 * v0.4.0 install base: a corrected price, settings, pantry, bulk stock,
 * a thumbs rating, a barcode link, a nutrition entry, a saved plan with
 * a grocery row, and Sunday reconciliation state) opens under version 9
 * through the real MIGRATION_8_9 with every row intact. The migration
 * adds only the recipe_photos table, created empty: no photo is
 * bundled, so every recipe starts with no photo.
 */
@RunWith(AndroidJUnit4::class)
class Migration89Test {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val dbName = "migration-8-9-test.db"

    // The version-8 schema: the v7 tables plus the reconciliation state.
    private val v8Ddl = listOf(
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
            "`portionCostCents` INTEGER, `checkedOff` INTEGER NOT NULL, " +
            "`reconciliationAdded` INTEGER NOT NULL DEFAULT 0)",
        "CREATE TABLE IF NOT EXISTS `settings` (`id` INTEGER NOT NULL, `householdSize` INTEGER NOT NULL, " +
            "`cookNightsJson` TEXT NOT NULL, `weeklyBudgetCents` INTEGER NOT NULL, " +
            "`wontEatJson` TEXT NOT NULL, `dietNotes` TEXT NOT NULL, `pantryStaplesJson` TEXT NOT NULL, " +
            "`onboardingComplete` INTEGER NOT NULL, `dietPresetsJson` TEXT NOT NULL DEFAULT '[]', " +
            "`remindersJson` TEXT NOT NULL DEFAULT '{}', " +
            "`reconciledPlanIdsJson` TEXT NOT NULL DEFAULT '[]', PRIMARY KEY(`id`))",
        "CREATE TABLE IF NOT EXISTS `recipe_ratings` (`recipeId` TEXT NOT NULL, " +
            "`rating` TEXT NOT NULL, `updatedAtMillis` INTEGER NOT NULL, PRIMARY KEY(`recipeId`))",
        "CREATE TABLE IF NOT EXISTS `barcode_links` (`barcode` TEXT NOT NULL, `itemId` TEXT NOT NULL, " +
            "`createdAtMillis` INTEGER NOT NULL, PRIMARY KEY(`barcode`))",
        "CREATE TABLE IF NOT EXISTS `ingredient_nutrition` (`itemId` TEXT NOT NULL, " +
            "`pantryItem` INTEGER NOT NULL, `fdcId` INTEGER NOT NULL, `fdcDescription` TEXT NOT NULL, " +
            "`fdcDataType` TEXT NOT NULL, `closestMatch` INTEGER NOT NULL, `source` TEXT NOT NULL, " +
            "`caloriesKcal` REAL, `proteinG` REAL, `fatG` REAL, `carbohydrateG` REAL, `fiberG` REAL, " +
            "`sugarsG` REAL, `sodiumMg` REAL, `saturatedFatG` REAL, `cholesterolMg` REAL, " +
            "`gramWeightsJson` TEXT NOT NULL, `gramWeightSource` TEXT, PRIMARY KEY(`itemId`))",
        "CREATE TABLE IF NOT EXISTS `ran_out_events` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
            "`itemKey` TEXT NOT NULL, `displayName` TEXT NOT NULL, `stapleSide` INTEGER NOT NULL, " +
            "`priceItemId` TEXT, `bulkItemId` TEXT, `pantryStapleName` TEXT, " +
            "`dateMarked` TEXT NOT NULL, `source` TEXT NOT NULL, `state` TEXT NOT NULL, " +
            "`cyclePlanId` TEXT)",
        "CREATE TABLE IF NOT EXISTS `cook_checkoffs` (`planId` TEXT NOT NULL, `day` TEXT NOT NULL, " +
            "`itemKey` TEXT NOT NULL, `checked` INTEGER NOT NULL, " +
            "PRIMARY KEY(`planId`, `day`, `itemKey`))",
    )

    @Before fun setUp() {
        context.deleteDatabase(dbName)
        createPopulatedV8Database()
    }

    @After fun tearDown() {
        context.deleteDatabase(dbName)
    }

    private fun createPopulatedV8Database() {
        val factory = FrameworkSQLiteOpenHelperFactory()
        val config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(dbName)
            .callback(object : SupportSQLiteOpenHelper.Callback(8) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    v8Ddl.forEach { db.execSQL(it) }
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            })
            .build()
        val helper = factory.create(config)
        val db = helper.writableDatabase
        db.execSQL(
            "INSERT INTO price_items (id, name, category, homeStore, packDescription, packAmount, " +
                "packUnit, packPriceCents, pricePerUnitCents, checkedDate, source, userCorrected, " +
                "alternatesJson, freezeNote) VALUES ('eggs', 'Eggs', 'Dairy', 'ALDI', " +
                "'12 count', 12.0, 'COUNT', 289, 24.08, '2026-10-22', 'Corrected in app', 1, '[]', NULL)",
        )
        db.execSQL(
            "INSERT INTO recipes (id, title, servings, activeMinutes, cuisine, mainProtein, " +
                "dietTagsJson, ingredientsJson, stepsJson) VALUES ('r1', 'Test Dinner', 4, 10, " +
                "'Test', 'Egg', '[]', '[]', '[\"Cook.\"]')",
        )
        db.execSQL("INSERT INTO pantry_items (id, name, owned) VALUES ('salt', 'Salt', 1)")
        db.execSQL(
            "INSERT INTO bulk_stock (itemId, amount, unit) VALUES ('pork-tenderloin', 4.5, 'POUND')",
        )
        db.execSQL(
            "INSERT INTO settings (id, householdSize, cookNightsJson, weeklyBudgetCents, wontEatJson, " +
                "dietNotes, pantryStaplesJson, onboardingComplete, dietPresetsJson, remindersJson, " +
                "reconciledPlanIdsJson) VALUES (1, 4, '[\"MON\",\"TUE\"]', 8000, '[]', '', " +
                "'[\"Salt\"]', 1, '[]', '{}', '[\"plan-1\"]')",
        )
        db.execSQL(
            "INSERT INTO recipe_ratings (recipeId, rating, updatedAtMillis) VALUES ('r1', 'UP', 7)",
        )
        db.execSQL(
            "INSERT INTO barcode_links (barcode, itemId, createdAtMillis) " +
                "VALUES ('012345678905', 'eggs', 42)",
        )
        db.execSQL(
            "INSERT INTO ingredient_nutrition (itemId, pantryItem, fdcId, fdcDescription, fdcDataType, " +
                "closestMatch, source, caloriesKcal, proteinG, fatG, carbohydrateG, fiberG, sugarsG, " +
                "sodiumMg, saturatedFatG, cholesterolMg, gramWeightsJson, gramWeightSource) VALUES " +
                "('eggs', 0, 171287, 'Egg, whole, raw, fresh', 'SR Legacy', 0, 'USDA', 143.0, 12.56, " +
                "9.51, 0.72, 0.0, 0.37, 142.0, 3.1, 372.0, '{}', NULL)",
        )
        db.execSQL(
            "INSERT INTO plans (id, weekStart, budgetCents, cashNeededCents, foodCostCents, " +
                "samsMonthlySavingsCents, nightsJson, notesJson, createdAtMillis) VALUES ('plan-1', " +
                "'2026-10-19', 8000, 289, 289, 0, " +
                "'[{\"day\":\"MON\",\"recipeId\":\"r1\"}]', '[]', 1)",
        )
        db.execSQL(
            "INSERT INTO grocery_items (planId, itemId, displayName, store, neededAmount, neededUnit, " +
                "packsToBuy, packPriceCents, portionCostCents, checkedOff, reconciliationAdded) " +
                "VALUES ('plan-1', 'eggs', 'Eggs', 'ALDI', 12.0, 'COUNT', 1, 289, 289, 1, 0)",
        )
        db.execSQL(
            "INSERT INTO ran_out_events (itemKey, displayName, stapleSide, priceItemId, bulkItemId, " +
                "pantryStapleName, dateMarked, source, state, cyclePlanId) VALUES " +
                "('pantry:olive oil', 'Olive oil', 1, NULL, NULL, 'Olive oil', '2026-10-25', " +
                "'COOKING', 'PENDING', 'plan-1')",
        )
        db.execSQL(
            "INSERT INTO cook_checkoffs (planId, day, itemKey, checked) " +
                "VALUES ('plan-1', 'MON', 'item:eggs', 1)",
        )
        helper.close()
    }

    @Test fun upgradeFromV8KeepsUserDataAndAddsEmptyPhotoTable() = runBlocking {
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
            )
            .build()
        try {
            assertEquals(15, db.openHelper.readableDatabase.version)

            // User data survives whole.
            val eggs = db.priceItemDao().getById("eggs")!!
            assertEquals(289, eggs.packPriceCents)
            assertTrue(eggs.userCorrected)
            assertEquals("UP", db.recipeRatingDao().getAll().single().rating)
            assertEquals("eggs", db.barcodeLinkDao().getByBarcode("012345678905")!!.itemId)
            assertEquals(171287L, db.ingredientNutritionDao().getAll().single().fdcId)
            assertEquals("Salt", db.pantryDao().getAll().single().name)
            assertEquals(4.5, db.bulkStockDao().getAll().single().amount, 0.001)
            val settings = with(SeedMapper) { db.settingsDao().get()!!.toModel() }
            assertEquals(4, settings.householdSize)
            assertEquals(listOf("plan-1"), settings.reconciledPlanIds)
            val plan = with(PlanMapper) { db.planDao().getLatest()!!.toStoredPlan() }
            assertEquals("plan-1", plan.id)
            val event = with(ReconciliationMapper) { db.ranOutDao().getAll().single().toModel() }
            assertEquals(RanOutState.PENDING, event.state)
            assertTrue(db.cookCheckoffDao().getForPlan("plan-1").single().checked)

            // The photo table starts empty, then holds a real reference.
            assertTrue(db.recipePhotoDao().getAll().isEmpty())
            db.recipePhotoDao().upsert(
                RecipePhotoEntity("r1", "r1.jpg", "Household photo", 5L),
            )
            val photo = with(SeedMapper) { db.recipePhotoDao().getByRecipeId("r1")!!.toModel() }
            assertEquals("r1.jpg", photo.fileName)
            assertEquals("Household photo", photo.credit)
        } finally {
            db.close()
        }
    }
}
