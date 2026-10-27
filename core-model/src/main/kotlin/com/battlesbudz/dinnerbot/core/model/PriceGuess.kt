package com.battlesbudz.dinnerbot.core.model

/**
 * Priced-ness and placeholder price guesses (Phase RI-3, plan section
 * 5.5, as corrected by the user 2026-10-24).
 *
 * The original plan computed guesses on the fly and never persisted
 * them. the user corrected the design: everything is in the pricing
 * book. An ingredient that matches nothing at import becomes a REAL
 * price book item at its guessed price (see [GuessPricer]), carrying
 * the persistent [PriceItem.guessPriced] mark until the first scan or
 * price correction writes the real shelf price and clears it. No price
 * ever comes from the internet (ruling 12): the guess is computed on
 * the phone from her own price book.
 *
 * Priced-ness is computed from the book as it stands: an ingredient
 * counts as priced when it resolves to a price book item — by the id
 * it carries, or, for an as-written ingredient, by exact normalized-
 * name equality — AND that item is not guess-marked. A recipe counts
 * as priced when every ingredient does, so a recipe leaves the
 * not-yet-priced class automatically as its items are shopped and
 * corrected.
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
     * carries, exactly as [Costing.aggregateNeeds] resolves, or — for
     * an as-written ingredient — by exact normalized-name equality with
     * a book item's name.
     */
    fun resolvedItem(
        ingredient: RecipeIngredient,
        itemsById: Map<String, PriceItem>,
    ): PriceItem? {
        val rawId = ingredient.priceItemId
        if (rawId != null) {
            return itemsById[rawId]
        }
        val name = ingredient.pantryItem ?: return null
        val words = NameWords.of(name)
        if (words.isEmpty()) return null
        return itemsById.values.firstOrNull { NameWords.of(it.name) == words }
    }

    /**
     * True when [ingredient] counts as priced against the book as it
     * stands: it resolves to a book item, and that item's price is a
     * real one — an item still carrying its import guess does not make
     * the ingredient priced (user correction, 2026-10-24).
     */
    fun isIngredientPriced(
        ingredient: RecipeIngredient,
        itemsById: Map<String, PriceItem>,
    ): Boolean = resolvedItem(ingredient, itemsById)?.guessPriced == false

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
     * True when [recipe] is one of the user's own (imported or hand-made)
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

    /**
     * The store a guessed line is shelved under (Phase C1): the first
     * equivalent item's home store, else the caller's [defaultStoreId]
     * (the user's first chosen store), else the first book item's
     * store. No chain is a code-level default; the final PresetStores
     * fallback exists only so an empty book with no stores chosen
     * still yields a value, and is unreachable in practice.
     */
    fun guessStore(
        name: String,
        priceItems: Collection<PriceItem>,
        defaultStoreId: String? = null,
    ): String =
        equivalentsFor(name, priceItems).firstOrNull()?.homeStore
            ?: defaultStoreId
            ?: priceItems.firstOrNull()?.homeStore
            ?: PresetStores.ALDI.id

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

/**
 * Import-time price book creation (user correction, 2026-10-24).
 *
 * When a recipe is imported, created by hand, or edited into a new
 * as-written ingredient, every ingredient that still matches nothing in
 * the book gets a real book item of its own — everything is in the
 * pricing book. The item carries the placeholder guess as its starting
 * price and the [PriceItem.guessPriced] mark; the first scan or price
 * correction replaces the price with the real one and clears the mark.
 * An ingredient whose name exactly matches an existing item — including
 * an item an earlier import created — links to it instead of making a
 * duplicate, so later imports dedupe on their own.
 *
 * The same pass backfills installs from before the correction: any
 * stored imported recipe still holding as-written ingredients is run
 * through [linkRecipes] on upgrade and gains its items identically.
 */
object GuessPricer {

    /** What a linking pass produced: the new book items plus the linked recipes. */
    data class LinkResult(
        val itemsToAdd: List<PriceItem>,
        val recipes: List<ImportedRecipe>,
    )

    /**
     * Link every as-written ingredient in [recipe] to the book: an
     * exact-name match when one exists, a new guess-priced item
     * otherwise. Returns the linked recipe and the items created
     * (empty when everything already resolved).
     */
    fun linkRecipe(
        recipe: Recipe,
        book: List<PriceItem>,
        checkedDate: String,
        defaultStoreId: String? = null,
    ): Pair<Recipe, List<PriceItem>> {
        val working = book.toMutableList()
        val created = mutableListOf<PriceItem>()
        val ingredients = recipe.ingredients.map { ingredient ->
            if (ingredient.priceItemId != null) return@map ingredient
            val asWritten = ingredient.pantryItem ?: return@map ingredient
            val words = NameWords.of(asWritten)
            val match = working.firstOrNull { NameWords.of(it.name) == words }
            if (match != null) {
                ingredient.copy(priceItemId = match.id, pantryItem = null)
            } else {
                val item = guessItemFor(ingredient, working, checkedDate, defaultStoreId)
                working += item
                created += item
                ingredient.copy(priceItemId = item.id, pantryItem = null)
            }
        }
        return recipe.copy(ingredients = ingredients) to created
    }

    /** [linkRecipe] across stored imported recipes, threading the growing book. */
    fun linkRecipes(
        records: List<ImportedRecipe>,
        book: List<PriceItem>,
        checkedDate: String,
        defaultStoreId: String? = null,
    ): LinkResult {
        val working = book.toMutableList()
        val created = mutableListOf<PriceItem>()
        val linked = records.map { record ->
            val (recipe, made) = linkRecipe(record.recipe, working, checkedDate, defaultStoreId)
            working += made
            created += made
            if (made.isEmpty() && recipe == record.recipe) record else record.copy(recipe = recipe)
        }
        return LinkResult(created, linked)
    }

    /**
     * The guess-priced book item for one unmatched [ingredient]: the
     * plan's median guess as its price, the ingredient's own parsed
     * amount as its pack, the store its equivalents shop at, and a
     * user-item id in the scan-added convention.
     */
    fun guessItemFor(
        ingredient: RecipeIngredient,
        book: List<PriceItem>,
        checkedDate: String,
        defaultStoreId: String? = null,
    ): PriceItem {
        val name = ingredient.pantryItem?.trim().orEmpty()
        val guess = RecipePricing.guessFor(name, book)
        val priceCents = guess?.cents ?: FALLBACK_GUESS_CENTS
        val amount = if (ingredient.quantity.amount > 0.0) ingredient.quantity.amount else 1.0
        val pack = Quantity(amount, ingredient.quantity.unit)
        return PriceItem(
            id = uniqueUserId(name, book),
            name = name,
            category = RecipePricing.equivalentsFor(name, book).firstOrNull()?.category
                ?: DEFAULT_CATEGORY,
            homeStore = RecipePricing.guessStore(name, book, defaultStoreId),
            packDescription = "${formatAmount(amount)} ${pack.unit.label} (from a recipe)",
            packQuantity = pack,
            packPriceCents = priceCents,
            pricePerUnitCents = priceCents.toDouble() / amount,
            checkedDate = checkedDate,
            source = GUESS_SOURCE,
            guessPriced = true,
        )
    }

    /** "user-<slug>" in the scan-added convention, suffixed until it is free. */
    internal fun uniqueUserId(name: String, book: List<PriceItem>): String {
        val slug = NameWords.of(name).joinToString("-").ifBlank { "item" }
        val taken = book.map { it.id }.toSet()
        var candidate = "user-$slug"
        var suffix = 2
        while (candidate in taken) {
            candidate = "user-$slug-$suffix"
            suffix++
        }
        return candidate
    }

    private fun formatAmount(amount: Double): String =
        if (amount % 1.0 == 0.0) amount.toInt().toString()
        else amount.toString().trimEnd('0').trimEnd('.')

    /** The book is never empty in the app; a dollar if it somehow is. */
    const val FALLBACK_GUESS_CENTS: Int = 100

    const val DEFAULT_CATEGORY: String = "Other"

    const val GUESS_SOURCE: String = "Recipe import — guessed price"
}

/**
 * The single builder behind the shared add-an-ingredient form
 * (v0.6.2 hotfix): every door that adds an ingredient to the price
 * book by hand — the ingredient editor, the price book itself, and a
 * barcode scan of a product the book does not have — builds its item
 * here, so the record is identical wherever it was added.
 *
 * Two outcomes, by whether the user knows the price:
 *
 * - A typed price is her number: the item is priced, flagged
 *   user-corrected like any price she enters, and carries the
 *   caller's [pricedSource] note.
 * - No price: the item is created anyway — a missing price must
 *   never block adding an ingredient (the dead end hit 2026-10-25)
 *   — at the [RecipePricing] median guess for its name, flagged
 *   [PriceItem.guessPriced] so every surface labels it a guess, and
 *   the first scan or price correction replaces it with the real
 *   shelf price and clears the mark, exactly as import guesses have
 *   worked since v0.5.3.
 *
 * The id is the "user-" convention of the scan-added items
 * ([GuessPricer.uniqueUserId]), so seed refreshes — which only ever
 * touch seed ids — can never overwrite or drop a hand-added item,
 * and backups carry it like any other user item.
 */
object NewBookItem {

    /** Source note on an item added by hand at a price the user typed. */
    const val HAND_PRICED_SOURCE: String = "Added by hand"

    /** Source note on an item added by hand with no price; its price is a guess. */
    const val HAND_GUESS_SOURCE: String = "Added by hand — guessed price"

    /** Source note on an item added from a barcode scan at a typed price. */
    const val SCAN_PRICED_SOURCE: String = "Added by barcode scan"

    /** Source note on an item added from a barcode scan with no price typed. */
    const val SCAN_GUESS_SOURCE: String = "Added by barcode scan — guessed price"

    /**
     * Build the book item for one hand-added ingredient. [priceCents]
     * is the pack price the user typed, or null when they do not know
     * it and the item starts guess-priced. [category] blank stands in
     * with the category of an equivalent book item, else
     * [GuessPricer.DEFAULT_CATEGORY]; [packDescription] blank stands
     * in with the pack amount and unit.
     */
    fun build(
        name: String,
        category: String,
        homeStore: String,
        packDescription: String,
        packQuantity: Quantity,
        priceCents: Int?,
        book: List<PriceItem>,
        checkedDate: String,
        pricedSource: String = HAND_PRICED_SOURCE,
        guessSource: String = HAND_GUESS_SOURCE,
    ): PriceItem {
        val trimmedName = name.trim()
        require(trimmedName.isNotEmpty()) { "a new item needs a name" }
        require(packQuantity.amount > 0.0) { "pack quantity must be positive" }
        require(priceCents == null || priceCents > 0) { "price must be positive" }
        val resolvedCategory = category.trim().ifEmpty {
            RecipePricing.equivalentsFor(trimmedName, book).firstOrNull()?.category
                ?: GuessPricer.DEFAULT_CATEGORY
        }
        val resolvedDescription = packDescription.trim().ifEmpty {
            "${formatAmount(packQuantity.amount)} ${packQuantity.unit.label}"
        }
        return if (priceCents != null) {
            PriceItem(
                id = GuessPricer.uniqueUserId(trimmedName, book),
                name = trimmedName,
                category = resolvedCategory,
                homeStore = homeStore,
                packDescription = resolvedDescription,
                packQuantity = packQuantity,
                packPriceCents = priceCents,
                pricePerUnitCents = priceCents.toDouble() / packQuantity.amount,
                checkedDate = checkedDate,
                source = pricedSource,
                userCorrected = true,
            )
        } else {
            val guess = RecipePricing.guessFor(trimmedName, book)
            val guessCents = guess?.cents ?: GuessPricer.FALLBACK_GUESS_CENTS
            PriceItem(
                id = GuessPricer.uniqueUserId(trimmedName, book),
                name = trimmedName,
                category = resolvedCategory,
                homeStore = homeStore,
                packDescription = resolvedDescription,
                packQuantity = packQuantity,
                packPriceCents = guessCents,
                pricePerUnitCents = guessCents.toDouble() / packQuantity.amount,
                checkedDate = checkedDate,
                source = guessSource,
                guessPriced = true,
            )
        }
    }

    private fun formatAmount(amount: Double): String =
        if (amount % 1.0 == 0.0) amount.toInt().toString()
        else amount.toString().trimEnd('0').trimEnd('.')
}
