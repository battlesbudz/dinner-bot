package com.battlesbudz.dinnerbot.feature.`import`

/**
 * Reading a recipe from pasted plain text (Phase RI-2): the intake
 * route that works with no connection at all and the fallback every
 * other route lands on. The reading is a first pass, not a verdict:
 * the review screen shows it whole and the user corrects it there.
 *
 * Shape rules, deliberately simple: the first line is the title unless
 * it reads as an ingredient; "Ingredients" and "Instructions" (and
 * their kin) switch sections when a page uses them; without headers,
 * lines that parse with an amount or unit are ingredients and the
 * rest, once ingredients have started ending, are steps. Serves /
 * yield / time lines are read for their numbers wherever they appear.
 */
object RecipeTextParser {

    private val INGREDIENT_HEADERS = setOf(
        "ingredients", "ingredient", "what you need", "shopping list",
    )
    private val STEP_HEADERS = setOf(
        "instructions", "instruction", "directions", "direction", "method",
        "preparation", "steps", "how to make it", "recipe",
    )

    fun parse(text: String, sourceLink: String? = null): ImportedDraft {
        val lines = text.lines().map { it.trim() }
        var title: String? = null
        var link = sourceLink
        var servings: Int? = null
        var minutes: Int? = null
        val ingredients = mutableListOf<ParsedIngredient>()
        val steps = mutableListOf<String>()
        var section: Section = Section.UNSET
        var sawIngredient = false

        for (rawLine in lines) {
            if (rawLine.isBlank()) continue
            val line = rawLine.trimEnd(':').trim()
            val lower = line.lowercase()

            // A lone URL line is the source, not content.
            if (link == null && (lower.startsWith("http://") || lower.startsWith("https://")) &&
                !lower.contains(' ')
            ) {
                link = rawLine
                continue
            }
            if (lower in INGREDIENT_HEADERS) {
                section = Section.INGREDIENTS
                continue
            }
            if (lower in STEP_HEADERS && title != null) {
                section = Section.STEPS
                continue
            }

            // Serves / yield / time lines carry fields, not content.
            val servingsRead = readServings(line)
            if (servingsRead != null) {
                servings = servingsRead
                continue
            }
            val minutesRead = readMinutes(line)
            if (minutesRead != null) {
                if (minutes == null) minutes = minutesRead
                continue
            }

            if (title == null && section == Section.UNSET && !looksLikeIngredient(line)) {
                title = line
                continue
            }

            when (section) {
                Section.INGREDIENTS -> {
                    IngredientLineParser.parse(rawLine).forEach { ingredients += it }
                    sawIngredient = ingredients.isNotEmpty()
                }
                Section.STEPS -> steps += cleanStep(rawLine)
                Section.UNSET -> {
                    if (looksLikeIngredient(line)) {
                        IngredientLineParser.parse(rawLine).forEach { ingredients += it }
                        sawIngredient = ingredients.isNotEmpty()
                    } else if (sawIngredient || looksLikeStep(line)) {
                        section = Section.STEPS
                        steps += cleanStep(rawLine)
                    } else if (title == null) {
                        title = line
                    } else {
                        // A line after the title that reads as neither:
                        // keep it with the ingredients only until the
                        // first real step appears; the review sorts out
                        // what the reading got wrong.
                        IngredientLineParser.parse(rawLine).forEach { ingredients += it }
                    }
                }
            }
        }

        return ImportedDraft(
            title = title ?: "",
            servings = servings ?: 4,
            activeMinutes = minutes ?: 0,
            cuisine = null,
            steps = steps,
            ingredients = ingredients,
            sourceLink = link,
        )
    }

    private enum class Section { UNSET, INGREDIENTS, STEPS }

    /** A line that starts with an amount or a unit word reads as an ingredient. */
    private fun looksLikeIngredient(line: String): Boolean {
        val parsed = IngredientLineParser.parse(line)
        val first = parsed.firstOrNull() ?: return false
        return first.amount != null || first.unit != null || first.unitWord != null
    }

    /** A long sentence-like line reads as a step once ingredients have run. */
    private fun looksLikeStep(line: String): Boolean =
        line.length > 60 && (line.endsWith('.') || line.contains(". "))

    private fun cleanStep(line: String): String =
        line.replace(Regex("^\\d+\\s*[.)]\\s*"), "")
            .replace(Regex("^[-*•]\\s*"), "")
            .trim()

    private val SERVINGS_PATTERNS = listOf(
        Regex("(?i)\\bserves\\s*:?\\s*(\\d+)"),
        Regex("(?i)\\bservings?\\s*:?\\s*(\\d+)"),
        Regex("(?i)\\byield\\s*:?\\s*(\\d+)"),
        Regex("(?i)\\bmakes\\s+(\\d+)\\s+servings?"),
    )

    private fun readServings(line: String): Int? {
        if (line.length > 60) return null
        for (pattern in SERVINGS_PATTERNS) {
            val value = pattern.find(line)?.groupValues?.get(1)?.toIntOrNull()
            if (value != null && value in 1..48) return value
        }
        return null
    }

    private val MINUTES_PATTERNS = listOf(
        Regex("(?i)\\btotal\\s*(?:time)?\\s*:?\\s*(\\d+)\\s*(?:min|mins|minutes)"),
        Regex("(?i)\\bprep\\s*(?:time)?\\s*:?\\s*(\\d+)\\s*(?:min|mins|minutes)"),
        Regex("(?i)\\bcook\\s*(?:time)?\\s*:?\\s*(\\d+)\\s*(?:min|mins|minutes)"),
        Regex("(?i)\\bready\\s+in\\s*:?\\s*(\\d+)\\s*(?:min|mins|minutes)"),
    )

    private fun readMinutes(line: String): Int? {
        if (line.length > 60) return null
        if (!line.contains("min", ignoreCase = true)) return null
        for (pattern in MINUTES_PATTERNS) {
            val value = pattern.find(line)?.groupValues?.get(1)?.toIntOrNull()
            if (value != null && value in 1..24 * 60) return value
        }
        return null
    }
}
