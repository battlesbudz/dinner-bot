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

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: PriceItemEntity)

    /** Andrea's in-app correction: her number always wins and is flagged as user-corrected. */
    @Query(
        "UPDATE price_items SET packPriceCents = :packPriceCents, pricePerUnitCents = :pricePerUnitCents, " +
            "userCorrected = 1 WHERE id = :id"
    )
    suspend fun updatePrice(id: String, packPriceCents: Int, pricePerUnitCents: Double?)

    @Query("UPDATE price_items SET homeStore = :store, userCorrected = 1 WHERE id = :id")
    suspend fun updateHomeStore(id: String, store: String)

    @Query("DELETE FROM price_items")
    suspend fun deleteAll()
}

@Dao
interface PantryDao {
    @Query("SELECT * FROM pantry_items ORDER BY name")
    suspend fun getAll(): List<PantryItemEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: PantryItemEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<PantryItemEntity>)

    @Query("DELETE FROM pantry_items")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM pantry_items")
    suspend fun count(): Int
}

@Dao
interface BulkStockDao {
    @Query("SELECT * FROM bulk_stock")
    suspend fun getAll(): List<BulkStockEntity>

    @Query("SELECT * FROM bulk_stock WHERE itemId = :itemId")
    suspend fun getById(itemId: String): BulkStockEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(stock: BulkStockEntity)

    @Query("DELETE FROM bulk_stock WHERE itemId = :itemId")
    suspend fun delete(itemId: String)

    @Query("DELETE FROM bulk_stock")
    suspend fun deleteAll()
}

@Dao
interface PlanDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(plan: PlanEntity)

    @Query("SELECT * FROM plans ORDER BY createdAtMillis DESC LIMIT 1")
    suspend fun getLatest(): PlanEntity?

    @Query("SELECT * FROM plans WHERE id = :id")
    suspend fun getById(id: String): PlanEntity?

    @Query("DELETE FROM plans")
    suspend fun deleteAll()
}

@Dao
interface GroceryDao {
    @Insert
    suspend fun insertAll(items: List<GroceryItemEntity>)

    @Query("SELECT * FROM grocery_items WHERE planId = :planId ORDER BY store, displayName")
    suspend fun getForPlan(planId: String): List<GroceryItemEntity>

    @Query("UPDATE grocery_items SET checkedOff = :checked WHERE rowId = :rowId")
    suspend fun setChecked(rowId: Long, checked: Boolean)

    @Query("UPDATE grocery_items SET packPriceCents = :packPriceCents WHERE rowId = :rowId")
    suspend fun updatePackPrice(rowId: Long, packPriceCents: Int)

    @Query("DELETE FROM grocery_items WHERE planId = :planId")
    suspend fun deleteForPlan(planId: String)

    @Query("DELETE FROM grocery_items")
    suspend fun deleteAll()
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
