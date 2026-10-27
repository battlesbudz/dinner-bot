package com.battlesbudz.dinnerbot.feature.`import`

import com.battlesbudz.dinnerbot.core.model.Costing
import com.battlesbudz.dinnerbot.core.model.ImportedRecipe
import com.battlesbudz.dinnerbot.core.model.MeasureUnit
import com.battlesbudz.dinnerbot.core.model.PriceItem
import com.battlesbudz.dinnerbot.core.model.Quantity
import com.battlesbudz.dinnerbot.core.model.Recipe
import com.battlesbudz.dinnerbot.core.model.RecipeOrigin
import com.battlesbudz.dinnerbot.core.model.Store
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase RI-2, plan section 5.5: the review's readings. Each imported
 * line becomes exactly one reading (matched, or as-written at no
 * invented price); suggestions are offered, never applied; and the
 * review's pantry coverage is computed through the same Costing the
 * weekly plan uses, pinned equal here.
 */
class ImportReviewTest {

    private fun item(
        id: String, name: String,
        pack: Quantity = Quantity(1.0, MeasureUnit.POUND),
    ) = PriceItem(
        id = id, name = name, category = "Test",
        homeStore = "aldi", packDescription = "pack",
        packQuantity = pack, packPriceCents = 100,
        pricePerUnitCents = 100.0, checkedDate = "2026-10-24", source = "test",
    )

    private val book = listOf(
        item("flour", "Flour"),
        item("yellow-onions", "Yellow onions"),
        item("russet-potatoes", "Russet potatoes"),
        item("pasta-sauce", "Pasta sauce", pack = Quantity(1.0, MeasureUnit.COUNT)),
        item("chicken-breast", "Chicken breast"),
        item("ground-turkey", "Ground turkey"),
        item("salmon-fillets", "Salmon fillets"),
        item("shrimp", "Shrimp"),
    )
    private val itemsById = book.associateBy { it.id }

    private fun draftOf(vararg lines: String) = ImportedDraft(
        title = "Test dinner", servings = 4, activeMinutes = 20,
        cuisine = null,
        ingredients = lines.flatMap { IngredientLineParser.parse(it) },
        steps = listOf("Cook."),
        sourceLink = null,
    )

    private fun rows(draft: ImportedDraft, pantry: Set<String> = emptySet()) =
        draft.ingredients.map { ImportReview.rowFor(it, book, pantry) }

    @Test fun matchedLineBecomesAPricedIngredientWithItsQuantity() {
        val row = rows(draftOf("2 cups flour")).single()
        assertEquals("flour", row.ingredient.priceItemId)
        assertEquals(Quantity(2.0, MeasureUnit.CUP), row.ingredient.quantity)
        assertTrue(row.ingredient.essential)
        assertNull(row.suggestion)
    }

    @Test fun unmatchedLineStaysAsWrittenPricedAtNothing() {
        val row = rows(draftOf("2 spring onions, sliced")).single()
        assertEquals("spring onions", row.ingredient.pantryItem)
        assertNull(row.ingredient.priceItemId)
        // Costing treats an as-written ingredient as no cost and no
        // invented nutrition: it aggregates under its own name only.
        val (_, unpricedNames) = Costing.aggregateNeeds(
            listOf(Recipe(
                id = "x", title = "x", servings = 4, activeMinutes = 1,
                cuisine = "T", mainProtein = "Other",
                ingredients = listOf(row.ingredient), steps = listOf("Cook."),
                origin = RecipeOrigin.IMPORTED,
            )),
            4, itemsById,
        )
        assertEquals(listOf("spring onions"), unpricedNames)
    }

    @Test fun suggestionIsOfferedAndAppliesOnlyOnTap() {
        var row = rows(draftOf("1 onion, diced")).single()
        assertEquals("yellow-onions", row.suggestion?.id)
        assertEquals("onion", row.ingredient.pantryItem)
        row = ImportReview.applySuggestion(row, book, emptySet())
        assertEquals("yellow-onions", row.ingredient.priceItemId)
        assertEquals(Quantity(1.0, MeasureUnit.COUNT), row.ingredient.quantity)
        assertNull(row.suggestion)
    }

    @Test fun containerAmountOnAMatchedSinglePackBecomesAQuantitySuggestion() {
        val row = rows(draftOf("2 jars pasta sauce")).single()
        assertEquals("pasta-sauce", row.ingredient.priceItemId)
        // Not applied: the row still holds the parser's count reading.
        assertEquals(Quantity(2.0, MeasureUnit.COUNT), row.ingredient.quantity)
        assertEquals(Quantity(2.0, MeasureUnit.COUNT), row.quantitySuggestion)
        val applied = ImportReview.applyQuantitySuggestion(row, book, emptySet())
        assertNull(applied.quantitySuggestion)
        assertEquals(Quantity(2.0, MeasureUnit.COUNT), applied.ingredient.quantity)
    }

    @Test fun pantryCoveredLineReadsCoveredAndCooksUnchanged() {
        val row = rows(draftOf("2 pounds russet potatoes"), pantry = setOf("Potatoes")).single()
        assertTrue(row.ingredient.priceItemId == "russet-potatoes")
        assertTrue(row.pantryCovered)
    }

    @Test fun asWrittenLineCoveredByAGenericPantryNameReadsCovered() {
        // Battles's D5 example: her pantry holds generic "Potatoes";
        // the recipe's specific potatoes read as covered by it.
        val row = ImportReview.rowFor(
            IngredientLineParser.parse("2 pounds waxy potatoes").single(),
            book, setOf("Potatoes"),
        )
        assertEquals("waxy potatoes", row.ingredient.pantryItem)
        assertTrue(row.pantryCovered)
    }

    @Test fun reviewCoverageEqualsTheWeeklyPlansCoverage() {
        // Plan section 5.4's rule: the review and the weekly list
        // cannot disagree, because both read coverage through Costing.
        val draft = draftOf(
            "2 pounds russet potatoes",
            "2 pounds waxy potatoes",
            "1 cup flour",
        )
        val pantry = setOf("Potatoes")
        val reviewRows = rows(draft, pantry)
        val recipe = ImportReview.buildRecipe(
            id = "imported-test", title = "Test dinner", servings = 4,
            activeMinutes = 20, cuisine = "", steps = listOf("Cook."),
            ingredients = reviewRows.map { it.ingredient },
            origin = RecipeOrigin.IMPORTED, priceItems = book,
        )
        val coveredIds = Costing.pantryCoveredItemIds(pantry, book)
        val planCoveredFlags = recipe.ingredients.map { ingredient ->
            val id = ingredient.priceItemId
            (id != null && id in coveredIds) ||
                Costing.pantryNameCoversName(
                    pantry,
                    id?.let { itemsById[it]?.name } ?: ingredient.pantryItem ?: "",
                )
        }
        assertEquals(reviewRows.map { it.pantryCovered }, planCoveredFlags)
        assertEquals(listOf(true, true, false), reviewRows.map { it.pantryCovered })
    }

    private fun build(draft: ImportedDraft, title: String = "Test dinner"): Recipe =
        ImportReview.buildRecipe(
            id = "imported-test", title = title, servings = 4,
            activeMinutes = 20, cuisine = "", steps = listOf("Cook."),
            ingredients = rows(draft).map { it.ingredient },
            origin = RecipeOrigin.IMPORTED, priceItems = book,
        )

    @Test fun mainProteinDerivesFromTheFirstMatchedEssentialProtein() {
        val draft = draftOf("1 cup flour", "1 pound ground turkey", "2 pounds chicken breast")
        assertEquals("Ground turkey", build(draft).mainProtein)
    }

    @Test fun mainProteinFallsBackToOther() {
        assertEquals("Other", build(draftOf("1 cup flour")).mainProtein)
    }

    // Phase C2: the seed book gained a Seafood category; imports matched
    // to those items classify as their protein, not "Other".
    @Test fun mainProteinRecognizesSeafoodItems() {
        assertEquals("Salmon", build(draftOf("1 pound salmon fillets")).mainProtein)
        assertEquals("Shrimp", build(draftOf("1 pound shrimp")).mainProtein)
    }

    @Test fun duplicateSourceFindsTheEarlierImport() {
        val earlier = ImportedRecipe(
            recipe = Recipe(
                id = "imported-1-x", title = "Soup", servings = 4, activeMinutes = 10,
                cuisine = "Test", mainProtein = "Other",
                ingredients = rows(draftOf("1 cup flour")).map { it.ingredient },
                steps = listOf("Cook."), origin = RecipeOrigin.IMPORTED,
            ),
            sourceLink = "https://example.com/soup", createdAtMillis = 5L,
        )
        assertEquals(
            "imported-1-x",
            ImportReview.findDuplicate(listOf(earlier), "https://example.com/soup")?.recipe?.id,
        )
        assertNull(ImportReview.findDuplicate(listOf(earlier), "https://example.com/other"))
        assertNull(ImportReview.findDuplicate(listOf(earlier), null))
    }

    @Test fun builtRecipeCarriesIdsOriginAndNoInventedTags() {
        val draft = draftOf("1 cup flour")
        val built = ImportReview.buildRecipe(
            id = com.battlesbudz.dinnerbot.core.model.ImportedRecipeIds.newId(
                RecipeOrigin.IMPORTED, "My Soup", 7L,
            ),
            title = "My Soup", servings = 2, activeMinutes = 15,
            cuisine = "Italian", steps = listOf("Boil."),
            ingredients = rows(draft).map { it.ingredient },
            origin = RecipeOrigin.IMPORTED, priceItems = book,
        )
        assertTrue(built.id.startsWith("imported-"))
        assertEquals(RecipeOrigin.IMPORTED, built.origin)
        assertEquals("My Soup", built.title)
        assertEquals(2, built.servings)
        assertEquals("Italian", built.cuisine)
        assertTrue(built.dietTags.isEmpty())
        assertFalse(built.ingredients.isEmpty())
    }
}
