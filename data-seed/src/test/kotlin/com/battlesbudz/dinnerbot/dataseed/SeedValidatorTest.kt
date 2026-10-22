package com.battlesbudz.dinnerbot.dataseed

import com.battlesbudz.dinnerbot.core.model.PriceItem
import com.battlesbudz.dinnerbot.core.model.Quantity
import com.battlesbudz.dinnerbot.core.model.Recipe
import com.battlesbudz.dinnerbot.core.model.RecipeIngredient
import com.battlesbudz.dinnerbot.core.model.Store
import org.junit.Assert.assertTrue
import org.junit.Test

class SeedValidatorTest {
    private fun item(id: String, price: Int) = PriceItem(
        id = id, name = id, category = "Meat", homeStore = Store.WALMART,
        packDescription = "pack", packQuantity = Quantity.pounds(1.0),
        packPriceCents = price, pricePerUnitCents = price.toDouble(),
        checkedDate = "2026-10-22", source = "test",
    )

    private fun recipe(itemId: String) = Recipe(
        id = "r1", title = "R", servings = 4, activeMinutes = 5, cuisine = "American",
        mainProtein = "Ground turkey",
        ingredients = listOf(RecipeIngredient(priceItemId = itemId, quantity = Quantity.pounds(1.0))),
        steps = listOf("Cook."),
    )

    @Test fun validDataPasses() {
        assertTrue(SeedValidator.validate(listOf(item("ground-turkey", 198)), listOf(recipe("ground-turkey"))).isEmpty())
    }

    @Test fun zeroPriceFails() {
        val errors = SeedValidator.validate(listOf(item("ground-turkey", 0)), listOf(recipe("ground-turkey")))
        assertTrue(errors.any { it.contains("price must be positive") })
    }

    @Test fun unknownIngredientItemFails() {
        val errors = SeedValidator.validate(listOf(item("ground-turkey", 198)), listOf(recipe("mystery-meat")))
        assertTrue(errors.any { it.contains("unknown price item") })
    }

    @Test fun badDateFails() {
        val bad = item("ground-turkey", 198).copy(checkedDate = "Oct 22 2026")
        val errors = SeedValidator.validate(listOf(bad), listOf(recipe("ground-turkey")))
        assertTrue(errors.any { it.contains("yyyy-MM-dd") })
    }
}
