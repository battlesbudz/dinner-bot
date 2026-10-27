package com.battlesbudz.dinnerbot.feature.planner

import com.battlesbudz.dinnerbot.core.model.Costing
import com.battlesbudz.dinnerbot.core.model.HouseholdSettings
import com.battlesbudz.dinnerbot.core.model.MeasureUnit
import com.battlesbudz.dinnerbot.core.model.PriceItem
import com.battlesbudz.dinnerbot.core.model.Quantity
import com.battlesbudz.dinnerbot.core.model.Recipe
import com.battlesbudz.dinnerbot.core.model.RecipeIngredient
import com.battlesbudz.dinnerbot.core.model.Store
import com.battlesbudz.dinnerbot.core.model.StorePrice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Two-week planning (Phase V2-3): the fortnight is generated as one event.
 * No dinner repeats across the two weeks, each week reports its own cash
 * against the weekly cap and the trip total is their sum, Sam's stock is
 * drawn across the weeks in order, every purchase at its home store,
 * horizon, and week 2's perishables split to the fresh top-up list.
 */
class WeekPlannerFortnightTest {

    private fun item(
        id: String, category: String, store: String, packAmount: Double, unit: MeasureUnit,
        packPrice: Int, perUnit: Double? = null, alternates: List<StorePrice> = emptyList(),
        freezeNote: String? = null,
    ) = PriceItem(
        id = id, name = id, category = category, homeStore = store, packDescription = "pack",
        packQuantity = Quantity(packAmount, unit), packPriceCents = packPrice,
        pricePerUnitCents = perUnit ?: packPrice / packAmount,
        checkedDate = "2026-10-22", source = "test", alternates = alternates,
        freezeNote = freezeNote,
    )

    private val turkey = item("ground-turkey", "Meat", "walmart", 1.0, MeasureUnit.POUND, 198)
    private val tenderloin = item(
        "pork-tenderloin", "Meat", "sams-club", 9.0, MeasureUnit.POUND, 2168,
        perUnit = 240.9, alternates = listOf(StorePrice("aldi", 297.0)),
        freezeNote = "Freeze the rest.",
    )
    private val spaghetti = item("spaghetti", "Dry and canned", "aldi", 32.0, MeasureUnit.OUNCE, 209, perUnit = 6.53)
    private val eggs = item("eggs", "Dairy and eggs", "aldi", 12.0, MeasureUnit.COUNT, 185, perUnit = 15.4)
    private val milk = item("milk", "Dairy and eggs", "aldi", 64.0, MeasureUnit.FLUID_OUNCE, 299, perUnit = 4.67)
    private val potatoes = item("russet-potatoes", "Produce", "aldi", 5.0, MeasureUnit.POUND, 329, perUnit = 65.8)
    private val book = listOf(turkey, tenderloin, spaghetti, eggs, milk, potatoes)

    private fun recipe(id: String, vararg ingredients: RecipeIngredient) = Recipe(
        id = id, title = id, servings = 4, activeMinutes = 10, cuisine = "Test",
        mainProtein = "Test", ingredients = ingredients.toList(), steps = listOf("Cook."),
    )

    private fun priced(itemId: String, amount: Double, unit: MeasureUnit) =
        RecipeIngredient(priceItemId = itemId, quantity = Quantity(amount, unit))

    /** Cheap dinner: eggs + spaghetti, 394c of packs at a 5-night scale. */
    private fun cheapDinner(id: String) = recipe(
        id,
        priced("eggs", 6.0, MeasureUnit.COUNT),
        priced("spaghetti", 16.0, MeasureUnit.OUNCE),
    )

    /** Pork dinner using [pounds] of tenderloin plus eggs. */
    private fun porkDinner(id: String, pounds: Double) = recipe(
        id,
        priced("pork-tenderloin", pounds, MeasureUnit.POUND),
        priced("eggs", 6.0, MeasureUnit.COUNT),
    )

    private fun settings(budget: Int, nights: List<String>) = HouseholdSettings(
        householdSize = 4, cookNights = nights, weeklyBudgetCents = budget,
    )

    private val fiveNights = listOf("MON", "TUE", "WED", "THU", "FRI")

    private fun fortnight(
        recipes: List<Recipe>,
        budget: Int = 0,
        nights: List<String> = fiveNights,
        stock: Map<String, Quantity> = emptyMap(),
        pantry: Set<String> = emptySet(),
        variation: Int = 0,
    ) = WeekPlanner.generateFortnight(
        settings(budget, nights), recipes, book, stock, pantry,
        week1Start = "2026-10-19", week2Start = "2026-10-26", variation = variation,
    )

    @Test fun noDinnerRepeatsAcrossTheTwoWeeks() {
        val recipes = (1..12).map { cheapDinner("c$it") }
        for (variation in 0..10) {
            val plan = fortnight(recipes, variation = variation)
            val week1Ids = plan.week1.nights.map { it.recipeId }
            val week2Ids = plan.week2.nights.map { it.recipeId }
            assertEquals("variation $variation: week 1 full", 5, week1Ids.size)
            assertEquals("variation $variation: week 2 full", 5, week2Ids.size)
            assertTrue(
                "variation $variation repeats across weeks: $week1Ids vs $week2Ids",
                week1Ids.intersect(week2Ids.toSet()).isEmpty(),
            )
            assertEquals(week1Ids.size, week1Ids.distinct().size)
            assertEquals(week2Ids.size, week2Ids.distinct().size)
        }
    }

    @Test fun eachWeekReportsItsOwnCashUnderTheCapAndTheTripIsTheSum() {
        val recipes = (1..12).map { cheapDinner("c$it") }
        val plan = fortnight(recipes, budget = 1500)
        assertTrue("week 1 cash ${plan.week1.cashNeededCents} <= 1500", plan.week1.cashNeededCents <= 1500)
        assertTrue("week 2 cash ${plan.week2.cashNeededCents} <= 1500", plan.week2.cashNeededCents <= 1500)
        assertTrue(plan.week1.cashNeededCents > 0 && plan.week2.cashNeededCents > 0)
        assertEquals(
            plan.week1.cashNeededCents + plan.week2.cashNeededCents,
            plan.tripCashCents,
        )
        // The two list sections together carry exactly the trip's cash.
        val sectionCash = (plan.bigTripLines + plan.topUpLines)
            .sumOf { it.packsToBuy * it.estimatedPackPriceCents }
        assertEquals(plan.tripCashCents, sectionCash)
        assertEquals(
            plan.week1.lines.size + plan.week2.lines.size,
            plan.bigTripLines.size + plan.topUpLines.size,
        )
    }

    @Test fun weekTwoDrawsTheLeftoverOfAWeekOneBulkPackBeforeRestocking() {
        // Ten pork dinners at 2 lb each: week 1 needs 10 lb, buys two 9 lb
        // packs, and leaves 8 lb of stock. Week 2 needs 10 lb, draws the
        // 8 lb at $0, and buys exactly one more pack for the rest.
        val recipes = (1..10).map { porkDinner("p$it", 2.0) }
        val plan = fortnight(recipes)
        val week1Pork = plan.week1.lines.single { it.itemId == "pork-tenderloin" }
        assertEquals("sams-club", week1Pork.store)
        assertEquals(2, week1Pork.packsToBuy)
        val week2Pork = plan.week2.lines.single { it.itemId == "pork-tenderloin" }
        assertEquals("sams-club", week2Pork.store)
        assertEquals(1, week2Pork.packsToBuy)

        val stockAfterWeek1 = WeekPlanner.projectedStockAfter(
            settings(0, fiveNights), plan.week1, recipes, book, emptyMap(), emptySet(),
        )
        assertEquals(Quantity.pounds(8.0), stockAfterWeek1["pork-tenderloin"])
    }

    @Test fun startingStockIsDrawnAcrossBothWeeksInOrder() {
        // 9 lb on hand, four pork dinners at 1.25 lb across the fortnight:
        // week 1 draws 2.5 lb, week 2 draws the next 2.5 lb, nothing is
        // bought in either week, and 4 lb remain projected at the end.
        val recipes = (1..4).map { porkDinner("p$it", 1.25) }
        val nights = listOf("MON", "TUE")
        val stock = mapOf("pork-tenderloin" to Quantity.pounds(9.0))
        val plan = fortnight(recipes, nights = nights, stock = stock)
        assertTrue(plan.week1.lines.none { it.itemId == "pork-tenderloin" })
        assertTrue(plan.week2.lines.none { it.itemId == "pork-tenderloin" })

        val stockAfterWeek1 = WeekPlanner.projectedStockAfter(
            settings(0, nights), plan.week1, recipes, book, stock, emptySet(),
        )
        assertEquals(Quantity.pounds(6.5), stockAfterWeek1["pork-tenderloin"])
        val stockAfterWeek2 = WeekPlanner.projectedStockAfter(
            settings(0, nights), plan.week2, recipes, book, stockAfterWeek1, emptySet(),
        )
        assertEquals(Quantity.pounds(4.0), stockAfterWeek2["pork-tenderloin"])
    }

    @Test fun bulkItemsAreBoughtAtTheirHomeStoreInBothWeeks() {
        // Phase C1: no gate, no horizon evaluation, no rerouting. Every
        // week buys the bulk pack at the item's own home store, however
        // light or heavy the usage is.
        val plan = fortnight((1..10).map { porkDinner("p$it", 2.0) })
        assertTrue(plan.week1.lines.any { it.itemId == "pork-tenderloin" && it.store == "sams-club" })
        assertTrue(plan.week2.lines.any { it.itemId == "pork-tenderloin" && it.store == "sams-club" })
        val light = fortnight((1..4).map { porkDinner("l$it", 1.0) })
        assertTrue(light.week1.lines.any { it.itemId == "pork-tenderloin" && it.store == "sams-club" })
    }

    @Test fun weekTwoPerishablesSplitToTheTopUpAndShelfStableRidesTheBigTrip() {
        val recipes = (1..10).map {
            recipe(
                "m$it",
                priced("spaghetti", 16.0, MeasureUnit.OUNCE),
                priced("milk", 8.0, MeasureUnit.FLUID_OUNCE),
            )
        }
        val plan = fortnight(recipes)
        // Week 2's milk is the only top-up line; week 1's milk rides the
        // big trip because week 1 is bought on the big trip anyway.
        assertEquals(listOf("milk"), plan.topUpLines.map { it.itemId })
        assertEquals(1, plan.bigTripLines.count { it.itemId == "milk" })
        // Both weeks' spaghetti (shelf-stable) is on the big trip.
        assertEquals(2, plan.bigTripLines.count { it.itemId == "spaghetti" })
        assertTrue(plan.notes.any { it.contains("top-up") })
    }

    @Test fun pantryOwnedItemsAreCoveredLinesInBothWeeks() {
        val recipes = (1..10).map {
            recipe(
                "d$it",
                priced("russet-potatoes", 1.0, MeasureUnit.POUND),
                priced("spaghetti", 16.0, MeasureUnit.OUNCE),
            )
        }
        val plan = fortnight(recipes, pantry = setOf("Potatoes"))
        // Phase RI-3: covered in both weeks means a crossed-out $0
        // line in both weeks (and on the big trip), never a buy line.
        assertEquals(
            "Potatoes",
            plan.week1.lines.single { it.itemId == "russet-potatoes" }.coveredByPantryName,
        )
        assertEquals(
            "Potatoes",
            plan.week2.lines.single { it.itemId == "russet-potatoes" }.coveredByPantryName,
        )
        assertTrue(
            plan.bigTripLines.filter { it.itemId == "russet-potatoes" }
                .all { it.coveredByPantryName != null && it.packsToBuy == 0 },
        )
        assertTrue(plan.bigTripLines.any { it.itemId == "russet-potatoes" })
        assertEquals(2, plan.bigTripLines.count { it.itemId == "spaghetti" })
    }

    @Test fun swapInWeekTwoNeverPullsAWeekOneDinner() {
        val recipes = (1..12).map { cheapDinner("c$it") }
        val plan = fortnight(recipes)
        val week1Ids = plan.week1.nights.map { it.recipeId }.toSet()
        val day = plan.week2.nights.first().day
        val before = plan.week2.nights.first { it.day == day }.recipeId
        val swapped = WeekPlanner.swapDinnerFortnight(
            settings(0, fiveNights), plan, weekIndex = 2, day = day,
            recipes = recipes, priceItems = book, stockOnHand = emptyMap(), pantryOwnedNames = emptySet(),
        )
        val after = swapped.week2.nights.first { it.day == day }.recipeId
        assertTrue("swap changed the dinner", after != before)
        assertFalse("swap pulled a week 1 dinner", after in week1Ids)
        assertEquals(plan.week1.nights.map { it.recipeId }, swapped.week1.nights.map { it.recipeId })
    }

    @Test fun swapInWeekTwoHasNowhereToGoWhenThePoolIsExactlyTheFortnight() {
        val recipes = (1..10).map { cheapDinner("c$it") }
        val plan = fortnight(recipes)
        val day = plan.week2.nights.first().day
        val before = plan.week2.nights.first { it.day == day }.recipeId
        val swapped = WeekPlanner.swapDinnerFortnight(
            settings(0, fiveNights), plan, weekIndex = 2, day = day,
            recipes = recipes, priceItems = book, stockOnHand = emptyMap(), pantryOwnedNames = emptySet(),
        )
        assertEquals(before, swapped.week2.nights.first { it.day == day }.recipeId)
    }

    @Test fun nightOffInWeekTwoAndBackOnWithoutTouchingWeekOne() {
        val recipes = (1..12).map { cheapDinner("c$it") }
        val plan = fortnight(recipes)
        val week1Ids = plan.week1.nights.map { it.recipeId }
        val day = plan.week2.nights[1].day

        val off = WeekPlanner.setNightCookingFortnight(
            settings(0, fiveNights), plan, weekIndex = 2, day = day, cooking = false, reason = "leftovers",
            recipes = recipes, priceItems = book, stockOnHand = emptyMap(), pantryOwnedNames = emptySet(),
        )
        val offNight = off.week2.nights.first { it.day == day }
        assertTrue(offNight.notCooking)
        assertEquals("leftovers", offNight.notCookingReason)
        assertEquals(week1Ids, off.week1.nights.map { it.recipeId })

        val backOn = WeekPlanner.setNightCookingFortnight(
            settings(0, fiveNights), off, weekIndex = 2, day = day, cooking = true,
            recipes = recipes, priceItems = book, stockOnHand = emptyMap(), pantryOwnedNames = emptySet(),
        )
        val restored = backOn.week2.nights.first { it.day == day }
        assertFalse(restored.notCooking)
        assertFalse("restored dinner came from week 1", restored.recipeId in week1Ids.toSet())
        assertEquals(week1Ids, backOn.week1.nights.map { it.recipeId })
    }

    @Test fun nightOffInWeekOneRepricesWeekTwoAgainstTheNewStockProjection() {
        // Ten pork dinners at 2 lb: week 1 buys two packs and leaves 8 lb,
        // so week 2's tenderloin line only needs the 2 lb stock does not
        // cover. Marking one week 1 night off drops week 1 to one pack and
        // a 1 lb leftover, so week 2 is repriced (same dinners) against the
        // new projection: its line now needs 9 lb.
        val recipes = (1..10).map { porkDinner("p$it", 2.0) }
        val plan = fortnight(recipes)
        assertEquals(
            Quantity.pounds(2.0),
            plan.week2.lines.single { it.itemId == "pork-tenderloin" }.needed,
        )
        val day = plan.week1.nights.first().day
        val edited = WeekPlanner.setNightCookingFortnight(
            settings(0, fiveNights), plan, weekIndex = 1, day = day, cooking = false, reason = "other",
            recipes = recipes, priceItems = book, stockOnHand = emptyMap(), pantryOwnedNames = emptySet(),
        )
        assertEquals(
            1,
            edited.week1.lines.single { it.itemId == "pork-tenderloin" }.packsToBuy,
        )
        // Week 2 kept its dinners but was repriced against 1 lb of stock.
        assertEquals(
            plan.week2.nights.map { it.recipeId },
            edited.week2.nights.map { it.recipeId },
        )
        assertEquals(
            Quantity.pounds(9.0),
            edited.week2.lines.single { it.itemId == "pork-tenderloin" }.needed,
        )
    }
}
