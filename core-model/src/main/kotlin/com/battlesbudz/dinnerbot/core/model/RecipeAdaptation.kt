package com.battlesbudz.dinnerbot.core.model

/**
 * The won't-eat rule, exactly as the user defined it (2026-10-22).
 *
 * Every ingredient in a recipe is marked [RecipeIngredient.essential] (the
 * dish cannot be made without it) or removable (the dish works without it).
 * Against the household won't-eat list, matched by ingredient name ignoring
 * case:
 *
 * - A won't-eat ingredient that is REMOVABLE comes out of the recipe
 *   everywhere the recipe is used: the recipe detail view, plan costing,
 *   and grocery list aggregation. The recipe itself stays.
 * - A won't-eat ingredient that is ESSENTIAL means the dish cannot be made
 *   for this household, so the recipe is kept out of generated weeks: its
 *   id lands in [Result.blockedFromPlanning] and the planner never sees it.
 *   Recipe browsing is otherwise unchanged.
 *
 * The rule above governs LIBRARY recipes. A recipe the user added herself
 * (imported or hand-made, Phase RI-2) is exempt under the user's ruling 3:
 * it passes through untouched, never blocked and never stripped, and
 * cooks as written; her ingredient editing is its adaptation path.
 *
 * This is the one adaptation step shared by the planner, the recipes UI,
 * costing, and the grocery list, so the rule behaves identically everywhere.
 */
object RecipeAdaptation {

    data class Result(
        /** Every recipe, with removable won't-eat ingredients stripped out. */
        val recipes: List<Recipe>,
        /** Ids of recipes that must never be generated into a week. */
        val blockedFromPlanning: Set<String>,
    ) {
        /** The recipes the planner is allowed to use. */
        val plannable: List<Recipe> get() = recipes.filter { it.id !in blockedFromPlanning }
    }

    fun adapt(
        recipes: List<Recipe>,
        wontEat: List<String>,
        itemsById: Map<String, PriceItem>,
    ): Result {
        val terms = wontEat.map { it.trim().lowercase() }.filter { it.isNotEmpty() }
        if (terms.isEmpty()) return Result(recipes, emptySet())

        val blocked = mutableSetOf<String>()
        val adapted = recipes.map { recipe ->
            // Ruling 3 (Recipe Import epic): a recipe the user added
            // herself — imported or hand-made — is exempt from the
            // won't-eat planner entirely. It is never blocked and none
            // of its ingredients are stripped at read time; it cooks
            // exactly as written, and her editing is its adaptation
            // path. (Plan section 11's reading of the exemption's
            // reach: rotation block AND read-time stripping.)
            if (recipe.origin != RecipeOrigin.LIBRARY) return@map recipe
            var isBlocked = false
            val kept = recipe.ingredients.filter { ingredient ->
                val name = ingredientName(ingredient, itemsById)
                val matched = name != null && terms.any { term -> nameMatches(name, term) }
                when {
                    !matched -> true
                    ingredient.essential -> {
                        // The dish needs this ingredient and the household
                        // won't eat it: the recipe cannot be planned. The
                        // ingredient itself stays in the stored recipe.
                        isBlocked = true
                        true
                    }
                    else -> false // removable and not eaten: out it comes
                }
            }
            if (isBlocked) blocked += recipe.id
            if (kept.size == recipe.ingredients.size) recipe else recipe.copy(ingredients = kept)
        }
        return Result(adapted, blocked)
    }

    /**
     * The name a won't-eat entry is matched against: the pantry staple name,
     * or the price book name of the priced item (items name themselves
     * since Phase C1: there is no substitution layer to resolve through).
     */
    fun ingredientName(ingredient: RecipeIngredient, itemsById: Map<String, PriceItem>): String? {
        ingredient.pantryItem?.let { return it }
        val rawId = ingredient.priceItemId ?: return null
        val item = itemsById[rawId]
        return item?.name ?: rawId
    }

    /**
     * Case-insensitive name match. A term matches when it appears in the
     * ingredient name ("onion" in "Yellow onions") or the name appears in
     * the term ("onions" entered for an ingredient named "Onion"), so
     * everyday plural and phrasing differences still land.
     */
    fun nameMatches(ingredientName: String, term: String): Boolean {
        val name = ingredientName.trim().lowercase()
        val t = term.trim().lowercase()
        if (name.isEmpty() || t.isEmpty()) return false
        return name.contains(t) || t.contains(name)
    }
}
