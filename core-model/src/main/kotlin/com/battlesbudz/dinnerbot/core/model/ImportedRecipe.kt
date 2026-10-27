package com.battlesbudz.dinnerbot.core.model

import kotlinx.serialization.Serializable

/**
 * A recipe the user brought into the app (Phase RI-2): imported
 * from a page or a paste, or created by hand. User-owned data in every
 * sense (plan ruling 11): it lives in its own table, no seed refresh
 * ever touches it, it rides in backups, and its ratings and photos key
 * off the recipe id in their existing tables.
 *
 * The [recipe] is a full library-shaped [Recipe] whose origin is
 * [RecipeOrigin.IMPORTED] or [RecipeOrigin.MANUAL]; that origin is what
 * the planner's won't-eat exemption (ruling 3) and RI-3's first-cook
 * keep prompt key on. [sourceLink] is where an imported recipe came
 * from, shown on its screen; a hand-made recipe has none.
 */
@Serializable
data class ImportedRecipe(
    val recipe: Recipe,
    val sourceLink: String? = null,
    val createdAtMillis: Long,
) {
    init {
        require(recipe.origin == RecipeOrigin.IMPORTED || recipe.origin == RecipeOrigin.MANUAL) {
            "an imported recipe record carries an IMPORTED or MANUAL recipe, not ${recipe.origin}"
        }
    }
}

/**
 * Ids for user-added recipes, in their own namespace (Phase RI-2): the
 * prefix guarantees an imported or hand-made id can never collide with
 * a seed recipe id, and the timestamp keeps two recipes with the same
 * title distinct.
 */
object ImportedRecipeIds {
    const val IMPORTED_PREFIX = "imported-"
    const val MANUAL_PREFIX = "manual-"

    fun newId(origin: RecipeOrigin, title: String, nowMillis: Long): String {
        val prefix = when (origin) {
            RecipeOrigin.IMPORTED -> IMPORTED_PREFIX
            RecipeOrigin.MANUAL -> MANUAL_PREFIX
            RecipeOrigin.LIBRARY ->
                throw IllegalArgumentException("library recipes keep their seed ids")
        }
        val slug = title.lowercase()
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
            .take(40)
            .ifEmpty { "recipe" }
        return "$prefix$nowMillis-$slug"
    }
}
