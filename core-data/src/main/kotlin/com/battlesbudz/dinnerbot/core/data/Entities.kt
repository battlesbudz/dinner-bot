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
    /** Pantry-staple flag (v0.4.3): priced like everything else, staple for coverage and restock rules. */
    @ColumnInfo(defaultValue = "0")
    val staple: Boolean = false,
    /**
     * Guess-priced mark (the user's correction, 2026-10-24): the item
     * entered the book from a recipe import at a guessed price; the
     * first real price write clears it. Database v15.
     */
    @ColumnInfo(defaultValue = "0")
    val guessPriced: Boolean = false,
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

/** Quantity-tracked stock for bulk items. One row per price item. */
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
    /**
     * Legacy column, always 0 since Phase C1 deleted the warehouse
     * savings gate. Kept so existing databases need no table rebuild;
     * nothing reads it.
     */
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
    /**
     * PR v0.4.4: the planned item this line was swapped in for by a
     * barcode scan, or null for an ordinary planner line. Rows written
     * before the column existed read null.
     */
    val substitutedForItemId: String? = null,
    /**
     * Phase RI-3: the owned pantry entry covering this line, when the
     * line is on the list as already home (crossed through, $0). Null
     * on every buy line. Rows written before the column existed read
     * null.
     */
    val coveredByPantryName: String? = null,
    /** Phase RI-3: this buy line exists because the user uncrossed a covered line for this week. */
    @ColumnInfo(defaultValue = "0")
    val uncrossed: Boolean = false,
    /** Phase RI-3: the placeholder price guess carried by a priced-less line; null on priced lines. */
    val priceGuessCents: Int? = null,
)

/**
 * One asked-and-answered first-cook keep prompt (Phase RI-3): the row
 * existing at all is the record that the prompt fired for this recipe,
 * whatever she answered — a thumbs rating, or a dismissal, which
 * records nothing else and never asks again. Working state, not
 * household data: it does not ride in backups (a restored phone has no
 * plans, so there is no first cook to ask about until she cooks again,
 * and being asked once more there is harmless).
 */
@Entity(tableName = "keep_prompt_asked")
data class KeepPromptEntity(
    @PrimaryKey val recipeId: String,
    val askedAtMillis: Long,
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

/**
 * One remembered barcode-scan substitution (PR v0.4.4): a planned item
 * was swapped for a scanned product for one week only, and the app keeps
 * the pairing so it can suggest the same swap next time. [planId] and
 * [recipeId] are context, both nullable; a record restored from a backup
 * carries no plan id.
 */
@Entity(tableName = "scan_substitutions")
data class ScanSubstitutionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val planId: String? = null,
    val originalItemId: String,
    val substituteItemId: String,
    val recipeId: String? = null,
    val substitutePriceCents: Int,
    val createdAtMillis: Long,
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

/**
 * the user's saved household version of one library recipe (Phase RI-1,
 * steps column added v0.6.3): the full ingredient list as she last saved
 * it, JSON-encoded, plus her edited steps once she has saved a steps
 * edit ([stepsJson] stays null until then, so the seed steps stand).
 * One row per edited recipe; absence of a row means the seed version
 * stands, and restore-original deletes the row. Household data in every
 * sense: the seed refresh never writes here, and the rows ride in
 * backups (format v6, steps from format v9).
 */
@Entity(tableName = "recipe_overrides")
data class RecipeOverrideEntity(
    @PrimaryKey val recipeId: String,
    /** JSON-encoded List<RecipeIngredient>: the household ingredient list. */
    val ingredientsJson: String,
    val updatedAtMillis: Long,
    /** JSON-encoded List<String>: her household steps; null = seed steps stand. */
    val stepsJson: String? = null,
)

/**
 * Nutrition facts scanned from one price book item's own product label
 * (v0.6.3): the Open Food Facts nutriments captured when its barcode
 * was looked up, refreshed on every rescan. Household data: the seed
 * refresh never writes here (the USDA seed lives in
 * ingredient_nutrition), and the rows ride in backups (format v9).
 */
@Entity(tableName = "label_nutrition")
data class LabelNutritionEntity(
    @PrimaryKey val itemId: String,
    /** The barcode these facts were read from. */
    val barcode: String,
    /** JSON-encoded NutritionValues: the label's per-100 g facts. */
    val per100gJson: String,
    /** JSON-encoded NutritionValues: the label's per-serving facts, when published. */
    val perServingJson: String?,
    /** The serving as the label words it, display only. */
    val servingSizeText: String?,
    /** Grams in one label serving, when the serving is stated in a weight unit. */
    val servingGrams: Double?,
    val updatedAtMillis: Long,
)

/**
 * One recipe the user brought into the app herself (Phase RI-2): imported
 * from a page or a paste, or created by hand. The recipe fields mirror
 * [RecipeEntity]'s shape, but this is household data, not seed content:
 * the seed refresh never reads or writes this table, and the rows ride
 * in backups (format v7). [origin] stores the RecipeOrigin enum name
 * (IMPORTED or MANUAL); [sourceUrl] is where an imported recipe came
 * from, null for hand-made recipes.
 */
@Entity(tableName = "imported_recipes")
data class ImportedRecipeEntity(
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
    val sourceUrl: String? = null,
    val origin: String,
    val createdAtMillis: Long,
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

/**
 * One of the user's stores (Phase C1): stores are user data, kept in
 * their own table so onboarding and settings can reorder them. The
 * row order is the user's shopping order; [sortOrder] carries it.
 */
@Entity(tableName = "stores")
data class StoreEntity(
    @PrimaryKey val id: String,
    val name: String,
    val sortOrder: Int,
)

/**
 * The single-row device state for the premium gates (Phase C3): the
 * cached entitlement and when it was last checked, the free-tier
 * counters (starter scans, and the scans and imports used in their
 * current months), and the prompt cadence timestamps. This is device
 * state for the freemium layer, NOT household data, and deliberately
 * does not ride in backups.
 */
@Entity(tableName = "premium_state")
data class PremiumStateEntity(
    @PrimaryKey val id: Int = 1,
    val cachedPremium: Boolean = false,
    val premiumCheckedAtMillis: Long = 0,
    val starterScansUsed: Int = 0,
    val scanMonth: String? = null,
    val scansUsedInMonth: Int = 0,
    val importMonth: String? = null,
    val importsUsedInMonth: Int = 0,
    val lastPromptDismissedAtMillis: Long = 0,
    val firstSeenAtMillis: Long = 0,
)

/**
 * One accumulated savings record per planned week (Phase C3): what the
 * week was budgeted and what it actually needed in cash. Plans
 * themselves are replaced on regeneration, so the savings history
 * keeps its own lightweight record. Also device-local history, not
 * backup content.
 */
@Entity(tableName = "savings_records")
data class SavingsRecordEntity(
    @PrimaryKey val weekStart: String,
    val budgetCents: Int,
    val cashNeededCents: Int,
    val updatedAtMillis: Long,
)
