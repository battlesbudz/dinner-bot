package com.battlesbudz.dinnerbot.core.model

import kotlinx.serialization.Serializable

/**
 * Where a recipe came from (Recipe Import epic, plan section 3). RI-1
 * ships with every recipe [LIBRARY]; RI-2's imported and hand-made
 * recipes carry the other origins, and the planner's won't-eat exemption
 * (ruling 3) and the first-cook keep prompt (ruling 7) key on this.
 */
@Serializable
enum class RecipeOrigin { LIBRARY, IMPORTED, MANUAL }

/**
 * Andrea's saved household version of one library recipe (Phase RI-1,
 * steps added v0.6.3). The override stores the recipe's FULL ingredient
 * list as she last saved it, and, once she edits the steps, her steps
 * too: [steps] is null while she has never edited the steps, which
 * means the seed steps stand (an ingredients-only save must never wipe
 * a steps edit, and a steps-only save must never wipe her ingredient
 * list, so the two halves are saved independently and merged here).
 * Title, servings, and tags always stay the seed's. Absence of a row
 * means the seed version stands, and restore-original is deleting the
 * row. Overrides are household data: they ride in backups and no seed
 * refresh ever writes to them (Battles's rule: changed recipes are
 * never refreshed; refresh delivers additions only).
 */
@Serializable
data class RecipeOverride(
    val recipeId: String,
    val ingredients: List<RecipeIngredient>,
    val updatedAtMillis: Long,
    /** Her household steps for this recipe; null = seed steps stand. */
    val steps: List<String>? = null,
)

/**
 * One planned night's tonight-only ingredient edits (Phase RI-1, ruling
 * D1): the night's full ingredient list as edited for that night only.
 * Stored on the night's [PlannedNight] record inside the plan, so the
 * edits live exactly as long as the night does and never touch the
 * saved recipe. Applied LAST in the per-night resolution order
 * (saved recipe, then won't-eat adaptation, then tonight-only edits).
 */
@Serializable
data class TonightOverrides(
    val ingredients: List<RecipeIngredient>,
)

/**
 * The ingredient editor's operations (Phase RI-1), pure list transforms
 * shared by every door into the editor (recipe screen, planned night /
 * cook view, and RI-2's import review). Each returns the new full list;
 * an edit session saves the resulting list wholesale, as a
 * [RecipeOverride] or as a night's [TonightOverrides].
 */
object RecipeEditing {

    /** Replace the ingredient at [index] with price book [item], keeping its quantity, note, and essential mark. */
    fun substitute(
        ingredients: List<RecipeIngredient>,
        index: Int,
        item: PriceItem,
    ): List<RecipeIngredient> {
        require(index in ingredients.indices) { "no ingredient at index $index" }
        return ingredients.mapIndexed { i, ingredient ->
            if (i == index) {
                ingredient.copy(priceItemId = item.id, pantryItem = null)
            } else {
                ingredient
            }
        }
    }

    /** Take the ingredient at [index] out of the list. */
    fun remove(ingredients: List<RecipeIngredient>, index: Int): List<RecipeIngredient> {
        require(index in ingredients.indices) { "no ingredient at index $index" }
        return ingredients.filterIndexed { i, _ -> i != index }
    }

    /** Change the quantity of the ingredient at [index]; unit changes ride along in [quantity]. */
    fun setQuantity(
        ingredients: List<RecipeIngredient>,
        index: Int,
        quantity: Quantity,
    ): List<RecipeIngredient> {
        require(index in ingredients.indices) { "no ingredient at index $index" }
        require(quantity.amount > 0.0) { "quantity must be positive" }
        return ingredients.mapIndexed { i, ingredient ->
            if (i == index) ingredient.copy(quantity = quantity) else ingredient
        }
    }

    /** Flip the essential / removable mark of the ingredient at [index] (Battles's won't-eat rule reads this mark). */
    fun setEssential(
        ingredients: List<RecipeIngredient>,
        index: Int,
        essential: Boolean,
    ): List<RecipeIngredient> {
        require(index in ingredients.indices) { "no ingredient at index $index" }
        return ingredients.mapIndexed { i, ingredient ->
            if (i == index) ingredient.copy(essential = essential) else ingredient
        }
    }

    /** Add a price book item to the list at [quantity]; new ingredients default to essential, like unmarked seed ingredients. */
    fun add(
        ingredients: List<RecipeIngredient>,
        item: PriceItem,
        quantity: Quantity,
        essential: Boolean = true,
        note: String? = null,
    ): List<RecipeIngredient> {
        require(quantity.amount > 0.0) { "quantity must be positive" }
        return ingredients + RecipeIngredient(
            priceItemId = item.id,
            quantity = quantity,
            note = note,
            essential = essential,
        )
    }
}

/**
 * The effective recipe set: the ONE merge point where seed recipes and
 * household data become the recipes every surface consumes (Phase RI-1,
 * plan section 5.1). The merge is pure: stored seed recipes plus the
 * household override rows in, effective recipes out. A recipe with an
 * override cooks her ingredient list and, once she has edited them,
 * her steps; title, servings, and tags stay the seed's. There is no
 * second path that can leak the seed version of a corrected dish.
 */
object EffectiveRecipes {

    /** [recipe] with [override]'s ingredient list (and steps, once edited) standing in for its own, when the override names this recipe. */
    fun applyOverride(recipe: Recipe, override: RecipeOverride?): Recipe {
        if (override == null || override.recipeId != recipe.id) return recipe
        return recipe.copy(
            ingredients = override.ingredients,
            steps = override.steps ?: recipe.steps,
        )
    }

    /** Every recipe with its household override applied; overrides naming no known recipe are ignored. */
    fun applyOverrides(
        recipes: List<Recipe>,
        overrides: Map<String, RecipeOverride>,
    ): List<Recipe> = recipes.map { applyOverride(it, overrides[it.id]) }

    /**
     * The full effective set (Phase RI-2): the library recipes (already
     * override-merged by [applyOverrides]) plus Andrea's own imported
     * and hand-made recipes. User recipes live in their own table and
     * their ids are prefixed, so a collision cannot occur; the filter
     * is a guard, and a library id always stands over a duplicate.
     */
    fun mergeUserRecipes(
        library: List<Recipe>,
        userRecipes: List<Recipe>,
    ): List<Recipe> {
        if (userRecipes.isEmpty()) return library
        val libraryIds = library.map { it.id }.toSet()
        return library + userRecipes.filter { it.id !in libraryIds }
    }

    /** True when the household has a saved edit for [recipeId] (the recipe screen's "your edits" marker reads this). */
    fun hasOverride(recipeId: String, overrides: Map<String, RecipeOverride>): Boolean =
        recipeId in overrides

    /**
     * One night's dinner as it will actually be cooked: the saved recipe
     * (already override-merged and won't-eat adapted by the caller) with
     * the night's tonight-only edits applied last. A night with no edits
     * resolves to the saved recipe unchanged.
     */
    fun applyTonight(recipe: Recipe, night: PlannedNight): Recipe {
        val edits = night.tonightEdits ?: return recipe
        return recipe.copy(ingredients = edits.ingredients)
    }

    /** The recipe one planned night cooks, resolved from [recipesById]; null when the night's recipe is unknown. */
    fun resolveNight(night: PlannedNight, recipesById: Map<String, Recipe>): Recipe? =
        recipesById[night.recipeId]?.let { applyTonight(it, night) }

    /** The dinners a set of nights cooks, in night order, not-cooking nights out. */
    fun resolveDinners(nights: List<PlannedNight>, recipesById: Map<String, Recipe>): List<Recipe> =
        nights.filterNot { it.notCooking }.mapNotNull { resolveNight(it, recipesById) }
}
