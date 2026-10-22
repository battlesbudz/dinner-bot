package com.battlesbudz.dinnerbot.dataseed

import com.battlesbudz.dinnerbot.core.model.Costing
import com.battlesbudz.dinnerbot.core.model.DietPreset
import com.battlesbudz.dinnerbot.core.model.Dimension
import com.battlesbudz.dinnerbot.core.model.IngredientNutrition
import com.battlesbudz.dinnerbot.core.model.MeasureUnit
import com.battlesbudz.dinnerbot.core.model.NutritionCalculator
import com.battlesbudz.dinnerbot.core.model.PriceItem
import com.battlesbudz.dinnerbot.core.model.Recipe
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Validates the canonical seed data (repo-root seed-data/). Rules come from the
 * Scope and Implementation Plans: every recipe ingredient must map to a priced
 * price book item (or be an explicit pantry staple), no zero prices, every item
 * has a home store (enum, so presence is structural), pack sizes present, and
 * the household rule: nothing references beef.
 */
object SeedValidator {
    fun validate(priceItems: List<PriceItem>, recipes: List<Recipe>): List<String> {
        val errors = mutableListOf<String>()

        val itemIds = priceItems.map { it.id }
        if (itemIds.size != itemIds.toSet().size) errors += "duplicate price item ids"
        for (item in priceItems) {
            if (item.name.isBlank()) errors += "price item ${item.id}: blank name"
            if (item.category.isBlank()) errors += "price item ${item.id}: blank category"
            if (item.packPriceCents <= 0) errors += "price item ${item.id}: price must be positive, was ${item.packPriceCents}"
            if (item.packQuantity.amount <= 0.0) errors += "price item ${item.id}: pack size must be positive"
            if (item.source.isBlank()) errors += "price item ${item.id}: missing source"
            runCatching { LocalDate.parse(item.checkedDate, DateTimeFormatter.ISO_LOCAL_DATE) }
                .onFailure { errors += "price item ${item.id}: checkedDate '${item.checkedDate}' is not yyyy-MM-dd" }
            if (item.name.contains("beef", ignoreCase = true) || item.id.contains("beef", ignoreCase = true)) {
                errors += "price item ${item.id}: beef is banned by the household rule"
            }
        }

        val recipeIds = recipes.map { it.id }
        if (recipeIds.size != recipeIds.toSet().size) errors += "duplicate recipe ids"
        val byId = priceItems.associateBy { it.id }
        for (recipe in recipes) {
            if (recipe.title.contains("beef", ignoreCase = true)) {
                errors += "recipe ${recipe.id}: beef is banned by the household rule"
            }
            if (recipe.servings <= 0) errors += "recipe ${recipe.id}: servings must be positive"
            if (recipe.steps.isEmpty()) errors += "recipe ${recipe.id}: no steps"
            if (recipe.ingredients.isEmpty()) errors += "recipe ${recipe.id}: no ingredients"
            for (ingredient in recipe.ingredients) {
                if (ingredient.quantity.amount <= 0.0) {
                    errors += "recipe ${recipe.id}: ingredient quantity must be positive"
                }
                val itemId = ingredient.priceItemId
                if (itemId != null && itemId !in byId) {
                    errors += "recipe ${recipe.id}: ingredient references unknown price item '$itemId'"
                }
            }
        }
        return errors
    }

    /**
     * Validate the nutrition entries (Phase V2-6) against the shipped
     * ingredient set the recipes actually use. The honesty rules from the
     * V2-0 capture, made mechanical:
     * - every entry traces to a named FoodData Central record (id,
     *   description, source note) and carries at least calories;
     * - every ingredient the recipes use either has an entry or is named
     *   on the known-incomplete list, and the list names nothing else, so
     *   it matches the seed exactly and can never quietly grow stale;
     * - a gram weight is recorded only for a non-weight unit (weight
     *   converts exactly), is positive, and names where it came from.
     */
    fun validateNutrition(
        entries: List<IngredientNutrition>,
        priceItems: List<PriceItem>,
        recipes: List<Recipe>,
        knownIncomplete: List<String>,
    ): List<String> {
        val errors = mutableListOf<String>()
        val entryIds = entries.map { it.itemId }
        if (entryIds.size != entryIds.toSet().size) errors += "duplicate nutrition entry ids"
        val priceIds = priceItems.map { it.id }.toSet()
        for (entry in entries) {
            if (entry.fdcId <= 0) errors += "nutrition entry ${entry.itemId}: missing FoodData Central id"
            if (entry.fdcDescription.isBlank()) errors += "nutrition entry ${entry.itemId}: blank FDC description"
            if (entry.source.isBlank()) errors += "nutrition entry ${entry.itemId}: missing source"
            if (entry.per100g.caloriesKcal == null) {
                errors += "nutrition entry ${entry.itemId}: no calories value; an entry without one is not an entry"
            }
            if (!entry.pantryItem && entry.itemId !in priceIds) {
                errors += "nutrition entry ${entry.itemId}: not a shipped price item"
            }
            if (entry.gramWeights.isNotEmpty() && entry.gramWeightSource.isNullOrBlank()) {
                errors += "nutrition entry ${entry.itemId}: gram weights without a gram weight source"
            }
            for ((unitName, grams) in entry.gramWeights) {
                val unit = runCatching { MeasureUnit.valueOf(unitName) }.getOrNull()
                if (unit == null) {
                    errors += "nutrition entry ${entry.itemId}: gram weight for unknown unit '$unitName'"
                } else if (unit.dimension == Dimension.WEIGHT) {
                    errors += "nutrition entry ${entry.itemId}: gram weight for weight unit '$unitName' is meaningless"
                }
                if (grams <= 0.0) errors += "nutrition entry ${entry.itemId}: gram weight for '$unitName' must be positive"
            }
        }
        val byId = entries.filterNot { it.pantryItem }.map { it.itemId }.toSet()
        val bySlug = entries.filter { it.pantryItem }.map { it.itemId }.toSet()
        val usedPriced = recipes.flatMap { it.ingredients }
            .mapNotNull { it.priceItemId }
            .map { Costing.substituteItemId(it) }.toSet()
        val usedPantrySlugs = recipes.flatMap { it.ingredients }
            .mapNotNull { it.pantryItem }
            .map { NutritionCalculator.pantrySlug(it) }.toSet()
        val incomplete = knownIncomplete.toSet()
        if (incomplete.size != knownIncomplete.size) errors += "known-incomplete list has duplicates"
        for (id in usedPriced) {
            if (id !in byId && id !in incomplete) {
                errors += "shipped ingredient '$id' has no nutrition entry and is not on the known-incomplete list"
            }
        }
        for (slug in usedPantrySlugs) {
            if (slug !in bySlug && slug !in incomplete) {
                errors += "pantry staple '$slug' has no nutrition entry and is not on the known-incomplete list"
            }
        }
        for (id in incomplete) {
            if (id in byId || id in bySlug) {
                errors += "known-incomplete list names '$id', which has a nutrition entry"
            }
            if (id !in usedPriced && id !in usedPantrySlugs) {
                errors += "known-incomplete list names '$id', which no recipe uses"
            }
        }
        return errors
    }

    /**
     * Validate the diet preset definitions (Phase V2-2) against the recipe
     * library they filter: unique ids, readable name and summary, non-blank
     * won't-eat entries, and every tag a preset names (excluded or required)
     * must be a tag the library actually uses, so a preset can never filter
     * on a typo'd tag and silently match nothing.
     */
    fun validatePresets(presets: List<DietPreset>, recipes: List<Recipe>): List<String> {
        val errors = mutableListOf<String>()
        if (presets.isEmpty()) errors += "diet preset list is empty"
        val seen = mutableSetOf<String>()
        for (preset in presets) {
            val id = preset.id.trim()
            if (id.isEmpty()) {
                errors += "diet preset has a blank id"
            } else if (!seen.add(id.lowercase())) {
                errors += "duplicate diet preset id: $id"
            }
            if (preset.name.isBlank()) errors += "diet preset '$id' has a blank name"
            if (preset.summary.isBlank()) errors += "diet preset '$id' has a blank summary"
            for (entry in preset.wontEatEntries) {
                if (entry.isBlank()) errors += "diet preset '$id' has a blank won't-eat entry"
            }
        }
        val libraryTags = recipes.flatMap { it.dietTags }
            .map { it.trim().lowercase() }.toSet()
        for (preset in presets) {
            for (tag in preset.excludedTags + listOfNotNull(preset.requiredTag)) {
                if (tag.trim().lowercase() !in libraryTags) {
                    errors += "diet preset '${preset.id}' names tag '$tag' that no recipe carries"
                }
            }
        }
        return errors
    }
}
