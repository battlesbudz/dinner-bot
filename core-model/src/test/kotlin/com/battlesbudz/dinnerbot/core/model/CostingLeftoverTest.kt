package com.battlesbudz.dinnerbot.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Phase V2-2: the freeze-guidance leftover for a bought Sam's bulk pack. */
class CostingLeftoverTest {

    private val tenderloin = PriceItem(
        id = "pork-tenderloin", name = "Pork tenderloin", category = "Meat",
        homeStore = Store.SAMS_CLUB, packDescription = "about 9 lb",
        packQuantity = Quantity.pounds(9.0), packPriceCents = 2168,
        pricePerUnitCents = 240.9, checkedDate = "2026-10-22", source = "test",
        freezeNote = "Freeze the rest.",
    )

    private fun line(
        store: Store, needed: Quantity, packs: Int, itemId: String = "pork-tenderloin",
    ) = GroceryLine(
        itemId = itemId, displayName = itemId, store = store, needed = needed,
        packsToBuy = packs, estimatedPackPriceCents = 2168, portionCostCents = 0,
    )

    @Test fun leftoverIsPackMinusNeeded() {
        val leftover = Costing.buyLeftover(
            line(Store.SAMS_CLUB, Quantity.pounds(1.25), packs = 1), tenderloin,
        )
        assertEquals(7.75, leftover!!.amount, 0.001)
        assertEquals(MeasureUnit.POUND, leftover.unit)
    }

    @Test fun twoPacksLeaveTheSumMinusNeeded() {
        val leftover = Costing.buyLeftover(
            line(Store.SAMS_CLUB, Quantity.pounds(10.0), packs = 2), tenderloin,
        )
        assertEquals(8.0, leftover!!.amount, 0.001)
    }

    @Test fun exactUseLeavesNothing() {
        assertNull(Costing.buyLeftover(line(Store.SAMS_CLUB, Quantity.pounds(9.0), packs = 1), tenderloin))
    }

    @Test fun noPurchaseNoLeftover() {
        assertNull(Costing.buyLeftover(line(Store.SAMS_CLUB, Quantity.pounds(1.0), packs = 0), tenderloin))
    }

    @Test fun regularStoreLineHasNoLeftover() {
        assertNull(Costing.buyLeftover(line(Store.WALMART, Quantity.pounds(1.0), packs = 1), tenderloin))
    }

    @Test fun nonSamsItemHasNoLeftover() {
        val aldiItem = tenderloin.copy(homeStore = Store.ALDI)
        assertNull(
            Costing.buyLeftover(
                line(Store.SAMS_CLUB, Quantity.pounds(1.0), packs = 1), aldiItem,
            ),
        )
    }

    @Test fun neededInAnotherUnitStillConverts() {
        val leftover = Costing.buyLeftover(
            line(Store.SAMS_CLUB, Quantity(20.0, MeasureUnit.OUNCE), packs = 1), tenderloin,
        )
        // 9 lb pack - 20 oz (1.25 lb) needed = 7.75 lb, stated in the pack unit.
        assertEquals(7.75, leftover!!.amount, 0.001)
        assertEquals(MeasureUnit.POUND, leftover.unit)
    }
}
