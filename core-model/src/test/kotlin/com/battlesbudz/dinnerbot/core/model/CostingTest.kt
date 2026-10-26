package com.battlesbudz.dinnerbot.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CostingTest {

    private fun item(
        id: String,
        store: String,
        packAmount: Double,
        unit: MeasureUnit,
        packPrice: Int,
        perUnit: Double? = null,
        alternates: List<StorePrice> = emptyList(),
    ) = PriceItem(
        id = id,
        name = id,
        category = "Test",
        homeStore = store,
        packDescription = "pack",
        packQuantity = Quantity(packAmount, unit),
        packPriceCents = packPrice,
        pricePerUnitCents = perUnit ?: packPrice / packAmount,
        checkedDate = "2026-10-22",
        source = "test",
        alternates = alternates,
    )

    private val turkey = item("ground-turkey", "walmart", 1.0, MeasureUnit.POUND, 198)
    private val beef = item("ground-beef", "walmart", 1.0, MeasureUnit.POUND, 500)
    private val tenderloin = item(
        "pork-tenderloin", "sams-club", 9.0, MeasureUnit.POUND, 2168,
        perUnit = 240.9,
        alternates = listOf(StorePrice("aldi", 297.0), StorePrice("walmart", 417.0)),
    )
    private val spaghetti = item("spaghetti", "aldi", 32.0, MeasureUnit.OUNCE, 209, perUnit = 6.53)
    private val eggs = item("eggs", "aldi", 12.0, MeasureUnit.COUNT, 185, perUnit = 15.4)

    private fun recipe(id: String, vararg ingredients: RecipeIngredient) = Recipe(
        id = id, title = id, servings = 4, activeMinutes = 10, cuisine = "Test",
        mainProtein = "Test", ingredients = ingredients.toList(), steps = listOf("Cook."),
    )

    private fun priced(itemId: String, amount: Double, unit: MeasureUnit) =
        RecipeIngredient(priceItemId = itemId, quantity = Quantity(amount, unit))

    private val book = listOf(turkey, beef, tenderloin, spaghetti, eggs).associateBy { it.id }

    @Test fun groundBeefCostsAsItselfWithNoSubstitution() {
        // Phase C1: automatic substitution is gone entirely. A beef item
        // costs, plans and lists as itself, at its own price.
        val dinner = recipe("meatloaf", priced("ground-beef", 1.0, MeasureUnit.POUND))
        val costed = Costing.costRecipes(listOf(dinner), 4, book, emptyMap())
        assertEquals(1, costed.lines.size)
        assertEquals("ground-beef", costed.lines[0].itemId)
        assertEquals("walmart", costed.lines[0].store)
        assertEquals(500, costed.cashNeededCents)
    }

    @Test fun bulkStockCoversNeedAtZeroCash() {
        val dinner = recipe("pork-skillet", priced("pork-tenderloin", 1.25, MeasureUnit.POUND))
        val stock = mapOf("pork-tenderloin" to Quantity.pounds(9.0))
        val costed = Costing.costRecipes(listOf(dinner), 4, book, stock)
        assertTrue(costed.lines.none { it.itemId == "pork-tenderloin" })
        assertEquals(0, costed.cashNeededCents)
        assertTrue(costed.stockCoveredItemIds.contains("pork-tenderloin"))
        // Portion cost is still reported as information: 1.25 lb x 240.9c.
        assertEquals(301, costed.foodCostCents)
    }

    @Test fun bulkPackChargesFullPriceInBuyWeek() {
        // Bulk pack costing is ordinary pantry logic (Phase C1): the full
        // pack is charged in the buy week at the item's home store.
        val dinner = recipe("pork-feast", priced("pork-tenderloin", 7.0, MeasureUnit.POUND))
        val costed = Costing.costRecipes(listOf(dinner), 4, book, emptyMap())
        val line = costed.lines.single { it.itemId == "pork-tenderloin" }
        assertEquals("sams-club", line.store)
        assertEquals(1, line.packsToBuy)
        assertEquals(2168, costed.cashNeededCents)
    }

    @Test fun smallBulkNeedIsNeverReroutedToAnotherStore() {
        // Phase C1: the warehouse gate and its rerouting are deleted. Even
        // a small need buys the full pack at the item's own home store;
        // no line is ever moved to a cheaper alternate store.
        val dinner = recipe("pork-skillet", priced("pork-tenderloin", 1.25, MeasureUnit.POUND))
        val costed = Costing.costRecipes(listOf(dinner), 4, book, emptyMap())
        val line = costed.lines.single { it.itemId == "pork-tenderloin" }
        assertEquals("sams-club", line.store)
        assertEquals(1, line.packsToBuy)
        assertEquals(2168, costed.cashNeededCents)
    }

    @Test fun partialStockCoverBuysOnlyTheRemainder() {
        // Heavy usage (7 lb) with 5 lb on hand: still buys one full pack.
        val dinner = recipe("pork-feast", priced("pork-tenderloin", 7.0, MeasureUnit.POUND))
        val stock = mapOf("pork-tenderloin" to Quantity.pounds(5.0))
        val costed = Costing.costRecipes(listOf(dinner), 4, book, stock)
        assertTrue(costed.stockCoveredItemIds.contains("pork-tenderloin"))
        val line = costed.lines.single { it.itemId == "pork-tenderloin" }
        assertEquals("sams-club", line.store)
        assertEquals(2168, line.estimatedPackPriceCents)
    }

    @Test fun packsRoundUpAndScalingMultipliesNeeds() {
        val dinner = recipe(
            "pasta-night",
            priced("spaghetti", 16.0, MeasureUnit.OUNCE),
            priced("eggs", 6.0, MeasureUnit.COUNT),
        )
        val forFour = Costing.costRecipes(listOf(dinner), 4, book, emptyMap())
        assertEquals(209 + 185, forFour.cashNeededCents)
        val forEight = Costing.costRecipes(listOf(dinner), 8, book, emptyMap())
        // 32 oz spaghetti = 1 pack still; 12 eggs = 1 pack still.
        assertEquals(209 + 185, forEight.cashNeededCents)
        val doubleDinner = Costing.costRecipes(listOf(dinner, dinner), 8, book, emptyMap())
        // 64 oz spaghetti = 2 packs; 24 eggs = 2 packs.
        assertEquals(2 * 209 + 2 * 185, doubleDinner.cashNeededCents)
    }

    @Test fun pantryIngredientsNeverProduceLines() {
        val dinner = recipe(
            "rice-night",
            RecipeIngredient(pantryItem = "Long grain white rice", quantity = Quantity.cups(2.0)),
            priced("eggs", 6.0, MeasureUnit.COUNT),
        )
        val (needs, pantry) = Costing.aggregateNeeds(listOf(dinner), 4, book)
        assertEquals(listOf("Long grain white rice"), pantry)
        assertEquals(listOf("eggs"), needs.map { it.item.id })
    }
}
