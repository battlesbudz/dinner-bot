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
    ],
    version = 5,
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

        fun create(context: Context): DinnerBotDatabase =
            Room.databaseBuilder(context.applicationContext, DinnerBotDatabase::class.java, "dinner-bot.db")
                .addMigrations(MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                .fallbackToDestructiveMigration()
                .build()
    }
}
