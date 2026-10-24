package com.battlesbudz.dinnerbot.feature.`import`

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Phase RI-2, plan section 5.2: JSON-LD extraction from recipe pages,
 * against the shapes real sites publish. Any shape not understood
 * returns null and the caller falls back to pasted text — never a
 * partial guess.
 */
class RecipeJsonLdTest {

    private fun page(jsonLd: String) = """
        <html><head><title>Site</title>
        <script type="application/ld+json">$jsonLd</script>
        </head><body>Recipe page</body></html>
    """.trimIndent()

    @Test fun basicRecipeWithStepArray() {
        val draft = RecipeJsonLd.extract(page("""
            {
              "@context": "https://schema.org",
              "@type": "Recipe",
              "name": "Lemon Chicken",
              "recipeYield": "4 servings",
              "prepTime": "PT10M",
              "cookTime": "PT25M",
              "recipeIngredient": ["1 pound chicken breast", "2 lemons"],
              "recipeInstructions": [
                {"@type": "HowToStep", "text": "Sear the chicken."},
                {"@type": "HowToStep", "text": "Add the lemon."}
              ]
            }
        """.trimIndent()), "https://example.com/lemon")!!
        assertEquals("Lemon Chicken", draft.title)
        assertEquals(4, draft.servings)
        assertEquals(35, draft.activeMinutes)
        assertEquals(2, draft.ingredients.size)
        assertEquals("chicken breast", draft.ingredients[0].name)
        assertEquals(listOf("Sear the chicken.", "Add the lemon."), draft.steps)
        assertEquals("https://example.com/lemon", draft.sourceLink)
    }

    @Test fun stringInstructionsSplitIntoSteps() {
        val draft = RecipeJsonLd.extract(page("""
            {
              "@type": "Recipe",
              "name": "Rice",
              "recipeIngredient": ["2 cups rice"],
              "recipeInstructions": "Boil the water.\nAdd the rice.\nRest ten minutes."
            }
        """.trimIndent()), null)!!
        assertEquals(
            listOf("Boil the water.", "Add the rice.", "Rest ten minutes."),
            draft.steps,
        )
    }

    @Test fun sectionedInstructionsFlattenInOrder() {
        val draft = RecipeJsonLd.extract(page("""
            {
              "@type": "Recipe",
              "name": "Layered Bake",
              "recipeIngredient": ["1 cup rice"],
              "recipeInstructions": [
                {"@type": "HowToSection", "name": "Base", "itemListElement": [
                  {"@type": "HowToStep", "text": "Cook the rice."}
                ]},
                {"@type": "HowToSection", "name": "Top", "itemListElement": [
                  {"@type": "HowToStep", "text": "Bake until golden."}
                ]}
              ]
            }
        """.trimIndent()), null)!!
        assertEquals(listOf("Cook the rice.", "Bake until golden."), draft.steps)
    }

    @Test fun recipeInsideGraphIsFound() {
        val draft = RecipeJsonLd.extract(page("""
            {
              "@context": "https://schema.org",
              "@graph": [
                {"@type": "WebPage", "name": "Page"},
                {"@type": "Recipe", "name": "Graph Soup",
                 "recipeIngredient": ["4 cups water"],
                 "recipeInstructions": [{"@type": "HowToStep", "text": "Boil."}]}
              ]
            }
        """.trimIndent()), null)!!
        assertEquals("Graph Soup", draft.title)
    }

    @Test fun recipeInTopLevelArrayIsFound() {
        val draft = RecipeJsonLd.extract(page("""
            [
              {"@type": "WebSite", "name": "Site"},
              {"@type": "Recipe", "name": "Array Pie",
               "recipeIngredient": ["1 cup flour"], "recipeInstructions": "Bake."}
            ]
        """.trimIndent()), null)!!
        assertEquals("Array Pie", draft.title)
    }

    @Test fun hoursAndMinutesDurationsAdd() {
        val draft = RecipeJsonLd.extract(page("""
            {
              "@type": "Recipe", "name": "Slow Stew",
              "totalTime": "PT1H20M",
              "recipeIngredient": ["1 pound pork"],
              "recipeInstructions": "Cook low and slow."
            }
        """.trimIndent()), null)!!
        assertEquals(80, draft.activeMinutes)
    }

    @Test fun htmlInFieldsIsStripped() {
        val draft = RecipeJsonLd.extract(page("""
            {
              "@type": "Recipe", "name": "<b>Bold</b> Soup &amp; More",
              "recipeIngredient": ["1 cup <i>rice</i>"],
              "recipeInstructions": "Stir &amp; serve."
            }
        """.trimIndent()), null)!!
        assertEquals("Bold Soup & More", draft.title)
        assertEquals("rice", draft.ingredients.single().name)
        assertEquals(listOf("Stir & serve."), draft.steps)
    }

    @Test fun pageWithoutARecipeReturnsNull() {
        assertNull(RecipeJsonLd.extract(page("""{"@type": "WebPage", "name": "Blog"}"""), null))
        assertNull(RecipeJsonLd.extract("<html><body>No structured data</body></html>", null))
        assertNull(RecipeJsonLd.extract("", null))
    }

    @Test fun recipeWithoutIngredientsReturnsNull() {
        assertNull(
            RecipeJsonLd.extract(
                page("""{"@type": "Recipe", "name": "Empty", "recipeIngredient": []}"""),
                null,
            ),
        )
    }
}
