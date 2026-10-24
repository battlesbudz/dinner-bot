package com.battlesbudz.dinnerbot.feature.recipes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.battlesbudz.dinnerbot.core.model.PriceItem
import com.battlesbudz.dinnerbot.core.model.RanOutEvent
import com.battlesbudz.dinnerbot.core.model.RanOutKeys
import com.battlesbudz.dinnerbot.core.model.Recipe
import com.battlesbudz.dinnerbot.core.model.RecipeIngredient

/**
 * The cook view for one planned dinner (Phase V2-7, scope B4 step 0):
 * tonight's ingredients, scaled to the household, each with a check-off
 * Andrea ticks as she uses it while cooking and preparing. The check-off
 * state is kept per planned dinner, so a half-cooked night resumes where
 * it stopped.
 *
 * At the bottom, the ran-out marking: one toggle per ingredient for
 * "used all of this and ran out". A marked ingredient joins the ran-out
 * list the Sunday reconciliation consumes first; unmarking takes the
 * pending mark back off.
 */
@Composable
fun CookScreen(
    dayLabel: String,
    recipe: Recipe,
    priceItems: List<PriceItem>,
    householdSize: Int,
    checkedKeys: Set<String>,
    ranOutKeys: Set<String>,
    onToggleChecked: (String, Boolean) -> Unit,
    /** (the event the mark writes, marked) — unmarking removes the pending mark. */
    onToggleRanOut: (RanOutEvent, Boolean) -> Unit,
    today: String,
    planId: String,
    pantryOwnedNames: Set<String>,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    /** A tonight-only ingredient edit saved from this view (Phase RI-1): it lands on this night's plan only. */
    onSaveTonightOnly: (List<RecipeIngredient>) -> Unit = { _ -> },
    /** A from-now-on ingredient edit saved from this view (Phase RI-1): it becomes the recipe's household version. */
    onSaveFromNowOn: (List<RecipeIngredient>) -> Unit = { _ -> },
) {
    val itemsById = remember(priceItems) { priceItems.associateBy { it.id } }
    val ingredients = remember(recipe, householdSize) { recipe.scaledIngredients(householdSize) }
    var editingIngredients by remember(recipe.id) { mutableStateOf(false) }
    var pendingScope by remember(recipe.id) {
        mutableStateOf<List<RecipeIngredient>?>(null)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TextButton(onClick = onBack) { Text("Back to the week") }
        Text("Cooking: ${recipe.title}", style = MaterialTheme.typography.headlineSmall)
        Text(
            "$dayLabel · check off each ingredient as you use it.",
            style = MaterialTheme.typography.bodyMedium,
        )
        // Ingredient editing from a planned night (Phase RI-1): saving
        // asks tonight only, or from now on — tonight only is the
        // default, and a tonight-only change never touches the saved
        // recipe (the peppers case / broccoli case in Battles's ruling).
        OutlinedButton(
            onClick = { editingIngredients = true },
            modifier = Modifier.testTag("edit-ingredients"),
        ) { Text("Edit tonight's ingredients") }
        if (editingIngredients) {
            IngredientEditorDialog(
                recipeTitle = recipe.title,
                initialIngredients = recipe.ingredients,
                priceItems = priceItems,
                onDismiss = { editingIngredients = false },
                onSave = { edited ->
                    editingIngredients = false
                    pendingScope = edited
                },
            )
        }
        pendingScope?.let { edited ->
            EditScopeDialog(
                onTonightOnly = {
                    pendingScope = null
                    onSaveTonightOnly(edited)
                },
                onFromNowOn = {
                    pendingScope = null
                    onSaveFromNowOn(edited)
                },
                onDismiss = { pendingScope = null },
            )
        }

        ingredients.forEach { ingredient ->
            val key = RanOutKeys.forIngredient(ingredient)
            val name = when {
                ingredient.pantryItem != null -> "${ingredient.pantryItem} (pantry)"
                else -> itemsById[ingredient.priceItemId]?.name ?: (ingredient.priceItemId ?: "")
            }
            val qty = ingredient.quantity
            val noteText = ingredient.note?.let { " — $it" } ?: ""
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Checkbox(
                    checked = key in checkedKeys,
                    onCheckedChange = { checked -> onToggleChecked(key, checked) },
                    modifier = Modifier.testTag("cook-used-$key"),
                )
                Text(
                    "${trimNumber(qty.amount)} ${qty.unit.label} $name$noteText",
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }

        Text("Steps", style = MaterialTheme.typography.titleMedium)
        recipe.steps.forEachIndexed { index, step ->
            Text("${index + 1}. $step", style = MaterialTheme.typography.bodyMedium)
        }

        Text(
            "Ran out of anything?",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 16.dp),
        )
        Text(
            "Mark anything you used the last of. It goes on the ran-out list for Sunday's " +
                "check before the next week is planned.",
            style = MaterialTheme.typography.bodySmall,
        )
        ingredients.forEach { ingredient ->
            val key = RanOutKeys.forIngredient(ingredient)
            val name = when {
                ingredient.pantryItem != null -> ingredient.pantryItem
                else -> itemsById[ingredient.priceItemId]?.name ?: (ingredient.priceItemId ?: "")
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Checkbox(
                    checked = key in ranOutKeys,
                    onCheckedChange = { marked ->
                        onToggleRanOut(
                            RanOutKeys.cookingEvent(
                                ingredient, itemsById, pantryOwnedNames, today, planId,
                            ),
                            marked,
                        )
                    },
                    modifier = Modifier.testTag("cook-ranout-$key"),
                )
                Text("$name — ran out", style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

private fun trimNumber(value: Double): String =
    if (value == value.toLong().toDouble()) value.toLong().toString()
    else String.format("%.2f", value).trimEnd('0').trimEnd('.')
