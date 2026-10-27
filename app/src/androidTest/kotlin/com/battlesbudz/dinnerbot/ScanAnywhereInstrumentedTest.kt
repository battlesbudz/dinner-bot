package com.battlesbudz.dinnerbot

import android.Manifest
import androidx.activity.ComponentActivity
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.battlesbudz.dinnerbot.core.model.LabelNutrition
import com.battlesbudz.dinnerbot.core.model.MeasureUnit
import com.battlesbudz.dinnerbot.core.model.NutritionValues
import com.battlesbudz.dinnerbot.core.model.PriceItem
import com.battlesbudz.dinnerbot.core.model.Quantity
import com.battlesbudz.dinnerbot.core.model.RecipeIngredient
import com.battlesbudz.dinnerbot.core.model.Store
import com.battlesbudz.dinnerbot.feature.barcode.OffProduct
import com.battlesbudz.dinnerbot.feature.pricebook.AddPriceItemDialog
import com.battlesbudz.dinnerbot.feature.recipes.IngredientEditorDialog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * v0.6.3, the scan-anywhere build, as far as an emulator can prove it
 * (the camera decode itself is BarcodeInstrumentedTest's ground; here
 * the scanner surface and the Open Food Facts lookup are fixtured at
 * the form's own seams):
 *
 * - The ingredient picker's new-ingredient entry is a real filled
 *   button labeled "+ New ingredient" (Battles: the old entry read
 *   like a warning).
 * - Scanning inside the shared add-ingredient form prefills the name
 *   and the pack from the OFF product, and confirming hands back the
 *   created item, the barcode to link to it, and the label facts to
 *   store on it.
 * - Scanning a barcode the book already knows completes with the
 *   existing item — no duplicate is created — and its facts refresh.
 */
@RunWith(AndroidJUnit4::class)
class ScanAnywhereInstrumentedTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Before fun grantCamera() {
        // The form asks for the camera only when Scan is tapped; grant
        // it up front so the tap goes straight to the (fixtured)
        // scanner, like a phone where she already allowed it.
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.uiAutomation.grantRuntimePermission(
            instrumentation.targetContext.packageName,
            Manifest.permission.CAMERA,
        )
    }

    private val eggsItem = PriceItem(
        id = "eggs", name = "Eggs", category = "Dairy & Eggs",
        homeStore = "aldi", packDescription = "12 ct carton",
        packQuantity = Quantity(12.0, MeasureUnit.COUNT),
        packPriceCents = 289, pricePerUnitCents = 24.08,
        checkedDate = "2026-10-25", source = "seed",
    )

    private val pastaProduct = OffProduct(
        name = "Spaghetti",
        quantityText = "500 g",
        productQuantity = 500.0,
        productQuantityUnit = "g",
        servingSizeText = "80 g",
        servingQuantity = 80.0,
        servingQuantityUnit = "g",
        per100g = NutritionValues(caloriesKcal = 350.0, proteinG = 12.0, sodiumMg = 5.0),
        perServing = NutritionValues(caloriesKcal = 280.0, proteinG = 9.6),
    )

    private fun fieldText(tag: String): String =
        composeRule.onNodeWithTag(tag).fetchSemanticsNode()
            .config[SemanticsProperties.EditableText].text

    @Test fun pickerShowsARealNewIngredientButton() {
        composeRule.setContent {
            IngredientEditorDialog(
                recipeTitle = "Test dinner",
                initialIngredients = listOf(
                    RecipeIngredient(
                        priceItemId = "eggs", quantity = Quantity(2.0, MeasureUnit.COUNT),
                    ),
                ),
                priceItems = listOf(eggsItem),
                onDismiss = {},
                onSave = {},
            )
        }
        composeRule.onNodeWithTag("ingredient-add").performClick()
        composeRule.onNodeWithTag("picker-add-new").assertIsDisplayed()
        composeRule.onNodeWithText("+ New ingredient").assertIsDisplayed()
        // And it is a button that opens the shared form.
        composeRule.onNodeWithTag("picker-add-new").performClick()
        composeRule.onNodeWithTag("add-item-name").assertIsDisplayed()
        composeRule.onNodeWithTag("add-item-scan").assertIsDisplayed()
    }

    @Test fun scanInTheAddFormPrefillsAndCapturesFactsAndLink() {
        var created: PriceItem? = null
        var linkedBarcode: String? = null
        var linkedItemId: String? = null
        var facts: LabelNutrition? = null
        composeRule.setContent {
            AddPriceItemDialog(
                book = listOf(eggsItem),
                today = "2026-10-25",
                onConfirm = { created = it },
                onDismiss = {},
                onBarcodeLinked = { code, itemId ->
                    linkedBarcode = code
                    linkedItemId = itemId
                },
                onLabelFacts = { _, captured -> facts = captured },
                productLookup = { pastaProduct },
                scannerContent = { onScanned, _ ->
                    LaunchedEffect(Unit) { onScanned("3017620422003") }
                },
            )
        }
        composeRule.onNodeWithTag("add-item-scan").performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) { fieldText("add-item-name") == "Spaghetti" }
        assertEquals("500", fieldText("add-item-pack-amount"))
        assertEquals("500 g · serving 80 g", fieldText("add-item-pack-description"))
        composeRule.onNodeWithTag("add-item-price").performScrollTo().performTextInput("2.50")
        composeRule.onNodeWithTag("add-item-confirm").performClick()
        assertNotNull("confirming must build the item", created)
        val item = created!!
        assertEquals("Spaghetti", item.name)
        assertEquals(Quantity(500.0, MeasureUnit.GRAM), item.packQuantity)
        assertEquals(250, item.packPriceCents)
        assertEquals("3017620422003", linkedBarcode)
        assertEquals(item.id, linkedItemId)
        assertNotNull("the label facts must be captured for the item", facts)
        assertEquals("3017620422003", facts!!.barcode)
        assertEquals(item.id, facts!!.itemId)
        assertEquals(350.0, facts!!.per100g.caloriesKcal!!, 0.001)
        assertEquals(80.0, facts!!.servingGrams!!, 0.001)
    }

    @Test fun scanOfAKnownBarcodeUsesTheExistingItemAndRefreshesItsFacts() {
        var usedExisting: PriceItem? = null
        var refreshed: LabelNutrition? = null
        var confirmCalled = false
        composeRule.setContent {
            AddPriceItemDialog(
                book = listOf(eggsItem),
                today = "2026-10-25",
                onConfirm = { confirmCalled = true },
                onDismiss = {},
                existingItemForBarcode = { code ->
                    if (code == "0021234500000") eggsItem else null
                },
                onExistingItemScanned = { usedExisting = it },
                onLabelFacts = { _, captured -> refreshed = captured },
                productLookup = {
                    pastaProduct.copy(
                        name = "Eggs",
                        per100g = NutritionValues(caloriesKcal = 155.0, proteinG = 13.0),
                    )
                },
                scannerContent = { onScanned, _ ->
                    LaunchedEffect(Unit) { onScanned("0021234500000") }
                },
            )
        }
        composeRule.onNodeWithTag("add-item-scan").performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) { usedExisting != null }
        assertEquals("eggs", usedExisting!!.id)
        composeRule.waitUntil(timeoutMillis = 10_000) { refreshed != null }
        assertEquals("eggs", refreshed!!.itemId)
        assertEquals(155.0, refreshed!!.per100g.caloriesKcal!!, 0.001)
        assertFalse("no duplicate item may be confirmed", confirmCalled)
    }
}
