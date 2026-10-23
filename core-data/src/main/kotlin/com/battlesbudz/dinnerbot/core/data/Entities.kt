package com.battlesbudz.dinnerbot.core.data

import androidx.room.ColumnInfo
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
    /** Freeze guidance for oversize packs (Phase V2-2). Seed-owned text, refreshed from the bundle. */
    val freezeNote: String? = null,
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
    /** Phase V2-7: the line came from (or was topped up by) a Sunday reconciliation. */
    @ColumnInfo(defaultValue = "0")
    val reconciliationAdded: Boolean = false,
)

/**
 * One ingredient's USDA nutrition entry (Phase V2-6). Seed content,
 * refreshed wholesale from the bundled nutrition.json; never user-edited,
 * so a refresh can never destroy user data. The per-100 g columns are
 * nullable exactly where FoodData Central publishes no value.
 */
@Entity(tableName = "ingredient_nutrition")
data class IngredientNutritionEntity(
    @PrimaryKey val itemId: String,
    val pantryItem: Boolean,
    val fdcId: Long,
    val fdcDescription: String,
    val fdcDataType: String,
    val closestMatch: Boolean,
    val source: String,
    val caloriesKcal: Double?,
    val proteinG: Double?,
    val fatG: Double?,
    val carbohydrateG: Double?,
    val fiberG: Double?,
    val sugarsG: Double?,
    val sodiumMg: Double?,
    val saturatedFatG: Double?,
    val cholesterolMg: Double?,
    /** JSON-encoded Map<String, Double>: grams for one unit of a non-weight unit, keyed by unit name. */
    val gramWeightsJson: String = "{}",
    val gramWeightSource: String? = null,
)

/** One learned barcode link per barcode (Phase V2-5); the barcode is the key. */
@Entity(tableName = "barcode_links")
data class BarcodeLinkEntity(
    @PrimaryKey val barcode: String,
    val itemId: String,
    val createdAtMillis: Long,
)

/**
 * One household photo per recipe (Epic issue #10): a reference to the
 * image file in app-local storage plus its credit. Absence of a row
 * means the recipe has no photo and renders exactly as before. The image
 * bytes are never in the database and never in the backup file.
 */
@Entity(tableName = "recipe_photos")
data class RecipePhotoEntity(
    @PrimaryKey val recipeId: String,
    val fileName: String,
    val credit: String,
    val updatedAtMillis: Long,
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
    /** JSON-encoded List<String> of selected diet preset ids (Phase V2-2). */
    @ColumnInfo(defaultValue = "[]")
    val dietPresetsJson: String = "[]",
    val dietNotes: String,
    /** JSON-encoded List<String>. */
    val pantryStaplesJson: String,
    val onboardingComplete: Boolean,
    /** JSON-encoded ReminderSettings (Phase V2-4); "{}" decodes to every reminder off. */
    @ColumnInfo(defaultValue = "'{}'")
    val remindersJson: String = "{}",
    /** JSON-encoded List<String>: plan ids whose cycle a reconciliation consumed (Phase V2-7). */
    @ColumnInfo(defaultValue = "[]")
    val reconciledPlanIdsJson: String = "[]",
)

/**
 * One dated ran-out record (Phase V2-7): marked while cooking, inferred
 * and confirmed or dismissed at a Sunday reconciliation, or checked off
 * the pantry list there. [source] and [state] store the enum names.
 */
@Entity(tableName = "ran_out_events")
data class RanOutEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val itemKey: String,
    val displayName: String,
    val stapleSide: Boolean,
    val priceItemId: String?,
    val bulkItemId: String?,
    val pantryStapleName: String?,
    val dateMarked: String,
    val source: String,
    val state: String,
    val cyclePlanId: String?,
)

/**
 * Ingredient check-offs inside one planned dinner (Phase V2-7, step 0):
 * working state for the cook view so a half-cooked night can be resumed.
 * Cleared when the plan is replaced.
 */
@Entity(tableName = "cook_checkoffs", primaryKeys = ["planId", "day", "itemKey"])
data class CookCheckoffEntity(
    val planId: String,
    val day: String,
    val itemKey: String,
    val checked: Boolean,
)
