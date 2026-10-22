package com.battlesbudz.dinnerbot.core.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "price_items")
data class PriceItemEntity(
    @PrimaryKey val id: String,
    val name: String,
    val category: String,
    val homeStore: String,
    val packDescription: String,
    val packAmount: Double,
    val packUnit: String,
    val packPriceCents: Int,
    val pricePerUnitCents: Double?,
    val checkedDate: String,
    val source: String,
    val userCorrected: Boolean,
    /** JSON-encoded List<StorePrice>; alternate per-unit prices at other stores. */
    val alternatesJson: String = "[]",
)

@Entity(tableName = "recipes")
data class RecipeEntity(
    @PrimaryKey val id: String,
    val title: String,
    val servings: Int,
    val activeMinutes: Int,
    val cuisine: String,
    val mainProtein: String,
    /** JSON-encoded List<String>. */
    val dietTagsJson: String,
    /** JSON-encoded List<RecipeIngredient>. */
    val ingredientsJson: String,
    /** JSON-encoded List<String>. */
    val stepsJson: String,
)

@Entity(tableName = "pantry_items")
data class PantryItemEntity(
    @PrimaryKey val id: String,
    val name: String,
    val owned: Boolean,
)

/** Quantity-tracked stock for Sam's Club bulk items only. One row per price item. */
@Entity(tableName = "bulk_stock")
data class BulkStockEntity(
    @PrimaryKey val itemId: String,
    val amount: Double,
    val unit: String,
)

/** A generated week plan. Nights are JSON-encoded List<PlannedNight>. */
@Entity(tableName = "plans")
data class PlanEntity(
    @PrimaryKey val id: String,
    val weekStart: String,
    val budgetCents: Int,
    val cashNeededCents: Int,
    val foodCostCents: Int,
    val samsMonthlySavingsCents: Int,
    /** JSON-encoded List<PlannedNight>. */
    val nightsJson: String,
    /** JSON-encoded List<String>: plain-language planner notes and named restocks. */
    val notesJson: String,
    val createdAtMillis: Long,
)

/** One shopping line belonging to a plan, with persisted shopping state. */
@Entity(tableName = "grocery_items")
data class GroceryItemEntity(
    @PrimaryKey(autoGenerate = true) val rowId: Long = 0,
    val planId: String,
    val itemId: String,
    val displayName: String,
    val store: String,
    val neededAmount: Double,
    val neededUnit: String,
    val packsToBuy: Int,
    /** Estimated price of ONE pack (or of the whole rerouted quantity when packsToBuy is 1 by weight). */
    val packPriceCents: Int,
    val portionCostCents: Int?,
    val checkedOff: Boolean,
)

/** One thumbs rating per recipe (Phase V2-1); absence of a row means unrated. */
@Entity(tableName = "recipe_ratings")
data class RecipeRatingEntity(
    @PrimaryKey val recipeId: String,
    /** RecipeRating enum name: UP or DOWN. */
    val rating: String,
    val updatedAtMillis: Long,
)

@Entity(tableName = "settings")
data class SettingsEntity(
    @PrimaryKey val id: Int = 1,
    val householdSize: Int,
    /** JSON-encoded List<String> of night codes, MON..SUN. */
    val cookNightsJson: String,
    val weeklyBudgetCents: Int,
    /** JSON-encoded List<String>. */
    val wontEatJson: String,
    val dietNotes: String,
    /** JSON-encoded List<String>. */
    val pantryStaplesJson: String,
    val onboardingComplete: Boolean,
)
