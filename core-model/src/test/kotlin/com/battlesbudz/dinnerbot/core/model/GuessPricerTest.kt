package com.battlesbudz.dinnerbot.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Battles's correction (2026-10-24): importing a recipe puts its
 * unmatched ingredients IN the price book — a real item at the guessed
 * price, marked as a guess until it is shopped. Linking is the same
 * pass at import, on a from-now-on edit, and in the upgrade backfill,
 * and later imports dedupe against the items earlier imports created.
 */
class GuessPricerTest {

    private fun item(id: String, name: String, packPrice: Int, store: String = "aldi") =
        PriceItem(
            id = id, name = name, category = "Produce", homeStore = store,
            packDescription = "pack", packQuantity = Quantity(1.0, MeasureUnit.POUND),
            packPriceCents = packPrice, pricePerUnitCents = packPrice.toDouble(),
            checkedDate = "2026-10-01", source = "test",
        )

    private val yellowOnion = item("yellow-onion", "Yellow onion", 129)
    private val redOnion = item("red-onion", "Red onion", 149)
    private val eggs = item("eggs", "Eggs", 185)
    private val book = listOf(yellowOnion, redOnion, eggs)

    private fun asWritten(name: String, amount: Double = 1.0, unit: MeasureUnit = MeasureUnit.POUND) =
        RecipeIngredient(pantryItem = name, quantity = Quantity(amount, unit))

    private fun priced(itemId: String) =
        RecipeIngredient(priceItemId = itemId, quantity = Quantity(1.0, MeasureUnit.POUND))

    private fun recipe(id: String, vararg ingredients: RecipeIngredient) = Recipe(
        id = id, title = id, servings = 4, activeMinutes = 10, cuisine = "Test",
        mainProtein = "Test", ingredients = ingredients.toList(),
        steps = listOf("Cook."), origin = RecipeOrigin.IMPORTED,
    )

    @Test fun anUnmatchedIngredientBecomesAGuessPricedBookItem() {
        val soup = recipe("soup", asWritten("White onion", 2.0), priced("eggs"))
        val (linked, created) = GuessPricer.linkRecipe(soup, book, "2026-10-24")
        assertEquals(1, created.size)
        val item = created.single()
        // Median of the two onion equivalents, (129 + 149 + 1) / 2.
        assertEquals(139, item.packPriceCents)
        assertTrue(item.guessPriced)
        assertFalse(item.userCorrected)
        assertEquals("user-white-onion", item.id)
        assertEquals("White onion", item.name)
        assertEquals("Produce", item.category)
        assertEquals("aldi", item.homeStore)
        // The pack is the ingredient's own parsed amount.
        assertEquals(Quantity(2.0, MeasureUnit.POUND), item.packQuantity)
        assertEquals(69.5, item.pricePerUnitCents!!, 0.001)
        assertEquals("2026-10-24", item.checkedDate)
        // The recipe now points at the item; nothing stays as written.
        val ingredient = linked.ingredients.first()
        assertEquals("user-white-onion", ingredient.priceItemId)
        assertNull(ingredient.pantryItem)
        assertEquals(Quantity(2.0, MeasureUnit.POUND), ingredient.quantity)
    }

    @Test fun noEquivalentsFallsBackToTheWholeBookMedian() {
        val exotic = recipe("exotic", asWritten("Saffron", 0.5, MeasureUnit.OUNCE))
        val (_, created) = GuessPricer.linkRecipe(exotic, book, "2026-10-24")
        // Whole-book median of 129, 149, 185.
        assertEquals(149, created.single().packPriceCents)
        assertEquals(Quantity(0.5, MeasureUnit.OUNCE), created.single().packQuantity)
    }

    @Test fun anExactNameMatchLinksInsteadOfCreating() {
        val soup = recipe("soup", asWritten("yellow ONION"))
        val (linked, created) = GuessPricer.linkRecipe(soup, book, "2026-10-24")
        assertTrue(created.isEmpty())
        assertEquals("yellow-onion", linked.ingredients.single().priceItemId)
        assertNull(linked.ingredients.single().pantryItem)
    }

    @Test fun laterImportsDedupeAgainstItemsEarlierImportsCreated() {
        val first = recipe("first", asWritten("White onion"))
        val second = recipe("second", asWritten("White onion"), asWritten("Shallots"))
        val result = GuessPricer.linkRecipes(
            listOf(
                ImportedRecipe(first, null, 1L),
                ImportedRecipe(second, null, 2L),
            ),
            book, "2026-10-24",
        )
        // White onion created once; shallots is its own item.
        assertEquals(2, result.itemsToAdd.size)
        val firstLink = result.recipes[0].recipe.ingredients.single().priceItemId
        val secondLink = result.recipes[1].recipe.ingredients.first().priceItemId
        assertEquals(firstLink, secondLink)
        assertEquals("user-white-onion", firstLink)
    }

    @Test fun dedupeAlsoAppliesWithinOneRecipe() {
        val soup = recipe("soup", asWritten("White onion"), asWritten("white onion"))
        val (linked, created) = GuessPricer.linkRecipe(soup, book, "2026-10-24")
        assertEquals(1, created.size)
        assertEquals(
            listOf("user-white-onion", "user-white-onion"),
            linked.ingredients.map { it.priceItemId },
        )
    }

    @Test fun alreadyLinkedRecipesPassThroughUntouched() {
        val done = recipe("done", priced("eggs"))
        val record = ImportedRecipe(done, "https://example.com/x", 7L)
        val result = GuessPricer.linkRecipes(listOf(record), book, "2026-10-24")
        assertTrue(result.itemsToAdd.isEmpty())
        assertEquals(listOf(record), result.recipes)
    }

    @Test fun aSlugCollisionWithADifferentItemGetsASuffix() {
        val taken = item("user-white-onion", "White onions (bagged)", 210)
        val soup = recipe("soup", asWritten("White onion"))
        val (_, created) = GuessPricer.linkRecipe(soup, book + taken, "2026-10-24")
        assertEquals("user-white-onion-2", created.single().id)
    }
}
