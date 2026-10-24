package com.battlesbudz.dinnerbot.feature.`import`

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull

/**
 * Reading a recipe out of a page's structured data (Phase RI-2, scope
 * B2): most recipe sites embed a schema.org Recipe as JSON-LD, in three
 * instruction shapes (one string, a list of strings, HowToStep and
 * HowToSection objects), and this reads all of them. One-shot and
 * total: a page with no usable structured recipe returns null, and the
 * import falls back to paste text, which is the guaranteed path.
 */
object RecipeJsonLd {

    private val json = Json { ignoreUnknownKeys = true }

    private val SCRIPT_BLOCK = Regex(
        "<script[^>]*type\\s*=\\s*[\"']application/ld\\+json[\"'][^>]*>(.*?)</script>",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    )

    /** The first usable Recipe in [html]'s JSON-LD blocks, or null. */
    fun extract(html: String, pageUrl: String?): ImportedDraft? {
        for (match in SCRIPT_BLOCK.findAll(html)) {
            val body = match.groupValues[1].trim()
            if (body.isEmpty()) continue
            val root = runCatching { json.parseToJsonElement(body) }.getOrNull() ?: continue
            val recipeNode = findRecipe(root) ?: continue
            val draft = readRecipe(recipeNode, pageUrl)
            if (draft != null) return draft
        }
        return null
    }

    /** A Recipe node anywhere in the JSON: top level, in an array, or in an @graph. */
    private fun findRecipe(element: JsonElement): JsonObject? {
        when (element) {
            is JsonArray -> {
                for (child in element) findRecipe(child)?.let { return it }
            }
            is JsonObject -> {
                if (isRecipeType(element["@type"])) return element
                (element["@graph"] as? JsonArray)?.let { graph ->
                    for (child in graph) findRecipe(child)?.let { return it }
                }
                for ((key, child) in element) {
                    if (key == "@type") continue
                    if (child is JsonObject || child is JsonArray) {
                        findRecipe(child)?.let { return it }
                    }
                }
            }
            else -> Unit
        }
        return null
    }

    private fun isRecipeType(type: JsonElement?): Boolean = when (type) {
        is JsonPrimitive -> type.contentOrNull?.equals("Recipe", ignoreCase = true) == true
        is JsonArray -> type.any {
            (it as? JsonPrimitive)?.contentOrNull?.equals("Recipe", ignoreCase = true) == true
        }
        else -> false
    }

    private fun readRecipe(node: JsonObject, pageUrl: String?): ImportedDraft? {
        val title = node["name"].asText()?.let { stripHtml(it) }.orEmpty()
        val ingredientLines = (node["recipeIngredient"] as? JsonArray)
            ?.mapNotNull { it.asText() }
            ?.map { stripHtml(it) }
            ?: emptyList()
        // A node with no name or no ingredients is not a usable recipe:
        // the import falls back to paste text rather than opening a
        // review Andrea cannot save.
        if (title.isEmpty() || ingredientLines.isEmpty()) return null
        val ingredients = ingredientLines.flatMap { IngredientLineParser.parse(it) }
        val steps = instructionTexts(node["recipeInstructions"])
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        val totalMinutes = durationMinutes(node["totalTime"].asText())
            ?: listOfNotNull(
                durationMinutes(node["prepTime"].asText()),
                durationMinutes(node["cookTime"].asText()),
            ).takeIf { it.isNotEmpty() }?.sum()
        return ImportedDraft(
            title = title,
            servings = yieldServings(node["recipeYield"]) ?: 4,
            activeMinutes = totalMinutes ?: 0,
            cuisine = node["recipeCuisine"].asTexts().firstOrNull(),
            steps = steps,
            ingredients = ingredients,
            sourceLink = pageUrl,
        )
    }

    /** Instructions in every shipped shape, flattened to step strings. */
    private fun instructionTexts(element: JsonElement?): List<String> = when (element) {
        null -> emptyList()
        is JsonPrimitive -> element.contentOrNull
            // Lines are split BEFORE any HTML stripping: stripping
            // collapses whitespace, newlines included, and a
            // newline-separated instruction string would merge into
            // one step.
            ?.lines()
            ?.map { stripHtml(it) }
            ?.filter { it.isNotEmpty() }
            ?: emptyList()
        is JsonArray -> element.flatMap { instructionTexts(it) }
        is JsonObject -> {
            val own = element["text"].asText()?.let { listOf(stripHtml(it)) } ?: emptyList()
            val nested = (element["itemListElement"] as? JsonArray)
                ?.flatMap { instructionTexts(it) } ?: emptyList()
            own + nested
        }
        else -> emptyList()
    }

    /** recipeYield as servings: a number, "4 servings", "Serves 4", or a list of those. */
    private fun yieldServings(element: JsonElement?): Int? = when (element) {
        is JsonPrimitive -> element.intOrNull
            ?: element.contentOrNull?.let { text ->
                Regex("\\d+").find(text)?.value?.toIntOrNull()
            }
        is JsonArray -> element.firstNotNullOfOrNull { yieldServings(it) }
        else -> null
    }?.takeIf { it in 1..48 }

    /** ISO-8601 durations as recipe sites write them: PT45M, PT1H15M, PT2H. */
    private fun durationMinutes(text: String?): Int? {
        if (text.isNullOrBlank()) return null
        val match = Regex("(?i)PT(?:(\\d+)H)?(?:(\\d+)M)?(?:(\\d+)S)?").find(text) ?: return null
        val hours = match.groupValues[1].toIntOrNull() ?: 0
        val minutes = match.groupValues[2].toIntOrNull() ?: 0
        val seconds = match.groupValues[3].toIntOrNull() ?: 0
        val total = hours * 60 + minutes + if (seconds > 0) 1 else 0
        return total.takeIf { it > 0 }
    }

    private fun stripHtml(text: String): String = text
        .replace(Regex("<[^>]*>"), " ")
        .replace("&nbsp;", " ")
        .replace("&amp;", "&")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&apos;", "'")
        .replace(Regex("\\s+"), " ")
        .trim()

    private fun JsonElement?.asText(): String? =
        (this as? JsonPrimitive)?.contentOrNull

    private fun JsonElement?.asTexts(): List<String> = when (this) {
        is JsonPrimitive -> listOfNotNull(contentOrNull)
        is JsonArray -> mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
        else -> emptyList()
    }
}
