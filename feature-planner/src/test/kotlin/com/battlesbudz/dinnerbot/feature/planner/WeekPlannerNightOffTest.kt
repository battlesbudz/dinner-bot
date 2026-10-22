package com.battlesbudz.dinnerbot.feature.planner

import com.battlesbudz.dinnerbot.core.model.Costing
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
 * Phase V2-2: per-night not-cooking inside a generated week. Marking a night
 * off removes exactly that night's dinner, ingredients, and cash; marking it
 * back on restores a dinner under the normal rules.
 */
class WeekPlannerNightOffTest {

    private fun item(id: String, store: Store, packAmount: Double, unit: MeasureUnit, packPrice: Int) =
        PriceItem(
            id = id, name = id, category = "Test", homeStore = store, packDescription = "pack",
            packQuantity = Quantity(packAmount, unit), packPriceCents = packPrice,
            pricePerUnitCents = packPrice / packAmount,
            checkedDate = "2026-10-22", source = "test",
        )

    private val eggs = item("eggs", Store.ALDI, 12.0, MeasureUnit.COUNT, 185)
    private val spaghetti = item("spaghetti", Store.ALDI, 32.0, MeasureUnit.OUNCE, 209)
    private val turkey = item("ground-turkey", Store.WALMART, 1.0, MeasureUnit.POUND, 198)
    private val book = listOf(eggs, spaghetti, turkey)
    private val itemsById = book.associateBy { it.id }

    private fun recipe(id: String, vararg ingredients: RecipeIngredient) = Recipe(
        id = id, title = id, servings = 4, activeMinutes = 10, cuisine = "Test",
        mainProtein = "Test", ingredients = ingredients.toList(), steps = listOf("Cook."),
    )

    private fun priced(itemId: String, amount: Double, unit: MeasureUnit) =
        RecipeIngredient(priceItemId = itemId, quantity = Quantity(amount, unit))

    /** Eggs + spaghetti: cash 185 + 209 = 394. Uses eggs, shared with eggsOnly. */
    private val eggsAndPasta = recipe(
        "eggs-pasta",
        priced("eggs", 6.0, MeasureUnit.COUNT),
        priced("spaghetti", 16.0, MeasureUnit.OUNCE),
    )

    /** Eggs only: cash 185. Shares the eggs line with eggsAndPasta. */
    private val eggsOnly = recipe(
        "eggs-only",
        priced("eggs", 6.0, MeasureUnit.COUNT),
    )

    /** Turkey only: cash 198. The restore pool's third dinner. */
    private val turkeyOnly = recipe(
        "turkey-only",
        priced("ground-turkey", 1.0, MeasureUnit.POUND),
    )

    private val pool = listOf(eggsAndPasta, eggsOnly, turkeyOnly)

    private fun settings(budget: Int, nights: List<String>) = HouseholdSettings(
        householdSize = 4, cookNights = nights, weeklyBudgetCents = budget,
    )

    private fun twoNightPlan(budget: Int = 100_00): WeekPlanner.WeekPlan {
        val plan = WeekPlanner.generate(
            settings(budget, listOf("MON", "TUE")), pool, book, emptyMap(), emptySet(), "2026-10-19",
        )
        // Ranked cheapest first: MON = eggs-only (185), TUE = eggs-pasta (394).
        assertEquals(listOf("eggs-only", "eggs-pasta"), plan.nights.map { it.recipeId })
        return plan
    }

    private fun setCooking(
        plan: WeekPlanner.WeekPlan, day: String, cooking: Boolean, reason: String? = null,
        budget: Int = 100_00, recipes: List<Recipe> = pool,
    ) = WeekPlanner.setNightCooking(
        settings(budget, plan.nights.map { it.day }), plan, day, cooking, reason,
        recipes, book, emptyMap(), emptySet(),
    )

    @Test fun nightOffRemovesExactlyThatNightsIngredientsAndCash() {
        val plan = twoNightPlan()
        val off = setCooking(plan, "TUE", cooking = false, reason = "leftovers")

        val night = off.nights.single { it.day == "TUE" }
        assertTrue(night.notCooking)
        assertEquals("leftovers", night.notCookingReason)
        assertFalse(off.nights.single { it.day == "MON" }.notCooking)

        // The off dinner's unique ingredient is gone from the list entirely.
        assertTrue(off.lines.none { it.itemId == "spaghetti" })
        // Cash is exactly the remaining dinner's costed cash.
        val expected = Costing.costRecipes(listOf(eggsOnly), 4, itemsById, emptyMap())
        assertEquals(expected.cashNeededCents, off.cashNeededCents)
        assertEquals(185, off.cashNeededCents)
        assertEquals(expected.lines, off.lines)
    }

    @Test fun sharedIngredientKeepsTheCookingNightsShare() {
        val plan = twoNightPlan()
        // Together the two dinners need 12 eggs.
        val eggsLineBefore = plan.lines.single { it.itemId == "eggs" }
        assertEquals(12.0, eggsLineBefore.needed.amount, 0.001)

        val off = setCooking(plan, "TUE", cooking = false, reason = "eating out")
        // Only the off dinner's share is removed: the line keeps exactly the
        // cooking night's 6 eggs instead of dropping the ingredient entirely
        // (which is what removing the whole line would have done).
        val eggsLine = off.lines.single { it.itemId == "eggs" }
        assertEquals(6.0, eggsLine.needed.amount, 0.001)
        assertEquals(1, eggsLine.packsToBuy)
    }

    @Test fun everyNightOffCostsNothing() {
        val plan = twoNightPlan()
        val oneOff = setCooking(plan, "MON", cooking = false, reason = "other")
        val allOff = setCooking(oneOff, "TUE", cooking = false, reason = "leftovers")
        assertEquals(0, allOff.cashNeededCents)
        assertTrue(allOff.lines.isEmpty())
        assertTrue(allOff.nights.all { it.notCooking })
    }

    @Test fun markingBackOnRestoresADinnerAndItsCost() {
        val plan = twoNightPlan()
        val off = setCooking(plan, "TUE", cooking = false, reason = "leftovers")
        val back = setCooking(off, "TUE", cooking = true)

        val night = back.nights.single { it.day == "TUE" }
        assertFalse(night.notCooking)
        assertEquals(null, night.notCookingReason)
        // Ranked first among the dinners the week is not already cooking,
        // the night's own dinner comes back, and the week returns to the
        // exact cash and list it had before the night was marked off.
        assertEquals("eggs-pasta", night.recipeId)
        assertEquals(back.nights.size, back.nights.map { it.recipeId }.distinct().size)
        assertEquals(plan.cashNeededCents, back.cashNeededCents)
        assertEquals(plan.lines, back.lines)
    }

    @Test fun restoreWithNoDinnerAvailableStaysOffAndSaysWhy() {
        val plan = WeekPlanner.generate(
            settings(100_00, listOf("MON")), listOf(eggsOnly), book,
            emptyMap(), emptySet(), "2026-10-19",
        )
        val off = WeekPlanner.setNightCooking(
            settings(100_00, listOf("MON")), plan, "MON", cooking = false, reason = "other",
            listOf(eggsOnly), book, emptyMap(), emptySet(),
        )
        // The candidate pool has since emptied (every dinner thumbed down or
        // filtered out after the plan was made): nothing can be restored.
        val back = WeekPlanner.setNightCooking(
            settings(100_00, listOf("MON")), off, "MON", cooking = true, reason = null,
            emptyList(), book, emptyMap(), emptySet(),
        )
        assertTrue(back.nights.single().notCooking)
        assertTrue(back.notes.any { it.contains("stays marked not cooking") })
    }

    @Test fun restoreOverBudgetStillRestoresAndSaysSo() {
        val plan = twoNightPlan()
        val off = setCooking(plan, "TUE", cooking = false, reason = "leftovers")
        // Budget cut to $2: the only unused dinner that is not already cooking
        // and not the off night's own is turkey-only at 198 on top of 185.
        val tightPool = listOf(eggsOnly, turkeyOnly)
        val back = setCooking(off, "TUE", cooking = true, budget = 200, recipes = tightPool)
        val night = back.nights.single { it.day == "TUE" }
        assertFalse(night.notCooking)
        assertEquals("turkey-only", night.recipeId)
        assertTrue(back.cashNeededCents > 200)
        assertTrue(back.notes.any { it.contains("over the") && it.contains("budget") })
    }

    @Test fun swapIgnoresTheOffNight() {
        val plan = twoNightPlan()
        val off = setCooking(plan, "TUE", cooking = false, reason = "eating out")
        // Pool narrowed to eggs-only and turkey-only so turkey-only is the
        // one possible swap; what matters is what happens to the off night.
        val swapped = WeekPlanner.swapDinner(
            settings(100_00, listOf("MON", "TUE")), off, "MON",
            listOf(eggsOnly, turkeyOnly), book, emptyMap(), emptySet(),
        )
        // MON swapped to the best unused candidate; TUE stayed off, untouched,
        // and its dinner's ingredients never entered the costing.
        assertEquals("turkey-only", swapped.nights.single { it.day == "MON" }.recipeId)
        val tue = swapped.nights.single { it.day == "TUE" }
        assertTrue(tue.notCooking)
        assertEquals("eggs-pasta", tue.recipeId)
        assertTrue(swapped.lines.none { it.itemId == "spaghetti" })
        assertTrue(swapped.lines.none { it.itemId == "eggs" })
        val expected = Costing.costRecipes(listOf(turkeyOnly), 4, itemsById, emptyMap())
        assertEquals(expected.cashNeededCents, swapped.cashNeededCents)
    }

    @Test fun swappingAnOffNightChangesNothing() {
        val plan = twoNightPlan()
        val off = setCooking(plan, "TUE", cooking = false, reason = "other")
        val swapped = WeekPlanner.swapDinner(
            settings(100_00, listOf("MON", "TUE")), off, "TUE",
            pool, book, emptyMap(), emptySet(),
        )
        assertEquals(off, swapped)
    }

    /**
     * Codex review regression (PR #14): a dinner already cooking in the week
     * can fall out of the candidate pool when a diet preset changes in
     * Settings. Repricing the edited week must still price that dinner from
     * the full recipe set; the narrowed pool supplies candidates only.
     */
    @Test fun nightOffKeepsPricingACookingDinnerOutsideTheCandidatePool() {
        val plan = twoNightPlan()
        val updated = WeekPlanner.setNightCooking(
            settings(100_00, listOf("MON", "TUE")), plan, "MON", cooking = false,
            reason = "leftovers", recipes = pool, priceItems = book,
            stockOnHand = emptyMap(), pantryOwnedNames = emptySet(),
            candidatePool = listOf(eggsOnly),
        )
        // TUE's eggs-pasta is not in the candidate pool, but it is still
        // cooking and displayed, so its ingredients and cash stay.
        assertTrue(updated.lines.any { it.itemId == "spaghetti" })
        val expected = Costing.costRecipes(listOf(eggsAndPasta), 4, itemsById, emptyMap())
        assertEquals(expected.cashNeededCents, updated.cashNeededCents)
        assertEquals(expected.lines, updated.lines)
    }

    @Test fun swapKeepsPricingACookingDinnerOutsideTheCandidatePool() {
        val plan = twoNightPlan()
        val swapped = WeekPlanner.swapDinner(
            settings(100_00, listOf("MON", "TUE")), plan, "MON",
            recipes = pool, priceItems = book,
            stockOnHand = emptyMap(), pantryOwnedNames = emptySet(),
            candidatePool = listOf(turkeyOnly),
        )
        assertEquals("turkey-only", swapped.nights.single { it.day == "MON" }.recipeId)
        // TUE's eggs-pasta was never a swap candidate, but it is still
        // cooking, so the repriced week includes it in full.
        val expected = Costing.costRecipes(listOf(turkeyOnly, eggsAndPasta), 4, itemsById, emptyMap())
        assertEquals(expected.cashNeededCents, swapped.cashNeededCents)
        assertEquals(expected.lines, swapped.lines)
    }

    @Test fun restoreDrawsCandidatesOnlyFromThePool() {
        val plan = twoNightPlan()
        val off = setCooking(plan, "TUE", cooking = false, reason = "other")
        val back = WeekPlanner.setNightCooking(
            settings(100_00, listOf("MON", "TUE")), off, "TUE", cooking = true,
            recipes = pool, priceItems = book,
            stockOnHand = emptyMap(), pantryOwnedNames = emptySet(),
            candidatePool = listOf(turkeyOnly),
        )
        // eggs-pasta resolves from the full set but is not a candidate, so
        // the restored dinner is the pool's turkey dinner.
        assertEquals("turkey-only", back.nights.single { it.day == "TUE" }.recipeId)
    }

    @Test fun markingOffAnAlreadyOffNightChangesNothing() {
        val plan = twoNightPlan()
        val off = setCooking(plan, "TUE", cooking = false, reason = "other")
        val again = setCooking(off, "TUE", cooking = false, reason = "leftovers")
        assertEquals(off, again)
    }
}
