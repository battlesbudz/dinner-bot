package com.battlesbudz.dinnerbot.feature.backup

import com.battlesbudz.dinnerbot.core.model.BulkStock
import com.battlesbudz.dinnerbot.core.model.HouseholdSettings
import com.battlesbudz.dinnerbot.core.model.MeasureUnit
import com.battlesbudz.dinnerbot.core.model.PantryItem
import com.battlesbudz.dinnerbot.core.model.PriceItem
import com.battlesbudz.dinnerbot.core.model.Quantity
import com.battlesbudz.dinnerbot.core.model.Store
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** Phase V2-5: barcode links ride in backup format v3; older files import with none. */
class BackupBarcodeLinksTest {

    private fun data(links: List<BackupBarcodeLink>) = BackupData(
        settings = HouseholdSettings(
            householdSize = 5,
            cookNights = listOf("MON", "WED", "FRI"),
            weeklyBudgetCents = 9000,
            onboardingComplete = true,
        ),
        priceItems = listOf(
            PriceItem(
                id = "rotini", name = "Rotini", category = "Pantry",
                homeStore = Store.ALDI, packDescription = "16 oz",
                packQuantity = Quantity(16.0, MeasureUnit.OUNCE), packPriceCents = 129,
                pricePerUnitCents = 8.1, checkedDate = "2026-10-22", source = "test",
            ),
        ),
        pantryItems = listOf(PantryItem("salt", "Salt", owned = true)),
        bulkStock = listOf(BulkStock("pork-tenderloin", Quantity.pounds(4.5))),
        barcodeLinks = links,
    )

    @Test fun linksRoundTripThroughExportImport() {
        val original = data(
            listOf(
                BackupBarcodeLink("0012345678901", "rotini"),
                BackupBarcodeLink("0098765432109", "rotini"),
            ),
        )
        val restored = BackupEngine.import(BackupEngine.export(original))
        assertEquals(4, restored.version)
        assertEquals(original.barcodeLinks, restored.barcodeLinks)
        assertEquals(original.priceItems, restored.priceItems)
        assertEquals(original.settings, restored.settings)
    }

    @Test fun versionTwoBackupImportsWithNoLinks() {
        // A real v2 file predates the barcodeLinks field entirely.
        val exported = BackupEngine.export(data(listOf(BackupBarcodeLink("001", "rotini"))))
        val parsed = Json.parseToJsonElement(exported).jsonObject
        val v2 = JsonObject(parsed - "barcodeLinks" + ("version" to JsonPrimitive(2)))
        val restored = BackupEngine.import(Json.encodeToString(JsonObject.serializer(), v2))
        assertEquals(2, restored.version)
        assertTrue(restored.barcodeLinks.isEmpty())
        assertEquals(1, restored.priceItems.size)
    }

    @Test fun duplicateBarcodesAreRejected() {
        val dup = data(
            listOf(
                BackupBarcodeLink("0012345678901", "rotini"),
                BackupBarcodeLink("0012345678901", "rotini"),
            ),
        )
        val error = assertThrows(IllegalArgumentException::class.java) {
            BackupEngine.import(BackupEngine.export(dup))
        }
        assertTrue(error.message!!.contains("duplicate barcode"))
    }

    @Test fun linkToAnItemOutsideTheBookIsRejected() {
        val orphan = data(listOf(BackupBarcodeLink("0012345678901", "not-in-the-book")))
        val error = assertThrows(IllegalArgumentException::class.java) {
            BackupEngine.import(BackupEngine.export(orphan))
        }
        assertTrue(error.message!!.contains("not in its price book"))
    }
}
