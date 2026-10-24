package com.battlesbudz.dinnerbot.feature.backup

import com.battlesbudz.dinnerbot.core.model.HouseholdSettings
import com.battlesbudz.dinnerbot.core.model.MeasureUnit
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

/**
 * PR v0.4.4: remembered scan substitutions ride in backup format v5,
 * and items Andrea added from scans ride in the price book section
 * like any other book item (they are ordinary items with "user-" ids).
 * Older files import with substitutions empty.
 */
class BackupSubstitutionsTest {

    private val breast = PriceItem(
        id = "chicken-breast", name = "Boneless skinless chicken breast", category = "Meat",
        homeStore = Store.ALDI, packDescription = "Fresh family pack",
        packQuantity = Quantity(4.2, MeasureUnit.POUND), packPriceCents = 836,
        pricePerUnitCents = 199.0, checkedDate = "2026-10-21", source = "test",
    )

    private val drumsticks = PriceItem(
        id = "user-chicken-drumsticks", name = "Chicken drumsticks", category = "Meat",
        homeStore = Store.ALDI, packDescription = "Family pack, about 4 lb",
        packQuantity = Quantity(4.0, MeasureUnit.POUND), packPriceCents = 499,
        pricePerUnitCents = 124.75, checkedDate = "2026-10-23",
        source = "Added by barcode scan", userCorrected = true,
    )

    private val sugar = PriceItem(
        id = "user-granulated-sugar", name = "Granulated sugar", category = "Dry and canned",
        homeStore = Store.WALMART, packDescription = "Bag, 4 lb",
        packQuantity = Quantity(4.0, MeasureUnit.POUND), packPriceCents = 342,
        pricePerUnitCents = 85.5, checkedDate = "2026-10-23",
        source = "Added by barcode scan", userCorrected = true,
    )

    private fun data(swaps: List<BackupScanSubstitution>) = BackupData(
        settings = HouseholdSettings(
            householdSize = 5,
            cookNights = listOf("MON", "WED", "FRI"),
            weeklyBudgetCents = 9000,
            onboardingComplete = true,
        ),
        priceItems = listOf(breast, drumsticks, sugar),
        pantryItems = emptyList(),
        bulkStock = emptyList(),
        barcodeLinks = listOf(
            BackupBarcodeLink("036800123456", "user-granulated-sugar"),
            BackupBarcodeLink("0023456789012", "user-chicken-drumsticks"),
        ),
        scanSubstitutions = swaps,
    )

    @Test fun substitutionsAndUserAddedItemsRoundTrip() {
        val original = data(
            listOf(
                BackupScanSubstitution(
                    originalItemId = "chicken-breast",
                    substituteItemId = "user-chicken-drumsticks",
                    recipeId = "sheet-pan-chicken",
                    substitutePriceCents = 499,
                    createdAtMillis = 123456789L,
                ),
            ),
        )
        val restored = BackupEngine.import(BackupEngine.export(original))
        assertEquals(5, restored.version)
        assertEquals(original.scanSubstitutions, restored.scanSubstitutions)
        // The user-added items are in the book wholesale, prices and all,
        // and the links that point at them still resolve.
        assertEquals(original.priceItems, restored.priceItems)
        assertEquals(original.barcodeLinks, restored.barcodeLinks)
        val restoredSugar = restored.priceItems.single { it.id == "user-granulated-sugar" }
        assertEquals(342, restoredSugar.packPriceCents)
        assertTrue(restoredSugar.userCorrected)
    }

    @Test fun versionFourBackupImportsWithNoSubstitutions() {
        // A real v4 file predates the scanSubstitutions field entirely.
        val exported = BackupEngine.export(
            data(
                listOf(
                    BackupScanSubstitution("chicken-breast", "user-chicken-drumsticks", null, 499, 1L),
                ),
            ),
        )
        val parsed = Json.parseToJsonElement(exported).jsonObject
        val v4 = JsonObject(parsed - "scanSubstitutions" + ("version" to JsonPrimitive(4)))
        val restored = BackupEngine.import(Json.encodeToString(JsonObject.serializer(), v4))
        assertEquals(4, restored.version)
        assertTrue(restored.scanSubstitutions.isEmpty())
        // The user-added items in the book still come across.
        assertEquals(3, restored.priceItems.size)
    }

    @Test fun swapForAnItemOutsideTheBookIsRejected() {
        val orphan = data(
            listOf(
                BackupScanSubstitution("chicken-breast", "not-in-the-book", null, 499, 1L),
            ),
        )
        val error = assertThrows(IllegalArgumentException::class.java) {
            BackupEngine.import(BackupEngine.export(orphan))
        }
        assertTrue(error.message!!.contains("not in its price book"))
    }

    @Test fun duplicateSwapsAreRejected() {
        val swap = BackupScanSubstitution("chicken-breast", "user-chicken-drumsticks", null, 499, 1L)
        val dup = data(listOf(swap, swap))
        val error = assertThrows(IllegalArgumentException::class.java) {
            BackupEngine.import(BackupEngine.export(dup))
        }
        assertTrue(error.message!!.contains("duplicate scan substitutions"))
    }
}
