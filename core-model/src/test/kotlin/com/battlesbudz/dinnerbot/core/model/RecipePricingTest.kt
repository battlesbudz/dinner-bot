package com.battlesbudz.dinnerbot.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Priced-ness and placeholder guesses (Phase RI-3). Priced-ness is
 * computed from the book as it stands, never stored; guesses come from
 * equivalent items already in the book and are never persisted as
 * prices (they live only on grocery lines, which these pure functions
 * produce without touching the book).
 */
class RecipePricingTest {

    private fun item(id: String, name: String, packPrice: Int, store: Store = Store.ALDI) = PriceItem(
        id = id, name = name, category = "Test", homeStore = store,
        packDescription = "pack", packQuantity = Quantity(1.0, MeasureUnit.POUND),
        packPriceCents = packPrice, pricePerUnitCents = packPrice.toDouble(),
        checkedDate = "2026-10-24", source = "test",
    )

    private val yellowOnion = item("yellow-onion", "Yellow onion", 129)
    private val redOnion = item("red-onion", "Red onion", 149)
    private val eggs = item("eggs", "Eggs", 185)
    private val book = listOf(yellowOnion, redOnion, eggs)
    private val byId = book.associateBy { it.id }

    private fun priced(itemId: String) =
        RecipeIngredient(priceItemId = itemId, quantity = Quantity(1.0, MeasureUnit.POUND))

    private fun asWritten(name: String) =
        RecipeIngredient(pantryItem = name, quantity = Quantity(1.0, MeasureUnit.POUND))

    private fun recipe(
        id: String,
        origin: RecipeOrigin,
        vararg ingredients: RecipeIngredient,
    ) = Recipe(
        id = id, title = id, servings = 4, activeMinutes = 10, cuisine = "Test",
        mainProtein = "Test", ingredients = ingredients.toList(),
        steps = listOf("Cook."), origin = origin,
    )

    @Test fun ingredientWithBookIdIsPriced() {
        assertTrue(RecipePricing.isIngredientPriced(priced("yellow-onion"), byId))
    }

    @Test fun beefIngredientIsPricedThroughTheTurkeySubstitute() {
        val withBeef = byId + ("ground-beef" to item("ground-beef", "Ground beef", 500)) +
            ("ground-turkey" to item("ground-turkey", "Ground turkey", 198))
        assertTrue(RecipePricing.isIngredientPriced(priced("ground-beef"), withBeef))
    }

    @Test fun asWrittenIngredientPricesWhenTheBookGainsItsExactName() {
        val ingredient = asWritten("Scallions")
        assertFalse(RecipePricing.isIngredientPriced(ingredient, byId))
        val grown = byId + ("scallions" to item("scallions", "Scallions", 99))
        assertTrue(RecipePricing.isIngredientPriced(ingredient, grown))
        // Name equality is normalized: case and spacing do not matter.
        assertTrue(RecipePricing.isIngredientPriced(asWritten("  scallions "), grown))
    }

    @Test fun nearNamesDoNotPriceAnAsWrittenIngredient() {
        // Strict matching (Phase RI-2 ruling): "spring onion" is not
        // "yellow onion", and no head-noun shortcut prices it.
        assertFalse(RecipePricing.isIngredientPriced(asWritten("Spring onion"), byId))
        assertFalse(RecipePricing.isIngredientPriced(asWritten("Onion"), byId))
    }

    @Test fun libraryRecipeNeverCountsAsUnpricedUserRecipe() {
        val library = recipe("lib", RecipeOrigin.LIBRARY, asWritten("Saffron"))
        assertFalse(RecipePricing.isRecipePriced(library, byId))
        assertFalse(RecipePricing.isUnpricedUserRecipe(library, byId))
    }

    @Test fun importedRecipeWithAnUnpricedIngredientCounts() {
        val imported = recipe(
            "imp", RecipeOrigin.IMPORTED, priced("eggs"), asWritten("Smoked paprika"),
        )
        assertTrue(RecipePricing.isUnpricedUserRecipe(imported, byId))
        assertEquals(
            listOf("Smoked paprika"),
            RecipePricing.unpricedIngredients(imported, byId).map { it.pantryItem },
        )
        val manual = recipe("man", RecipeOrigin.MANUAL, asWritten("Smoked paprika"))
        assertTrue(RecipePricing.isUnpricedUserRecipe(manual, byId))
        val pricedImport = recipe("imp2", RecipeOrigin.IMPORTED, priced("eggs"))
        assertFalse(RecipePricing.isUnpricedUserRecipe(pricedImport, byId))
    }

    @Test fun guessPrefersHeadNounEquivalentsMedian() {
        val guess = RecipePricing.guessFor("White onion", book)!!
        // Yellow 129 and red 149 share the head noun "onion"; eggs do not.
        assertEquals(139, guess.cents)
        assertFalse(guess.wholeBookFallback)
        assertEquals(listOf("Yellow onion", "Red onion"), guess.equivalentItemNames)
    }

    @Test fun guessFallsBackToWholeBookMedian() {
        val guess = RecipePricing.guessFor("Saffron", book)!!
        assertEquals(149, guess.cents) // median of 129, 149, 185
        assertTrue(guess.wholeBookFallback)
        assertTrue(guess.equivalentItemNames.isEmpty())
    }

    @Test fun guessIsNullOnlyForAnEmptyBook() {
        assertNull(RecipePricing.guessFor("Saffron", emptyList()))
    }

    @Test fun guessStoreComesFromTheFirstEquivalentElseAldi() {
        val walmartOnion = item("w-onion", "Sweet onion", 159, Store.WALMART)
        assertEquals(
            Store.WALMART,
            RecipePricing.guessStore("White onion", listOf(walmartOnion, eggs)),
        )
        assertEquals(Store.ALDI, RecipePricing.guessStore("Saffron", listOf(eggs)))
    }

    @Test fun guessesNeverWriteIntoTheBook() {
        val before = book.toList()
        RecipePricing.guessFor("White onion", book)
        RecipePricing.guessFor("Saffron", book)
        assertEquals(before, book)
    }
}
