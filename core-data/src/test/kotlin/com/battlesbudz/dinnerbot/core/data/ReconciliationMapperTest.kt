package com.battlesbudz.dinnerbot.core.data

import com.battlesbudz.dinnerbot.core.model.CookCheckoff
import com.battlesbudz.dinnerbot.core.model.RanOutEvent
import com.battlesbudz.dinnerbot.core.model.RanOutSource
import com.battlesbudz.dinnerbot.core.model.RanOutState
import org.junit.Assert.assertEquals
import org.junit.Test

/** Phase V2-7: ran-out records and cook check-offs round-trip through their entities. */
class ReconciliationMapperTest {

    @Test fun ranOutEventRoundTripsThroughEntity() {
        val event = RanOutEvent(
            id = 42,
            itemKey = "item:pork-tenderloin",
            displayName = "Pork tenderloin",
            stapleSide = true,
            priceItemId = "pork-tenderloin",
            bulkItemId = "pork-tenderloin",
            pantryStapleName = null,
            dateMarked = "2026-10-18",
            source = RanOutSource.INFERRED,
            state = RanOutState.CONFIRMED,
            cyclePlanId = "plan-1",
        )
        val back = with(ReconciliationMapper) { event.toEntity().toModel() }
        assertEquals(event, back)
    }

    @Test fun pantryStapleEventRoundTripsThroughEntity() {
        val event = RanOutEvent(
            id = 7,
            itemKey = "pantry:olive oil",
            displayName = "Olive oil",
            stapleSide = true,
            pantryStapleName = "Olive oil",
            dateMarked = "2026-10-12",
            source = RanOutSource.COOKING,
            state = RanOutState.PENDING,
            cyclePlanId = "plan-9",
        )
        val back = with(ReconciliationMapper) { event.toEntity().toModel() }
        assertEquals(event, back)
    }

    @Test fun cookCheckoffRoundTripsThroughEntity() {
        val checkoff = CookCheckoff(
            planId = "plan-1", day = "TUE", itemKey = "item:spaghetti", checked = true,
        )
        val back = with(ReconciliationMapper) { checkoff.toEntity().toModel() }
        assertEquals(checkoff, back)
    }

    @Test fun groceryLineReconciliationFlagRoundTripsThroughEntity() {
        val stored = PlanMapper.StoredPlan(
            id = "plan-1", weekStart = "2026-10-19", budgetCents = 7500,
            cashNeededCents = 2168, foodCostCents = 2168, samsMonthlySavingsCents = 505,
            nights = emptyList(), notes = emptyList(), createdAtMillis = 1L,
        )
        val line = com.battlesbudz.dinnerbot.core.model.GroceryLine(
            itemId = "pork-tenderloin", displayName = "Pork tenderloin",
            store = com.battlesbudz.dinnerbot.core.model."sams-club",
            needed = com.battlesbudz.dinnerbot.core.model.Quantity.pounds(9.0),
            packsToBuy = 1, estimatedPackPriceCents = 2168,
            reconciliationAdded = true,
        )
        val back = with(PlanMapper) { line.toEntity(stored.id).toModel() }
        assertEquals(line, back)
    }
}
