package com.battlesbudz.dinnerbot.feature.`import`

import com.battlesbudz.dinnerbot.core.model.MeasureUnit

/**
 * The ingredient line parser (Phase RI-2, plan section 5.3). Pure: one
 * raw line in, one [ParsedIngredient] out, no state and no invention.
 *
 * The rules that matter:
 * - Amounts read as integers, decimals, fractions, mixed numbers, and
 *   unicode fractions; a range ("1-2 cups") resolves to the LARGER
 *   amount so a list never under-buys.
 * - Unit words map onto the app's closed unit system. Words outside it
 *   (bunch, stalk, head, clove, can, jar, pinch, and kin) are kept as
 *   written and never guessed into a conversion.
 * - Prep language (finely chopped, minced, diced, peeled, and their
 *   kin, a conservative named list) is stripped into the note. Words
 *   that change what the ingredient IS (dried versus fresh) are never
 *   stripped.
 * - A line that cannot be read still comes back whole, its name the
 *   line as written: the parser fails toward visible, never toward
 *   dropping or silently reshaping an ingredient.
 */
object IngredientLineParser {

    private val UNICODE_FRACTIONS = mapOf(
        '¼' to 0.25, '½' to 0.5, '¾' to 0.75,
        '⅓' to 1.0 / 3.0, '⅔' to 2.0 / 3.0,
        '⅛' to 0.125, '⅜' to 0.375, '⅝' to 0.625, '⅞' to 0.875,
    )

    private val UNIT_WORDS: Map<String, MeasureUnit> = mapOf(
        "cup" to MeasureUnit.CUP, "cups" to MeasureUnit.CUP,
        "tablespoon" to MeasureUnit.TABLESPOON, "tablespoons" to MeasureUnit.TABLESPOON,
        "tbsp" to MeasureUnit.TABLESPOON,
        "teaspoon" to MeasureUnit.TEASPOON, "teaspoons" to MeasureUnit.TEASPOON,
        "tsp" to MeasureUnit.TEASPOON,
        "ounce" to MeasureUnit.OUNCE, "ounces" to MeasureUnit.OUNCE, "oz" to MeasureUnit.OUNCE,
        "pound" to MeasureUnit.POUND, "pounds" to MeasureUnit.POUND,
        "lb" to MeasureUnit.POUND, "lbs" to MeasureUnit.POUND,
        "gram" to MeasureUnit.GRAM, "grams" to MeasureUnit.GRAM, "g" to MeasureUnit.GRAM,
        "kilogram" to MeasureUnit.KILOGRAM, "kilograms" to MeasureUnit.KILOGRAM,
        "kg" to MeasureUnit.KILOGRAM,
        "milliliter" to MeasureUnit.MILLILITER, "milliliters" to MeasureUnit.MILLILITER,
        "ml" to MeasureUnit.MILLILITER,
        "liter" to MeasureUnit.LITER, "liters" to MeasureUnit.LITER,
        "litre" to MeasureUnit.LITER, "litres" to MeasureUnit.LITER, "l" to MeasureUnit.LITER,
        "each" to MeasureUnit.EACH,
    )

    /** Unit words outside the closed system: kept as written, never converted. */
    private val OUTSIDE_UNIT_WORDS = setOf(
        "clove", "cloves", "can", "cans", "bunch", "bunches", "stalk", "stalks",
        "head", "heads", "jar", "jars", "pinch", "pinches", "slice", "slices",
        "package", "packages", "pkg", "bag", "bags", "box", "boxes", "sprig", "sprigs",
        "piece", "pieces", "container", "containers", "bottle", "bottles",
        "tin", "tins", "carton", "cartons", "scoop", "scoops",
    )

    /**
     * Prep words stripped into the note when they lead or trail the
     * name. Conservative on purpose: none of these ever names what an
     * ingredient is, and words like fresh or dried (which do) are
     * deliberately absent.
     */
    private val PREP_WORDS = setOf(
        "finely", "chopped", "minced", "diced", "peeled", "melted", "softened",
        "sifted", "beaten", "thawed", "drained", "rinsed", "trimmed", "seeded",
        "cored", "crushed", "cooked", "uncooked",
    )

    /** Parse one line; a line may carry two ingredients ("1 cup flour; 1 tsp salt"). */
    fun parse(line: String): List<ParsedIngredient> =
        splitCombined(line).mapNotNull { parseSingle(it) }

    private fun splitCombined(line: String): List<String> {
        val trimmed = line.trim()
        if (trimmed.isEmpty()) return emptyList()
        val parts = mutableListOf<String>()
        var rest = trimmed
        while (true) {
            val semi = rest.indexOf(';')
            val andIdx = findAmountAnd(rest)
            val cut = listOf(semi, andIdx).filter { it > 0 }.minOrNull()
            if (cut == null) {
                parts += rest
                break
            }
            parts += rest.substring(0, cut)
            rest = rest.substring(if (rest[cut] == ';') cut + 1 else cut + " and ".length)
                .trim()
            if (rest.isEmpty()) break
        }
        return parts.map { it.trim() }.filter { it.isNotEmpty() }
    }

    /** Index of an " and " that is followed by a fresh amount, or -1. */
    private fun findAmountAnd(text: String): Int {
        var idx = text.indexOf(" and ")
        while (idx >= 0) {
            val after = text.substring(idx + " and ".length).trimStart()
            if (after.isNotEmpty() && readAmount(after.split(Regex("\\s+")), 0) != null) return idx
            idx = text.indexOf(" and ", idx + 1)
        }
        return -1
    }

    private fun parseSingle(rawLine: String): ParsedIngredient? {
        val raw = rawLine.trim().trimEnd('.').trim()
        if (raw.isEmpty()) return null

        val noteParts = mutableListOf<String>()

        // Parentheticals are notes (sizes, brands, substitutions).
        val parens = mutableListOf<String>()
        val noParens = Regex("\\(([^)]*)\\)").replace(raw) { match ->
            match.groupValues[1].trim().takeIf { it.isNotEmpty() }?.let { parens += it }
            " "
        }.replace(Regex("\\s+"), " ").trim()

        // A comma tail is prep or serving language: "onion, finely chopped".
        val commaIdx = noParens.indexOf(',')
        val head: String
        if (commaIdx >= 0) {
            head = noParens.substring(0, commaIdx).trim()
            noParens.substring(commaIdx + 1).trim()
                .takeIf { it.isNotEmpty() }?.let { noteParts += it }
        } else {
            head = noParens
        }

        val tokens = head.split(Regex("\\s+")).filter { it.isNotEmpty() }.toMutableList()
        var i = 0

        // Leading amount.
        var amount: Double? = null
        val amountRead = if (tokens.isNotEmpty()) readAmount(tokens, 0) else null
        if (amountRead != null) {
            amount = amountRead.value
            i = amountRead.tokensUsed
        }

        // "a pinch of salt": the article is the amount when a unit follows.
        if (amount == null && tokens.size >= 2 &&
            (tokens[0].equals("a", true) || tokens[0].equals("an", true)) &&
            (isUnitWord(tokens[1]) || isOutsideUnitWord(tokens[1]))
        ) {
            amount = 1.0
            i = 1
        }

        // Unit: two-word fluid ounce first, then the single-token maps.
        var unit: MeasureUnit? = null
        var unitWord: String? = null
        if (i < tokens.size) {
            val first = cleanUnitToken(tokens[i])
            val second = tokens.getOrNull(i + 1)?.let { cleanUnitToken(it) }
            when {
                (first == "fl" || first == "fluid") &&
                    (second == "oz" || second == "ounce" || second == "ounces") -> {
                    unit = MeasureUnit.FLUID_OUNCE
                    i += 2
                }
                UNIT_WORDS.containsKey(first) -> {
                    unit = UNIT_WORDS[first]
                    i += 1
                }
                OUTSIDE_UNIT_WORDS.contains(first) -> {
                    unitWord = first
                    i += 1
                }
            }
        }

        // "1 can of black beans": the "of" is packaging grammar, not name.
        if ((unit != null || unitWord != null) && i < tokens.size &&
            tokens[i].equals("of", ignoreCase = true)
        ) {
            i += 1
        }

        // What is left is the name, with prep words at its edges moved
        // into the note.
        val nameTokens = tokens.subList(i, tokens.size).toMutableList()
        val strippedPrep = mutableListOf<String>()
        while (nameTokens.isNotEmpty() && nameTokens.first().lowercase() in PREP_WORDS) {
            strippedPrep += nameTokens.removeAt(0)
        }
        while (nameTokens.isNotEmpty() && nameTokens.last().lowercase() in PREP_WORDS) {
            strippedPrep += nameTokens.removeAt(nameTokens.lastIndex)
        }
        var name = nameTokens.joinToString(" ")
        if (name.endsWith(" to taste", ignoreCase = true)) {
            noteParts += "to taste"
            name = name.dropLast(" to taste".length).trim()
        }
        if (strippedPrep.isNotEmpty()) {
            noteParts.add(0, strippedPrep.joinToString(" "))
        }
        noteParts += parens

        // Never lose the line: if the reading consumed everything, the
        // name is the head as written.
        if (name.isBlank()) {
            name = head
            amount = null
            unit = null
            unitWord = null
        }

        return ParsedIngredient(
            amount = amount,
            unit = unit,
            unitWord = unitWord,
            name = name,
            note = noteParts.filter { it.isNotBlank() }.joinToString(", ").ifBlank { null },
            rawText = rawLine.trim(),
        )
    }

    private fun isUnitWord(token: String): Boolean = UNIT_WORDS.containsKey(cleanUnitToken(token))

    private fun isOutsideUnitWord(token: String): Boolean =
        OUTSIDE_UNIT_WORDS.contains(cleanUnitToken(token))

    private fun cleanUnitToken(token: String): String = token.trim().lowercase().trimEnd('.')

    private data class AmountRead(val value: Double, val tokensUsed: Int)

    /**
     * Read an amount starting at token [start]: an integer, decimal,
     * fraction, mixed number, or unicode fraction, or a range of any of
     * those (which resolves to the larger end). Null when the token is
     * not an amount.
     */
    private fun readAmount(tokens: List<String>, start: Int): AmountRead? {
        val first = tokens.getOrNull(start) ?: return null

        // Single-token range: "1-2", "1–2", "1/2-1".
        val rangeParts = first.split('-', '–')
        if (rangeParts.size == 2 && rangeParts.all { it.isNotEmpty() }) {
            val low = singleAmount(rangeParts[0])
            val high = singleAmount(rangeParts[1])
            if (low != null && high != null) return AmountRead(maxOf(low, high), 1)
        }

        val whole = singleAmount(first) ?: return null

        // "1 to 2": a spread-out range resolves to the larger end too.
        if (tokens.getOrNull(start + 1)?.equals("to", ignoreCase = true) == true) {
            val second = tokens.getOrNull(start + 2)?.let { singleAmount(it) }
            if (second != null) return AmountRead(maxOf(whole, second), 3)
        }

        // Mixed number: "1 1/2" or "1 ½".
        val fraction = tokens.getOrNull(start + 1)?.let { fractionOf(it) }
        if (fraction != null && first.all { ch -> ch.isDigit() }) {
            return AmountRead(whole + fraction, 2)
        }
        return AmountRead(whole, 1)
    }

    /** One amount token with no range or mix: "2", "1.5", "1/2", "½", "1½", ".5". */
    private fun singleAmount(token: String): Double? {
        val t = token.trim()
        if (t.isEmpty()) return null
        fractionOf(t)?.let { return it }
        // Digit run followed by a unicode fraction: "1½".
        if (t.length > 1 && t.last() in UNICODE_FRACTIONS &&
            t.dropLast(1).all { it.isDigit() }
        ) {
            val whole = t.dropLast(1).toDoubleOrNull() ?: return null
            return whole + UNICODE_FRACTIONS.getValue(t.last())
        }
        return t.toDoubleOrNull()?.takeIf { it > 0.0 }
    }

    /** "1/2" or one unicode fraction character; null otherwise. */
    private fun fractionOf(token: String): Double? {
        val t = token.trim()
        if (t.length == 1 && t[0] in UNICODE_FRACTIONS) return UNICODE_FRACTIONS[t[0]]
        val parts = t.split('/')
        if (parts.size == 2) {
            val top = parts[0].toDoubleOrNull()
            val bottom = parts[1].toDoubleOrNull()
            if (top != null && bottom != null && bottom != 0.0) return top / bottom
        }
        return null
    }
}
