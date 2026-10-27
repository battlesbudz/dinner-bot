package com.battlesbudz.dinnerbot.core.model

import kotlinx.serialization.Serializable

/**
 * Nutrition facts captured from a product's own label through an
 * Open Food Facts barcode lookup (v0.6.3, the user's call: "rather grab
 * the nutrition facts from the barcode instead of just guessing off of
 * National data"). One record per price book item, keyed by item id,
 * refreshed every time the item's barcode is scanned again.
 *
 * The facts are stored exactly as the label states them: [per100g] in
 * the OFF nutriments per-100 g form, and [perServing] in the
 * per-serving form when OFF publishes it. A field the label does not
 * state stays null, exactly like a field FoodData Central does not
 * publish; nothing is estimated or filled in here.
 *
 * [servingGrams] is the label serving expressed in grams when OFF
 * states the serving in a weight unit; it is the bridge that lets a
 * count line ("2 eggs") be costed from the per-100 g facts when no
 * per-serving set was published. A serving OFF states only in volume
 * (ml) yields no gram bridge and stays null: converting it would need
 * a density the label never gave.
 */
@Serializable
data class LabelNutrition(
    val itemId: String,
    /** The barcode these facts were read from; scanning it again refreshes them. */
    val barcode: String,
    val per100g: NutritionValues,
    /** The label's per-serving facts, when OFF publishes them. */
    val perServing: NutritionValues? = null,
    /** The serving as the label words it ("1 cup (240 ml)"), display only. */
    val servingSizeText: String? = null,
    /** Grams in one label serving, when OFF states the serving in a weight unit. */
    val servingGrams: Double? = null,
    val updatedAtMillis: Long = 0L,
)
