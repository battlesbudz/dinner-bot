package com.battlesbudz.dinnerbot.feature.barcode

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * v0.6.3: the OFF product response read as a whole — name, pack
 * quantity, serving, and the label nutriments. Fixtures mimic the real
 * api/v2 shape, including nutriments published as strings and a salt
 * figure standing in for sodium, because OFF does both in the wild.
 */
class OffResponseParserTest {

    private val pastaBody = """
        {"code":"3017620422003","status":1,"status_verbose":"product found",
         "product":{
           "product_name":"Spaghetti",
           "product_name_en":"Spaghetti",
           "quantity":"500 g",
           "product_quantity":500,
           "product_quantity_unit":"g",
           "serving_size":"80 g",
           "serving_quantity":80,
           "serving_quantity_unit":"g",
           "nutriments":{
             "energy-kcal_100g":350,
             "energy-kcal_serving":280,
             "proteins_100g":12.0,
             "proteins_serving":9.6,
             "fat_100g":2.0,
             "carbohydrates_100g":70.0,
             "sugars_100g":3.0,
             "fiber_100g":3.3,
             "salt_100g":0.0125,
             "saturated-fat_100g":0.4,
             "cholesterol_100g":0.0
           }
         }}
    """.trimIndent()

    @Test fun parsesNameQuantityAndServing() {
        val product = OffResponseParser.parseProduct(pastaBody)!!
        assertEquals("Spaghetti", product.name)
        assertEquals("500 g", product.quantityText)
        assertEquals(500.0, product.productQuantity!!, 0.001)
        assertEquals("g", product.productQuantityUnit)
        assertEquals("80 g", product.servingSizeText)
        assertEquals(80.0, product.servingGrams!!, 0.001)
    }

    @Test fun parsesPer100gAndPerServingFacts() {
        val product = OffResponseParser.parseProduct(pastaBody)!!
        val per100 = product.per100g!!
        assertEquals(350.0, per100.caloriesKcal!!, 0.001)
        assertEquals(12.0, per100.proteinG!!, 0.001)
        assertEquals(70.0, per100.carbohydrateG!!, 0.001)
        assertEquals(3.3, per100.fiberG!!, 0.001)
        // Salt 0.0125 g per 100 g converts to sodium at 40% by weight:
        // 5 mg. OFF published no sodium key, so the salt figure rules.
        assertEquals(5.0, per100.sodiumMg!!, 0.001)
        assertEquals(0.4, per100.saturatedFatG!!, 0.001)
        assertEquals(0.0, per100.cholesterolMg!!, 0.001)
        val perServing = product.perServing!!
        assertEquals(280.0, perServing.caloriesKcal!!, 0.001)
        assertEquals(9.6, perServing.proteinG!!, 0.001)
    }

    @Test fun sodiumKeyBeatsTheSaltDerivation() {
        val body = """
            {"status":1,"product":{"product_name":"Soup",
             "nutriments":{"sodium_100g":0.36,"salt_100g":0.5,"energy-kcal_100g":45}}}
        """.trimIndent()
        val product = OffResponseParser.parseProduct(body)!!
        // 0.36 g sodium = 360 mg; the salt figure would derive
        // 200 mg, so 360 proves the explicit key won.
        assertEquals(360.0, product.per100g!!.sodiumMg!!, 0.001)
    }

    @Test fun energyFallsBackToKilojoules() {
        val body = """
            {"status":1,"product":{"product_name":"Juice",
             "nutriments":{"energy_100g":176.0,"proteins_100g":0.5}}}
        """.trimIndent()
        val product = OffResponseParser.parseProduct(body)!!
        // 176 kJ / 4.184 = 42.07 kcal.
        assertEquals(42.07, product.per100g!!.caloriesKcal!!, 0.01)
    }

    @Test fun nutrimentsPublishedAsStringsStillParse() {
        val body = """
            {"status":1,"product":{"product_name":"Jam",
             "nutriments":{"energy-kcal_100g":"240","sugars_100g":"55.5"}}}
        """.trimIndent()
        val product = OffResponseParser.parseProduct(body)!!
        assertEquals(240.0, product.per100g!!.caloriesKcal!!, 0.001)
        assertEquals(55.5, product.per100g!!.sugarsG!!, 0.001)
    }

    @Test fun unknownProductParsesToNull() {
        assertNull(OffResponseParser.parseProduct("""{"status":0,"status_verbose":"product not found"}"""))
        assertNull(OffResponseParser.parseProduct("not json at all"))
    }

    @Test fun productWithNoUsableFieldsParsesToNull() {
        assertNull(OffResponseParser.parseProduct("""{"status":1,"product":{}}"""))
    }

    @Test fun servingStatedOnlyInMillilitersGivesNoGramBridge() {
        val body = """
            {"status":1,"product":{"product_name":"Milk","serving_size":"240 ml",
             "serving_quantity":240,"serving_quantity_unit":"ml",
             "nutriments":{"energy-kcal_100g":64}}}
        """.trimIndent()
        val product = OffResponseParser.parseProduct(body)!!
        assertNull(product.servingGrams)
        assertEquals("240 ml", product.servingSizeText)
    }

    @Test fun servingGramsReadFromTheServingTextWhenNoQuantityFields() {
        val body = """
            {"status":1,"product":{"product_name":"Eggs","serving_size":"1 egg (50 g)",
             "nutriments":{"energy-kcal_100g":155}}}
        """.trimIndent()
        val product = OffResponseParser.parseProduct(body)!!
        assertEquals(50.0, product.servingGrams!!, 0.001)
    }

    @Test fun labelNutritionIsNullWithoutPer100gFacts() {
        val body = """{"status":1,"product":{"product_name":"Mystery","quantity":"1 kg"}}"""
        val product = OffResponseParser.parseProduct(body)!!
        assertNull(product.per100g)
        assertNull(product.labelNutrition("item-1", "123", 5L))
    }
}
