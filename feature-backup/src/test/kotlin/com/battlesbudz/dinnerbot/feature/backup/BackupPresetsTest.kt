package com.battlesbudz.dinnerbot.feature.backup

import com.battlesbudz.dinnerbot.core.model.HouseholdSettings
import com.battlesbudz.dinnerbot.core.model.MeasureUnit
import com.battlesbudz.dinnerbot.core.model.PriceItem
import com.battlesbudz.dinnerbot.core.model.Quantity
import com.battlesbudz.dinnerbot.core.model.Store
import org.junit.Assert.assertEquals
import org.junit.Test

/** Phase V2-2: the household's diet preset selection survives a backup round trip. */
class BackupPresetsTest {

    @Test fun dietPresetSelectionRoundTrips() {
        val original = BackupData(
            settings = HouseholdSettings(
                householdSize = 4,
                cookNights = listOf("MON", "TUE", "WED", "THU", "FRI"),
                weeklyBudgetCents = 100_00,
                wontEat = listOf("mushrooms"),
                dietPresets = listOf("dairy-free", "no-pork"),
                onboardingComplete = true,
            ),
            // Import requires a non-empty price book, as a real backup has.
            priceItems = listOf(
                PriceItem(
                    id = "eggs", name = "Eggs", category = "Dairy",
                    homeStore = "aldi", packDescription = "12 ct",
                    packQuantity = Quantity(12.0, MeasureUnit.COUNT), packPriceCents = 185,
                    pricePerUnitCents = 15.4, checkedDate = "2026-10-22", source = "test",
                ),
            ),
            pantryItems = emptyList(),
            bulkStock = emptyList(),
        )
        val restored = BackupEngine.import(BackupEngine.export(original))
        assertEquals(listOf("dairy-free", "no-pork"), restored.settings.dietPresets)
        assertEquals(listOf("mushrooms"), restored.settings.wontEat)
    }
}
