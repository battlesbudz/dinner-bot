package com.battlesbudz.dinnerbot.feature.backup

import com.battlesbudz.dinnerbot.core.model.BulkStock
import com.battlesbudz.dinnerbot.core.model.CookCheckoff
import com.battlesbudz.dinnerbot.core.model.HouseholdSettings
import com.battlesbudz.dinnerbot.core.model.MeasureUnit
import com.battlesbudz.dinnerbot.core.model.PantryItem
import com.battlesbudz.dinnerbot.core.model.PriceItem
import com.battlesbudz.dinnerbot.core.model.Quantity
import com.battlesbudz.dinnerbot.core.model.RanOutEvent
import com.battlesbudz.dinnerbot.core.model.RanOutSource
import com.battlesbudz.dinnerbot.core.model.RanOutState
import com.battlesbudz.dinnerbot.core.model.Store
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase V2-7: the Sunday reconciliation state (ran-out events and cook
 * check-offs) rides in backup format v4, so a phone swap mid-week does
 * not lose the ran-out list. Older files import with both empty.
 */
class BackupReconciliationTest {

    private val event = RanOutEvent(
        id = 3,
        itemKey = "pantry:olive oil",
        displayName = "Olive oil",
        stapleSide = true,
        pantryStapleName = "Olive oil",
        dateMarked = "2026-10-18",
        source = RanOutSource.COOKING,
        state = RanOutState.PENDING,
        cyclePlanId = "plan-1",
    )
    private val checkoff = CookCheckoff(
        planId = "plan-1", day = "MON", itemKey = "item:pork-tenderloin", checked = true,
    )

    private fun data(
        events: List<RanOutEvent> = listOf(event),
        checkoffs: List<CookCheckoff> = listOf(checkoff),
    ) = BackupData(
        settings = HouseholdSettings(
            householdSize = 4,
            cookNights = listOf("MON", "TUE"),
            weeklyBudgetCents = 8000,
            onboardingComplete = true,
            reconciledPlanIds = listOf("plan-0"),
        ),
        priceItems = listOf(
            PriceItem(
                id = "pork-tenderloin", name = "Pork tenderloin", category = "Meat",
                homeStore = Store.SAMS_CLUB, packDescription = "9 lb",
                packQuantity = Quantity(9.0, MeasureUnit.POUND), packPriceCents = 2168,
                pricePerUnitCents = 240.9, checkedDate = "2026-10-22", source = "test",
            ),
        ),
        pantryItems = listOf(PantryItem("olive-oil", "Olive oil", owned = true)),
        bulkStock = listOf(BulkStock("pork-tenderloin", Quantity.pounds(4.5))),
        ranOutEvents = events,
        cookCheckoffs = checkoffs,
    )

    @Test fun reconciliationStateRoundTripsThroughExportImport() {
        val original = data()
        val restored = BackupEngine.import(BackupEngine.export(original))
        assertEquals(7, restored.version)
        assertEquals(original.ranOutEvents, restored.ranOutEvents)
        assertEquals(original.cookCheckoffs, restored.cookCheckoffs)
        assertEquals(listOf("plan-0"), restored.settings.reconciledPlanIds)
    }

    @Test fun versionThreeBackupImportsWithNoReconciliationState() {
        val exported = BackupEngine.export(data())
        val parsed = Json.parseToJsonElement(exported).jsonObject
        val v3 = JsonObject(
            parsed - "ranOutEvents" - "cookCheckoffs" + ("version" to JsonPrimitive(3)),
        )
        val restored = BackupEngine.import(Json.encodeToString(JsonObject.serializer(), v3))
        assertEquals(3, restored.version)
        assertTrue(restored.ranOutEvents.isEmpty())
        assertTrue(restored.cookCheckoffs.isEmpty())
    }

    @Test fun duplicateRanOutRecordsAreRejected() {
        val broken = data(events = listOf(event, event))
        assertThrows(IllegalArgumentException::class.java) {
            BackupEngine.import(BackupEngine.export(broken))
        }
    }

    @Test fun duplicateCookCheckoffsAreRejected() {
        val broken = data(checkoffs = listOf(checkoff, checkoff))
        assertThrows(IllegalArgumentException::class.java) {
            BackupEngine.import(BackupEngine.export(broken))
        }
    }
}
