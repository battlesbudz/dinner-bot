package com.battlesbudz.dinnerbot.core.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Database version 2 (Phase 2) adds pantry_items, bulk_stock, plans and
 * grocery_items, plus the price_items.alternatesJson column.
 *
 * v0.1.1 deliberately kept version 2: the essential/removable ingredient
 * marks live inside the recipes JSON column, so there was NO schema change,
 * no migration ran, and no user table was touched on upgrade.
 *
 * Version 3 (Phase V2-1, v0.2.0) adds recipe_ratings (one thumbs rating per
 * recipe) through the real [MIGRATION_2_3] below. It only creates the new
 * table; every v2 table and row carries over untouched, which the
 * instrumented migration test proves from a populated v2 database.
 *
 * Version 4 (Phase V2-2, v0.2.1) adds two columns through [MIGRATION_3_4]:
 * price_items.freezeNote (seed-owned freeze guidance on Sam's bulk items)
 * and settings.dietPresetsJson (the household's selected diet preset ids).
 * The per-night not-cooking state needs NO column: it lives inside the
 * plan's existing nightsJson, and the new PlannedNight fields carry
 * defaults, so a v3 plan's JSON decodes with every night still cooking.
 * The instrumented migration test proves the upgrade from a populated v3
 * database, legacy plan JSON included.
 *
 * Version 5 (Phase V2-4, v0.2.4) adds one column through [MIGRATION_4_5]:
 * settings.remindersJson, the household's reminder switches and times as
 * one JSON blob defaulting to "{}", which decodes to every reminder off.
 * The instrumented migration test proves the upgrade from a populated v4
 * database.
 *
 * Version 6 (Phase V2-5, v0.3.0) adds the barcode_links table through
 * [MIGRATION_5_6]: one learned barcode-to-item link per barcode, created
 * empty on upgrade. It only creates the new table; every v5 table and
 * row carries over untouched, which the instrumented migration test
 * proves from a populated v5 database.
 *
 * Version 7 (Phase V2-6, v0.3.1) adds the ingredient_nutrition table
 * through [MIGRATION_6_7]: one USDA FoodData Central entry per shipped
 * ingredient, created empty on upgrade and filled from the bundled seed
 * by the seed refresh. It is seed content, never user data; every v6
 * table and row carries over untouched, which the instrumented
 * migration test proves from a populated v6 database.
 *
 * Version 8 (Phase V2-7, v0.4.0) adds the Sunday reconciliation state
 * through [MIGRATION_7_8]: the ran_out_events and cook_checkoffs tables
 * (both created empty; they are working state that accumulates from the
 * upgrade on), grocery_items.reconciliationAdded (whether a line came
 * from a reconciliation), and settings.reconciledPlanIdsJson (the plan
 * ids whose cycles a reconciliation has consumed). Every v7 table and
 * row carries over untouched, which the instrumented migration test
 * proves from a populated v7 database.
 *
 * Version 9 (Epic issue #10, v0.4.2) adds the recipe_photos table
 * through [MIGRATION_8_9]: one household photo reference (file name in
 * app-local storage plus credit) per recipe, created empty on upgrade —
 * no photo is bundled, so every recipe starts with no photo and renders
 * exactly as before until the household attaches one. Every v8 table
 * and row carries over untouched, which the instrumented migration
 * test proves from a populated v8 database.
 *
 * Version 10 (v0.4.3) adds the price_items.staple column through
 * [MIGRATION_9_10]: the eleven pantry staples became priced price book
 * items (Battles: "everything should be in the pricing book"), and the
 * flag carries their staple identity for pantry coverage and Sunday
 * restock now that recipes reference them as priced items. The column
 * defaults to 0; the seed refresh inserts the new staple rows (flagged)
 * and no stored row is rewritten. Every v9 table and row carries over
 * untouched, which the instrumented migration test proves from a
 * populated v9 database.
 *
 * Version 11 (PR v0.4.4) adds the barcode rework state through
 * [MIGRATION_10_11]: the scan_substitutions table (remembered scan
 * swaps, created empty; they accumulate from the upgrade on) and
 * grocery_items.substitutedForItemId (the planned item a line was
 * swapped in for, null on every stored row). Every v10 table and row
 * carries over untouched, which the instrumented migration test
 * proves from a populated v10 database.
 *
 * Version 12 (Phase RI-1, v0.5.0) adds the recipe_overrides table
 * through [MIGRATION_11_12]: Andrea's saved household ingredient list
 * for one library recipe per row, created empty on upgrade — no recipe
 * has household edits until she makes one. Tonight-only edits need NO
 * table: they ride inside the plans' existing nightsJson as a new
 * PlannedNight field with a null default, so a v11 plan's JSON decodes
 * with every night cooking the saved recipe. Every v11 table and row
 * carries over untouched, which the instrumented migration test proves
 * from a populated v11 database.
 *
 * Version 13 (Phase RI-2, v0.5.1) adds the imported_recipes table
 * through [MIGRATION_12_13]: Andrea's own imported and hand-made
 * recipes, one full recipe per row, created empty on upgrade — the app
 * gains no recipes until she brings one in. The table is household
 * data: the seed refresh never touches it. Every v12 table and row
 * carries over untouched, which the instrumented migration test
 * proves from a populated v12 database.
 *
 * fallbackToDestructiveMigration remains only as a pre-1.0 safety net for
 * development installs; with a real migration registered for every version
 * step it never triggers on a real upgrade path.
 */
@Database(
    entities = [
        PriceItemEntity::class,
        RecipeEntity::class,
        SettingsEntity::class,
        PantryItemEntity::class,
        BulkStockEntity::class,
        PlanEntity::class,
        GroceryItemEntity::class,
        RecipeRatingEntity::class,
        RecipePhotoEntity::class,
        BarcodeLinkEntity::class,
        IngredientNutritionEntity::class,
        RanOutEventEntity::class,
        CookCheckoffEntity::class,
        ScanSubstitutionEntity::class,
        RecipeOverrideEntity::class,
        ImportedRecipeEntity::class,
    ],
    version = 13,
    exportSchema = false,
)
abstract class DinnerBotDatabase : RoomDatabase() {
    abstract fun priceItemDao(): PriceItemDao
    abstract fun recipeDao(): RecipeDao
    abstract fun settingsDao(): SettingsDao
    abstract fun pantryDao(): PantryDao
    abstract fun bulkStockDao(): BulkStockDao
    abstract fun planDao(): PlanDao
    abstract fun groceryDao(): GroceryDao
    abstract fun recipeRatingDao(): RecipeRatingDao
    abstract fun recipePhotoDao(): RecipePhotoDao
    abstract fun barcodeLinkDao(): BarcodeLinkDao
    abstract fun ingredientNutritionDao(): IngredientNutritionDao
    abstract fun ranOutDao(): RanOutDao
    abstract fun cookCheckoffDao(): CookCheckoffDao
    abstract fun scanSubstitutionDao(): ScanSubstitutionDao
    abstract fun recipeOverrideDao(): RecipeOverrideDao
    abstract fun importedRecipeDao(): ImportedRecipeDao

    companion object {
        /** v2 to v3: create the recipe_ratings table. Nothing else changes. */
        val MIGRATION_2_3: Migration = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `recipe_ratings` (" +
                        "`recipeId` TEXT NOT NULL, `rating` TEXT NOT NULL, " +
                        "`updatedAtMillis` INTEGER NOT NULL, PRIMARY KEY(`recipeId`))",
                )
            }
        }

        /** v3 to v4: freeze notes on price items, diet preset selection on settings. Additive only. */
        val MIGRATION_3_4: Migration = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `price_items` ADD COLUMN `freezeNote` TEXT")
                db.execSQL("ALTER TABLE `settings` ADD COLUMN `dietPresetsJson` TEXT NOT NULL DEFAULT '[]'")
            }
        }

        /** v4 to v5: the reminder settings blob on the settings row. Additive only. */
        val MIGRATION_4_5: Migration = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `settings` ADD COLUMN `remindersJson` TEXT NOT NULL DEFAULT '{}'")
            }
        }

        /** v5 to v6: create the barcode_links table. Nothing else changes. */
        val MIGRATION_5_6: Migration = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `barcode_links` (" +
                        "`barcode` TEXT NOT NULL, `itemId` TEXT NOT NULL, " +
                        "`createdAtMillis` INTEGER NOT NULL, PRIMARY KEY(`barcode`))",
                )
            }
        }

        /** v6 to v7: create the ingredient_nutrition table. Nothing else changes. */
        val MIGRATION_6_7: Migration = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `ingredient_nutrition` (" +
                        "`itemId` TEXT NOT NULL, `pantryItem` INTEGER NOT NULL, " +
                        "`fdcId` INTEGER NOT NULL, `fdcDescription` TEXT NOT NULL, " +
                        "`fdcDataType` TEXT NOT NULL, `closestMatch` INTEGER NOT NULL, " +
                        "`source` TEXT NOT NULL, `caloriesKcal` REAL, `proteinG` REAL, " +
                        "`fatG` REAL, `carbohydrateG` REAL, `fiberG` REAL, `sugarsG` REAL, " +
                        "`sodiumMg` REAL, `saturatedFatG` REAL, `cholesterolMg` REAL, " +
                        "`gramWeightsJson` TEXT NOT NULL, `gramWeightSource` TEXT, " +
                        "PRIMARY KEY(`itemId`))",
                )
            }
        }

        /** v7 to v8: Sunday reconciliation state (Phase V2-7). Additive only. */
        val MIGRATION_7_8: Migration = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `ran_out_events` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `itemKey` TEXT NOT NULL, " +
                        "`displayName` TEXT NOT NULL, `stapleSide` INTEGER NOT NULL, " +
                        "`priceItemId` TEXT, `bulkItemId` TEXT, `pantryStapleName` TEXT, " +
                        "`dateMarked` TEXT NOT NULL, `source` TEXT NOT NULL, `state` TEXT NOT NULL, " +
                        "`cyclePlanId` TEXT)",
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `cook_checkoffs` (" +
                        "`planId` TEXT NOT NULL, `day` TEXT NOT NULL, `itemKey` TEXT NOT NULL, " +
                        "`checked` INTEGER NOT NULL, PRIMARY KEY(`planId`, `day`, `itemKey`))",
                )
                db.execSQL(
                    "ALTER TABLE `grocery_items` ADD COLUMN `reconciliationAdded` INTEGER NOT NULL DEFAULT 0",
                )
                db.execSQL(
                    "ALTER TABLE `settings` ADD COLUMN `reconciledPlanIdsJson` TEXT NOT NULL DEFAULT '[]'",
                )
            }
        }

        /** v8 to v9: create the recipe_photos table (Epic issue #10). Nothing else changes. */
        val MIGRATION_8_9: Migration = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `recipe_photos` (" +
                        "`recipeId` TEXT NOT NULL, `fileName` TEXT NOT NULL, " +
                        "`credit` TEXT NOT NULL, `updatedAtMillis` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`recipeId`))",
                )
            }
        }

        /**
         * v9 to v10: add the price_items staple flag (v0.4.3, the staples
         * move into the price book). Stored rows default to not-staple;
         * the seed refresh inserts the eleven new staple rows already
         * flagged. Nothing else changes.
         */
        val MIGRATION_9_10: Migration = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE `price_items` ADD COLUMN `staple` INTEGER NOT NULL DEFAULT 0",
                )
            }
        }

        /**
         * v10 to v11: the barcode rework state (PR v0.4.4). Create the
         * scan_substitutions table and add the nullable marker column to
         * grocery_items. Additive only; no stored row is rewritten.
         */
        val MIGRATION_10_11: Migration = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `scan_substitutions` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `planId` TEXT, " +
                        "`originalItemId` TEXT NOT NULL, `substituteItemId` TEXT NOT NULL, " +
                        "`recipeId` TEXT, `substitutePriceCents` INTEGER NOT NULL, " +
                        "`createdAtMillis` INTEGER NOT NULL)",
                )
                db.execSQL(
                    "ALTER TABLE `grocery_items` ADD COLUMN `substitutedForItemId` TEXT",
                )
            }
        }

        /**
         * v11 to v12: create the recipe_overrides table (Phase RI-1).
         * Nothing else changes: tonight-only edits ride the plans'
         * existing nights JSON, so no plan row is rewritten either.
         */
        val MIGRATION_11_12: Migration = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `recipe_overrides` (" +
                        "`recipeId` TEXT NOT NULL, `ingredientsJson` TEXT NOT NULL, " +
                        "`updatedAtMillis` INTEGER NOT NULL, PRIMARY KEY(`recipeId`))",
                )
            }
        }

        /**
         * v12 to v13: create the imported_recipes table (Phase RI-2).
         * Nothing else changes: the table starts empty, and no stored
         * row anywhere is rewritten.
         */
        val MIGRATION_12_13: Migration = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `imported_recipes` (" +
                        "`id` TEXT NOT NULL, `title` TEXT NOT NULL, " +
                        "`servings` INTEGER NOT NULL, `activeMinutes` INTEGER NOT NULL, " +
                        "`cuisine` TEXT NOT NULL, `mainProtein` TEXT NOT NULL, " +
                        "`dietTagsJson` TEXT NOT NULL, `ingredientsJson` TEXT NOT NULL, " +
                        "`stepsJson` TEXT NOT NULL, `sourceUrl` TEXT, " +
                        "`origin` TEXT NOT NULL, `createdAtMillis` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`id`))",
                )
            }
        }

        fun create(context: Context): DinnerBotDatabase =
            Room.databaseBuilder(context.applicationContext, DinnerBotDatabase::class.java, "dinner-bot.db")
                .addMigrations(
                    MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6,
                    MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10,
                    MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13,
                )
                .fallbackToDestructiveMigration()
                .build()
    }
}
