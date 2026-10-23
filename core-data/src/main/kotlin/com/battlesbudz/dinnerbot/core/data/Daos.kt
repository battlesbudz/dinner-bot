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

    /** Seed-owned freeze guidance refresh (Phase V2-2): touches only the note, never prices. */
    @Query("UPDATE price_items SET freezeNote = :freezeNote WHERE id = :id")
    suspend fun updateFreezeNote(id: String, freezeNote: String?)

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

    /**
     * The most recent generation's plans in week order (Phase V2-3): one
     * row for a one-week plan, two for a fortnight. Generation clears the
     * table first, so everything returned belongs to the same generation.
     */
    @Query("SELECT * FROM plans ORDER BY weekStart ASC, createdAtMillis ASC LIMIT :limit")
    suspend fun getRecent(limit: Int): List<PlanEntity>

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

    @Query("DELETE FROM recipes")
    suspend fun deleteAll()
}

@Dao
interface RecipeRatingDao {
    @Query("SELECT * FROM recipe_ratings")
    suspend fun getAll(): List<RecipeRatingEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(rating: RecipeRatingEntity)

    @Query("DELETE FROM recipe_ratings WHERE recipeId = :recipeId")
    suspend fun delete(recipeId: String)

    @Query("DELETE FROM recipe_ratings")
    suspend fun deleteAll()
}

@Dao
interface BarcodeLinkDao {
    @Query("SELECT * FROM barcode_links ORDER BY barcode")
    suspend fun getAll(): List<BarcodeLinkEntity>

    @Query("SELECT * FROM barcode_links WHERE barcode = :barcode")
    suspend fun getByBarcode(barcode: String): BarcodeLinkEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(link: BarcodeLinkEntity)

    @Query("DELETE FROM barcode_links WHERE barcode = :barcode")
    suspend fun delete(barcode: String)

    @Query("DELETE FROM barcode_links")
    suspend fun deleteAll()
}

@Dao
interface IngredientNutritionDao {
    @Query("SELECT COUNT(*) FROM ingredient_nutrition")
    suspend fun count(): Int

    @Query("SELECT * FROM ingredient_nutrition ORDER BY itemId")
    suspend fun getAll(): List<IngredientNutritionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entries: List<IngredientNutritionEntity>)

    @Query("DELETE FROM ingredient_nutrition")
    suspend fun deleteAll()
}

@Dao
interface RanOutDao {
    @Query("SELECT * FROM ran_out_events ORDER BY dateMarked, id")
    suspend fun getAll(): List<RanOutEventEntity>

    @Query("SELECT * FROM ran_out_events WHERE state = :state ORDER BY dateMarked, id")
    suspend fun getByState(state: String): List<RanOutEventEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(event: RanOutEventEntity): Long

    @Query("UPDATE ran_out_events SET state = :state WHERE id = :id")
    suspend fun setState(id: Long, state: String)

    /** Un-marking in the cook view removes that ingredient's pending cooking mark. */
    @Query("DELETE FROM ran_out_events WHERE itemKey = :itemKey AND state = 'PENDING' AND source = 'COOKING'")
    suspend fun deletePendingCookingMark(itemKey: String)

    /** Restock bookkeeping: every confirmed record for one priced item. */
    @Query("UPDATE ran_out_events SET state = 'RESTOCKED' WHERE state = 'CONFIRMED' AND priceItemId = :itemId")
    suspend fun markRestockedForItem(itemId: String)

    /** Restock bookkeeping: every confirmed record for one pantry staple name. */
    @Query(
        "UPDATE ran_out_events SET state = 'RESTOCKED' WHERE state = 'CONFIRMED' " +
            "AND pantryStapleName = :name COLLATE NOCASE"
    )
    suspend fun markRestockedForStaple(name: String)

    @Query("DELETE FROM ran_out_events")
    suspend fun deleteAll()
}

@Dao
interface CookCheckoffDao {
    @Query("SELECT * FROM cook_checkoffs")
    suspend fun getAll(): List<CookCheckoffEntity>

    @Query("SELECT * FROM cook_checkoffs WHERE planId = :planId")
    suspend fun getForPlan(planId: String): List<CookCheckoffEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(checkoff: CookCheckoffEntity)

    @Query("DELETE FROM cook_checkoffs WHERE planId = :planId")
    suspend fun deleteForPlan(planId: String)

    @Query("DELETE FROM cook_checkoffs")
    suspend fun deleteAll()
}

@Dao
interface SettingsDao {
    @Query("SELECT * FROM settings WHERE id = 1")
    suspend fun get(): SettingsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(settings: SettingsEntity)
}
