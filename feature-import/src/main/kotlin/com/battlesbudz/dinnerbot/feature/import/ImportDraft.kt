package com.battlesbudz.dinnerbot.feature.`import`

import com.battlesbudz.dinnerbot.core.model.MeasureUnit
import com.battlesbudz.dinnerbot.core.model.Quantity

/**
 * One ingredient line as the parser read it (Phase RI-2, plan section
 * 5.3). [amount] and [unit] are set only when the line's amount read
 * cleanly into the app's closed unit system; a unit word outside that
 * system (bunch, stalk, head, clove, can, jar, pinch) is kept as
 * [unitWord] and never guessed into a conversion. [name] is the
 * ingredient as written, with prep language moved into [note]. A line
 * the parser could not resolve still exists whole here: failing toward
 * visible-as-written is the design, and the review screen is where
 * Andrea corrects the reading.
 */
data class ParsedIngredient(
    val amount: Double?,
    val unit: MeasureUnit?,
    val unitWord: String?,
    val name: String,
    val note: String?,
    val rawText: String,
) {
    /** The amount in a closed-system unit, when the line resolved fully. */
    val quantity: Quantity?
        get() = if (amount != null && unit != null) Quantity(amount, unit) else null

    /** True when amount and unit both resolved into the closed system. */
    val quantityResolved: Boolean get() = quantity != null
}

/**
 * A whole recipe as an intake route read it, before review: the fields
 * the review screen opens with. Nothing here is saved; the review is
 * where a draft becomes a recipe.
 */
data class ImportedDraft(
    val title: String,
    val servings: Int,
    val activeMinutes: Int,
    val cuisine: String?,
    val steps: List<String>,
    val ingredients: List<ParsedIngredient>,
    val sourceLink: String?,
) {
    companion object {
        /** The blank draft a hand-made recipe starts from (Phase RI-2). */
        fun manual(): ImportedDraft = ImportedDraft(
            title = "",
            servings = 4,
            activeMinutes = 30,
            cuisine = null,
            steps = emptyList(),
            ingredients = emptyList(),
            sourceLink = null,
        )
    }
}
