package com.battlesbudz.dinnerbot.feature.grocerylist.barcode

import com.battlesbudz.dinnerbot.core.model.BarcodeLink
import com.battlesbudz.dinnerbot.core.model.MeasureUnit
import com.battlesbudz.dinnerbot.core.model.PriceItem
import com.battlesbudz.dinnerbot.core.model.Quantity
import com.battlesbudz.dinnerbot.core.model.Store
import com.battlesbudz.dinnerbot.feature.pricebook.PriceCorrection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase V2-5: the scan flow's decisions. Resolution goes through the
 * links learned on the phone, link-now candidates lead with the list's
 * own items, and a scan correction writes the price book record exactly
 * the way a typed correction does, with the scan source note.
 */
class BarcodeScanLogicTest {

    private fun item(id: String, name: String = id, packPrice: Int = 200) = PriceItem(
        id = id, name = name, category = "Test", homeStore = Store.ALDI,
        packDescription = "pack", packQuantity = Quantity(2.0, MeasureUnit.POUND),
        packPriceCents = packPrice, pricePerUnitCents = packPrice / 2.0,
        checkedDate = "2026-10-22", source = "seed",
    )

    private val rotini = item("rotini", "Rotini", 129)
    private val sauce = item("pasta-sauce", "Pasta sauce", 185)
    private val eggs = item("eggs", "Eggs", 185)
    private val book = listOf(rotini, sauce, eggs)
    private val byId = book.associateBy { it.id }

    @Test fun knownCodeResolvesToItsLinkedItem() {
        val links = listOf(BarcodeLink("0012345678901", "rotini", 1L))
        val resolved = BarcodeScanLogic.resolve("0012345678901", links, byId)
        assertEquals(BarcodeScanLogic.Resolution.Linked(rotini), resolved)
    }

    @Test fun unknownCodeIsUnlinked() {
        val links = listOf(BarcodeLink("0012345678901", "rotini", 1L))
        val resolved = BarcodeScanLogic.resolve("0099999999999", links, byId)
        assertEquals(BarcodeScanLogic.Resolution.Unlinked("0099999999999"), resolved)
    }

    @Test fun linkToAMissingItemFallsBackToUnlinked() {
        // A stale link whose item left the book must not crash the flow:
        // the code is simply offered for linking again.
        val links = listOf(BarcodeLink("0012345678901", "gone-item", 1L))
        val resolved = BarcodeScanLogic.resolve("0012345678901", links, byId)
        assertTrue(resolved is BarcodeScanLogic.Resolution.Unlinked)
    }

    @Test fun candidatesLeadWithTheListThenTheBookByName() {
        val candidates = BarcodeScanLogic.linkCandidates(
            allItems = book,
            listItemIds = listOf("eggs", "rotini"),
        )
        assertEquals(listOf("eggs", "rotini", "pasta-sauce"), candidates.map { it.id })
    }

    @Test fun candidatesPutAPerLineScanTargetFirstAndNeverDuplicate() {
        val candidates = BarcodeScanLogic.linkCandidates(
            allItems = book,
            listItemIds = listOf("eggs", "rotini"),
            preferredItemId = "pasta-sauce",
        )
        assertEquals(listOf("pasta-sauce", "eggs", "rotini"), candidates.map { it.id })
        assertEquals(candidates.size, candidates.distinctBy { it.id }.size)
    }

    @Test fun scanCorrectionWritesTheSameRecordAsATypedCorrection() {
        val shelfCents = 149
        val viaScan = BarcodeScanLogic.scanCorrection(rotini, shelfCents)
        val viaTyping = PriceCorrection.applyPackPrice(rotini, shelfCents)
        // Identical in every field but the source note, which records
        // that this correction came through a scan.
        assertEquals(viaTyping.copy(source = BarcodeScanLogic.SCAN_SOURCE), viaScan)
        assertEquals(BarcodeScanLogic.SCAN_SOURCE, viaScan.source)
        assertEquals(shelfCents, viaScan.packPriceCents)
        assertEquals(shelfCents / 2.0, viaScan.pricePerUnitCents!!, 0.001)
        assertTrue(viaScan.userCorrected)
    }

    @Test fun offNameComesFromTheProductRecord() {
        val body = """{"status":1,"code":"3017620422003","product":{"product_name":"Nutella","brands":"Ferrero"}}"""
        assertEquals("Nutella", BarcodeScanLogic.parseOffProductName(body))
    }

    @Test fun offNameFallsBackToGenericName() {
        val body = """{"status":1,"product":{"generic_name":"Hazelnut spread"}}"""
        assertEquals("Hazelnut spread", BarcodeScanLogic.parseOffProductName(body))
    }

    @Test fun offUnknownCodeHasNoName() {
        val body = """{"status":0,"status_verbose":"product not found"}"""
        assertNull(BarcodeScanLogic.parseOffProductName(body))
    }

    @Test fun offEmptyOrGarbageResponsesHaveNoName() {
        assertNull(BarcodeScanLogic.parseOffProductName(""))
        assertNull(BarcodeScanLogic.parseOffProductName("not json at all"))
        assertNull(BarcodeScanLogic.parseOffProductName("""{"status":1,"product":{}}"""))
        assertNull(BarcodeScanLogic.parseOffProductName("""{"status":1,"product":{"product_name":"  "}}"""))
    }

    @Test fun offNameUnescapesJsonEscapes() {
        val body = """{"status":1,"product":{"product_name":"Café \"Crème\""}}"""
        assertEquals("Café \"Crème\"", BarcodeScanLogic.parseOffProductName(body))
    }
}
