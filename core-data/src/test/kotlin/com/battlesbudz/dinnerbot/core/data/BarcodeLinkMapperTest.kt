package com.battlesbudz.dinnerbot.core.data

import com.battlesbudz.dinnerbot.core.model.BarcodeLink
import org.junit.Assert.assertEquals
import org.junit.Test

/** Phase V2-5: learned barcode links convert between model and entity losslessly. */
class BarcodeLinkMapperTest {

    @Test fun linkRoundTripsThroughEntity() {
        val link = BarcodeLink(barcode = "0012345678901", itemId = "rotini", createdAtMillis = 42L)
        val entity = with(SeedMapper) { link.toEntity() }
        assertEquals("0012345678901", entity.barcode)
        assertEquals("rotini", entity.itemId)
        assertEquals(42L, entity.createdAtMillis)
        assertEquals(link, with(SeedMapper) { entity.toModel() })
    }
}
