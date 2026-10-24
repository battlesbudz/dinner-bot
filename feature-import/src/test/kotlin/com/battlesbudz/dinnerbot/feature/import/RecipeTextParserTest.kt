package com.battlesbudz.dinnerbot.feature.`import`

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase RI-2: pasted-text reading — the guaranteed intake path. The
 * reading is a first pass the review corrects, but its shape rules are
 * pinned here: headers switch sections, field lines are read for their
 * numbers, and nothing is invented.
 */
class RecipeTextParserTest {

    @Test fun headeredTextReadsAllSections() {
        val draft = RecipeTextParser.parse(
            """
            Weeknight Chicken
            Serves 4
            Total time: 35 minutes
            Ingredients
            1 pound chicken breast
            2 spring onions, sliced
            1 teaspoon salt
            Instructions
            Cook the chicken.
            Slice the onions over the top.
            """.trimIndent(),
        )
        assertEquals("Weeknight Chicken", draft.title)
        assertEquals(4, draft.servings)
        assertEquals(35, draft.activeMinutes)
        assertEquals(3, draft.ingredients.size)
        assertEquals("chicken breast", draft.ingredients[0].name)
        assertEquals("spring onions", draft.ingredients[1].name)
        assertEquals(
            listOf("Cook the chicken.", "Slice the onions over the top."),
            draft.steps,
        )
        assertNull(draft.sourceLink)
    }

    @Test fun headerlessTextReadsTitleIngredientsAndSteps() {
        val draft = RecipeTextParser.parse(
            """
            Pantry Pasta
            8 ounces spaghetti
            2 tablespoons olive oil
            Warm the oil in a wide pan over medium heat until it shimmers.
            Add the pasta to salted boiling water and cook until tender.
            """.trimIndent(),
        )
        assertEquals("Pantry Pasta", draft.title)
        assertEquals(2, draft.ingredients.size)
        assertEquals(2, draft.steps.size)
        assertTrue(draft.steps[0].startsWith("Warm the oil"))
    }

    @Test fun aLoneUrlLineBecomesTheSourceLink() {
        val draft = RecipeTextParser.parse(
            """
            Soup Night
            https://example.com/soup
            Ingredients
            4 cups water
            """.trimIndent(),
        )
        assertEquals("https://example.com/soup", draft.sourceLink)
        assertEquals(1, draft.ingredients.size)
    }

    @Test fun numberedStepsAreCleaned() {
        val draft = RecipeTextParser.parse(
            """
            Rice Bowls
            Ingredients
            2 cups rice
            Instructions
            1. Cook the rice.
            2) Serve it warm.
            """.trimIndent(),
        )
        assertEquals(listOf("Cook the rice.", "Serve it warm."), draft.steps)
    }

    @Test fun defaultsWhenFieldsAreAbsent() {
        val draft = RecipeTextParser.parse(
            """
            Plain Dinner
            Ingredients
            1 cup rice
            """.trimIndent(),
        )
        assertEquals(4, draft.servings)
        assertEquals(0, draft.activeMinutes)
    }
}
