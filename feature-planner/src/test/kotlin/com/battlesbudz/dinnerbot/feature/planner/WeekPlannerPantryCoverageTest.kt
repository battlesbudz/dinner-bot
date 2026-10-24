package com.battlesbudz.dinnerbot.feature.planner

import com.battlesbudz.dinnerbot.core.model.HouseholdSettings
import com.battlesbudz.dinnerbot.core.model.MeasureUnit
import com.battlesbudz.dinnerbot.core.model.PriceItem
import com.battlesbudz.dinnerbot.core.model.Quantity
import com.battlesbudz.dinnerbot.core.model.Recipe
import com.battlesbudz.dinnerbot.core.model.RecipeIngredient
import com.battlesbudz.dinnerbot.core.model.Store
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pantry-owned priced items at the planner level (hotfix for the bug
 * Battles reported 2026-10-22). Generation prices the week with pantry
 * coverage, and [WeekPlanner.recost] reprices the current week when
 * ownership toggles, so the plan total and the shopping list always agree.
 */
class WeekPlannerPantryCoverageTest {

    private val potatoes = PriceItem(
        id = "russet-potatoes", name = "Russet potatoes", category = "Produce",
        homeStore = Store.ALDI, packDescription = "5 lb bag",
        packQuantity = Quantity(5.0, MeasureUnit.POUND), packPriceCents = 349,
        pricePerUnitCents = 69.8, checkedDate = "2026-10-22", source = "test",
    )
    private val eggs = PriceItem(
        id = "eggs", name = "Eggs", category = "Dairy",
        homeStore = Store.ALDI, packDescription = "12 ct",
        packQuantity = Quantity(12.0, MeasureUnit.COUNT), packPriceCents = 185,
        pricePerUnitCents = 15.4, checkedDate = "2026-10-22", source = "test",
    )
    private val book = listOf(potatoes, eggs)

    private fun recipe(id: String, vararg ingredients: RecipeIngredient) = Recipe(
        id = id, title = id, servings = 4, activeMinutes = 10, cuisine = "Test",
        mainProtein = "Test", ingredients = ingredients.toList(), steps = listOf("Cook."),
    )

    private fun priced(itemId: String, amount: Double, unit: MeasureUnit) =
        RecipeIngredient(priceItemId = itemId, quantity = Quantity(amount, unit))

    private val potatoDinner = recipe(
        "potato-dinner",
        priced("russet-potatoes", 2.0, MeasureUnit.POUND),
        priced("eggs", 6.0, MeasureUnit.COUNT),
    )
    private val eggsOnly = recipe(
        "eggs-only",
        priced("eggs", 6.0, MeasureUnit.COUNT),
    )
    private val pool = listOf(potatoDinner, eggsOnly)

    private val settings = HouseholdSettings(
        householdSize = 4, cookNights = listOf("MON", "TUE"), weeklyBudgetCents = 100_00,
    )

    private fun generate(pantryOwnedNames: Set<String>) = WeekPlanner.generate(
        settings = settings,
        recipes = pool,
        priceItems = book,
        stockOnHand = emptyMap(),
        pantryOwnedNames = pantryOwnedNames,
        weekStart = "2026-10-19",
    )

    private fun linesCash(plan: WeekPlanner.WeekPlan): Int =
        plan.lines.sumOf { it.packsToBuy * it.estimatedPackPriceCents }

    @Test fun generatedWeekShowsOwnedPantryItemsAsCoveredLines() {
        val plan = generate(setOf("Potatoes"))
        // Phase RI-3: the covered item stays on the list as a $0
        // covered line naming the pantry entry; the money is unchanged.
        val covered = plan.lines.single { it.itemId == "russet-potatoes" }
        assertEquals("Potatoes", covered.coveredByPantryName)
        assertEquals(0, covered.packsToBuy)
        // Both dinners together need 12 eggs: exactly one pack, all the cash.
        assertEquals(185, plan.cashNeededCents)
        // Plan total and list are the same computation: they cannot disagree.
        assertEquals(plan.cashNeededCents, linesCash(plan))
    }

    @Test fun generatedWeekWithoutOwnershipBuysPotatoes() {
        val plan = generate(emptySet())
        assertTrue(plan.lines.any { it.itemId == "russet-potatoes" })
        assertEquals(349 + 185, plan.cashNeededCents)
        assertEquals(plan.cashNeededCents, linesCash(plan))
    }

    @Test fun recostFollowsOwnershipTogglesBothWays() {
        val bought = generate(emptySet())
        assertEquals(349 + 185, bought.cashNeededCents)

        // Mark "Potatoes" owned: the current week reprices in place —
        // the buy line becomes a covered line (Phase RI-3).
        val owned = WeekPlanner.recost(
            settings, bought, pool, book, emptyMap(), setOf("Potatoes"),
        )
        val coveredLine = owned.lines.single { it.itemId == "russet-potatoes" }
        assertEquals("Potatoes", coveredLine.coveredByPantryName)
        assertEquals(0, coveredLine.packsToBuy)
        assertEquals(185, owned.cashNeededCents)
        assertEquals(owned.cashNeededCents, linesCash(owned))
        assertEquals(bought.nights, owned.nights)

        // Mark it not owned again: the buy line and its cash come back.
        val restored = WeekPlanner.recost(
            settings, owned, pool, book, emptyMap(), emptySet(),
        )
        assertTrue(restored.lines.any { it.itemId == "russet-potatoes" })
        assertEquals(349 + 185, restored.cashNeededCents)
        assertEquals(restored.cashNeededCents, linesCash(restored))
    }

    @Test fun recostKeepsCheckOffsOnSurvivingLines() {
        val bought = generate(emptySet())
        val shopped = bought.copy(
            lines = bought.lines.map { line ->
                if (line.itemId == "eggs") line.copy(checkedOff = true) else line
            },
        )
        val owned = WeekPlanner.recost(
            settings, shopped, pool, book, emptyMap(), setOf("Potatoes"),
        )
        val eggsLine = owned.lines.single { it.itemId == "eggs" }
        assertTrue(eggsLine.checkedOff)
        // The potatoes line survives too, as the covered line (RI-3).
        assertEquals(
            "Potatoes",
            owned.lines.single { it.itemId == "russet-potatoes" }.coveredByPantryName,
        )
    }
}
