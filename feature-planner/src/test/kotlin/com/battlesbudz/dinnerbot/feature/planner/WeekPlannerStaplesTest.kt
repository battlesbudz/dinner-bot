package com.battlesbudz.dinnerbot.feature.planner

import com.battlesbudz.dinnerbot.core.model.HouseholdSettings
import com.battlesbudz.dinnerbot.core.model.MeasureUnit
import com.battlesbudz.dinnerbot.core.model.PriceItem
import com.battlesbudz.dinnerbot.core.model.Quantity
import com.battlesbudz.dinnerbot.core.model.Recipe
import com.battlesbudz.dinnerbot.core.model.RecipeIngredient
import com.battlesbudz.dinnerbot.core.model.Store
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The v0.4.3 fix for Battles's 2026-10-23 field report: the pantry
 * staples are priced price book items now. A staple the pantry does not
 * mark owned is a normal priced buy line in the week's cash; a staple
 * marked owned is covered on-hand like any pantry-covered item; and the
 * old "not priced in the price book" plan note is never written again.
 * Packs and prices mirror the real seed captures (26 oz salt at 89
 * cents, 3 lb rice at 295).
 */
class WeekPlannerStaplesTest {

    private val salt = PriceItem(
        id = "salt", name = "Salt", category = "Dry and canned",
        homeStore = Store.ALDI, packDescription = "26 oz canister, iodized",
        packQuantity = Quantity(26.0, MeasureUnit.OUNCE), packPriceCents = 89,
        pricePerUnitCents = 3.4, checkedDate = "2026-10-23", source = "test",
        staple = true,
    )
    private val rice = PriceItem(
        id = "long-grain-white-rice", name = "Long grain white rice",
        category = "Dry and canned", homeStore = Store.ALDI,
        packDescription = "3 lb bag",
        packQuantity = Quantity(3.0, MeasureUnit.POUND), packPriceCents = 295,
        pricePerUnitCents = 98.3, checkedDate = "2026-10-23", source = "test",
        staple = true,
    )
    private val eggs = PriceItem(
        id = "eggs", name = "Eggs", category = "Dairy and eggs",
        homeStore = Store.ALDI, packDescription = "12 ct",
        packQuantity = Quantity(12.0, MeasureUnit.COUNT), packPriceCents = 185,
        pricePerUnitCents = 15.4, checkedDate = "2026-10-23", source = "test",
    )
    private val book = listOf(salt, rice, eggs)

    private fun priced(itemId: String, amount: Double, unit: MeasureUnit) =
        RecipeIngredient(priceItemId = itemId, quantity = Quantity(amount, unit))

    private val riceDinner = Recipe(
        id = "rice-dinner", title = "Rice Dinner", servings = 4, activeMinutes = 10,
        cuisine = "Test", mainProtein = "Egg",
        ingredients = listOf(
            priced("long-grain-white-rice", 2.0, MeasureUnit.CUP),
            priced("salt", 1.0, MeasureUnit.TEASPOON),
            priced("eggs", 6.0, MeasureUnit.COUNT),
        ),
        steps = listOf("Cook."),
    )
    private val pool = listOf(riceDinner)

    private val settings = HouseholdSettings(
        householdSize = 4, cookNights = listOf("MON"), weeklyBudgetCents = 100_00,
    )

    private fun generate(pantryOwnedNames: Set<String>) = WeekPlanner.generate(
        settings = settings,
        recipes = pool,
        priceItems = book,
        stockOnHand = emptyMap(),
        pantryOwnedNames = pantryOwnedNames,
        weekStart = "2026-10-26",
    )

    @Test fun unownedStaplesArePricedBuyLinesInTheWeeksCash() {
        val plan = generate(emptySet())
        val saltLine = plan.lines.single { it.itemId == "salt" }
        assertEquals(1, saltLine.packsToBuy)
        assertEquals(89, saltLine.estimatedPackPriceCents)
        val riceLine = plan.lines.single { it.itemId == "long-grain-white-rice" }
        assertEquals(1, riceLine.packsToBuy)
        assertEquals(295, riceLine.estimatedPackPriceCents)
        // One pack of each staple covers the dinner, plus one pack of eggs.
        assertEquals(89 + 295 + 185, plan.cashNeededCents)
        assertEquals(
            plan.cashNeededCents,
            plan.lines.sumOf { it.packsToBuy * it.estimatedPackPriceCents },
        )
    }

    @Test fun ownedStaplesAreCoveredOnHand() {
        val plan = generate(setOf("Salt", "Long grain white rice"))
        // Phase RI-3: the owned staples stay on the list as covered
        // lines naming their pantry entries.
        assertEquals(
            "Salt",
            plan.lines.single { it.itemId == "salt" }.coveredByPantryName,
        )
        assertEquals(
            "Long grain white rice",
            plan.lines.single { it.itemId == "long-grain-white-rice" }.coveredByPantryName,
        )
        // Only the eggs cost cash; the staples contribute $0.
        assertEquals(185, plan.cashNeededCents)
    }

    @Test fun theOldNotPricedNoteIsNeverWritten() {
        for (owned in listOf(emptySet(), setOf("Salt"), setOf("Salt", "Long grain white rice"))) {
            val plan = generate(owned)
            assertTrue(plan.notes.none { it.contains("not priced in the price book") })
            assertTrue(plan.notes.none { it.startsWith("Pantry staples these dinners use") })
        }
    }

    @Test fun recostDropsTheLegacyNoteStoredOnAnOldPlan() {
        val legacyNote = "Pantry staples these dinners use that are not marked owned: " +
            "Salt. They are not priced in the price book, so they are not on the " +
            "shopping list; check the pantry before you shop."
        val plan = generate(emptySet()).copy(notes = listOf(legacyNote))
        val recosted = WeekPlanner.recost(
            settings, plan, pool, book, emptyMap(), emptySet(),
        )
        assertTrue(recosted.notes.none { it.contains("not priced in the price book") })
        // And the repricing itself is the new truth: the staple is a line.
        assertTrue(recosted.lines.any { it.itemId == "salt" })
        assertEquals(89 + 295 + 185, recosted.cashNeededCents)
    }
}
