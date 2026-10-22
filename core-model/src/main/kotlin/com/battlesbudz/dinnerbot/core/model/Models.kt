package com.battlesbudz.dinnerbot.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class Store { ALDI, WALMART, SAMS_CLUB }

/** A per-unit price for the same item at a store other than its home store. */
@Serializable
data class StorePrice(
    val store: Store,
    /** Cents per 1.0 of the item's pack unit (same unit basis as [PriceItem.pricePerUnitCents]). */
    val pricePerUnitCents: Double,
)

/** One purchasable pack of one grocery item at its home store. */
@Serializable
data class PriceItem(
    val id: String,
    val name: String,
    val category: String,
    val homeStore: Store,
    val packDescription: String,
    val packQuantity: Quantity,
    val packPriceCents: Int,
    /** Cents per 1.0 of [packQuantity]'s unit, when a per-unit price makes sense. */
    val pricePerUnitCents: Double? = null,
    val checkedDate: String,
    val source: String,
    /** True when Andrea has corrected this price in the app; her number always wins over seed data. */
    val userCorrected: Boolean = false,
    /** Known per-unit prices at other stores, used by the Sam's Club savings gate. */
    val alternates: List<StorePrice> = emptyList(),
) {
    /** Cents per 1.0 of [packQuantity]'s unit; derived from the pack price when not stored. */
    val perUnitCents: Double
        get() = pricePerUnitCents ?: (packPriceCents.toDouble() / packQuantity.amount)

    /** Cheapest known non-Sam's per-unit price, or null when no alternate is known. */
    fun cheapestRegularAlternate(): StorePrice? =
        alternates.filter { it.store != Store.SAMS_CLUB }.minByOrNull { it.pricePerUnitCents }
}

@Serializable
data class PriceBookFile(val version: Int, val items: List<PriceItem>)

/**
 * One structured recipe ingredient. Exactly one of [priceItemId] (a priced
 * price book item) or [pantryItem] (a named pantry staple, not priced) is set.
 */
@Serializable
data class RecipeIngredient(
    val priceItemId: String? = null,
    val pantryItem: String? = null,
    val quantity: Quantity,
    val note: String? = null,
) {
    init {
        require((priceItemId == null) != (pantryItem == null)) {
            "ingredient must reference a price item or a pantry item, not both/neither"
        }
    }
    val isPantry: Boolean get() = pantryItem != null
}

@Serializable
data class Recipe(
    val id: String,
    val title: String,
    val servings: Int,
    val activeMinutes: Int,
    val cuisine: String,
    val mainProtein: String,
    val dietTags: List<String> = emptyList(),
    val ingredients: List<RecipeIngredient>,
    val steps: List<String>,
) {
    /** Scale every ingredient from the base [servings] to [forServings]. */
    fun scaledIngredients(forServings: Int): List<RecipeIngredient> {
        require(forServings > 0) { "servings must be positive" }
        val factor = forServings.toDouble() / servings.toDouble()
        if (factor == 1.0) return ingredients
        return ingredients.map { it.copy(quantity = it.quantity * factor) }
    }
}

@Serializable
data class RecipesFile(val version: Int, val recipes: List<Recipe>)

@Serializable
data class PlannedNight(val day: String, val recipeId: String)

@Serializable
data class MealPlan(
    val id: String,
    val weekStart: String,
    val budgetCents: Int,
    val nights: List<PlannedNight>,
)

/** A computed shopping line. Generated from plan + pantry + price book, never stored as truth. */
@Serializable
data class GroceryLine(
    val itemId: String,
    val displayName: String,
    val store: Store,
    val needed: Quantity,
    val packsToBuy: Int,
    val estimatedPackPriceCents: Int,
    val portionCostCents: Int? = null,
    val checkedOff: Boolean = false,
)

@Serializable
data class PantryItem(
    val id: String,
    val name: String,
    val owned: Boolean = true,
)

/** Quantity-tracked stock for Sam's Club bulk items only. */
@Serializable
data class BulkStock(
    val itemId: String,
    val onHand: Quantity,
)

@Serializable
data class HouseholdSettings(
    val householdSize: Int = 4,
    val cookNights: List<String> = listOf("MON", "TUE", "WED", "THU", "FRI"),
    val weeklyBudgetCents: Int = 0,
    val wontEat: List<String> = emptyList(),
    val dietNotes: String = "",
    val pantryStaples: List<String> = emptyList(),
    val onboardingComplete: Boolean = false,
)

object HouseholdRules {
    /** Battles 2026-10-21: this household never buys beef; ground turkey is always used instead. */
    const val GROUND_MEAT_SUBSTITUTION =
        "We never buy beef. Ground turkey is always used in place of ground beef."
    const val SUBSTITUTE_ITEM_ID = "ground-turkey"
}
