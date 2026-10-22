package com.battlesbudz.dinnerbot.dataseed

import com.battlesbudz.dinnerbot.core.model.DietPreset
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
