package com.battlesbudz.dinnerbot.core.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface PriceItemDao {
    @Query("SELECT COUNT(*) FROM price_items")
    suspend fun count(): Int

    @Query("SELECT * FROM price_items ORDER BY name")
    suspend fun getAll(): List<PriceItemEntity>

    @Query("SELECT * FROM price_items WHERE id = :id")
    suspend fun getById(id: String): PriceItemEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<PriceItemEntity>)
}

@Dao
interface RecipeDao {
    @Query("SELECT COUNT(*) FROM recipes")
    suspend fun count(): Int

    @Query("SELECT * FROM recipes ORDER BY title")
    suspend fun getAll(): List<RecipeEntity>

    @Query("SELECT * FROM recipes WHERE id = :id")
    suspend fun getById(id: String): RecipeEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(recipes: List<RecipeEntity>)
}

@Dao
interface SettingsDao {
    @Query("SELECT * FROM settings WHERE id = 1")
    suspend fun get(): SettingsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(settings: SettingsEntity)
}
