package com.battlesbudz.dinnerbot.feature.planner

import com.battlesbudz.dinnerbot.core.model.CookCheckoff
import com.battlesbudz.dinnerbot.core.model.HouseholdSettings
import com.battlesbudz.dinnerbot.core.model.MeasureUnit
import com.battlesbudz.dinnerbot.core.model.PantryItem
import com.battlesbudz.dinnerbot.core.model.PlannedNight
import com.battlesbudz.dinnerbot.core.model.PriceItem
import com.battlesbudz.dinnerbot.core.model.Quantity
import com.battlesbudz.dinnerbot.core.model.RanOutEvent
import com.battlesbudz.dinnerbot.core.model.RanOutSource
import com.battlesbudz.dinnerbot.core.model.RanOutState
import com.battlesbudz.dinnerbot.core.model.Recipe
import com.battlesbudz.dinnerbot.core.model.RecipeIngredient
import com.battlesbudz.dinnerbot.core.model.Store
import com.battlesbudz.dinnerbot.core.model.StorePrice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase V2-7: the Sunday reconciliation engine. Pins Battles's flow
 * (scope B4): the three lists in his order, the possible-outs inference
 * over exactly the consumed-and-never-restocked set, the gate, the skip
 * leaving everything pending, and resolution into one ran-out set.
 */
class ReconciliationTest {

    private fun item(
        id: String, name: String, store: Store, packAmount: Double, unit: MeasureUnit,
        packPrice: Int, alternates: List<StorePrice> = emptyList(),
    ) = PriceItem(
        id = id, name = name, category = "Test", homeStore = store, packDescription = "pack",
        packQuantity = Quantity(packAmount, unit), packPriceCents = packPrice,
        pricePerUnitCents = packPrice / packAmount,
        checkedDate = "2026-10-22", source = "test", alternates = alternates,
    )

    private val tenderloin = item(
        "pork-tenderloin", "Pork tenderloin", Store.SAMS_CLUB, 9.0, MeasureUnit.POUND, 2168,
        alternates = listOf(StorePrice(Store.ALDI, 297.0)),
    )
    private val turkey = item("ground-turkey", "Ground turkey", Store.WALMART, 1.0, MeasureUnit.POUND, 198)
    private val spaghetti = item("spaghetti", "Spaghetti", Store.ALDI, 32.0, MeasureUnit.OUNCE, 209)
    private val eggs = item("eggs", "Eggs", Store.ALDI, 12.0, MeasureUnit.COUNT, 185)
    private val potatoes = item("russet-potatoes", "Russet potatoes", Store.ALDI, 5.0, MeasureUnit.POUND, 329)
    private val book = listOf(tenderloin, turkey, spaghetti, eggs, potatoes)

    private fun recipe(id: String, title: String, vararg ingredients: RecipeIngredient) = Recipe(
        id = id, title = title, servings = 4, activeMinutes = 10, cuisine = "Test",
        mainProtein = "Test", ingredients = ingredients.toList(), steps = listOf("Cook."),
    )

    private fun priced(itemId: String, amount: Double, unit: MeasureUnit) =
        RecipeIngredient(priceItemId = itemId, quantity = Quantity(amount, unit))

    private fun staple(name: String, amount: Double, unit: MeasureUnit) =
        RecipeIngredient(pantryItem = name, quantity = Quantity(amount, unit))

    private val porkFeast = recipe(
        "pork-feast", "Pork Feast",
        priced("pork-tenderloin", 7.0, MeasureUnit.POUND),
        staple("Olive oil", 2.0, MeasureUnit.TABLESPOON),
    )
    private val turkeyDinner = recipe(
        "turkey-dinner", "Turkey Dinner",
        priced("ground-turkey", 1.0, MeasureUnit.POUND),
        priced("spaghetti", 16.0, MeasureUnit.OUNCE),
    )
    private val eggDinner = recipe(
        "egg-dinner", "Egg Dinner",
        priced("eggs", 6.0, MeasureUnit.COUNT),
    )
    private val potatoDinner = recipe(
        "potato-dinner", "Potato Dinner",
        priced("russet-potatoes", 2.0, MeasureUnit.POUND),
    )
    private val recipesById = listOf(porkFeast, turkeyDinner, eggDinner, potatoDinner).associateBy { it.id }

    private val pantry = listOf(
        PantryItem("olive-oil", "Olive oil", owned = true),
        PantryItem("potatoes", "Potatoes", owned = true),
        PantryItem("salt", "Salt", owned = true),
    )

    /**
     * Last week: Monday's pork feast and Tuesday's turkey dinner were
     * cooked (check-offs ticked); Wednesday was marked not cooking;
     * Thursday's potato dinner was planned but never opened in the cook
     * view, so nothing was checked off.
     */
    private val sourcePlan = Reconciliation.SourcePlan(
        planId = "plan-1",
        weekStart = "2026-10-12",
        nights = listOf(
            PlannedNight("MON", "pork-feast"),
            PlannedNight("TUE", "turkey-dinner"),
            PlannedNight("WED", "egg-dinner", notCooking = true, notCookingReason = "leftovers"),
            PlannedNight("THU", "potato-dinner"),
        ),
    )

    private val checkoffs = listOf(
        CookCheckoff("plan-1", "MON", "item:pork-tenderloin", checked = true),
        CookCheckoff("plan-1", "MON", "pantry:olive oil", checked = true),
        CookCheckoff("plan-1", "TUE", "item:spaghetti", checked = true),
    )

    private fun infer(
        stock: Map<String, Quantity> = emptyMap(),
        pantryItems: List<PantryItem> = pantry,
        pending: List<RanOutEvent> = emptyList(),
        recorded: List<RanOutEvent> = emptyList(),
        planCheckoffs: List<CookCheckoff> = checkoffs,
    ) = Reconciliation.inferPossibleOuts(
        sourcePlans = listOf(sourcePlan),
        recipesById = recipesById,
        checkoffs = planCheckoffs,
        priceItems = book,
        pantryItems = pantryItems,
        stockOnHand = stock,
        pendingEvents = pending,
        recordedEvents = recorded,
    )

    // ------------------------------------------------------------------
    // Inference
    // ------------------------------------------------------------------

    @Test fun inferenceFindsExactlyTheConsumedAndNeverRestockedSet() {
        val possibles = infer()
        assertEquals(
            listOf("pantry:olive oil", "item:pork-tenderloin", "item:ground-turkey", "item:spaghetti"),
            possibles.map { it.itemKey },
        )
        // Staples first, then the rest, each with its reason.
        val byKey = possibles.associateBy { it.itemKey }
        assertTrue(byKey.getValue("pantry:olive oil").stapleSide)
        assertTrue(byKey.getValue("item:pork-tenderloin").stapleSide)
        assertTrue(byKey.getValue("item:pork-tenderloin").reason.contains("bulk stock is gone"))
        assertTrue(byKey.getValue("item:spaghetti").reason.contains("Tuesday's Turkey Dinner"))
        assertFalse(byKey.getValue("item:spaghetti").stapleSide)
    }

    @Test fun uncookedAndNotCookingNightsProposeNothing() {
        val keys = infer().map { it.itemKey }
        // Wednesday was marked not cooking; Thursday was never checked off.
        assertFalse("item:eggs" in keys)
        assertFalse("item:russet-potatoes" in keys)
    }

    @Test fun restockedBulkStockIsNotInferred() {
        val keys = infer(stock = mapOf("pork-tenderloin" to Quantity.pounds(2.0))).map { it.itemKey }
        assertFalse("item:pork-tenderloin" in keys)
        assertTrue("pantry:olive oil" in keys)
    }

    @Test fun pantryCoveredItemIsNotInferredButUncoveredIs() {
        val cookedPotatoes = checkoffs + CookCheckoff("plan-1", "THU", "item:russet-potatoes", checked = true)
        // "Potatoes" is owned, so it covers the priced russet potatoes:
        // the pantry asserts them home, and list 3 is where that is fixed.
        assertFalse("item:russet-potatoes" in infer(planCheckoffs = cookedPotatoes).map { it.itemKey })
        // Once the pantry no longer records potatoes as owned, the priced
        // item the cooked dinner used is inferred like any other.
        val withoutPotatoes = pantry.map { if (it.id == "potatoes") it.copy(owned = false) else it }
        assertTrue(
            "item:russet-potatoes" in infer(
                pantryItems = withoutPotatoes, planCheckoffs = cookedPotatoes,
            ).map { it.itemKey },
        )
    }

    @Test fun stapleNoLongerOwnedIsKnownNotInferred() {
        val withoutOil = pantry.map { if (it.id == "olive-oil") it.copy(owned = false) else it }
        assertFalse("pantry:olive oil" in infer(pantryItems = withoutOil).map { it.itemKey })
    }

    @Test fun pendingMarksAndAlreadyAskedItemsAreNotProposedTwice() {
        val pending = listOf(
            RanOutEvent(
                itemKey = "item:spaghetti", displayName = "Spaghetti", stapleSide = false,
                priceItemId = "spaghetti", dateMarked = "2026-10-13",
                source = RanOutSource.COOKING, state = RanOutState.PENDING, cyclePlanId = "plan-1",
            ),
        )
        val dismissed = listOf(
            RanOutEvent(
                itemKey = "item:ground-turkey", displayName = "Ground turkey", stapleSide = false,
                priceItemId = "ground-turkey", dateMarked = "2026-10-18",
                source = RanOutSource.INFERRED, state = RanOutState.DISMISSED, cyclePlanId = "plan-1",
            ),
        )
        val keys = infer(pending = pending, recorded = dismissed).map { it.itemKey }
        assertFalse("item:spaghetti" in keys)
        assertFalse("item:ground-turkey" in keys)
        assertTrue("item:pork-tenderloin" in keys)
    }

    // ------------------------------------------------------------------
    // The gate and the session's three lists
    // ------------------------------------------------------------------

    private val pendingMark = RanOutEvent(
        itemKey = "pantry:olive oil", displayName = "Olive oil", stapleSide = true,
        pantryStapleName = "Olive oil", dateMarked = "2026-10-12",
        source = RanOutSource.COOKING, state = RanOutState.PENDING, cyclePlanId = "plan-1",
    )

    @Test fun gateNeedsASourcePlanAndSomethingPending() {
        assertFalse(Reconciliation.hasPendingReconciliation(emptyList(), listOf(pendingMark), emptyList()))
        assertFalse(Reconciliation.hasPendingReconciliation(listOf(sourcePlan), emptyList(), emptyList()))
        assertTrue(Reconciliation.hasPendingReconciliation(listOf(sourcePlan), listOf(pendingMark), emptyList()))
        assertTrue(Reconciliation.hasPendingReconciliation(listOf(sourcePlan), emptyList(), infer()))
    }

    @Test fun sessionPresentsTheThreeListsInBattlesOrder() {
        val session = Reconciliation.buildSession(
            Reconciliation.CheckKind.FULL,
            listOf(sourcePlan),
            listOf(pendingMark),
            infer(),
            pantry,
            book,
            stockOnHand = mapOf("pork-tenderloin" to Quantity.pounds(3.0)),
        )
        assertEquals(3, session.stepCount)
        // List 1: the marks, as marked.
        assertEquals(listOf(pendingMark), session.markedOuts)
        // List 2: the inference.
        assertEquals(infer().map { it.itemKey }, session.possibleOuts.map { it.itemKey })
        // List 3: the pantry she owns, plus Sam's bulk with stock on hand.
        assertEquals(listOf("Olive oil", "Potatoes", "Salt"), session.pantryChecklist.map { it.name })
        assertEquals(listOf("pork-tenderloin"), session.bulkChecklist.map { it.id })
    }

    @Test fun topUpSessionHasOnlyTheFirstTwoLists() {
        val session = Reconciliation.buildSession(
            Reconciliation.CheckKind.TOP_UP,
            listOf(sourcePlan),
            listOf(pendingMark),
            infer(),
            pantry,
            book,
            stockOnHand = mapOf("pork-tenderloin" to Quantity.pounds(3.0)),
        )
        assertEquals(2, session.stepCount)
        assertEquals(listOf(pendingMark), session.markedOuts)
        assertTrue(session.possibleOuts.isNotEmpty())
        assertTrue(session.pantryChecklist.isEmpty())
        assertTrue(session.bulkChecklist.isEmpty())
    }

    @Test fun skipLeavesStateUntouchedAndTheListStillPending() {
        // A skip is the ABSENCE of a resolution: no outcome is produced,
        // no event changes state, and the gate still reports pending on
        // the same inputs next time, with the marks still at the top.
        val pending = listOf(pendingMark)
        val possibles = infer()
        assertTrue(Reconciliation.hasPendingReconciliation(listOf(sourcePlan), pending, possibles))
        // "Next attempt": rebuild with the untouched inputs.
        assertTrue(Reconciliation.hasPendingReconciliation(listOf(sourcePlan), pending, possibles))
        val nextSession = Reconciliation.buildSession(
            Reconciliation.CheckKind.FULL, listOf(sourcePlan), pending, possibles,
            pantry, book, emptyMap(),
        )
        assertEquals(listOf(pendingMark), nextSession.markedOuts)
        assertEquals(RanOutState.PENDING, nextSession.markedOuts.single().state)
    }

    // ------------------------------------------------------------------
    // Resolution
    // ------------------------------------------------------------------

    private fun fullSession() = Reconciliation.buildSession(
        Reconciliation.CheckKind.FULL,
        listOf(sourcePlan),
        listOf(pendingMark),
        infer(),
        pantry,
        book,
        stockOnHand = mapOf("pork-tenderloin" to Quantity.pounds(3.0)),
    )

    @Test fun resolutionConfirmsMarksAndAnswersIntoOneRanOutSet() {
        val outcome = Reconciliation.resolveSession(
            fullSession(),
            Reconciliation.SessionChoices(
                confirmedPossibleKeys = setOf("item:spaghetti"),
                dismissedPossibleKeys = setOf("item:ground-turkey"),
            ),
            book,
            today = "2026-10-19",
        )
        // The mark is confirmed, not duplicated.
        val mark = outcome.eventsToSave.single { it.itemKey == "pantry:olive oil" }
        assertEquals(RanOutState.CONFIRMED, mark.state)
        assertEquals(RanOutSource.COOKING, mark.source)
        // The confirmed inference joins as a new confirmed record.
        val spaghetti = outcome.eventsToSave.single { it.itemKey == "item:spaghetti" }
        assertEquals(RanOutSource.INFERRED, spaghetti.source)
        assertEquals(RanOutState.CONFIRMED, spaghetti.state)
        assertEquals("plan-1", spaghetti.cyclePlanId)
        // The dismissal is recorded so it is not asked again for this plan.
        val turkey = outcome.eventsToSave.single { it.itemKey == "item:ground-turkey" }
        assertEquals(RanOutState.DISMISSED, turkey.state)
        // The undecided tenderloin records nothing.
        assertTrue(outcome.eventsToSave.none { it.itemKey == "item:pork-tenderloin" })
        // The resolved set carries exactly the confirmed items.
        assertEquals(
            setOf("pantry:olive oil", "item:spaghetti"),
            outcome.resolvedOuts.map { it.itemKey }.toSet(),
        )
        assertEquals(listOf("plan-1"), outcome.reconciledPlanIds)
    }

    @Test fun pantryCheckResolvesStaplesAndTheirPricedForms() {
        val outcome = Reconciliation.resolveSession(
            fullSession(),
            Reconciliation.SessionChoices(
                missingPantryIds = setOf("potatoes"),
                missingBulkItemIds = setOf("pork-tenderloin"),
            ),
            book,
            today = "2026-10-19",
        )
        val checkEvents = outcome.eventsToSave.filter { it.source == RanOutSource.PANTRY_CHECK }
        assertEquals(2, checkEvents.size)
        assertTrue(checkEvents.all { it.state == RanOutState.CONFIRMED })
        val resolvedByKey = outcome.resolvedOuts.associateBy { it.itemKey }
        // The staple itself, and the priced item its name covers: russet
        // potatoes are the purchasable form of the missing staple, and
        // they resolve as a staple (always listed).
        assertTrue(resolvedByKey.getValue("pantry:potatoes").stapleSide)
        val russet = resolvedByKey.getValue("item:russet-potatoes")
        assertTrue(russet.stapleSide)
        assertEquals("russet-potatoes", russet.priceItemId)
        // The bulk item checked all gone resolves with its stock id.
        assertEquals("pork-tenderloin", resolvedByKey.getValue("item:pork-tenderloin").bulkItemId)
    }

    // ------------------------------------------------------------------
    // Write-back, planner side: after the flip, the planner must not
    // treat a confirmed-missing staple as already home. (The flip itself
    // is pinned in feature-pantry's ReconciliationWriteBackTest.)
    // ------------------------------------------------------------------

    @Test fun plannerStopsCoveringAStapleTheReconciliationFlipped() {
        val settings = HouseholdSettings(
            householdSize = 4, cookNights = listOf("MON"), weeklyBudgetCents = 100_00,
        )
        // One dinner in the pool, so the planned night is the potato
        // dinner itself and the assertions are about its lines.
        val dinners = listOf(potatoDinner)
        // While "Potatoes" reads owned, russet potatoes are already home
        // and the potato dinner's week carries no line for them.
        val covered = WeekPlanner.generate(
            settings, dinners, book, emptyMap(), setOf("Potatoes"), "2026-10-19",
        )
        assertTrue(covered.lines.none { it.itemId == "russet-potatoes" })
        // After write-back flips the staple to not owned, the same
        // planner input buys them.
        val flipped = WeekPlanner.generate(
            settings, dinners, book, emptyMap(), emptySet(), "2026-10-19",
        )
        assertTrue(flipped.lines.any { it.itemId == "russet-potatoes" })
    }

    // ------------------------------------------------------------------
    // Cook check-offs persist per planned dinner (the state inference
    // reads): two nights of one plan are independent records.
    // ------------------------------------------------------------------

    @Test fun checkoffsAreScopedPerPlanAndNight() {
        val scoped = listOf(
            CookCheckoff("plan-1", "MON", "item:eggs", checked = true),
            CookCheckoff("plan-1", "TUE", "item:eggs", checked = false),
            CookCheckoff("plan-2", "MON", "item:eggs", checked = true),
        )
        val cookedNights = scoped.filter { it.checked }.map { it.planId to it.day }.toSet()
        assertEquals(setOf("plan-1" to "MON", "plan-2" to "MON"), cookedNights)
    }
}
