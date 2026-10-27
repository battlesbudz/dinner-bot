package com.battlesbudz.dinnerbot.feature.grocerylist

import com.battlesbudz.dinnerbot.core.model.Costing
import com.battlesbudz.dinnerbot.core.model.MealPlan
import com.battlesbudz.dinnerbot.core.model.MeasureUnit
import com.battlesbudz.dinnerbot.core.model.PlannedNight
import com.battlesbudz.dinnerbot.core.model.PriceItem
import com.battlesbudz.dinnerbot.core.model.Quantity
import com.battlesbudz.dinnerbot.core.model.Recipe
import com.battlesbudz.dinnerbot.core.model.RecipeIngredient
import com.battlesbudz.dinnerbot.core.model.RecipeOrigin
import com.battlesbudz.dinnerbot.core.model.Store
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase RI-3 at the list builder: covered lines are present at $0,
 * uncrossing one turns it into a buy line at its book price (the total
 * rises by exactly that much), and the builder's total is the same
 * number the planner's costing produces for the same dinners.
 */
class GroceryListBuilderRi3Test {

    private fun item(id: String, name: String, packAmount: Double, unit: MeasureUnit, packPrice: Int) =
        PriceItem(
            id = id, name = name, category = "Test", homeStore = "aldi",
            packDescription = "pack", packQuantity = Quantity(packAmount, unit),
            packPriceCents = packPrice, pricePerUnitCents = packPrice / packAmount,
            checkedDate = "2026-10-24", source = "test",
        )

    private val potatoes = item("russet-potatoes", "Russet potatoes", 5.0, MeasureUnit.POUND, 349)
    private val eggs = item("eggs", "Eggs", 12.0, MeasureUnit.COUNT, 185)
    private val book = listOf(potatoes, eggs)

    private fun priced(itemId: String, amount: Double, unit: MeasureUnit) =
        RecipeIngredient(priceItemId = itemId, quantity = Quantity(amount, unit))

    private val potatoDinner = Recipe(
        id = "potato-dinner", title = "potato-dinner", servings = 4, activeMinutes = 10,
        cuisine = "Test", mainProtein = "Test",
        ingredients = listOf(
            priced("russet-potatoes", 2.0, MeasureUnit.POUND),
            priced("eggs", 6.0, MeasureUnit.COUNT),
        ),
        steps = listOf("Cook."), origin = RecipeOrigin.LIBRARY,
    )
    private val recipesById = mapOf(potatoDinner.id to potatoDinner)

    private val plan = MealPlan(
        id = "p1", weekStart = "2026-10-26", budgetCents = 100_00,
        nights = listOf(PlannedNight("MON", "potato-dinner")),
    )

    @Test fun coveredLinePresentAtZeroAndTotalMatchesPlannerCosting() {
        val built = GroceryListBuilder.build(
            plan, recipesById, book, emptyMap(), servings = 4,
            pantryOwnedNames = setOf("Potatoes"),
        )
        val covered = built.lines.single { it.itemId == "russet-potatoes" }
        assertEquals("Potatoes", covered.coveredByPantryName)
        assertEquals(0, GroceryListBuilder.lineCashCents(covered))
        assertEquals(185, built.totalCashCents)

        val costed = Costing.costRecipes(
            listOf(potatoDinner), 4, book.associateBy { it.id }, emptyMap(),
            pantryCoveredItemIds = setOf("russet-potatoes"),
            pantryOwnedNames = setOf("Potatoes"),
            forList = true,
        )
        assertEquals(costed.cashNeededCents, built.totalCashCents)
        assertEquals(costed.lines.map { it.itemId }, built.lines.map { it.itemId })
    }

    @Test fun uncrossingTurnsTheCoveredLineIntoABuyLineAtBookPrice() {
        val crossed = GroceryListBuilder.build(
            plan, recipesById, book, emptyMap(), servings = 4,
            pantryOwnedNames = setOf("Potatoes"),
        )
        val uncrossed = GroceryListBuilder.build(
            plan, recipesById, book, emptyMap(), servings = 4,
            pantryOwnedNames = setOf("Potatoes"),
            uncrossedItemIds = setOf("russet-potatoes"),
        )
        val line = uncrossed.lines.single { it.itemId == "russet-potatoes" }
        assertNull(line.coveredByPantryName)
        assertTrue(line.uncrossed)
        assertEquals(349, GroceryListBuilder.lineCashCents(line))
        assertEquals(crossed.totalCashCents + 349, uncrossed.totalCashCents)
    }
}
