package com.battlesbudz.dinnerbot.feature.`import`

import com.battlesbudz.dinnerbot.core.model.Costing
import com.battlesbudz.dinnerbot.core.model.Dimension
import com.battlesbudz.dinnerbot.core.model.ImportedRecipe
import com.battlesbudz.dinnerbot.core.model.MeasureUnit
import com.battlesbudz.dinnerbot.core.model.PriceItem
import com.battlesbudz.dinnerbot.core.model.Quantity
import com.battlesbudz.dinnerbot.core.model.Recipe
import com.battlesbudz.dinnerbot.core.model.RecipeEditing
import com.battlesbudz.dinnerbot.core.model.RecipeIngredient
import com.battlesbudz.dinnerbot.core.model.RecipeOrigin

/**
 * One ingredient row on the import review screen (Phase RI-2, plan
 * section 5.4): the source line beside the app's reading of it.
 * [ingredient] is the current reading as a recipe ingredient (a matched
 * book item, or the parsed name staying as written text); [suggestion]
 * and [quantitySuggestion] are offers the user can accept, never silent
 * applications; [pantryCovered] is read from the costing layer, the
 * same computation the weekly list uses, so the review and the list
 * can never disagree about what is already home.
 */
data class ReviewIngredient(
    val sourceLine: String,
    val ingredient: RecipeIngredient,
    val parsed: ParsedIngredient? = null,
    val suggestion: PriceItem? = null,
    val quantitySuggestion: Quantity? = null,
    val pantryCovered: Boolean = false,
) {
    /** True when the row reads as a priced price book item. */
    val matched: Boolean get() = ingredient.priceItemId != null
}

/**
 * The review screen's non-editor half (Phase RI-2): turning a parsed
 * draft into rows, applying the user's confirmations, and building the
 * recipe she saves. Pure, so every rule is testable on the JVM.
 */
object ImportReview {

    /**
     * Words that name a container a count-sold item comes in: a "can" of
     * an item the book sells by count may be offered as a count reading
     * (plan section 5.3), as a suggestion only.
     */
    private val CONTAINER_WORDS = setOf(
        "can", "cans", "jar", "jars", "bag", "bags", "box", "boxes",
        "package", "packages", "pkg", "bottle", "bottles", "tin", "tins",
        "carton", "cartons", "container", "containers",
    )

    /** Rows for a whole draft, in the draft's order. */
    fun buildRows(
        draft: ImportedDraft,
        priceItems: List<PriceItem>,
        pantryOwnedNames: Collection<String>,
    ): List<ReviewIngredient> = draft.ingredients.map { parsed ->
        rowFor(parsed, priceItems, pantryOwnedNames)
    }

    /** The review row for one parsed line. */
    fun rowFor(
        parsed: ParsedIngredient,
        priceItems: List<PriceItem>,
        pantryOwnedNames: Collection<String>,
    ): ReviewIngredient {
        val itemsById = priceItems.associateBy { it.id }
        val matched = ImportMatcher.match(parsed.name, priceItems)
        val quantity = parsed.quantity ?: Quantity(parsed.amount ?: 1.0, MeasureUnit.COUNT)
        val ingredient = if (matched != null) {
            RecipeIngredient(
                priceItemId = matched.id,
                quantity = quantity,
                note = parsed.note,
                essential = true,
            )
        } else {
            RecipeIngredient(
                pantryItem = parsed.name,
                quantity = quantity,
                note = parsed.note,
                essential = true,
            )
        }
        val quantitySuggestion = if (matched != null && parsed.quantity == null &&
            parsed.amount != null && parsed.unitWord != null &&
            parsed.unitWord in CONTAINER_WORDS &&
            matched.packQuantity.unit.dimension == Dimension.COUNT
        ) {
            Quantity(parsed.amount, matched.packQuantity.unit)
        } else {
            null
        }
        return ReviewIngredient(
            sourceLine = parsed.rawText,
            ingredient = ingredient,
            parsed = parsed,
            suggestion = if (matched == null) {
                ImportMatcher.suggestion(parsed.name, priceItems)
            } else {
                null
            },
            quantitySuggestion = quantitySuggestion,
            pantryCovered = isCovered(ingredient, itemsById, pantryOwnedNames),
        )
    }

    /**
     * Pantry coverage for one reading, read from the costing layer
     * exactly as the weekly list reads it (plan section 5.4): a priced
     * ingredient is covered when its item is covered; an as-written
     * ingredient is covered when an owned pantry name covers its name.
     */
    fun isCovered(
        ingredient: RecipeIngredient,
        itemsById: Map<String, PriceItem>,
        pantryOwnedNames: Collection<String>,
    ): Boolean {
        val itemId = ingredient.priceItemId
        if (itemId != null) {
            return itemId in Costing.pantryCoveredItemIds(pantryOwnedNames, itemsById.values)
        }
        val name = ingredient.pantryItem ?: return false
        return Costing.pantryNameCoversName(pantryOwnedNames, name)
    }

    /** the user confirmed the near-match suggestion: the row now reads as that book item. */
    fun applySuggestion(
        row: ReviewIngredient,
        priceItems: List<PriceItem>,
        pantryOwnedNames: Collection<String>,
    ): ReviewIngredient {
        val item = row.suggestion ?: return row
        val ingredients = RecipeEditing.substitute(listOf(row.ingredient), 0, item)
        return row.copy(
            ingredient = ingredients.single(),
            suggestion = null,
            quantitySuggestion = row.quantitySuggestion,
            pantryCovered = isCovered(
                ingredients.single(), priceItems.associateBy { it.id }, pantryOwnedNames,
            ),
        )
    }

    /** the user accepted the container reading of the row's amount. */
    fun applyQuantitySuggestion(
        row: ReviewIngredient,
        priceItems: List<PriceItem>,
        pantryOwnedNames: Collection<String>,
    ): ReviewIngredient {
        val quantity = row.quantitySuggestion ?: return row
        val ingredients = RecipeEditing.setQuantity(listOf(row.ingredient), 0, quantity)
        return row.copy(
            ingredient = ingredients.single(),
            quantitySuggestion = null,
            pantryCovered = isCovered(
                ingredients.single(), priceItems.associateBy { it.id }, pantryOwnedNames,
            ),
        )
    }

    /**
     * Rows after the RI-1 editor saved a new full list over the review:
     * rows whose ingredient survived unchanged keep their source line
     * and context; rows the editor created or changed read fresh, with
     * coverage recomputed and no stale suggestions.
     */
    fun rowsAfterEditorSave(
        previous: List<ReviewIngredient>,
        edited: List<RecipeIngredient>,
        priceItems: List<PriceItem>,
        pantryOwnedNames: Collection<String>,
    ): List<ReviewIngredient> {
        val itemsById = priceItems.associateBy { it.id }
        val remaining = previous.toMutableList()
        return edited.map { ingredient ->
            val keptAt = remaining.indexOfFirst { it.ingredient == ingredient }
            if (keptAt >= 0) {
                remaining.removeAt(keptAt).copy(
                    ingredient = ingredient,
                    pantryCovered = isCovered(ingredient, itemsById, pantryOwnedNames),
                )
            } else {
                ReviewIngredient(
                    sourceLine = "",
                    ingredient = ingredient,
                    pantryCovered = isCovered(ingredient, itemsById, pantryOwnedNames),
                )
            }
        }
    }

    /**
     * The main protein tag of an imported recipe (scope B2): the first
     * matched, essential, protein ingredient's protein, or "Other" when
     * none is. Diet tags start empty by the same rule.
     */
    fun deriveMainProtein(
        ingredients: List<RecipeIngredient>,
        itemsById: Map<String, PriceItem>,
    ): String {
        for (ingredient in ingredients) {
            if (!ingredient.essential) continue
            val item = ingredient.priceItemId?.let { itemsById[it] } ?: continue
            proteinOf(item)?.let { return it }
        }
        return "Other"
    }

    private fun proteinOf(item: PriceItem): String? {
        val id = item.id.lowercase()
        val name = item.name.lowercase()
        return when {
            id.contains("chicken") || name.contains("chicken") -> "Chicken"
            id.contains("turkey") || name.contains("turkey") -> "Ground turkey"
            id.contains("pork") || name.contains("pork") -> "Pork"
            id.contains("egg") || name.contains("egg") -> "Egg"
            id.contains("cheese") || name.contains("cheese") -> "Cheese"
            id.contains("salmon") || name.contains("salmon") -> "Salmon"
            id.contains("cod") || name.contains("cod") -> "Cod"
            id.contains("shrimp") || name.contains("shrimp") -> "Shrimp"
            item.category.equals("Meat", ignoreCase = true) -> item.name
            item.category.equals("Seafood", ignoreCase = true) -> item.name
            else -> null
        }
    }

    /** An earlier import from the same source link, when one exists (duplicate-source check). */
    fun findDuplicate(
        existing: List<ImportedRecipe>,
        sourceLink: String?,
    ): ImportedRecipe? {
        val link = sourceLink?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return existing.firstOrNull { it.sourceLink?.trim() == link }
    }

    /** The recipe the review saves, with its origin and derived protein tag. */
    fun buildRecipe(
        id: String,
        title: String,
        servings: Int,
        activeMinutes: Int,
        cuisine: String,
        steps: List<String>,
        ingredients: List<RecipeIngredient>,
        origin: RecipeOrigin,
        priceItems: List<PriceItem>,
    ): Recipe = Recipe(
        id = id,
        title = title,
        servings = servings,
        activeMinutes = activeMinutes,
        cuisine = cuisine,
        mainProtein = deriveMainProtein(ingredients, priceItems.associateBy { it.id }),
        dietTags = emptyList(),
        ingredients = ingredients,
        steps = steps,
        origin = origin,
    )
}
