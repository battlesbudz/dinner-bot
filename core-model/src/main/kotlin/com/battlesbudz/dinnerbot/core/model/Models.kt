package com.battlesbudz.dinnerbot.core.model

import kotlinx.serialization.Serializable

// Stores are user data since Phase C1: see Stores.kt for the Store type.

/** A per-unit price for the same item at a store other than its home store. */
@Serializable
data class StorePrice(
    /** The other store's id (see Store); ids, not names, so renames are free. */
    val store: String,
    /** Cents per 1.0 of the item's pack unit (same unit basis as [PriceItem.pricePerUnitCents]). */
    val pricePerUnitCents: Double,
)

/** One purchasable pack of one grocery item at its home store. */
@Serializable
data class PriceItem(
    val id: String,
    val name: String,
    val category: String,
    /** Id of the user's store this item is bought at (see Store). */
    val homeStore: String,
    val packDescription: String,
    val packQuantity: Quantity,
    val packPriceCents: Int,
    /** Cents per 1.0 of [packQuantity]'s unit, when a per-unit price makes sense. */
    val pricePerUnitCents: Double? = null,
    val checkedDate: String,
    val source: String,
    /** True when the user has corrected this price in the app; their number always wins over seed data. */
    val userCorrected: Boolean = false,
    /**
     * Known per-unit prices at other stores. Pure price data since
     * Phase C1: nothing routes a purchase to another store any more;
     * the record survives so a scan-price correction on a line at that
     * store can still be written back against it.
     */
    val alternates: List<StorePrice> = emptyList(),
    /**
     * Short project-written freezing guidance for oversize bulk packs,
     * Phase V2-2. Shown on the grocery line when a pack is bought and
     * on the pantry stock entry while stock is on hand. Since Phase C1
     * the note doubles as the bulk marker: an item carrying one is
     * stock-tracked, at any store. Seed-owned: refreshed from the
     * bundled price book, never user-edited.
     */
    val freezeNote: String? = null,
    /**
     * True for the pantry staples (rice, butter, oil, flour, salt, the
     * spices, soy sauce): priced like every other item since v0.4.3
     * ("everything should be in the pricing book"), but still
     * staples for the rules that treat them differently — the pantry owned
     * checklist covers them by name, and Sunday reconciliation always
     * restocks them rather than only when a planned dinner used them.
     */
    val staple: Boolean = false,
    /**
     * True when this item's price is a placeholder guess, not a shelf
     * price anyone has seen (user correction, 2026-10-24): an
     * ingredient that matched nothing at recipe import becomes a real
     * price book item at its guessed price — everything is in the
     * pricing book — and the first scan or price correction writes the
     * real price and clears this mark. Shown as a guess wherever the
     * price shows, and a recipe using a guess-priced item counts as
     * not yet priced (Phase RI-3) until the item is shopped.
     */
    val guessPriced: Boolean = false,
) {
    /** Cents per 1.0 of [packQuantity]'s unit; derived from the pack price when not stored. */
    val perUnitCents: Double
        get() = pricePerUnitCents ?: (packPriceCents.toDouble() / packQuantity.amount)

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
     * the user's won't-eat rule: true when the dish cannot be made without
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
    /**
     * Where this recipe came from (Recipe Import epic). Every recipe the
     * seed bundle carries is [RecipeOrigin.LIBRARY]; RI-2's imported and
     * hand-made recipes carry their own origin. Defaults to LIBRARY so
     * every stored and bundled recipe written before origins existed
     * reads as a library recipe.
     */
    val origin: RecipeOrigin = RecipeOrigin.LIBRARY,
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
    /**
     * Tonight-only ingredient edits for this night (Phase RI-1, ruling
     * D1): the night's full ingredient list as the user edited it for
     * tonight only. Rides inside the plan's nights JSON, so no plan
     * written before this field existed changes shape; a night without
     * edits reads null and cooks the saved recipe. The edits leave with
     * the dinner: swapping the night's recipe or planning a replacement
     * dinner clears them.
     */
    val tonightEdits: TonightOverrides? = null,
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
    val store: String,
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
    /**
     * Set when this line is on the list because a barcode scan swapped it
     * in for a planned item (PR v0.4.4): the value names the planned
     * item(s) whose lines this one replaced for THIS week only — usually
     * one price book id; a comma-joined list of ids when a write-time
     * merged line that also stood for the substitute's own planned use
     * was itself swapped (see BarcodeScanLogic.displacedOriginsOf). The
     * marker is how repricing the week keeps the swap (the planned lines
     * stay off, this line stays on at its scanned price) instead of
     * regenerating it away. The recipe and future weeks never see it.
     */
    val substitutedForItemId: String? = null,
    /**
     * The owned pantry entry covering this line (Phase RI-3, the user's
     * weekly-list ruling): the line is on the list because a planned
     * dinner needs the item, but the pantry already has it, so it renders
     * grayed and crossed through at $0 with the pantry entry's name shown
     * beneath the item's name. A covered line always has packsToBuy 0.
     * Null on every ordinary buy line.
     */
    val coveredByPantryName: String? = null,
    /**
     * True when this buy line exists because the user uncrossed a covered
     * line for this week (Phase RI-3): the item is in her pantry AND on
     * this week's list at its book price, because the pantry's version is
     * the wrong kind or she wants more. The flag rides the plan's rows so
     * repricing keeps the line a buy line; the pantry row itself is never
     * touched by uncrossing.
     */
    val uncrossed: Boolean = false,
    /**
     * Retired with the user's 2026-10-24 correction: v0.5.2's computed,
     * never-persisted line guess. Guesses now live on the price book
     * item itself ([PriceItem.guessPriced]) and lines price from the
     * item like any other. The field stays only so plans stored by
     * v0.5.2 keep their stored shape; nothing populates it anymore.
     */
    val priceGuessCents: Int? = null,
)

/**
 * The planned items a swap marker names, in order (see
 * [GroceryLine.substitutedForItemId]): every origin a reprice must
 * keep off the fresh list. Item ids are slugs and never contain a
 * comma. Lives in the core model because the planner's stock
 * projection reconciles against the same origins.
 */
fun displacedOriginsOf(marker: String?): List<String> =
    marker?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()

@Serializable
data class PantryItem(
    val id: String,
    val name: String,
    val owned: Boolean = true,
)

/** Quantity-tracked stock for bulk items (see PriceItem.freezeNote). */
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
    /**
     * The user's stores, in the order they shop them (Phase C1). The
     * first is the default wherever a store must be picked before the
     * user has chosen one. Persisted in the stores table, not in the
     * settings row; the data layer combines the two on load.
     */
    val stores: List<Store> = emptyList(),
) {
    /** The user's first chosen store: the only default the app has (Phase C1). */
    val firstStore: Store? get() = stores.firstOrNull()
}

