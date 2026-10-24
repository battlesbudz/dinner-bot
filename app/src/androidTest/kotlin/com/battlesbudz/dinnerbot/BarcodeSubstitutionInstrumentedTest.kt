package com.battlesbudz.dinnerbot

import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.battlesbudz.dinnerbot.core.data.DinnerBotDatabase
import com.battlesbudz.dinnerbot.core.data.GroceryItemEntity
import com.battlesbudz.dinnerbot.core.data.PlanEntity
import com.battlesbudz.dinnerbot.core.data.ScanSubstitutionEntity
import com.battlesbudz.dinnerbot.core.model.MeasureUnit
import com.battlesbudz.dinnerbot.core.model.PriceItem
import com.battlesbudz.dinnerbot.core.model.Quantity
import com.battlesbudz.dinnerbot.core.model.ScanSubstitution
import com.battlesbudz.dinnerbot.core.model.Store
import com.battlesbudz.dinnerbot.feature.grocerylist.barcode.LinkBarcodeDialog
import com.battlesbudz.dinnerbot.feature.grocerylist.barcode.NewItemFormDialog
import com.battlesbudz.dinnerbot.feature.grocerylist.barcode.SubstituteChoiceDialog
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * PR v0.4.4, the barcode rework's new surfaces, as far as an emulator
 * can prove them (the same bar Phase V2-5 set: the camera decode is
 * covered by BarcodeInstrumentedTest, and a real package scan stays a
 * device check):
 *
 * - The link picker offers "add it as a new item" — the sugar case no
 *   longer dead-ends at a list of items the product is not.
 * - The new-item form builds the price book item from the package in
 *   hand, its name prefilled from the lookup hint.
 * - The substitute choice shows the remembered swap for the pairing
 *   ("Last time you swapped in ...") and fires the buy-instead and
 *   save-price-only choices with the typed cents.
 * - The write layer the flow lands on (item upsert + link, row swap,
 *   plan totals, remembered record) works against the real schema.
 */
@RunWith(AndroidJUnit4::class)
class BarcodeSubstitutionInstrumentedTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private fun item(id: String, name: String, packPrice: Int = 200) = PriceItem(
        id = id, name = name, category = "Meat", homeStore = Store.ALDI,
        packDescription = "pack", packQuantity = Quantity(2.0, MeasureUnit.POUND),
        packPriceCents = packPrice, pricePerUnitCents = packPrice / 2.0,
        checkedDate = "2026-10-22", source = "seed",
    )

    private val breast = item("chicken-breast", "Boneless skinless chicken breast", 836)
    private val drumsticks = item("user-chicken-drumsticks", "Chicken drumsticks", 499)

    @Test fun linkPickerOffersAddNewItem() {
        var addNewHint: String? = "not-called"
        var addNewCalled = false
        composeRule.setContent {
            LinkBarcodeDialog(
                barcode = "036800123456",
                candidates = listOf(breast),
                onPick = {},
                onAddNew = { hint -> addNewCalled = true; addNewHint = hint },
                onSkip = {},
            )
        }
        composeRule.onNodeWithTag("link-add-new-item").assertIsDisplayed()
        composeRule.onNodeWithText("None of these — add it as a new item").assertIsDisplayed()
        composeRule.onNodeWithTag("link-add-new-item").performClick()
        assertTrue("the add-new entry must fire", addNewCalled)
        // The hint is whatever the lookup produced (null offline is fine).
        assertTrue(addNewHint == null || addNewHint!!.isNotBlank())
    }

    @Test fun newItemFormBuildsTheItemFromThePackageInHand() {
        var created: PriceItem? = null
        composeRule.setContent {
            NewItemFormDialog(
                barcode = "036800123456",
                suggestedName = "Granulated sugar",
                initialStore = Store.ALDI,
                categories = listOf("Dry and canned", "Meat", "Other"),
                today = "2026-10-23",
                existingIds = setOf("chicken-breast"),
                onConfirm = { created = it },
                onDismiss = {},
            )
        }
        // The name arrives prefilled from the lookup hint.
        val prefilled = composeRule.onNodeWithTag("new-item-name")
            .fetchSemanticsNode().config[SemanticsProperties.EditableText].text
        assertEquals("Granulated sugar", prefilled)
        composeRule.onNodeWithTag("new-item-pack-description")
            .performScrollTo().performTextInput("Bag, 4 lb")
        composeRule.onNodeWithTag("new-item-pack-amount")
            .performScrollTo().performTextInput("4")
        composeRule.onNodeWithTag("new-item-price")
            .performScrollTo().performTextInput("3.42")
        composeRule.onNodeWithTag("new-item-confirm").performScrollTo().performClick()
        assertNotNull("confirming the form must build the item", created)
        val built = created!!
        assertEquals("user-granulated-sugar", built.id)
        assertEquals("Granulated sugar", built.name)
        assertEquals(Store.ALDI, built.homeStore)
        assertEquals("Bag, 4 lb", built.packDescription)
        assertEquals(342, built.packPriceCents)
        assertEquals(85.5, built.pricePerUnitCents!!, 0.001)
        assertEquals("2026-10-23", built.checkedDate)
        assertEquals("Added by barcode scan", built.source)
        assertTrue(built.userCorrected)
    }

    @Test fun substituteChoiceShowsTheRememberedSwapAndBuysInstead() {
        var boughtCents: Int? = null
        val remembered = ScanSubstitution(
            planId = "plan-1", originalItemId = "chicken-breast",
            substituteItemId = "user-chicken-drumsticks", recipeId = "sheet-pan-chicken",
            substitutePriceCents = 475, createdAtMillis = 100L,
        )
        composeRule.setContent {
            SubstituteChoiceDialog(
                scannedItem = drumsticks,
                lineDisplayName = "Boneless skinless chicken breast",
                initialCents = 499,
                remembered = remembered,
                rememberedSubstituteName = "Chicken drumsticks",
                onSubstitute = { boughtCents = it },
                onSavePriceOnly = {},
                onDismiss = {},
            )
        }
        composeRule.onNodeWithTag("substitute-remembered-hint").assertIsDisplayed()
        composeRule.onNodeWithText(
            "Last time you swapped in Chicken drumsticks for this.",
        ).assertIsDisplayed()
        composeRule.onNodeWithTag("substitute-buy-instead").performClick()
        assertEquals(499, boughtCents)
    }

    @Test fun substituteChoiceWithoutAMemoryShowsNoHint() {
        var savedCents: Int? = null
        composeRule.setContent {
            SubstituteChoiceDialog(
                scannedItem = drumsticks,
                lineDisplayName = "Boneless skinless chicken breast",
                initialCents = 499,
                remembered = null,
                rememberedSubstituteName = null,
                onSubstitute = {},
                onSavePriceOnly = { savedCents = it },
                onDismiss = {},
            )
        }
        composeRule.onNodeWithText("Just save the price").performClick()
        assertEquals(499, savedCents)
    }

    @Test fun substitutionWritesLandOnTheRealSchema() = runBlocking {
        // The exact writes MainActivity's substitution path performs,
        // against the real generated schema: the substitute joins the
        // book linked to its barcode, the planned row is replaced by a
        // marked row, the plan totals move, and the swap is remembered.
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val db = Room.inMemoryDatabaseBuilder(context, DinnerBotDatabase::class.java)
            .allowMainThreadQueries().build()
        try {
            db.planDao().insert(
                PlanEntity(
                    id = "plan-1", weekStart = "2026-10-19", budgetCents = 9000,
                    cashNeededCents = 1025, foodCostCents = 1025, samsMonthlySavingsCents = 0,
                    nightsJson = "[{\"day\":\"MON\",\"recipeId\":\"sheet-pan-chicken\"}]",
                    notesJson = "[]", createdAtMillis = 1L,
                ),
            )
            val breastRowId = db.groceryDao().insert(
                GroceryItemEntity(
                    planId = "plan-1", itemId = "chicken-breast",
                    displayName = "Boneless skinless chicken breast", store = "ALDI",
                    neededAmount = 4.2, neededUnit = "POUND", packsToBuy = 1,
                    packPriceCents = 836, portionCostCents = 836, checkedOff = false,
                ),
            )
            db.groceryDao().insert(
                GroceryItemEntity(
                    planId = "plan-1", itemId = "long-grain-white-rice",
                    displayName = "Long grain white rice", store = "ALDI",
                    neededAmount = 2.0, neededUnit = "POUND", packsToBuy = 1,
                    packPriceCents = 189, portionCostCents = 189, checkedOff = false,
                ),
            )

            // The writes, in the flow's order.
            db.priceItemDao().upsert(
                com.battlesbudz.dinnerbot.core.data.PriceItemEntity(
                    id = "user-chicken-drumsticks", name = "Chicken drumsticks",
                    category = "Meat", homeStore = "ALDI",
                    packDescription = "Family pack, about 4 lb", packAmount = 4.0,
                    packUnit = "POUND", packPriceCents = 499, pricePerUnitCents = 124.75,
                    checkedDate = "2026-10-23", source = "Added by barcode scan",
                    userCorrected = true,
                ),
            )
            db.barcodeLinkDao().upsert(
                com.battlesbudz.dinnerbot.core.data.BarcodeLinkEntity(
                    "0023456789012", "user-chicken-drumsticks", 100L,
                ),
            )
            db.groceryDao().deleteRow(breastRowId)
            db.groceryDao().insert(
                GroceryItemEntity(
                    planId = "plan-1", itemId = "user-chicken-drumsticks",
                    displayName = "Chicken drumsticks", store = "ALDI",
                    neededAmount = 4.0, neededUnit = "POUND", packsToBuy = 1,
                    packPriceCents = 499, portionCostCents = 499, checkedOff = false,
                    substitutedForItemId = "chicken-breast",
                ),
            )
            val plan = db.planDao().getById("plan-1")!!
            db.planDao().insert(
                plan.copy(cashNeededCents = 1025 - 836 + 499, foodCostCents = 1025 - 836 + 499),
            )
            db.scanSubstitutionDao().insert(
                ScanSubstitutionEntity(
                    planId = "plan-1", originalItemId = "chicken-breast",
                    substituteItemId = "user-chicken-drumsticks",
                    recipeId = "sheet-pan-chicken", substitutePriceCents = 499,
                    createdAtMillis = 100L,
                ),
            )

            // What the next load sees: the breast line is gone, the
            // drumsticks are on the list marked as the swap, the totals
            // moved by the difference, and the memory is recorded.
            val rows = db.groceryDao().getForPlan("plan-1")
            assertEquals(2, rows.size)
            assertNull(rows.firstOrNull { it.itemId == "chicken-breast" })
            val swapRow = rows.single { it.itemId == "user-chicken-drumsticks" }
            assertEquals("chicken-breast", swapRow.substitutedForItemId)
            assertEquals(499, swapRow.packPriceCents)
            assertEquals(688, db.planDao().getById("plan-1")!!.cashNeededCents)
            val record = db.scanSubstitutionDao().getAll().single()
            assertEquals("chicken-breast", record.originalItemId)
            assertEquals("sheet-pan-chicken", record.recipeId)
            assertEquals(
                "user-chicken-drumsticks",
                db.barcodeLinkDao().getByBarcode("0023456789012")!!.itemId,
            )
            // The breast's book price was never written by any of this:
            // no price_items row for it exists in this database at all,
            // because the substitution only upserts the substitute.
            assertNull(db.priceItemDao().getById("chicken-breast"))
            assertEquals(499, db.priceItemDao().getById("user-chicken-drumsticks")!!.packPriceCents)
        } finally {
            db.close()
        }
    }
}
