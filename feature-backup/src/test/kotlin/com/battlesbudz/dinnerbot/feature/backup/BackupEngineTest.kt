package com.battlesbudz.dinnerbot.feature.backup

import com.battlesbudz.dinnerbot.core.model.BulkStock
import com.battlesbudz.dinnerbot.core.model.HouseholdSettings
import com.battlesbudz.dinnerbot.core.model.MeasureUnit
import com.battlesbudz.dinnerbot.core.model.PantryItem
import com.battlesbudz.dinnerbot.core.model.PriceItem
import com.battlesbudz.dinnerbot.core.model.Quantity
import com.battlesbudz.dinnerbot.core.model.Store
import com.battlesbudz.dinnerbot.core.model.StorePrice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupEngineTest {

    private val data = BackupData(
        settings = HouseholdSettings(
            householdSize = 5,
            cookNights = listOf("MON", "WED", "FRI"),
            weeklyBudgetCents = 9000,
            wontEat = listOf("mushrooms"),
            pantryStaples = listOf("Salt"),
            onboardingComplete = true,
        ),
        priceItems = listOf(
            PriceItem(
                id = "pork-tenderloin", name = "Pork tenderloin", category = "Meat",
                homeStore = "sams-club", packDescription = "9 lb",
                packQuantity = Quantity(9.0, MeasureUnit.POUND), packPriceCents = 2199,
                pricePerUnitCents = 244.3, checkedDate = "2026-10-22", source = "Corrected in app",
                userCorrected = true,
                alternates = listOf(StorePrice("aldi", 297.0)),
            ),
        ),
        pantryItems = listOf(
            PantryItem("salt", "Salt", owned = true),
            PantryItem("flour", "Flour", owned = false),
        ),
        bulkStock = listOf(BulkStock("pork-tenderloin", Quantity.pounds(4.5))),
    )

    @Test fun exportImportRoundTrips() {
        val text = BackupEngine.export(data)
        val restored = BackupEngine.import(text)
        assertEquals(data.settings, restored.settings)
        assertEquals(data.priceItems, restored.priceItems)
        assertEquals(data.pantryItems, restored.pantryItems)
        assertEquals(data.bulkStock, restored.bulkStock)
        assertTrue(restored.priceItems.single().userCorrected)
    }

    @Test fun importRejectsGarbage() {
        assertThrows(IllegalArgumentException::class.java) {
            BackupEngine.import("this is not json at all")
        }
        assertThrows(IllegalArgumentException::class.java) {
            BackupEngine.import("{\"version\":1}")
        }
    }

    @Test fun importRejectsNewerVersionAndBrokenPrices() {
        val newer = BackupEngine.export(data.copy(version = 99))
        assertThrows(IllegalArgumentException::class.java) { BackupEngine.import(newer) }

        val broken = data.copy(
            priceItems = data.priceItems.map { it.copy(packPriceCents = 0) },
        )
        assertThrows(IllegalArgumentException::class.java) {
            BackupEngine.import(BackupEngine.export(broken))
        }
    }
}
