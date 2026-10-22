package com.battlesbudz.dinnerbot.core.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [PriceItemEntity::class, RecipeEntity::class, SettingsEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class DinnerBotDatabase : RoomDatabase() {
    abstract fun priceItemDao(): PriceItemDao
    abstract fun recipeDao(): RecipeDao
    abstract fun settingsDao(): SettingsDao

    companion object {
        fun create(context: Context): DinnerBotDatabase =
            Room.databaseBuilder(context.applicationContext, DinnerBotDatabase::class.java, "dinner-bot.db")
                .build()
    }
}
