package com.battlesbudz.dinnerbot.core.data

import com.battlesbudz.dinnerbot.core.model.MeasureUnit
import com.battlesbudz.dinnerbot.core.model.Store
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SeedDataParserTest {
    private val priceJson = """
        {"version":1,"items":[
          {"id":"ground-turkey","name":"Ground turkey","category":"Meat","homeStore":"WALMART",
           "packDescription":"Sold by the pound","packQuantity":{"amount":1.0,"unit":"POUND"},
           "packPriceCents":198,"pricePerUnitCents":198.0,"checkedDate":"2026-10-22",
           "source":"Battles, household actual","userCorrected":false}
        ]}
    """.trimIndent()

    private val recipeJson = """
        {"version":1,"recipes":[
          {"id":"r1","title":"Test Dinner","servings":4,"activeMinutes":10,"cuisine":"American",
           "mainProtein":"Ground turkey","dietTags":["Gluten-free"],
           "ingredients":[
             {"priceItemId":"ground-turkey","quantity":{"amount":1.0,"unit":"POUND"}},
             {"pantryItem":"Salt","quantity":{"amount":1.0,"unit":"TEASPOON"}}
           ],
           "steps":["Cook it."]}
        ]}
    """.trimIndent()

    @Test fun parsesPriceBook() {
        val items = SeedDataParser.parsePriceBook(priceJson)
        assertEquals(1, items.size)
        assertEquals(Store.WALMART, items[0].homeStore)
        assertEquals(198, items[0].packPriceCents)
        assertEquals(MeasureUnit.POUND, items[0].packQuantity.unit)
    }

    @Test fun parsesRecipes() {
        val recipes = SeedDataParser.parseRecipes(recipeJson)
        assertEquals(1, recipes.size)
        assertEquals(2, recipes[0].ingredients.size)
        assertTrue(recipes[0].ingredients[1].isPantry)
    }

    @Test fun parsesPriceBookFreezeNote() {
        val json = """
            {"version":1,"items":[
              {"id":"pork-tenderloin","name":"Pork tenderloin","category":"Meat","homeStore":"SAMS_CLUB",
               "packDescription":"about 9 lb","packQuantity":{"amount":9.0,"unit":"POUND"},
               "packPriceCents":2168,"pricePerUnitCents":240.9,"checkedDate":"2026-10-22",
               "source":"Battles, household actual","userCorrected":false,
               "freezeNote":"Bag flat and freeze."}
            ]}
        """.trimIndent()
        val items = SeedDataParser.parsePriceBook(json)
        assertEquals("Bag flat and freeze.", items[0].freezeNote)
    }

    @Test fun parsesDietPresets() {
        val json = """
            {"version":1,"presets":[
              {"id":"dairy-free","name":"Dairy-free","summary":"Drops dairy.",
               "wontEatEntries":["cheese","milk"]},
              {"id":"vegetarian","name":"Vegetarian","summary":"Tagged only.",
               "requiredTag":"Vegetarian"}
            ]}
        """.trimIndent()
        val presets = SeedDataParser.parseDietPresets(json)
        assertEquals(2, presets.size)
        assertEquals(listOf("cheese", "milk"), presets[0].wontEatEntries)
        assertEquals("Vegetarian", presets[1].requiredTag)
        assertTrue(presets[1].wontEatEntries.isEmpty())
    }
}
