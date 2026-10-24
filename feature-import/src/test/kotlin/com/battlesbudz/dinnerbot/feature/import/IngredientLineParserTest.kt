package com.battlesbudz.dinnerbot.feature.`import`

import com.battlesbudz.dinnerbot.core.model.MeasureUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase RI-2, plan section 5.3: the ingredient line parser against
 * real-world line shapes. A failure must fail toward visible-as-written,
 * never toward dropping or reshaping an ingredient.
 */
class IngredientLineParserTest {

    private fun single(line: String): ParsedIngredient {
        val parsed = IngredientLineParser.parse(line)
        assertEquals("expected one ingredient from: $line", 1, parsed.size)
        return parsed.single()
    }

    @Test fun plainAmountAndUnit() {
        val parsed = single("2 cups all-purpose flour")
        assertEquals(2.0, parsed.amount!!, 0.001)
        assertEquals(MeasureUnit.CUP, parsed.unit)
        assertEquals("all-purpose flour", parsed.name)
        assertTrue(parsed.quantityResolved)
    }

    @Test fun fractionAmount() {
        val parsed = single("1/2 teaspoon salt")
        assertEquals(0.5, parsed.amount!!, 0.001)
        assertEquals(MeasureUnit.TEASPOON, parsed.unit)
        assertEquals("salt", parsed.name)
    }

    @Test fun mixedNumberAmount() {
        val parsed = single("1 1/2 pounds chicken breast")
        assertEquals(1.5, parsed.amount!!, 0.001)
        assertEquals(MeasureUnit.POUND, parsed.unit)
    }

    @Test fun unicodeFractionAmount() {
        val parsed = single("½ cup butter")
        assertEquals(0.5, parsed.amount!!, 0.001)
        assertEquals(MeasureUnit.CUP, parsed.unit)
        assertEquals("butter", parsed.name)
    }

    @Test fun digitPlusUnicodeFraction() {
        val parsed = single("1½ cups milk")
        assertEquals(1.5, parsed.amount!!, 0.001)
        assertEquals(MeasureUnit.CUP, parsed.unit)
    }

    @Test fun rangeResolvesToTheLargerAmount() {
        val parsed = single("1-2 tablespoons olive oil")
        assertEquals(2.0, parsed.amount!!, 0.001)
        assertEquals(MeasureUnit.TABLESPOON, parsed.unit)
        assertEquals("olive oil", parsed.name)
    }

    @Test fun outsideUnitWordIsKeptNeverConverted() {
        val parsed = single("3 cloves garlic, minced")
        assertEquals(3.0, parsed.amount!!, 0.001)
        assertNull(parsed.unit)
        assertEquals("cloves", parsed.unitWord)
        assertEquals("garlic", parsed.name)
        assertTrue(parsed.note!!.contains("minced"))
        assertTrue(!parsed.quantityResolved)
    }

    @Test fun parentheticalQuantityBecomesNote() {
        val parsed = single("1 (14 ounce) can black beans")
        assertEquals(1.0, parsed.amount!!, 0.001)
        assertEquals("can", parsed.unitWord)
        assertEquals("black beans", parsed.name)
        assertTrue(parsed.note!!.contains("14 ounce"))
    }

    @Test fun prepTailAfterCommaBecomesNote() {
        val parsed = single("2 spring onions, finely sliced")
        assertEquals("spring onions", parsed.name)
        assertTrue(parsed.note!!.contains("finely sliced"))
    }

    @Test fun leadingPrepWordsMoveToTheNote() {
        val parsed = single("1 cup finely chopped onion")
        assertEquals("onion", parsed.name)
        assertTrue(parsed.note!!.contains("finely chopped"))
    }

    @Test fun wordsThatChangeTheIngredientAreNeverStripped() {
        assertEquals("fresh cilantro", single("1 bunch fresh cilantro").name)
        assertEquals("dried bay leaves", single("2 dried bay leaves").name)
    }

    @Test fun noQuantityLineStaysWhole() {
        val parsed = single("Salt and pepper to taste")
        assertNull(parsed.amount)
        assertEquals("Salt and pepper", parsed.name)
        assertEquals("to taste", parsed.note)
    }

    @Test fun articleBeforeAUnitWordIsTheAmount() {
        val parsed = single("a pinch of salt")
        assertEquals(1.0, parsed.amount!!, 0.001)
        assertEquals("pinch", parsed.unitWord)
        assertEquals("salt", parsed.name)
    }

    @Test fun fluidOuncesReadAsVolume() {
        val parsed = single("8 fl oz milk")
        assertEquals(8.0, parsed.amount!!, 0.001)
        assertEquals(MeasureUnit.FLUID_OUNCE, parsed.unit)
        assertEquals("milk", parsed.name)
    }

    @Test fun twoIngredientsOnOneLineSplitAtSemicolon() {
        val parsed = IngredientLineParser.parse("1 cup flour; 2 teaspoons sugar")
        assertEquals(2, parsed.size)
        assertEquals("flour", parsed[0].name)
        assertEquals("sugar", parsed[1].name)
    }

    @Test fun twoIngredientsOnOneLineSplitAtAmountAnd() {
        val parsed = IngredientLineParser.parse("1 cup flour and 1 tsp salt")
        assertEquals(2, parsed.size)
        assertEquals("flour", parsed[0].name)
        assertEquals("salt", parsed[1].name)
    }

    @Test fun andInsideANameDoesNotSplit() {
        val parsed = IngredientLineParser.parse("1 cup macaroni and cheese")
        assertEquals(1, parsed.size)
        assertEquals("macaroni and cheese", parsed.single().name)
    }

    @Test fun unreadableLineSurvivesAsWritten() {
        val parsed = single("Juice of 1 lemon")
        assertNull(parsed.amount)
        assertEquals("Juice of 1 lemon", parsed.name)
        assertEquals("Juice of 1 lemon", parsed.rawText)
    }

    @Test fun blankLinesParseToNothing() {
        assertTrue(IngredientLineParser.parse("   ").isEmpty())
    }
}
