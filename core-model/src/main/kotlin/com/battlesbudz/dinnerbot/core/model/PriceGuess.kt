package com.battlesbudz.dinnerbot.core.model

/**
 * Priced-ness and placeholder price guesses (Phase RI-3, plan section
 * 5.5; Battles's rulings 5 and 6 and his third-round placeholder ruling).
 *
 * Priced-ness is COMPUTED from the price book as it stands, never
 * stored: an ingredient counts as priced when it resolves to a price
 * book item, either by the item id it carries or because a book item
 * now exists whose normalized name equals the ingredient's as-written
 * text exactly. The moment an item enters the book (a barcode scan's
 * new-product flow, a typed correction) and its name matches, the
 * recipe's state updates with no stored flag to go stale.
 *
 * An ingredient with no real price gets a [PriceGuess]: the median pack
 * price of the equivalent items already in the book, where equivalents
 * share the ingredient's head noun (other onions for an onion variety);
 * the whole book's median pack price when nothing shares the head noun.
 * The guess counts in week totals and is labeled as a guess wherever it
 * appears. It never writes into the price book, never survives into a
 * backup, and is replaced the moment the ingredient is priced. No price
 * ever comes from the internet (ruling 12): the guess is computed on
 * the phone from her own price book.
 */
data class PriceGuess(
    /** The guessed pack price, cents. */
    val cents: Int,
    /** Names of the book items the guess was computed from, for provenance labels. */
    val equivalentItemNames: List<String>,
    /** True when no item shared the head noun and the whole book's median stood in. */
    val wholeBookFallback: Boolean,
)

object RecipePricing {

    /**
     * The synthetic grocery-line id for an as-written ingredient: it has
     * no price book item, so its list identity is its normalized name.
     * Deterministic, so a stored plan's rows and a fresh costing agree
     * on which line is which.
     */
    fun asWrittenItemId(name: String): String =
        "aswritten:" + NameWords.of(name).joinToString("-")

    /** True when [ingredient] is an as-written line (no book item reference). */
    fun isAsWritten(ingredient: RecipeIngredient): Boolean = ingredient.pantryItem != null

    /**
     * The book item an ingredient resolves to, if any: by the id it
     * carries (through the household beef substitution, then the raw id,
     * exactly as [Costing.aggregateNeeds] resolves), or — for an
     * as-written ingredient — by exact normalized-name equality with a
     * book item's name.
     */
    fun resolvedItem(
        ingredient: RecipeIngredient,
        itemsById: Map<String, PriceItem>,
    ): PriceItem? {
        val rawId = ingredient.priceItemId
        if (rawId != null) {
            return itemsById[Costing.substituteItemId(rawId)] ?: itemsById[rawId]
        }
        val name = ingredient.pantryItem ?: return null
        val words = NameWords.of(name)
        if (words.isEmpty()) return null
        return itemsById.values.firstOrNull { NameWords.of(it.name) == words }
    }

    /** True when [ingredient] counts as priced against the book as it stands. */
    fun isIngredientPriced(
        ingredient: RecipeIngredient,
        itemsById: Map<String, PriceItem>,
    ): Boolean = resolvedItem(ingredient, itemsById) != null

    /** The ingredients of [recipe] with no real price yet, in recipe order. */
    fun unpricedIngredients(
        recipe: Recipe,
        itemsById: Map<String, PriceItem>,
    ): List<RecipeIngredient> = recipe.ingredients.filterNot { isIngredientPriced(it, itemsById) }

    /** A recipe counts as priced when every ingredient counts as priced. */
    fun isRecipePriced(
        recipe: Recipe,
        itemsById: Map<String, PriceItem>,
    ): Boolean = unpricedIngredients(recipe, itemsById).isEmpty()

    /**
     * True when [recipe] is one of Andrea's own (imported or hand-made)
     * and not yet fully priced: the class the one-per-week generation
     * cap and the not-accurate note apply to (ruling 5). Library recipes
     * never count, whatever they contain.
     */
    fun isUnpricedUserRecipe(
        recipe: Recipe,
        itemsById: Map<String, PriceItem>,
    ): Boolean = recipe.origin != RecipeOrigin.LIBRARY && !isRecipePriced(recipe, itemsById)

    /** The head noun of a name: the last word of its normalized form ("yellow onion" -> "onion"). */
    fun headNoun(name: String): String? = NameWords.of(name).lastOrNull()

    /** Book items equivalent to an ingredient named [name]: those sharing its head noun. */
    fun equivalentsFor(name: String, priceItems: Collection<PriceItem>): List<PriceItem> {
        val head = headNoun(name) ?: return emptyList()
        return priceItems.filter { headNoun(it.name) == head }
    }

    /**
     * The placeholder guess for an ingredient named [name]: the median
     * pack price of its equivalent items, or of the whole book when no
     * item shares its head noun. Null only when the book is empty and
     * there is nothing to guess from.
     */
    fun guessFor(name: String, priceItems: Collection<PriceItem>): PriceGuess? {
        if (priceItems.isEmpty()) return null
        val equivalents = equivalentsFor(name, priceItems)
        return if (equivalents.isNotEmpty()) {
            PriceGuess(
                cents = medianCents(equivalents.map { it.packPriceCents }),
                equivalentItemNames = equivalents.map { it.name },
                wholeBookFallback = false,
            )
        } else {
            PriceGuess(
                cents = medianCents(priceItems.map { it.packPriceCents }),
                equivalentItemNames = emptyList(),
                wholeBookFallback = true,
            )
        }
    }

    /** The store a guessed line is shelved under: the first equivalent item's home store, else Aldi. */
    fun guessStore(name: String, priceItems: Collection<PriceItem>): Store =
        equivalentsFor(name, priceItems).firstOrNull()?.homeStore ?: Store.ALDI

    /** Median of a non-empty list of cent values; an even count averages the two middle values. */
    fun medianCents(values: List<Int>): Int {
        require(values.isNotEmpty()) { "no median of an empty list" }
        val sorted = values.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 1) {
            sorted[mid]
        } else {
            (sorted[mid - 1] + sorted[mid] + 1) / 2
        }
    }
}
