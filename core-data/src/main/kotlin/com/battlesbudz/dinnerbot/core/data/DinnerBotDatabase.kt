package com.battlesbudz.dinnerbot.core.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Database version 2 (Phase 2) adds pantry_items, bulk_stock, plans and
 * grocery_items, plus the price_items.alternatesJson column.
 *
 * Migration choice: fallbackToDestructiveMigration. Dinner Bot is pre-1.0 and
 * has never shipped a release, so there is no installed user data to protect;
 * the seed loader refills price items and recipes after a destructive bump,
 * and onboarding settings can be re-entered. Revisit before any post-1.0
 * schema change.
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
