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
    version = 3,
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

        fun create(context: Context): DinnerBotDatabase =
            Room.databaseBuilder(context.applicationContext, DinnerBotDatabase::class.java, "dinner-bot.db")
                .addMigrations(MIGRATION_2_3)
                .fallbackToDestructiveMigration()
                .build()
    }
}
