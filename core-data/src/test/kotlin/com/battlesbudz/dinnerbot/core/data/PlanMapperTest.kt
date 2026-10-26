package com.battlesbudz.dinnerbot.core.data

import com.battlesbudz.dinnerbot.core.model.GroceryLine
import com.battlesbudz.dinnerbot.core.model.MeasureUnit
import com.battlesbudz.dinnerbot.core.model.PlannedNight
import com.battlesbudz.dinnerbot.core.model.Quantity
import com.battlesbudz.dinnerbot.core.model.Store
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlanMapperTest {

    @Test fun storedPlanRoundTripsThroughEntity() {
        val stored = PlanMapper.StoredPlan(
            id = "plan-1",
            weekStart = "2026-10-19",
            budgetCents = 7500,
            cashNeededCents = 5620,
            foodCostCents = 4100,
            samsMonthlySavingsCents = 505,
            nights = listOf(
                PlannedNight("MON", "turkey-spaghetti"),
                PlannedNight("TUE", "egg-fried-rice"),
            ),
            notes = listOf("A note."),
            createdAtMillis = 123456789L,
        )
        val back = with(PlanMapper) { stored.toEntity().toStoredPlan() }
        assertEquals(stored, back)
    }

    @Test fun legacyNightsJsonFromV3DecodesAsAllCooking() {
        // A plan stored by v0.2.0 (DB v3): nightsJson has no notCooking
        // fields. The Phase V2-2 fields carry defaults, so the old JSON must
        // decode with every night cooking and no reason.
        val entity = PlanEntity(
            id = "plan-legacy",
            weekStart = "2026-10-19",
            budgetCents = 7500,
            cashNeededCents = 5620,
            foodCostCents = 4100,
            samsMonthlySavingsCents = 505,
            nightsJson = """[{"day":"MON","recipeId":"turkey-spaghetti"},{"day":"TUE","recipeId":"egg-fried-rice"}]""",
            notesJson = "[]",
            createdAtMillis = 123456789L,
        )
        val stored = with(PlanMapper) { entity.toStoredPlan() }
        assertEquals(2, stored.nights.size)
        assertTrue(stored.nights.all { !it.notCooking && it.notCookingReason == null })
    }

    @Test fun notCookingNightRoundTripsThroughEntity() {
        val stored = PlanMapper.StoredPlan(
            id = "plan-2",
            weekStart = "2026-10-19",
            budgetCents = 7500,
            cashNeededCents = 3000,
            foodCostCents = 2500,
            samsMonthlySavingsCents = 0,
            nights = listOf(
                PlannedNight("MON", "turkey-spaghetti"),
                PlannedNight("TUE", "egg-fried-rice", notCooking = true, notCookingReason = "leftovers"),
            ),
            notes = emptyList(),
            createdAtMillis = 123456789L,
        )
        val back = with(PlanMapper) { stored.toEntity().toStoredPlan() }
        assertEquals(stored, back)
    }

    @Test fun groceryLineRoundTripsThroughEntity() {
        val line = GroceryLine(
            itemId = "ground-turkey",
            displayName = "Ground turkey",
            store = "walmart",
            needed = Quantity(2.0, MeasureUnit.POUND),
            packsToBuy = 2,
            estimatedPackPriceCents = 198,
            portionCostCents = 396,
            checkedOff = true,
        )
        val back = with(PlanMapper) { line.toEntity("plan-1").toModel() }
        assertEquals(line, back)
    }

    @Test fun substitutedGroceryLineRoundTripsThroughEntity() {
        // PR v0.4.4: the swap marker is what lets a repricing keep the
        // substitution; it must survive the store exactly.
        val line = GroceryLine(
            itemId = "user-chicken-drumsticks",
            displayName = "Chicken drumsticks",
            store = "aldi",
            needed = Quantity(4.0, MeasureUnit.POUND),
            packsToBuy = 1,
            estimatedPackPriceCents = 499,
            portionCostCents = 499,
            substitutedForItemId = "chicken-breast",
        )
        val entity = with(PlanMapper) { line.toEntity("plan-1") }
        assertEquals("chicken-breast", entity.substitutedForItemId)
        assertEquals(line, with(PlanMapper) { entity.toModel() })
    }
}
