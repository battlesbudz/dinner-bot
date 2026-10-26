package com.battlesbudz.dinnerbot.core.model

import kotlinx.serialization.Serializable

/**
 * Nutrition for Dinner Bot (Phase V2-6), scoped by SCOPE_PLAN_V2 section B2:
 * figures are computed from each recipe's structured ingredients and
 * shown as information only. Nutrition never enters the planner,
 * costing, or any goal or budget logic, and the app makes no health
 * claims.
 *
 * Sources, in the order they are preferred (v0.6.3): an ingredient
 * whose price book item carries nutrition facts scanned from the
 * product's own label ([LabelNutrition], captured from Open Food Facts
 * by barcode) is computed from those facts; every other ingredient
 * reads the shipped USDA FoodData Central entry, exactly as before.
 *
 * Honesty rules, enforced here and in every display:
 * - A value the source does not publish for an entry is null in the
 *   seed and stays null through the math; it is never estimated or
 *   filled.
 * - Any ingredient with no entry, no gram weight for its unit, or a
 *   null field makes the affected recipe figures INCOMPLETE for that
 *   field. A label line whose quantity cannot be converted honestly
 *   falls back to the USDA entry for that line rather than fabricate.
 *   Displays render an incomplete total as partial, in words, instead
 *   of printing a number that pretends to be whole.
 */

/** The label fields Dinner Bot carries, in display order. */
enum class NutritionField(val label: String, val unitLabel: String) {
    CALORIES("Calories", "kcal"),
    PROTEIN("Protein", "g"),
    FAT("Fat", "g"),
    CARBOHYDRATE("Carbs", "g"),
    FIBER("Fiber", "g"),
    SUGARS("Sugars", "g"),
    SODIUM("Sodium", "mg"),
    SATURATED_FAT("Saturated fat", "g"),
    CHOLESTEROL("Cholesterol", "mg"),
}

/**
 * One set of nutrition figures. In a seed entry these are per 100 g of the
 * food; in a computed [RecipeNutrition] they are totals (whole recipe or
 * per serving). A null field means "no sourced value", never zero.
 */
@Serializable
data class NutritionValues(
    val caloriesKcal: Double? = null,
    val proteinG: Double? = null,
    val fatG: Double? = null,
    val carbohydrateG: Double? = null,
    val fiberG: Double? = null,
    val sugarsG: Double? = null,
    val sodiumMg: Double? = null,
    val saturatedFatG: Double? = null,
    val cholesterolMg: Double? = null,
) {
    fun value(field: NutritionField): Double? = when (field) {
        NutritionField.CALORIES -> caloriesKcal
        NutritionField.PROTEIN -> proteinG
        NutritionField.FAT -> fatG
        NutritionField.CARBOHYDRATE -> carbohydrateG
        NutritionField.FIBER -> fiberG
        NutritionField.SUGARS -> sugarsG
        NutritionField.SODIUM -> sodiumMg
        NutritionField.SATURATED_FAT -> saturatedFatG
        NutritionField.CHOLESTEROL -> cholesterolMg
    }

    /** Every field times [factor]; nulls stay null. */
    fun scaled(factor: Double): NutritionValues = NutritionValues(
        caloriesKcal = caloriesKcal?.times(factor),
        proteinG = proteinG?.times(factor),
        fatG = fatG?.times(factor),
        carbohydrateG = carbohydrateG?.times(factor),
        fiberG = fiberG?.times(factor),
        sugarsG = sugarsG?.times(factor),
        sodiumMg = sodiumMg?.times(factor),
        saturatedFatG = saturatedFatG?.times(factor),
        cholesterolMg = cholesterolMg?.times(factor),
    )

    companion object {
        fun of(field: NutritionField, value: Double?): NutritionValues = when (field) {
            NutritionField.CALORIES -> NutritionValues(caloriesKcal = value)
            NutritionField.PROTEIN -> NutritionValues(proteinG = value)
            NutritionField.FAT -> NutritionValues(fatG = value)
            NutritionField.CARBOHYDRATE -> NutritionValues(carbohydrateG = value)
            NutritionField.FIBER -> NutritionValues(fiberG = value)
            NutritionField.SUGARS -> NutritionValues(sugarsG = value)
            NutritionField.SODIUM -> NutritionValues(sodiumMg = value)
            NutritionField.SATURATED_FAT -> NutritionValues(saturatedFatG = value)
            NutritionField.CHOLESTEROL -> NutritionValues(cholesterolMg = value)
        }
    }
}

/**
 * One shipped ingredient's sourced nutrition entry (Phase V2-0 capture,
 * seed-data/nutrition.json). Exactly one entry per shipped ingredient,
 * keyed by price item id. [pantryItem] is the pre-v0.4.3 form: while the
 * staples were unpriced pantry names, their entries were keyed by the
 * slug of the staple name with the flag true; the staples are priced
 * items now and their entries carry the flag false like every other.
 */
@Serializable
data class IngredientNutrition(
    val itemId: String,
    val pantryItem: Boolean = false,
    val fdcId: Long,
    val fdcDescription: String,
    val fdcDataType: String = "",
    /** True when the shipped item is a packaged product and the FDC entry is the closest match for it. */
    val closestMatch: Boolean = false,
    val source: String,
    val per100g: NutritionValues,
    /**
     * Grams for ONE unit of a unit that is not itself a weight, keyed by
     * [MeasureUnit] name (for example "COUNT" -> 50.0 for one large egg).
     * Each weight comes from the FoodData Central record's published food
     * portions for [fdcId], or from the item's own verified pack facts in
     * the price book (a whole can, a slice of an 8-slice box), and says so
     * in [gramWeightSource]. Weight-unit quantities never need this: they
     * convert exactly through the unit system. An ingredient measured in a
     * unit with no entry here has no gram weight, so its contribution is
     * partial and the incomplete rule applies.
     */
    val gramWeights: Map<String, Double> = emptyMap(),
    val gramWeightSource: String? = null,
)

@Serializable
data class NutritionFile(
    val version: Int,
    val source: String = "",
    val capturedDate: String = "",
    val entries: List<IngredientNutrition>,
    /** Item ids knowingly without an entry; empty while coverage is complete. */
    val incomplete: List<String> = emptyList(),
)

/**
 * Computed nutrition for one recipe (or, summed, for a week): [totals]
 * for the servings the figures were computed at, [perServing] alongside,
 * and the fields whose totals are partial because at least one included
 * ingredient had no sourced value for them.
 */
data class RecipeNutrition(
    val totals: NutritionValues,
    val perServing: NutritionValues,
    val incompleteFields: Set<NutritionField>,
    /** True when any included ingredient's entry is a closest match for a packaged product. */
    val closestMatchUsed: Boolean,
    /** True when any included ingredient was computed from its own scanned label facts (v0.6.3). */
    val labelFactsUsed: Boolean = false,
    /** True when any included ingredient's numbers came from the USDA data (v0.6.3). */
    val usdaUsed: Boolean = false,
) {
    val isPartial: Boolean get() = incompleteFields.isNotEmpty()
}

object NutritionCalculator {

    /** Slug shared with the pantry seeding, so a staple name finds its entry. */
    fun pantrySlug(name: String): String =
        name.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')

    /**
     * Grams of one ingredient, or null when the quantity cannot be turned
     * into grams honestly: weight units convert exactly through the unit
     * system; any other unit needs the entry's recorded gram weight for
     * that unit (from the FDC record's portions or the pack's own facts).
     */
    fun gramsFor(ingredient: RecipeIngredient, entry: IngredientNutrition?): Double? {
        val quantity = ingredient.quantity
        if (quantity.unit.dimension == Dimension.WEIGHT) {
            return quantity.convertTo(MeasureUnit.GRAM).amount
        }
        val perUnit = entry?.gramWeights?.get(quantity.unit.name) ?: return null
        return perUnit * quantity.amount
    }

    /** The entry an ingredient reads: priced items after the household substitution, staples by name slug. */
    fun entryFor(
        ingredient: RecipeIngredient,
        byItemId: Map<String, IngredientNutrition>,
        byPantrySlug: Map<String, IngredientNutrition>,
    ): IngredientNutrition? {
        ingredient.pantryItem?.let { return byPantrySlug[pantrySlug(it)] }
        val rawId = ingredient.priceItemId ?: return null
        return byItemId[Costing.substituteItemId(rawId)] ?: byItemId[rawId]
    }

    /**
     * The scanned label facts an ingredient reads, when its price book
     * item has any (v0.6.3): priced items after the household
     * substitution, like [entryFor]. Pantry staples carry no barcode,
     * so they never have label facts.
     */
    fun labelFor(
        ingredient: RecipeIngredient,
        labelByItemId: Map<String, LabelNutrition>,
    ): LabelNutrition? {
        if (ingredient.pantryItem != null) return null
        val rawId = ingredient.priceItemId ?: return null
        return labelByItemId[Costing.substituteItemId(rawId)] ?: labelByItemId[rawId]
    }

    /**
     * One ingredient's contribution from its scanned label facts, or
     * null when the line cannot be converted honestly (the caller then
     * falls back to the USDA entry for that line rather than fabricate
     * a number): a weight line converts through the per-100 g facts; a
     * count line reads the label's per-serving facts, or the per-100 g
     * facts through the serving's gram weight when no per-serving set
     * was published; every other unit has no honest bridge from a
     * label.
     */
    fun labelContribution(
        ingredient: RecipeIngredient,
        label: LabelNutrition,
    ): NutritionValues? {
        val quantity = ingredient.quantity
        if (quantity.unit.dimension == Dimension.WEIGHT) {
            val grams = quantity.convertTo(MeasureUnit.GRAM).amount
            return label.per100g.scaled(grams / 100.0)
        }
        if (quantity.unit == MeasureUnit.COUNT || quantity.unit == MeasureUnit.EACH) {
            label.perServing?.let { return it.scaled(quantity.amount) }
            val servingGrams = label.servingGrams ?: return null
            return label.per100g.scaled(servingGrams * quantity.amount / 100.0)
        }
        return null
    }

    /**
     * Nutrition for [recipe] cooked at [forServings]. The recipe passed in
     * is the one the household actually cooks: callers pass the won't-eat
     * adapted recipe, so a removed removable ingredient is out of the
     * figures exactly as it is out of the dish. An ingredient whose item
     * carries scanned label facts is computed from the label (see
     * [labelContribution]); a label line that cannot be converted falls
     * back to the USDA entry for that line, unchanged.
     */
    fun recipeNutrition(
        recipe: Recipe,
        forServings: Int,
        byItemId: Map<String, IngredientNutrition>,
        byPantrySlug: Map<String, IngredientNutrition>,
        labelByItemId: Map<String, LabelNutrition> = emptyMap(),
    ): RecipeNutrition {
        require(forServings > 0) { "servings must be positive" }
        val sums = mutableMapOf<NutritionField, Double>()
        val incomplete = mutableSetOf<NutritionField>()
        var closest = false
        var labelUsed = false
        var usdaUsed = false
        for (ingredient in recipe.scaledIngredients(forServings)) {
            val label = labelFor(ingredient, labelByItemId)
            val fromLabel = label?.let { labelContribution(ingredient, it) }
            if (label != null && fromLabel != null) {
                labelUsed = true
                for (field in NutritionField.entries) {
                    val value = fromLabel.value(field)
                    if (value == null) {
                        incomplete += field
                    } else {
                        sums[field] = (sums[field] ?: 0.0) + value
                    }
                }
                continue
            }
            val entry = entryFor(ingredient, byItemId, byPantrySlug)
            if (entry == null) {
                incomplete += NutritionField.entries
                continue
            }
            if (entry.closestMatch) closest = true
            val grams = gramsFor(ingredient, entry)
            if (grams == null) {
                incomplete += NutritionField.entries
                continue
            }
            usdaUsed = true
            for (field in NutritionField.entries) {
                val per100 = entry.per100g.value(field)
                if (per100 == null) {
                    incomplete += field
                } else {
                    sums[field] = (sums[field] ?: 0.0) + per100 * grams / 100.0
                }
            }
        }
        val totals = NutritionValues(
            caloriesKcal = sums[NutritionField.CALORIES],
            proteinG = sums[NutritionField.PROTEIN],
            fatG = sums[NutritionField.FAT],
            carbohydrateG = sums[NutritionField.CARBOHYDRATE],
            fiberG = sums[NutritionField.FIBER],
            sugarsG = sums[NutritionField.SUGARS],
            sodiumMg = sums[NutritionField.SODIUM],
            saturatedFatG = sums[NutritionField.SATURATED_FAT],
            cholesterolMg = sums[NutritionField.CHOLESTEROL],
        )
        return RecipeNutrition(
            totals = totals,
            perServing = totals.scaled(1.0 / forServings),
            incompleteFields = incomplete,
            closestMatchUsed = closest,
            labelFactsUsed = labelUsed,
            usdaUsed = usdaUsed,
        )
    }

    /**
     * Sum computed figures (a week's nights into week totals): each part's
     * totals add into the combined totals and each part's per-serving
     * figures add into the combined per-serving figures, so a week reads
     * both per night and per serving. A field stays null only when no part
     * has a sourced value for it; incompleteness unions.
     */
    fun sum(parts: List<RecipeNutrition>): RecipeNutrition {
        fun combine(pick: (RecipeNutrition) -> NutritionValues): NutritionValues {
            val out = mutableMapOf<NutritionField, Double>()
            for (part in parts) {
                val values = pick(part)
                for (field in NutritionField.entries) {
                    values.value(field)?.let { out[field] = (out[field] ?: 0.0) + it }
                }
            }
            return NutritionValues(
                caloriesKcal = out[NutritionField.CALORIES],
                proteinG = out[NutritionField.PROTEIN],
                fatG = out[NutritionField.FAT],
                carbohydrateG = out[NutritionField.CARBOHYDRATE],
                fiberG = out[NutritionField.FIBER],
                sugarsG = out[NutritionField.SUGARS],
                sodiumMg = out[NutritionField.SODIUM],
                saturatedFatG = out[NutritionField.SATURATED_FAT],
                cholesterolMg = out[NutritionField.CHOLESTEROL],
            )
        }
        return RecipeNutrition(
            totals = combine { it.totals },
            perServing = combine { it.perServing },
            incompleteFields = parts.flatMap { it.incompleteFields }.toSet(),
            closestMatchUsed = parts.any { it.closestMatchUsed },
            labelFactsUsed = parts.any { it.labelFactsUsed },
            usdaUsed = parts.any { it.usdaUsed },
        )
    }
}
