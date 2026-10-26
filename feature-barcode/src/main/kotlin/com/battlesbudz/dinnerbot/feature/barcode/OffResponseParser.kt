package com.battlesbudz.dinnerbot.feature.barcode

import com.battlesbudz.dinnerbot.core.model.LabelNutrition
import com.battlesbudz.dinnerbot.core.model.NutritionValues

/**
 * One product as Open Food Facts reports it (Phase V2-5 name hint;
 * v0.6.3 adds the pack quantity and the label nutrition facts). Every
 * field is a suggestion OFF's crowd-sourced data offers: callers show
 * prefills for the user to edit, and the nutrition facts land on the
 * price book item as its label facts only because she confirmed the
 * item. Any field OFF does not publish stays null.
 */
data class OffProduct(
    val name: String?,
    /** The pack size as OFF words it ("500 g", "12 x 355 ml", "NET WT 16 OZ (453g)"). */
    val quantityText: String?,
    /** The numeric pack quantity OFF states, in [productQuantityUnit]. */
    val productQuantity: Double?,
    val productQuantityUnit: String?,
    /** The serving as OFF words it ("30 g", "1 cup (240 ml)"). */
    val servingSizeText: String?,
    val servingQuantity: Double?,
    val servingQuantityUnit: String?,
    /** The label's nutriments per 100 g; null when OFF publishes none. */
    val per100g: NutritionValues?,
    /** The label's nutriments per serving; null when OFF publishes none. */
    val perServing: NutritionValues?,
) {
    /**
     * Grams in one label serving, when the serving is stated in a
     * weight unit (directly, or in the serving text's parenthesized
     * gram figure). A serving stated only in volume yields null: no
     * density is invented.
     */
    val servingGrams: Double?
        get() {
            val quantity = servingQuantity
            if (quantity != null && quantity > 0) {
                when (servingQuantityUnit?.trim()?.lowercase()) {
                    null, "", "g", "gram", "grams" -> return quantity
                    "kg" -> return quantity * 1000.0
                }
            }
            val text = servingSizeText ?: return null
            val inParens = Regex("""\((\d+(?:\.\d+)?)\s*g\)""").find(text)
            if (inParens != null) return inParens.groupValues[1].toDoubleOrNull()
            val plain = Regex("""^(\d+(?:\.\d+)?)\s*g\b""").find(text.trim())
            return plain?.groupValues?.get(1)?.toDoubleOrNull()
        }

    /** The stored label facts for [itemId], or null when this product published no per-100 g facts. */
    fun labelNutrition(itemId: String, barcode: String, nowMillis: Long): LabelNutrition? =
        per100g?.let { per100 ->
            LabelNutrition(
                itemId = itemId,
                barcode = barcode,
                per100g = per100,
                perServing = perServing,
                servingSizeText = servingSizeText,
                servingGrams = servingGrams,
                updatedAtMillis = nowMillis,
            )
        }
}

/**
 * Reading an Open Food Facts product response as text (no JSON library
 * on this module's classpath). OFF answers {"status":1,"product":{...}}
 * for a known code and {"status":0,...} for an unknown one.
 */
object OffResponseParser {

    /** The full product, or null when the response names no known product. */
    fun parseProduct(responseBody: String): OffProduct? {
        if (extractJsonLong(responseBody, "status") == 0L) return null
        val name = parseProductName(responseBody)
        val nutriments = nutrimentsSegment(responseBody)
        val per100g = nutriments?.let { parseNutriments(it, "100g") }?.takeIf { it.hasAnyValue() }
        val perServing = nutriments?.let { parseNutriments(it, "serving") }?.takeIf { it.hasAnyValue() }
        val product = OffProduct(
            name = name,
            quantityText = extractJsonString(responseBody, "quantity")?.trim()?.takeIf { it.isNotEmpty() },
            productQuantity = extractJsonDouble(responseBody, "product_quantity"),
            productQuantityUnit = extractJsonString(responseBody, "product_quantity_unit"),
            servingSizeText = extractJsonString(responseBody, "serving_size")?.trim()?.takeIf { it.isNotEmpty() },
            servingQuantity = extractJsonDouble(responseBody, "serving_quantity"),
            servingQuantityUnit = extractJsonString(responseBody, "serving_quantity_unit"),
            per100g = per100g,
            perServing = perServing,
        )
        // A response with no name, no quantity, and no facts carries
        // nothing a caller can use; report it as no product at all.
        return if (product.name == null && product.quantityText == null && per100g == null) {
            null
        } else {
            product
        }
    }

    /**
     * Pull a product name out of an Open Food Facts product response, or
     * null when the response holds none. The lookup is only ever a hint;
     * a name found here is shown to the user as a suggestion and never
     * links or prices anything by itself.
     */
    fun parseProductName(responseBody: String): String? {
        if (extractJsonLong(responseBody, "status") == 0L) return null
        for (key in listOf("product_name", "product_name_en", "generic_name", "generic_name_en")) {
            val value = extractJsonString(responseBody, key)
            if (!value.isNullOrBlank()) return value.trim()
        }
        return null
    }

    private fun NutritionValues.hasAnyValue(): Boolean =
        listOf(
            caloriesKcal, proteinG, fatG, carbohydrateG, fiberG,
            sugarsG, sodiumMg, saturatedFatG, cholesterolMg,
        ).any { it != null }

    /**
     * One suffix family's nutriments ("100g" or "serving") read as
     * label facts. OFF expresses sodium and cholesterol per 100 g in
     * grams; the app's figures carry milligrams, so they convert.
     * Energy prefers the kcal key and converts the kJ key when only
     * that is published. Sodium falls back to the salt figure through
     * the label conversion (sodium is 40% of salt by weight).
     */
    private fun parseNutriments(segment: String, suffix: String): NutritionValues {
        fun grams(key: String): Double? = extractJsonDouble(segment, "${key}_$suffix")
        val energyKcal = grams("energy-kcal") ?: grams("energy")?.div(4.184)
        val sodiumMg = grams("sodium")?.times(1000.0)
            ?: grams("salt")?.times(400.0)
        return NutritionValues(
            caloriesKcal = energyKcal,
            proteinG = grams("proteins"),
            fatG = grams("fat"),
            carbohydrateG = grams("carbohydrates"),
            fiberG = grams("fiber"),
            sugarsG = grams("sugars"),
            sodiumMg = sodiumMg,
            saturatedFatG = grams("saturated-fat"),
            cholesterolMg = grams("cholesterol")?.times(1000.0),
        )
    }

    /** The "nutriments" object as a text segment, so its keys are read scoped to it. */
    private fun nutrimentsSegment(text: String): String? {
        val needle = "\"nutriments\""
        val keyAt = text.indexOf(needle)
        if (keyAt < 0) return null
        val open = text.indexOf('{', keyAt + needle.length)
        if (open < 0) return null
        var depth = 0
        var inString = false
        var i = open
        while (i < text.length) {
            val ch = text[i]
            if (inString) {
                if (ch == '\\') {
                    i++
                } else if (ch == '"') {
                    inString = false
                }
            } else {
                when (ch) {
                    '"' -> inString = true
                    '{' -> depth++
                    '}' -> {
                        depth--
                        if (depth == 0) return text.substring(open, i + 1)
                    }
                }
            }
            i++
        }
        return null
    }

    /** Reads the string value of the first "key" occurrence, unescaping JSON string escapes. */
    internal fun extractJsonString(text: String, key: String): String? {
        val needle = "\"$key\""
        var from = 0
        while (true) {
            val keyAt = text.indexOf(needle, from)
            if (keyAt < 0) return null
            var i = keyAt + needle.length
            while (i < text.length && text[i].isWhitespace()) i++
            if (i >= text.length || text[i] != ':') {
                from = keyAt + needle.length
                continue
            }
            i++
            while (i < text.length && text[i].isWhitespace()) i++
            if (i >= text.length || text[i] != '"') return null
            i++
            val out = StringBuilder()
            while (i < text.length) {
                val ch = text[i]
                when {
                    ch == '\\' && i + 1 < text.length -> {
                        when (val esc = text[i + 1]) {
                            'n' -> out.append('\n')
                            't' -> out.append('\t')
                            'r' -> out.append('\r')
                            'u' -> {
                                val hex = text.substring(i + 2, minOf(i + 6, text.length))
                                hex.toIntOrNull(16)?.let { out.append(it.toChar()) }
                                i += 4
                            }
                            else -> out.append(esc)
                        }
                        i += 2
                    }
                    ch == '"' -> return out.toString()
                    else -> {
                        out.append(ch)
                        i++
                    }
                }
            }
            return null
        }
    }

    /** Reads the numeric value of the first "key" occurrence, or null. */
    internal fun extractJsonLong(text: String, key: String): Long? =
        extractJsonDouble(text, key)?.toLong()

    /**
     * Reads the number value of the first "key" occurrence, as a JSON
     * number or as a quoted numeric string (OFF publishes some
     * nutriments as strings). Null when absent or not numeric.
     */
    internal fun extractJsonDouble(text: String, key: String): Double? {
        val needle = "\"$key\""
        val keyAt = text.indexOf(needle)
        if (keyAt < 0) return null
        var i = keyAt + needle.length
        while (i < text.length && (text[i].isWhitespace() || text[i] == ':')) i++
        if (i >= text.length) return null
        if (text[i] == '"') {
            return extractJsonString(text, key)?.toDoubleOrNull()
        }
        val start = i
        while (i < text.length &&
            (text[i].isDigit() || text[i] == '-' || text[i] == '+' || text[i] == '.' ||
                text[i] == 'e' || text[i] == 'E')
        ) {
            i++
        }
        if (i == start) return null
        return text.substring(start, i).toDoubleOrNull()
    }
}
