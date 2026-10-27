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
import com.battlesbudz.dinnerbot.core.data.ReconciliationMapper
import com.battlesbudz.dinnerbot.core.data.ScanSubstitutionEntity
import com.battlesbudz.dinnerbot.core.data.SeedMapper
import com.battlesbudz.dinnerbot.core.model.RanOutState
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * v0.4.4 upgrade proof: a populated version-10 database (the v0.4.3
 * install base: a corrected price, a flagged staple row, settings, an
 * owned pantry staple, bulk stock, a thumbs rating, a barcode link, a
 * nutrition entry, a saved plan with a grocery row, a recipe photo, and
 * Sunday reconciliation state) opens under version 11 through the real
 * MIGRATION_10_11 with every row intact. The migration adds the
 * scan_substitutions table (created empty; swaps accumulate from the
 * upgrade on) and the nullable grocery_items.substitutedForItemId
 * column (null on every stored row). (The builder chains on through
 * MIGRATION_11_12 to the current version, per the chain pattern every
 * version bump follows; the v11 additions are what this test proves.)
 */
@RunWith(AndroidJUnit4::class)
class Migration1011Test {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val dbName = "migration-10-11-test.db"

    // The version-10 schema: the v9 tables plus price_items.staple.
    private val v10Ddl = listOf(
        "CREATE TABLE IF NOT EXISTS `price_items` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, " +
            "`category` TEXT NOT NULL, `homeStore` TEXT NOT NULL, `packDescription` TEXT NOT NULL, " +
            "`packAmount` REAL NOT NULL, `packUnit` TEXT NOT NULL, `packPriceCents` INTEGER NOT NULL, " +
            "`pricePerUnitCents` REAL, `checkedDate` TEXT NOT NULL, `source` TEXT NOT NULL, " +
            "`userCorrected` INTEGER NOT NULL, `alternatesJson` TEXT NOT NULL, `freezeNote` TEXT, " +
            "`staple` INTEGER NOT NULL DEFAULT 0, " +
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
            "`cookNightsJson` TEXT NOT NULL, `weeklyBudgetCents` INTEGER NOT NULL, `wontEatJson` TEXT NOT NULL, " +
            "`dietNotes` TEXT NOT NULL, `pantryStaplesJson` TEXT NOT NULL, " +
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
        "CREATE TABLE IF NOT EXISTS `recipe_photos` (`recipeId` TEXT NOT NULL, " +
            "`fileName` TEXT NOT NULL, `credit` TEXT NOT NULL, " +
            "`updatedAtMillis` INTEGER NOT NULL, PRIMARY KEY(`recipeId`))",
    )

    @Before fun setUp() {
        context.deleteDatabase(dbName)
        createPopulatedV10Database()
    }

    @After fun tearDown() {
        context.deleteDatabase(dbName)
    }

    private fun createPopulatedV10Database() {
        val factory = FrameworkSQLiteOpenHelperFactory()
        val config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(dbName)
            .callback(object : SupportSQLiteOpenHelper.Callback(10) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    v10Ddl.forEach { db.execSQL(it) }
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            })
            .build()
        val helper = factory.create(config)
        val db = helper.writableDatabase
        db.execSQL(
            "INSERT INTO price_items (id, name, category, homeStore, packDescription, packAmount, " +
                "packUnit, packPriceCents, pricePerUnitCents, checkedDate, source, userCorrected, " +
                "alternatesJson, freezeNote, staple) VALUES ('chicken-breast', " +
                "'Boneless skinless chicken breast', 'Meat', 'ALDI', 'Fresh family pack', 4.2, " +
                "'POUND', 799, 190.24, '2026-10-23', 'Corrected by barcode scan', 1, '[]', NULL, 0)",
        )
        db.execSQL(
            "INSERT INTO price_items (id, name, category, homeStore, packDescription, packAmount, " +
                "packUnit, packPriceCents, pricePerUnitCents, checkedDate, source, userCorrected, " +
                "alternatesJson, freezeNote, staple) VALUES ('salt', 'Salt', 'Dry and canned', " +
                "'ALDI', '26 oz canister, iodized', 26.0, 'OUNCE', 89, 3.4, '2026-10-21', " +
                "'Phase 0 Aldi capture', 0, '[]', NULL, 1)",
        )
        db.execSQL(
            "INSERT INTO recipes (id, title, servings, activeMinutes, cuisine, mainProtein, " +
                "dietTagsJson, ingredientsJson, stepsJson) VALUES ('r1', 'Test Dinner', 4, 10, " +
                "'Test', 'Chicken', '[]', '[{\"essential\":true,\"priceItemId\":\"chicken-breast\"," +
                "\"quantity\":{\"amount\":1.0,\"unit\":\"POUND\"}}]', '[\"Cook.\"]')",
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
                "VALUES ('012345678905', 'chicken-breast', 42)",
        )
        db.execSQL(
            "INSERT INTO ingredient_nutrition (itemId, pantryItem, fdcId, fdcDescription, fdcDataType, " +
                "closestMatch, source, caloriesKcal, proteinG, fatG, carbohydrateG, fiberG, sugarsG, " +
                "sodiumMg, saturatedFatG, cholesterolMg, gramWeightsJson, gramWeightSource) VALUES " +
                "('salt', 1, 189544, 'Salt, table, iodized', 'SR Legacy', 0, 'USDA', 0.0, 0.0, " +
                "0.0, 0.0, 0.0, 0.0, 38758.0, 0.0, 0.0, '{\"TEASPOON\":6.0}', 'fixture')",
        )
        db.execSQL(
            "INSERT INTO plans (id, weekStart, budgetCents, cashNeededCents, foodCostCents, " +
                "samsMonthlySavingsCents, nightsJson, notesJson, createdAtMillis) VALUES ('plan-1', " +
                "'2026-10-19', 8000, 799, 799, 0, " +
                "'[{\"day\":\"MON\",\"recipeId\":\"r1\"}]', '[]', 1)",
        )
        db.execSQL(
            "INSERT INTO grocery_items (planId, itemId, displayName, store, neededAmount, neededUnit, " +
                "packsToBuy, packPriceCents, portionCostCents, checkedOff, reconciliationAdded) " +
                "VALUES ('plan-1', 'chicken-breast', 'Boneless skinless chicken breast', 'ALDI', " +
                "4.2, 'POUND', 1, 799, 799, 1, 0)",
        )
        db.execSQL(
            "INSERT INTO ran_out_events (itemKey, displayName, stapleSide, priceItemId, bulkItemId, " +
                "pantryStapleName, dateMarked, source, state, cyclePlanId) VALUES " +
                "('item:salt', 'Salt', 1, 'salt', NULL, 'Salt', '2026-10-25', " +
                "'COOKING', 'PENDING', 'plan-1')",
        )
        db.execSQL(
            "INSERT INTO cook_checkoffs (planId, day, itemKey, checked) " +
                "VALUES ('plan-1', 'MON', 'item:salt', 1)",
        )
        db.execSQL(
            "INSERT INTO recipe_photos (recipeId, fileName, credit, updatedAtMillis) " +
                "VALUES ('r1', 'r1.jpg', 'Household photo', 5)",
        )
        helper.close()
    }

    @Test fun upgradeFromV10KeepsUserDataAndAddsSubstitutionState() = runBlocking {
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
            )
            .build()
        try {
            assertEquals(17, db.openHelper.readableDatabase.version)

            // User data survives whole, corrected price and staple flag included.
            val breast = db.priceItemDao().getById("chicken-breast")!!
            assertEquals(799, breast.packPriceCents)
            assertTrue(breast.userCorrected)
            assertTrue(db.priceItemDao().getById("salt")!!.staple)
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
            val plan = with(PlanMapper) { db.planDao().getLatest()!!.toStoredPlan() }
            assertEquals("plan-1", plan.id)
            assertEquals(799, plan.cashNeededCents)
            assertEquals("r1.jpg", db.recipePhotoDao().getAll().single().fileName)
            val event = with(ReconciliationMapper) { db.ranOutDao().getAll().single().toModel() }
            assertEquals("item:salt", event.itemKey)
            assertEquals(RanOutState.PENDING, event.state)
            assertTrue(db.cookCheckoffDao().getForPlan("plan-1").single().checked)

            // The stored grocery row reads with no swap marker, and the
            // substitutions table starts empty.
            val storedRow = db.groceryDao().getForPlan("plan-1").single()
            assertNull(storedRow.substitutedForItemId)
            assertTrue(db.scanSubstitutionDao().getAll().isEmpty())

            // Both new shapes round-trip: a remembered swap record and
            // a grocery row carrying the marker.
            db.scanSubstitutionDao().insert(
                ScanSubstitutionEntity(
                    planId = "plan-1",
                    originalItemId = "chicken-breast",
                    substituteItemId = "user-chicken-drumsticks",
                    recipeId = "r1",
                    substitutePriceCents = 499,
                    createdAtMillis = 99L,
                ),
            )
            val saved = db.scanSubstitutionDao().getAll().single()
            assertEquals("user-chicken-drumsticks", saved.substituteItemId)
            assertEquals("r1", saved.recipeId)
            assertEquals(499, saved.substitutePriceCents)
            val model = with(PlanMapper) { storedRow.toModel() }
                .copy(itemId = "user-chicken-drumsticks", substitutedForItemId = "chicken-breast")
            db.groceryDao().insert(with(PlanMapper) { model.toEntity("plan-1") })
            val swappedRow = db.groceryDao().getForPlan("plan-1")
                .single { it.itemId == "user-chicken-drumsticks" }
            assertEquals("chicken-breast", swappedRow.substitutedForItemId)
        } finally {
            db.close()
        }
    }
}
