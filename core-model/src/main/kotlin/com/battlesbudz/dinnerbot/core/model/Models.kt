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
    /**
     * Short project-written freezing guidance for oversize (Sam's Club bulk)
     * packs, Phase V2-2. Shown on the grocery line when a pack is bought and
     * on the pantry stock entry while stock is on hand. Seed-owned: refreshed
     * from the bundled price book, never user-edited.
     */
    val freezeNote: String? = null,
    /**
     * True for the pantry staples (rice, butter, oil, flour, salt, the
     * spices, soy sauce): priced like every other item since v0.4.3
     * (Battles: "everything should be in the pricing book"), but still
     * staples for the rules that treat them differently — the pantry owned
     * checklist covers them by name, and Sunday reconciliation always
     * restocks them rather than only when a planned dinner used them.
     */
    val staple: Boolean = false,
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
 * price book item) or [pantryItem] (a named pantry staple, not priced) is
 * set. Since v0.4.3 every shipped recipe references price items only: the
 * staples are priced book items flagged [PriceItem.staple]. The pantryItem
 * form remains for stored data written before that change.
 */
@Serializable
data class RecipeIngredient(
    val priceItemId: String? = null,
    val pantryItem: String? = null,
    val quantity: Quantity,
    val note: String? = null,
    /**
     * Battles's won't-eat rule: true when the dish cannot be made without
     * this ingredient, false when the dish works fine without it. Defaults
     * to true so an unmarked ingredient is treated as needed, never dropped.
     */
    val essential: Boolean = true,
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
data class PlannedNight(
    val day: String,
    val recipeId: String,
    /**
     * Per-night exception inside one generated week (Phase V2-2): true when
     * this specific night is marked not cooking. Distinct from the setup
     * cook-nights setting, which stays the default shape of a week. While a
     * night is off, its dinner, ingredients, and cash are out of the plan
     * and the list; [recipeId] is kept so the stored plan round-trips, and
     * marking the night back on plans a dinner under the normal rules.
     */
    val notCooking: Boolean = false,
    /** Why the night is off: "leftovers", "eating out", or "other". */
    val notCookingReason: String? = null,
)

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
    /**
     * True when this line exists (or was topped up to a full pack)
     * because a Sunday reconciliation confirmed the item ran out
     * (Phase V2-7). Inside a fortnight such a line for week 2 belongs to
     * the top-up shop, never to the big trip that already happened.
     */
    val reconciliationAdded: Boolean = false,
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
    /** Ids of the diet presets (seed-data/diet-presets.json) switched on for this household, Phase V2-2. */
    val dietPresets: List<String> = emptyList(),
    val dietNotes: String = "",
    val pantryStaples: List<String> = emptyList(),
    val onboardingComplete: Boolean = false,
    /** Local on-device reminders (Phase V2-4); every reminder starts off. */
    val reminders: ReminderSettings = ReminderSettings(),
    /**
     * Ids of stored plans whose cooking cycle a Sunday reconciliation has
     * already consumed (Phase V2-7): a completed check, or the lighter
     * week-2 check of a fortnight, marks its source plans here so the
     * same week is never reconciled twice.
     */
    val reconciledPlanIds: List<String> = emptyList(),
)

object HouseholdRules {
    /** Battles 2026-10-21: this household never buys beef; ground turkey is always used instead. */
    const val GROUND_MEAT_SUBSTITUTION =
        "We never buy beef. Ground turkey is always used in place of ground beef."
    const val SUBSTITUTE_ITEM_ID = "ground-turkey"
}
