package com.battlesbudz.dinnerbot.core.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Database version 2 (Phase 2) adds pantry_items, bulk_stock, plans and
 * grocery_items, plus the price_items.alternatesJson column.
 *
 * v0.1.1 deliberately keeps version 2: the essential/removable ingredient
 * marks live inside the recipes JSON column, so there is NO schema change,
 * no migration runs, and no user table (settings, price_items, pantry,
 * bulk_stock, plans, grocery_items) is touched on upgrade. SeedLoader
 * refreshes only the recipes table from the bundled asset; recipes are
 * seed content the user cannot edit.
 *
 * fallbackToDestructiveMigration remains only as a pre-1.0 safety net for
 * development installs; it does not trigger without a version bump. Any
 * future schema change after v0.1.0 ships real user data must ship a real
 * migration instead.
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
    ],
    version = 2,
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

    companion object {
        fun create(context: Context): DinnerBotDatabase =
            Room.databaseBuilder(context.applicationContext, DinnerBotDatabase::class.java, "dinner-bot.db")
                .fallbackToDestructiveMigration()
                .build()
    }
}
