package com.battlesbudz.dinnerbot.feature.grocerylist

import com.battlesbudz.dinnerbot.core.model.MealPlan
import com.battlesbudz.dinnerbot.core.model.MeasureUnit
import com.battlesbudz.dinnerbot.core.model.PlannedNight
import com.battlesbudz.dinnerbot.core.model.PriceItem
import com.battlesbudz.dinnerbot.core.model.Quantity
import com.battlesbudz.dinnerbot.core.model.Recipe
import com.battlesbudz.dinnerbot.core.model.RecipeIngredient
import com.battlesbudz.dinnerbot.core.model.StoreIds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase C1: planning and listing group across the USER's stores,
 * including a custom store the user created by name — no chain is
 * special anywhere in the pipeline.
 */
class GroceryListStoresTest {

    private fun item(id: String, store: String, packPrice: Int) = PriceItem(
        id = id, name = id, category = "Test", homeStore = store, packDescription = "pack",
        packQuantity = Quantity(1.0, MeasureUnit.POUND), packPriceCents = packPrice,
        pricePerUnitCents = packPrice.toDouble(), checkedDate = "2026-10-22", source = "test",
    )

    private fun recipe(id: String, vararg ingredients: RecipeIngredient) = Recipe(
        id = id, title = id, servings = 4, activeMinutes = 10, cuisine = "Test",
        mainProtein = "Test", ingredients = ingredients.toList(), steps = listOf("Cook."),
    )

    private fun priced(itemId: String) =
        RecipeIngredient(priceItemId = itemId, quantity = Quantity(1.0, MeasureUnit.POUND))

    @Test fun listGroupsAcrossPresetAndCustomUserStores() {
        val custom = StoreIds.customStore("Corner Market", emptyList())
        assertEquals("corner-market", custom.id)
        val book = listOf(
            item("eggs-dzn", "aldi", 185),
            item("ground-turkey", custom.id, 240),
            item("milk-gal", "wegmans", 389),
        )
        val dinner = recipe(
            "mixed-night",
            priced("eggs-dzn"), priced("ground-turkey"), priced("milk-gal"),
        )
        val plan = MealPlan(
            id = "p1", weekStart = "2026-10-19", budgetCents = 100_00,
            nights = listOf(PlannedNight("MON", "mixed-night")),
        )
        val built = GroceryListBuilder.build(
            plan, mapOf("mixed-night" to dinner), book, emptyMap(), servings = 4,
        )
        assertEquals(185, built.cashByStoreCents["aldi"])
        assertEquals(240, built.cashByStoreCents["corner-market"])
        assertEquals(389, built.cashByStoreCents["wegmans"])
        assertEquals(185 + 240 + 389, built.totalCashCents)
        assertTrue(built.lines.all { it.store in built.cashByStoreCents.keys })
        // The custom store's display name resolves from the user's stores.
        assertEquals(
            "Corner Market",
            StoreIds.displayName("corner-market", listOf(custom)),
        )
    }
}
