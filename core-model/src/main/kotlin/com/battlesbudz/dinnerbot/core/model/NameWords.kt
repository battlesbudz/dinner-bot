package com.battlesbudz.dinnerbot.core.model

/**
 * The app's one name normalizer, shared by every rule that compares
 * ingredient and item names (Phase RI-2 extracted it from [Costing],
 * where the pantry-coverage rule first defined it, so the import
 * matcher compares names with exactly the shipped rule and not a
 * second copy of it).
 *
 * Normalization is deliberately conservative: case folded, punctuation
 * treated as a word break, and simple plurals folded to singular
 * ("potatoes" -> "potato", "onions" -> "onion"; only a trailing "oes"
 * or a single trailing "s", never "ss", so "glass" is untouched).
 */
object NameWords {

    /** Lowercased words of [name], each folded from a simple plural to its singular. */
    fun of(name: String): List<String> =
        name.lowercase()
            .split(Regex("[^a-z0-9]+"))
            .filter { it.isNotEmpty() }
            .map { singularOf(it) }

    /** Fold a simple plural to its singular; see [of] for the exact rule. */
    fun singularOf(word: String): String = when {
        word.length > 3 && word.endsWith("oes") -> word.dropLast(2)
        word.length > 1 && word.endsWith("s") && !word.endsWith("ss") -> word.dropLast(1)
        else -> word
    }

    /** True when [words] ends with [suffix] as whole words; a full-length suffix is an exact match. */
    fun endsWithWords(words: List<String>, suffix: List<String>): Boolean {
        if (suffix.size > words.size) return false
        return words.subList(words.size - suffix.size, words.size) == suffix
    }
}
