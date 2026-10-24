package com.battlesbudz.dinnerbot.feature.`import`

import com.battlesbudz.dinnerbot.core.model.NameWords
import com.battlesbudz.dinnerbot.core.model.PriceItem

/**
 * The strict import matcher (Phase RI-2, plan section 5.3). The rules
 * are the feature's honesty core:
 *
 * - A silent match happens ONLY when the normalized imported name
 *   equals a price book item's normalized name, word for word, whole
 *   name, under the app's shipped normalizer ([NameWords]). If two
 *   book items tie, that is ambiguity, not a match.
 * - Names differing in any word are different ingredients, by
 *   construction and with no hard-coded pair anywhere: "spring onion"
 *   never matches "yellow onion", because the word lists differ.
 * - The pantry-coverage whole-word-ending rule is deliberately NOT a
 *   match here. Coverage errs toward "already home," the safe
 *   direction for a pantry claim; import matching errs toward asking,
 *   because a wrong silent match puts the wrong food in a recipe. A
 *   word-ending near-match may be offered as a review suggestion,
 *   which Andrea confirms or it stays unmatched.
 * - Matching dedupes against the price book only. Nothing here creates
 *   a book item, edits the pantry, or writes a barcode link.
 */
object ImportMatcher {

    /** The one book item whose normalized name equals [name]'s, or null. */
    fun match(name: String, items: List<PriceItem>): PriceItem? {
        val words = NameWords.of(name)
        if (words.isEmpty()) return null
        return items.filter { NameWords.of(it.name) == words }.singleOrNull()
    }

    /**
     * A near-match to offer in review, never to apply silently: a book
     * item whose name and [name] share a whole-word ending in either
     * direction ("onion" toward "Yellow onions"). Equality is a match,
     * not a suggestion, and an ambiguous field suggests nothing.
     */
    fun suggestion(name: String, items: List<PriceItem>): PriceItem? {
        val words = NameWords.of(name)
        if (words.isEmpty()) return null
        return items.filter { item ->
            val itemWords = NameWords.of(item.name)
            itemWords != words &&
                (NameWords.endsWithWords(itemWords, words) ||
                    NameWords.endsWithWords(words, itemWords))
        }.singleOrNull()
    }
}
