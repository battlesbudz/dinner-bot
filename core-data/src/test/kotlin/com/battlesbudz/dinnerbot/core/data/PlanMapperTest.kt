package com.battlesbudz.dinnerbot.core.data

import com.battlesbudz.dinnerbot.core.model.GroceryLine
import com.battlesbudz.dinnerbot.core.model.MeasureUnit
import com.battlesbudz.dinnerbot.core.model.PlannedNight
import com.battlesbudz.dinnerbot.core.model.Quantity
import com.battlesbudz.dinnerbot.core.model.Store
import org.junit.Assert.assertEquals
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

    @Test fun groceryLineRoundTripsThroughEntity() {
        val line = GroceryLine(
            itemId = "ground-turkey",
            displayName = "Ground turkey",
            store = Store.WALMART,
            needed = Quantity(2.0, MeasureUnit.POUND),
            packsToBuy = 2,
            estimatedPackPriceCents = 198,
            portionCostCents = 396,
            checkedOff = true,
        )
        val back = with(PlanMapper) { line.toEntity("plan-1").toModel() }
        assertEquals(line, back)
    }
}
