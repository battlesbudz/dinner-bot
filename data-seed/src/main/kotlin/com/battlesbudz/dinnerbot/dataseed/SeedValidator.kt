package com.battlesbudz.dinnerbot.dataseed

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
}
