package com.battlesbudz.dinnerbot.feature.barcode

import com.battlesbudz.dinnerbot.core.model.MeasureUnit

/**
 * A pack size parsed out of Open Food Facts quantity data (v0.6.3):
 * the amount and unit for the price book's pack fields, plus the
 * description text to suggest. Any part that cannot be read honestly
 * stays null: an unparseable pack simply leaves the form's pack fields
 * for manual entry and never blocks the save.
 */
data class OffPack(
    val description: String?,
    val amount: Double?,
    val unit: MeasureUnit?,
)

/**
 * The conservative OFF quantity reader. Real-world quantity texts
 * include "500 g", "1 L", "12 x 355 ml", "NET WT 16 OZ (453g)". The
 * rules, in order: the structured product_quantity + unit wins; a
 * multipack "N x M unit" totals its packs; a parenthesized figure
 * (usually the metric twin of an imperial statement) wins over the
 * bare text; a plain "amount unit" reads directly. The serving size,
 * when OFF states one, folds into the suggested description so the
 * book remembers it; it never invents an amount.
 */
object OffPackParser {

    private val unitByName: Map<String, MeasureUnit> = mapOf(
        "g" to MeasureUnit.GRAM,
        "gram" to MeasureUnit.GRAM,
        "grams" to MeasureUnit.GRAM,
        "kg" to MeasureUnit.KILOGRAM,
        "ml" to MeasureUnit.MILLILITER,
        "l" to MeasureUnit.LITER,
        "liter" to MeasureUnit.LITER,
        "liters" to MeasureUnit.LITER,
        "litre" to MeasureUnit.LITER,
        "litres" to MeasureUnit.LITER,
        "oz" to MeasureUnit.OUNCE,
        "fl oz" to MeasureUnit.FLUID_OUNCE,
        "floz" to MeasureUnit.FLUID_OUNCE,
        "lb" to MeasureUnit.POUND,
        "lbs" to MeasureUnit.POUND,
    )

    // Longest alternatives first so "fl oz" wins over "oz" and
    // "grams" over "g" at the same position.
    private const val UNIT_PATTERN = "fl oz|grams?|kg|ml|liters?|litres?|lbs?|oz|l|g"

    private val multipackRegex = Regex(
        """(\d+(?:\.\d+)?)\s*[x×]\s*(\d+(?:\.\d+)?)\s*($UNIT_PATTERN)\b""",
        RegexOption.IGNORE_CASE,
    )
    private val parenthesizedRegex = Regex(
        """\((\d+(?:\.\d+)?)\s*($UNIT_PATTERN)\)""",
        RegexOption.IGNORE_CASE,
    )
    private val plainRegex = Regex(
        """(\d+(?:\.\d+)?)\s*($UNIT_PATTERN)\b""",
        RegexOption.IGNORE_CASE,
    )

    fun parse(
        quantityText: String?,
        productQuantity: Double?,
        productQuantityUnit: String?,
        servingSizeText: String?,
    ): OffPack {
        val text = quantityText?.trim()?.takeIf { it.isNotEmpty() }
        var amount: Double? = null
        var unit: MeasureUnit? = null

        val structuredUnit = productQuantityUnit?.trim()?.lowercase()?.let { unitByName[it] }
        if (productQuantity != null && productQuantity > 0 && structuredUnit != null) {
            amount = productQuantity
            unit = structuredUnit
        }
        if (amount == null && text != null) {
            val multipack = multipackRegex.find(text)
            val parenthesized = parenthesizedRegex.find(text)
            val plain = plainRegex.find(text)
            when {
                multipack != null -> {
                    val packs = multipack.groupValues[1].toDoubleOrNull()
                    val each = multipack.groupValues[2].toDoubleOrNull()
                    val parsedUnit = unitByName[multipack.groupValues[3].lowercase()]
                    if (packs != null && each != null && parsedUnit != null) {
                        amount = packs * each
                        unit = parsedUnit
                    }
                }
                parenthesized != null -> {
                    amount = parenthesized.groupValues[1].toDoubleOrNull()
                    unit = unitByName[parenthesized.groupValues[2].lowercase()]
                }
                plain != null -> {
                    amount = plain.groupValues[1].toDoubleOrNull()
                    unit = unitByName[plain.groupValues[2].lowercase()]
                }
            }
        }

        var description: String? = text
        if (description == null && amount != null && productQuantityUnit != null) {
            description = "${formatAmount(productQuantity ?: amount)} ${productQuantityUnit.trim()}"
        }
        val serving = servingSizeText?.trim()?.takeIf { it.isNotEmpty() }
        if (description != null && serving != null) {
            description = "$description · serving $serving"
        }
        return OffPack(description = description, amount = amount, unit = unit)
    }

    private fun formatAmount(amount: Double): String =
        if (amount == amount.toLong().toDouble()) {
            amount.toLong().toString()
        } else {
            amount.toString()
        }
}
