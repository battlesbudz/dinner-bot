package com.battlesbudz.dinnerbot.feature.grocerylist.barcode

import com.battlesbudz.dinnerbot.core.model.BarcodeLink
import com.battlesbudz.dinnerbot.core.model.GroceryLine
import com.battlesbudz.dinnerbot.core.model.MeasureUnit
import com.battlesbudz.dinnerbot.core.model.PlannedNight
import com.battlesbudz.dinnerbot.core.model.PriceItem
import com.battlesbudz.dinnerbot.core.model.Quantity
import com.battlesbudz.dinnerbot.core.model.Recipe
import com.battlesbudz.dinnerbot.core.model.RecipeIngredient
import com.battlesbudz.dinnerbot.core.model.ScanSubstitution
import com.battlesbudz.dinnerbot.core.model.Store
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * PR v0.4.4, the barcode rework's decisions, from Battles's two field
 * cases on the v0.4.2 build:
 *
 * - Sugar: a scanned product the book does not have becomes a book item
 *   from the scan (name, store, pack, typed price), the barcode links
 *   to it, and a rescan recalls it with the saved price.
 * - Drumsticks: a scan from the skinless-chicken-breast line that names
 *   a different product substitutes THIS WEEK ONLY: the breast line
 *   comes off this week's list, its book price is untouched, the
 *   substitute goes on at the typed price, the swap survives a reprice
 *   of the same week, the next week's lines are unaffected, and the
 *   swap is remembered for the same pairing only.
 */
class BarcodeSubstitutionLogicTest {

    private fun item(id: String, name: String = id, packPrice: Int = 200) = PriceItem(
        id = id, name = name, category = "Test", homeStore = Store.ALDI,
        packDescription = "pack", packQuantity = Quantity(2.0, MeasureUnit.POUND),
        packPriceCents = packPrice, pricePerUnitCents = packPrice / 2.0,
        checkedDate = "2026-10-22", source = "seed",
    )

    private val breast = item("chicken-breast", "Boneless skinless chicken breast", 836)
    private val rice = item("long-grain-white-rice", "Long grain white rice", 189)

    private fun line(item: PriceItem, packs: Int = 1, price: Int = item.packPriceCents) = GroceryLine(
        itemId = item.id, displayName = item.name, store = Store.ALDI,
        needed = item.packQuantity, packsToBuy = packs,
        estimatedPackPriceCents = price, portionCostCents = price,
    )

    // ---- Piece 1: a new product from a scan ----

    @Test fun newItemCarriesTheFormAndTheTypedPrice() {
        val created = BarcodeScanLogic.buildUserItem(
            name = "Granulated sugar",
            category = "Dry and canned",
            homeStore = Store.WALMART,
            packDescription = "Bag, 4 lb",
            packQuantity = Quantity(4.0, MeasureUnit.POUND),
            shelfPriceCents = 342,
            checkedDate = "2026-10-23",
            existingIds = setOf("chicken-breast"),
        )
        assertEquals("user-granulated-sugar", created.id)
        assertEquals("Granulated sugar", created.name)
        assertEquals("Dry and canned", created.category)
        assertEquals(Store.WALMART, created.homeStore)
        assertEquals("Bag, 4 lb", created.packDescription)
        assertEquals(Quantity(4.0, MeasureUnit.POUND), created.packQuantity)
        assertEquals(342, created.packPriceCents)
        assertEquals(342 / 4.0, created.pricePerUnitCents!!, 0.001)
        assertEquals("2026-10-23", created.checkedDate)
        assertEquals(BarcodeScanLogic.NEW_ITEM_SOURCE, created.source)
        assertTrue("a price Andrea typed is a user correction", created.userCorrected)
    }

    @Test fun newItemIdsNeverCollideWithSeedOrEachOther() {
        // Seed ids never carry the prefix, and a second product with the
        // same name gets a numbered id instead of overwriting the first.
        val first = BarcodeScanLogic.newItemId("Sugar", setOf("sugar"))
        assertEquals("user-sugar", first)
        val second = BarcodeScanLogic.newItemId("Sugar", setOf("sugar", "user-sugar"))
        assertEquals("user-sugar-2", second)
        val third = BarcodeScanLogic.newItemId("Sugar", setOf("sugar", "user-sugar", "user-sugar-2"))
        assertEquals("user-sugar-3", third)
    }

    @Test fun rescanOfANewItemRecallsItWithItsSavedPrice() {
        // The full loop of Battles's expectation: the price typed during
        // a scan is saved against that barcode and recalled on rescan.
        val created = BarcodeScanLogic.buildUserItem(
            name = "Granulated sugar", category = "Dry and canned",
            homeStore = Store.ALDI, packDescription = "Bag, 4 lb",
            packQuantity = Quantity(4.0, MeasureUnit.POUND),
            shelfPriceCents = 298, checkedDate = "2026-10-23",
            existingIds = setOf(breast.id, rice.id),
        )
        val bookAfter = listOf(breast, rice, created).associateBy { it.id }
        val linksAfter = listOf(BarcodeLink("036800123456", created.id, 100L))
        val resolved = BarcodeScanLogic.resolve("036800123456", linksAfter, bookAfter)
        val linked = resolved as BarcodeScanLogic.Resolution.Linked
        assertEquals(created, linked.item)
        assertEquals(298, linked.item.packPriceCents)
    }

    // ---- Piece 2: substitute instead, this week only ----

    @Test fun substituteLineReplacesThePlannedLineAtTheTypedPrice() {
        val drumsticks = BarcodeScanLogic.buildUserItem(
            name = "Chicken drumsticks", category = "Meat",
            homeStore = Store.ALDI, packDescription = "Family pack, about 4 lb",
            packQuantity = Quantity(4.0, MeasureUnit.POUND),
            shelfPriceCents = 512, checkedDate = "2026-10-23",
            existingIds = setOf(breast.id),
        )
        val planned = line(breast)
        val swapped = BarcodeScanLogic.substituteLine(planned, drumsticks, 499)
        assertEquals(drumsticks.id, swapped.itemId)
        assertEquals("Chicken drumsticks", swapped.displayName)
        assertEquals(Store.ALDI, swapped.store)
        assertEquals(1, swapped.packsToBuy)
        assertEquals(499, swapped.estimatedPackPriceCents)
        // The line's need is the dinner quantity (2 lb), not the pack
        // size (4 lb): the list reads honestly, and Costing.buyLeftover
        // can see the 2 lb that will remain (Codex, PR #24).
        assertEquals(Quantity(2.0, MeasureUnit.POUND), swapped.needed)
        // Food cost is what the dinner uses, the planner's definition:
        // the planned need is 2 lb, the pack is 4 lb at $4.99, so the
        // attributed cost is 2 lb worth ($2.50), not the whole pack.
        assertEquals(250, swapped.portionCostCents)
        assertEquals("chicken-breast", swapped.substitutedForItemId)
        // The planned line object and the breast's book price are untouched.
        assertNull(planned.substitutedForItemId)
        assertEquals(836, breast.packPriceCents)
    }

    @Test fun swapForALargerPackAttributesOnlyTheUsedPortion() {
        // Codex's case on PR #24: a 5 lb pack replacing a 1 lb need must
        // not attribute the whole shelf price as food cost.
        val planned = GroceryLine(
            itemId = breast.id, displayName = breast.name, store = Store.ALDI,
            needed = Quantity(1.0, MeasureUnit.POUND), packsToBuy = 1,
            estimatedPackPriceCents = 836, portionCostCents = 199,
        )
        val bigPack = item("user-chicken-drumsticks", "Chicken drumsticks", 600)
            .copy(packQuantity = Quantity(5.0, MeasureUnit.POUND))
        val swapped = BarcodeScanLogic.substituteLine(planned, bigPack, 600)
        assertEquals(600, swapped.estimatedPackPriceCents)
        assertEquals(120, swapped.portionCostCents)
    }

    @Test fun foldKeepsTheSwapWhenTheWeekIsRepriced() {
        // This week's stored lines after the swap, then a fresh recost
        // of the same dinners (which regenerates the breast line and
        // knows nothing of the swap): the fold keeps the breast off and
        // the drumsticks on, with honest deltas.
        val drumsticks = item("user-chicken-drumsticks", "Chicken drumsticks", 499)
        val swappedLine = BarcodeScanLogic.substituteLine(line(breast), drumsticks, 499)
        val current = listOf(line(rice), swappedLine)
        val fresh = listOf(line(rice), line(breast))
        val fold = BarcodeScanLogic.foldSubstitutedLines(current, fresh)
        assertEquals(listOf("long-grain-white-rice", "user-chicken-drumsticks"), fold.lines.map { it.itemId })
        assertEquals(499 - 836, fold.cashDeltaCents)
        assertEquals(499 - 836, fold.foodDeltaCents)
        assertEquals("chicken-breast", fold.lines[1].substitutedForItemId)
    }

    @Test fun foldWithoutSwapsIsTheIdentity() {
        // Next week's lines carry no swap markers: a swap recorded
        // against this week cannot leak into them.
        val fresh = listOf(line(rice), line(breast))
        val fold = BarcodeScanLogic.foldSubstitutedLines(fresh, fresh)
        assertEquals(fresh, fold.lines)
        assertEquals(0, fold.cashDeltaCents)
        assertEquals(0, fold.foodDeltaCents)
    }

    @Test fun foldDoesNotDuplicateASubstituteTheFreshCostingAlreadyBuys() {
        // Another dinner this week already buys drumsticks on its own:
        // the fresh line stands at its fresh price, the swap line is not
        // added twice, and the displaced breast line still comes off.
        // Crucially the fresh line takes the swap's marker (Codex, PR
        // #24): without it the NEXT reprice would forget the swap and
        // resurrect the breast line.
        val drumsticks = item("user-chicken-drumsticks", "Chicken drumsticks", 499)
        val swappedLine = BarcodeScanLogic.substituteLine(line(breast), drumsticks, 499)
        val current = listOf(swappedLine)
        val fresh = listOf(line(breast), line(drumsticks))
        val fold = BarcodeScanLogic.foldSubstitutedLines(current, fresh)
        assertEquals(listOf("user-chicken-drumsticks"), fold.lines.map { it.itemId })
        assertEquals("chicken-breast", fold.lines.single().substitutedForItemId)
        // No second pack is charged (the fresh line's $4.99 stands),
        // but the line now feeds both dinners: its portion is the fresh
        // dinner's $4.99 plus the swapped dinner's $4.99 (Codex, PR #24).
        assertEquals(-836, fold.cashDeltaCents)
        assertEquals(998, fold.lines.single().portionCostCents)
        assertEquals(-836 + 499, fold.foodDeltaCents)
    }

    @Test fun foldMergedMarkerSurvivesASecondReprice() {
        // The regression Codex named: fold once (the swap merges into
        // the fresh drumstick line), then reprice AGAIN from that
        // result. The breast line must stay off both times.
        val drumsticks = item("user-chicken-drumsticks", "Chicken drumsticks", 499)
        val swappedLine = BarcodeScanLogic.substituteLine(line(breast), drumsticks, 499)
        val fresh = listOf(line(rice), line(breast), line(drumsticks))
        val first = BarcodeScanLogic.foldSubstitutedLines(listOf(line(rice), swappedLine), fresh)
        assertEquals(
            listOf("long-grain-white-rice", "user-chicken-drumsticks"),
            first.lines.map { it.itemId },
        )
        val second = BarcodeScanLogic.foldSubstitutedLines(first.lines, fresh)
        assertEquals(
            listOf("long-grain-white-rice", "user-chicken-drumsticks"),
            second.lines.map { it.itemId },
        )
        assertEquals(
            "chicken-breast",
            second.lines.single { it.itemId == "user-chicken-drumsticks" }.substitutedForItemId,
        )
        assertEquals(-836, second.cashDeltaCents)
    }

    @Test fun twoSwapsForTheSameSubstituteCarryAsTwoPacks() {
        // Two different planned items swapped for drumsticks in one
        // week: one fresh drumstick line can absorb one marker, so the
        // second swap carries as its own line (two swaps, two packs),
        // and both displaced lines come off.
        val thighs = item("chicken-thighs", "Chicken thighs", 620)
        val drumsticks = item("user-chicken-drumsticks", "Chicken drumsticks", 499)
        val swapA = BarcodeScanLogic.substituteLine(line(breast), drumsticks, 499)
        val swapB = BarcodeScanLogic.substituteLine(line(thighs), drumsticks, 499)
        val fresh = listOf(line(breast), line(thighs), line(drumsticks))
        val fold = BarcodeScanLogic.foldSubstitutedLines(listOf(swapA, swapB), fresh)
        val drumstickLines = fold.lines.filter { it.itemId == "user-chicken-drumsticks" }
        assertEquals(2, drumstickLines.size)
        assertEquals(
            setOf("chicken-breast", "chicken-thighs"),
            drumstickLines.map { it.substitutedForItemId }.toSet(),
        )
        assertEquals(499 - 836 - 620, fold.cashDeltaCents)
        // The merged line carries both dinners' portions for its item.
        val merged = drumstickLines.single { it.substitutedForItemId == "chicken-breast" }
        assertEquals(998, merged.portionCostCents)
    }

    @Test fun chainedSwapKeepsTheRootDisplacedItem() {
        // She swapped breast for drumsticks, then scanned the drumstick
        // line and chose thighs instead (Codex, PR #24): the new line's
        // marker must stay the breast, the item the planner would
        // regenerate. Marking the drumsticks instead would resurrect
        // the breast line at the next reprice, next to the thighs.
        val drumsticks = item("user-chicken-drumsticks", "Chicken drumsticks", 499)
        val thighs = item("chicken-thighs", "Chicken thighs", 620)
        val firstSwap = BarcodeScanLogic.substituteLine(line(breast), drumsticks, 499)
        val chained = BarcodeScanLogic.substituteLine(firstSwap, thighs, 615)
        assertEquals("chicken-thighs", chained.itemId)
        assertEquals("chicken-breast", chained.substitutedForItemId)
        // And the fold honors the chain: the breast stays off, the
        // thighs are on, no drumstick or breast line reappears.
        val fresh = listOf(line(breast), line(rice))
        val fold = BarcodeScanLogic.foldSubstitutedLines(listOf(chained), fresh)
        assertEquals(
            listOf("long-grain-white-rice", "chicken-thighs"),
            fold.lines.map { it.itemId },
        )
    }

    // ---- Piece 3: the remembered substitution ----

    private fun record(
        original: String, substitute: String, recipe: String? = null, at: Long,
    ) = ScanSubstitution(
        planId = "plan-1", originalItemId = original, substituteItemId = substitute,
        recipeId = recipe, substitutePriceCents = 499, createdAtMillis = at,
    )

    @Test fun rememberedSwapSurfacesForTheSamePairingOnly() {
        val records = listOf(
            record("chicken-breast", "user-chicken-drumsticks", at = 100L),
            record("chicken-breast", "chicken-thighs", at = 200L),
            record("ground-turkey", "user-chicken-drumsticks", at = 300L),
        )
        // The most recent swap for the breast is the suggestion.
        val hint = BarcodeScanLogic.rememberedSubstitution(records, "chicken-breast")
        assertEquals("chicken-thighs", hint?.substituteItemId)
        // A different original item gets its own memory, and an item
        // never swapped gets none: memories never cross pairings.
        assertEquals(
            "user-chicken-drumsticks",
            BarcodeScanLogic.rememberedSubstitution(records, "ground-turkey")?.substituteItemId,
        )
        assertNull(BarcodeScanLogic.rememberedSubstitution(records, "eggs"))
    }

    @Test fun rememberedSwapPrefersTheSameDinnerWhenTheContextIsKnown() {
        val records = listOf(
            record("chicken-breast", "user-chicken-drumsticks", recipe = "sheet-pan-chicken", at = 100L),
            record("chicken-breast", "chicken-thighs", recipe = "chicken-soup", at = 200L),
        )
        assertEquals(
            "user-chicken-drumsticks",
            BarcodeScanLogic.rememberedSubstitution(records, "chicken-breast", "sheet-pan-chicken")
                ?.substituteItemId,
        )
        assertEquals(
            "chicken-thighs",
            BarcodeScanLogic.rememberedSubstitution(records, "chicken-breast", "chicken-soup")
                ?.substituteItemId,
        )
        // An unknown dinner falls back to the most recent swap overall.
        assertEquals(
            "chicken-thighs",
            BarcodeScanLogic.rememberedSubstitution(records, "chicken-breast", "some-new-dinner")
                ?.substituteItemId,
        )
    }

    @Test fun recipeContextIsTheOneDinnerUsingTheItem() {
        fun recipe(id: String, vararg itemIds: String) = Recipe(
            id = id, title = id, servings = 4, activeMinutes = 10,
            cuisine = "Test", mainProtein = "Chicken",
            ingredients = itemIds.map { RecipeIngredient(priceItemId = it, quantity = Quantity(1.0, MeasureUnit.COUNT)) },
            steps = listOf("Cook."),
        )
        val sheetPan = recipe("sheet-pan-chicken", "chicken-breast", "long-grain-white-rice")
        val soup = recipe("chicken-soup", "chicken-breast")
        val byId = mapOf(sheetPan.id to sheetPan, soup.id to soup)
        // Exactly one cooking dinner uses the item: that dinner is the context.
        assertEquals(
            "sheet-pan-chicken",
            BarcodeScanLogic.recipeContextFor(
                "chicken-breast",
                listOf(PlannedNight("MON", "sheet-pan-chicken"), PlannedNight("TUE", "other")),
                byId,
            ),
        )
        // Two dinners use it: no single context.
        assertNull(
            BarcodeScanLogic.recipeContextFor(
                "chicken-breast",
                listOf(PlannedNight("MON", "sheet-pan-chicken"), PlannedNight("TUE", "chicken-soup")),
                byId,
            ),
        )
        // A night marked not cooking does not count as context.
        assertEquals(
            "sheet-pan-chicken",
            BarcodeScanLogic.recipeContextFor(
                "chicken-breast",
                listOf(
                    PlannedNight("MON", "sheet-pan-chicken"),
                    PlannedNight("TUE", "chicken-soup", notCooking = true),
                ),
                byId,
            ),
        )
        // No dinner uses it: no context.
        assertNull(
            BarcodeScanLogic.recipeContextFor(
                "eggs",
                listOf(PlannedNight("MON", "sheet-pan-chicken")),
                byId,
            ),
        )
        assertFalse(breast.userCorrected)
    }
}
