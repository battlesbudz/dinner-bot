package com.battlesbudz.dinnerbot

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory

/**
 * A populated version-13 database (the v0.5.1 install base): everything
 * PopulatedV12Database carries (a corrected price, a flagged staple
 * row, a scan-added item, settings, an owned pantry staple, bulk
 * stock, a thumbs rating, a barcode link, a nutrition entry, a saved
 * plan with a plain grocery row AND a scan-swapped grocery row, a
 * recipe photo, Sunday reconciliation state, a remembered scan
 * substitution, and a household recipe override) plus the Phase RI-2
 * layer: one of Andrea's own imported recipes, with a priced
 * ingredient and an as-written one. The grocery rows deliberately
 * predate the RI-3 columns. Built with raw SQL the way the earlier
 * fixtures are. Used by the v13 -> v14 migration test.
 */
object PopulatedV13Database {

    private val v13Ddl = listOf(
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
            "`reconciliationAdded` INTEGER NOT NULL DEFAULT 0, `substitutedForItemId` TEXT)",
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
            "`fileName` TEXT NOT NULL, `credit` TEXT NOT NULL, `updatedAtMillis` INTEGER NOT NULL, " +
            "PRIMARY KEY(`recipeId`))",
        "CREATE TABLE IF NOT EXISTS `scan_substitutions` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
            "`planId` TEXT, `originalItemId` TEXT NOT NULL, `substituteItemId` TEXT NOT NULL, " +
            "`recipeId` TEXT, `substitutePriceCents` INTEGER NOT NULL, " +
            "`createdAtMillis` INTEGER NOT NULL)",
        "CREATE TABLE IF NOT EXISTS `recipe_overrides` (`recipeId` TEXT NOT NULL, " +
            "`ingredientsJson` TEXT NOT NULL, `updatedAtMillis` INTEGER NOT NULL, " +
            "PRIMARY KEY(`recipeId`))",
        "CREATE TABLE IF NOT EXISTS `imported_recipes` (`id` TEXT NOT NULL, " +
            "`title` TEXT NOT NULL, `servings` INTEGER NOT NULL, " +
            "`activeMinutes` INTEGER NOT NULL, `cuisine` TEXT NOT NULL, " +
            "`mainProtein` TEXT NOT NULL, `dietTagsJson` TEXT NOT NULL, " +
            "`ingredientsJson` TEXT NOT NULL, `stepsJson` TEXT NOT NULL, " +
            "`sourceUrl` TEXT, `origin` TEXT NOT NULL, " +
            "`createdAtMillis` INTEGER NOT NULL, PRIMARY KEY(`id`))",
    )

    /** Create (or recreate) the named database at version 13, populated. */
    fun create(context: Context, dbName: String) {
        context.deleteDatabase(dbName)
        val factory = FrameworkSQLiteOpenHelperFactory()
        val config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(dbName)
            .callback(object : SupportSQLiteOpenHelper.Callback(13) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    v13Ddl.forEach { db.execSQL(it) }
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
            "INSERT INTO price_items (id, name, category, homeStore, packDescription, packAmount, " +
                "packUnit, packPriceCents, pricePerUnitCents, checkedDate, source, userCorrected, " +
                "alternatesJson, freezeNote, staple) VALUES ('user-chicken-drumsticks', " +
                "'Chicken drumsticks', 'Meat', 'ALDI', 'Fresh family pack', 4.0, " +
                "'POUND', 499, 124.75, '2026-10-23', 'Added by barcode scan', 1, '[]', NULL, 0)",
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
                "packsToBuy, packPriceCents, portionCostCents, checkedOff, reconciliationAdded, " +
                "substitutedForItemId) " +
                "VALUES ('plan-1', 'chicken-breast', 'Boneless skinless chicken breast', 'ALDI', " +
                "4.2, 'POUND', 1, 799, 799, 1, 0, NULL)",
        )
        db.execSQL(
            "INSERT INTO grocery_items (planId, itemId, displayName, store, neededAmount, neededUnit, " +
                "packsToBuy, packPriceCents, portionCostCents, checkedOff, reconciliationAdded, " +
                "substitutedForItemId) " +
                "VALUES ('plan-1', 'user-chicken-drumsticks', 'Chicken drumsticks', 'ALDI', " +
                "4.0, 'POUND', 1, 499, 499, 0, 0, 'chicken-breast')",
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
        db.execSQL(
            "INSERT INTO scan_substitutions (planId, originalItemId, substituteItemId, recipeId, " +
                "substitutePriceCents, createdAtMillis) VALUES " +
                "('plan-1', 'chicken-breast', 'user-chicken-drumsticks', 'r1', 499, 99)",
        )
        db.execSQL(
            "INSERT INTO recipe_overrides (recipeId, ingredientsJson, updatedAtMillis) VALUES " +
                "('r1', '[{\"essential\":true,\"priceItemId\":\"chicken-breast\"," +
                "\"quantity\":{\"amount\":2.0,\"unit\":\"POUND\"}}]', 100)",
        )
        db.execSQL(
            "INSERT INTO imported_recipes (id, title, servings, activeMinutes, cuisine, " +
                "mainProtein, dietTagsJson, ingredientsJson, stepsJson, sourceUrl, origin, " +
                "createdAtMillis) VALUES ('imported-1', 'Her Imported Stew', 4, 25, " +
                "'Test', 'Chicken', '[]', " +
                "'[{\"priceItemId\":\"chicken-breast\",\"quantity\":{\"amount\":1.0," +
                "\"unit\":\"POUND\"}},{\"pantryItem\":\"Saffron\",\"quantity\":" +
                "{\"amount\":0.5,\"unit\":\"OUNCE\"}}]', '[\"Stew it.\"]', " +
                "'https://example.com/stew', 'IMPORTED', 1000)",
        )
        helper.close()
    }
}
