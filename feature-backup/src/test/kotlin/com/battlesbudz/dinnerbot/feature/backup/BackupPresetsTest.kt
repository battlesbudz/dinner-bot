package com.battlesbudz.dinnerbot.feature.backup

import com.battlesbudz.dinnerbot.core.model.HouseholdSettings
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
            priceItems = emptyList(),
            pantryItems = emptyList(),
            bulkStock = emptyList(),
        )
        val restored = BackupEngine.import(BackupEngine.export(original))
        assertEquals(listOf("dairy-free", "no-pork"), restored.settings.dietPresets)
        assertEquals(listOf("mushrooms"), restored.settings.wontEat)
    }
}
