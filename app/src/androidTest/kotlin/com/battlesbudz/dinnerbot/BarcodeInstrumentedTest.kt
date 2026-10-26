package com.battlesbudz.dinnerbot

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.room.Room
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.battlesbudz.dinnerbot.core.data.DinnerBotDatabase
import com.battlesbudz.dinnerbot.core.data.SeedLoader
import com.battlesbudz.dinnerbot.core.data.SettingsEntity
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Phase V2-5 barcode scanning, as far as an emulator can prove it:
 *
 * - ML Kit's bundled on-device decoder reads a generated UPC-A image,
 *   so the decode path itself is exercised with no camera and no
 *   network. (Reading a real package's code on a real phone stays a
 *   device check for Andrea's phone, as the phase plan states.)
 * - On the List tab, the scan entry asks for CAMERA only when tapped;
 *   denying it leaves the list fully usable with the fallback note;
 *   granting it opens the scanner surface, and Cancel closes it.
 */
@RunWith(AndroidJUnit4::class)
class BarcodeInstrumentedTest {

    @get:Rule
    val composeRule = createEmptyComposeRule()

    private lateinit var context: Context

    @Before fun setUp() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase("dinner-bot.db")
        val db = Room.databaseBuilder(context, DinnerBotDatabase::class.java, "dinner-bot.db")
            .allowMainThreadQueries()
            .build()
        try {
            val priceJson = context.assets.open("pricebook.json").bufferedReader().use { it.readText() }
            val recipesJson = context.assets.open("recipes.json").bufferedReader().use { it.readText() }
            SeedLoader(db).seedIfEmpty(priceJson, recipesJson)
            db.settingsDao().upsert(
                SettingsEntity(
                    id = 1,
                    householdSize = 4,
                    cookNightsJson = "[\"MON\",\"TUE\",\"WED\",\"THU\",\"FRI\"]",
                    weeklyBudgetCents = 100_00,
                    wontEatJson = "[]",
                    dietNotes = "",
                    pantryStaplesJson = "[]",
                    onboardingComplete = true,
                ),
            )
        } finally {
            db.close()
        }
        // Start from a known permission state whatever ran before.
        shell("pm revoke ${context.packageName} android.permission.CAMERA")
    }

    @After fun tearDown() {
        context.deleteDatabase("dinner-bot.db")
    }

    private fun shell(command: String) {
        val fd = InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand(command)
        android.os.ParcelFileDescriptor.AutoCloseInputStream(fd).use { it.readBytes() }
    }

    private fun waitForTag(tag: String) {
        composeRule.waitUntil(timeoutMillis = 15_000) {
            composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun waitForText(text: String) {
        composeRule.waitUntil(timeoutMillis = 15_000) {
            composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test fun mlKitDecodesAGeneratedBarcodeImage() {
        val digits = "012345678905"
        val image = InputImage.fromBitmap(renderUpcA(digits), 0)
        val barcodes = Tasks.await(BarcodeScanning.getClient().process(image), 30, TimeUnit.SECONDS)
        assertEquals(digits, barcodes.firstOrNull()?.rawValue)
    }

    @Test fun scanEntryAsksForCameraOnlyWhenTappedAndDegradesWhenDenied() {
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        ActivityScenario.launch(MainActivity::class.java).use {
            waitForText("Dinner Bot recipes")
            composeRule.onNodeWithTag("tab-List").performClick()
            waitForTag("scan-barcode-button")
            composeRule.onNodeWithTag("scan-barcode-button").assertIsDisplayed()

            // First tap: the system permission ask appears (the
            // permission controller's window, matched by package so
            // the test does not depend on its button wording); backing
            // out of it delivers a denial to the app.
            composeRule.onNodeWithTag("scan-barcode-button").performClick()
            val askAppeared = device.wait(
                Until.hasObject(By.pkg("com.google.android.permissioncontroller")),
                15_000,
            ) || device.wait(Until.hasObject(By.pkg("com.android.permissioncontroller")), 5_000)
            assertTrue("the system permission ask should appear on first scan tap", askAppeared)
            device.pressBack()

            // The list explains the fallback and stays fully usable.
            waitForTag("camera-denied-note")
            composeRule.onNodeWithTag("camera-denied-note").assertIsDisplayed()
            composeRule.onNodeWithTag("scan-barcode-button").assertIsDisplayed()

            // With the permission granted, the same tap opens the scanner.
            shell("pm grant ${context.packageName} android.permission.CAMERA")
            composeRule.onNodeWithTag("scan-barcode-button").performClick()
            waitForTag("barcode-scanner")
            composeRule.onNodeWithTag("barcode-scanner").assertIsDisplayed()
            composeRule.onNodeWithText("Cancel").performClick()
            composeRule.waitUntil(timeoutMillis = 15_000) {
                composeRule.onAllNodesWithTag("barcode-scanner").fetchSemanticsNodes().isEmpty()
            }
        }
    }

    /**
     * Renders a valid UPC-A symbol for [digits] (12 digits including the
     * check digit) as a bitmap: guard bars, L-coded left half, R-coded
     * (complemented) right half, on a white field with quiet zones.
     */
    private fun renderUpcA(digits: String): Bitmap {
        require(digits.length == 12 && digits.all { it.isDigit() })
        val lCodes = mapOf(
            '0' to "0001101", '1' to "0011001", '2' to "0010011", '3' to "0111101",
            '4' to "0100011", '5' to "0110001", '6' to "0101111", '7' to "0111011",
            '8' to "0110111", '9' to "0001011",
        )
        fun rCode(d: Char): String = lCodes.getValue(d).map { if (it == '0') '1' else '0' }.joinToString("")
        val modules = buildString {
            append("101")
            digits.substring(0, 6).forEach { append(lCodes.getValue(it)) }
            append("01010")
            digits.substring(6, 12).forEach { append(rCode(it)) }
            append("101")
        }
        require(modules.length == 95)
        val modulePx = 6
        val quietPx = 60
        val width = modules.length * modulePx + quietPx * 2
        val height = 240
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        val paint = Paint().apply { color = Color.BLACK }
        modules.forEachIndexed { index, bit ->
            if (bit == '1') {
                val left = (quietPx + index * modulePx).toFloat()
                canvas.drawRect(left, 20f, left + modulePx, height - 20f, paint)
            }
        }
        return bitmap
    }
}
