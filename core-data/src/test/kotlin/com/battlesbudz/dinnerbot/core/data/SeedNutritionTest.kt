package com.battlesbudz.dinnerbot.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Phase V2-6: the nutrition seed parses and round-trips through the entity mapper. */
class SeedNutritionTest {
    private val nutritionJson = """
        {"version":1,"source":"USDA FoodData Central","capturedDate":"2026-10-22","entries":[
          {"itemId":"eggs","pantryItem":false,"fdcId":171287,
           "fdcDescription":"Egg, whole, raw, fresh","fdcDataType":"SR Legacy",
           "closestMatch":false,"source":"USDA FoodData Central (fdcId 171287)",
           "per100g":{"caloriesKcal":143.0,"proteinG":12.56,"fatG":9.51,"carbohydrateG":0.72,
                      "fiberG":0.0,"sugarsG":null,"sodiumMg":142.0,"saturatedFatG":3.1,
                      "cholesterolMg":372.0},
           "gramWeights":{"COUNT":50.0},
           "gramWeightSource":"USDA FoodData Central food portions for fdcId 171287"},
          {"itemId":"salt","pantryItem":true,"fdcId":173468,
           "fdcDescription":"Salt, table","fdcDataType":"SR Legacy",
           "closestMatch":false,"source":"USDA FoodData Central (fdcId 173468)",
           "per100g":{"caloriesKcal":0.0,"proteinG":0.0,"fatG":0.0,"carbohydrateG":0.0,
                      "fiberG":0.0,"sugarsG":null,"sodiumMg":38758.0,"saturatedFatG":0.0,
                      "cholesterolMg":0.0},
           "gramWeights":{"TEASPOON":6.0},
           "gramWeightSource":"USDA FoodData Central food portions for fdcId 173468"}
        ],"incomplete":[]}
    """.trimIndent()

    @Test fun parsesNutritionFile() {
        val file = SeedDataParser.parseNutrition(nutritionJson)
        assertEquals(2, file.entries.size)
        assertTrue(file.incomplete.isEmpty())
        val eggs = file.entries[0]
        assertEquals(171287L, eggs.fdcId)
        assertEquals(50.0, eggs.gramWeights["COUNT"]!!, 0.001)
        // A field USDA does not publish stays null; it is never filled in.
        assertNull(eggs.per100g.sugarsG)
        assertTrue(file.entries[1].pantryItem)
    }

    @Test fun mapperRoundTripsAnEntry() {
        val entry = SeedDataParser.parseNutrition(nutritionJson).entries[0]
        val entity = with(SeedMapper) { entry.toEntity() }
        val restored = with(SeedMapper) { entity.toModel() }
        assertEquals(entry, restored)
    }
}
