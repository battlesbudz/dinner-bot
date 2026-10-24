package com.battlesbudz.dinnerbot.feature.recipes

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.window.Dialog
import com.battlesbudz.dinnerbot.core.model.MeasureUnit
import com.battlesbudz.dinnerbot.core.model.Money
import com.battlesbudz.dinnerbot.core.model.PriceItem
import com.battlesbudz.dinnerbot.core.model.Quantity
import com.battlesbudz.dinnerbot.core.model.RecipeEditing
import com.battlesbudz.dinnerbot.core.model.RecipeIngredient

/**
 * The ingredient editor (Phase RI-1): one component with three doors —
 * the recipe screen (saved-version edits), a planned night's cook view
 * (behind the tonight-only ask), and RI-2's import review. It edits a
 * recipe's full ingredient list with the operations Battles named:
 * substitute an ingredient for a price book item, remove one, change a
 * quantity, flip the essential / removable mark the won't-eat rule
 * reads, and add an ingredient from the price book.
 *
 * The editor owns a draft and hands the finished list back whole;
 * where the list lands (a household override, or one night's
 * tonight-only edits) is the caller's decision, never the editor's.
 * A list is never saved empty: a dinner with no ingredients cannot be
 * cooked or costed, so the last ingredient cannot be removed and Save
 * stays off until the draft differs from what was opened.
 */
@Composable
fun IngredientEditorDialog(
    recipeTitle: String,
    initialIngredients: List<RecipeIngredient>,
    priceItems: List<PriceItem>,
    onDismiss: () -> Unit,
    onSave: (List<RecipeIngredient>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val itemsById = remember(priceItems) { priceItems.associateBy { it.id } }
    var draft by remember { mutableStateOf(initialIngredients) }
    var pickerFor by remember { mutableStateOf<PickerTarget?>(null) }
    var quantityFor by remember { mutableStateOf<QuantityTarget?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.large,
            modifier = modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Edit ingredients", style = MaterialTheme.typography.titleLarge)
                Text(recipeTitle, style = MaterialTheme.typography.bodyMedium)
                LazyColumn(
                    modifier = Modifier.heightIn(max = 380.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(draft.indices.toList(), key = { it }) { index ->
                        val ingredient = draft[index]
                        val name = when {
                            ingredient.pantryItem != null -> "${ingredient.pantryItem} (pantry)"
                            else -> itemsById[ingredient.priceItemId]?.name
                                ?: (ingredient.priceItemId ?: "")
                        }
                        Column(modifier = Modifier.testTag("ingredient-row-$index")) {
                            Text(
                                "${trimEditorNumber(ingredient.quantity.amount)} " +
                                    "${ingredient.quantity.unit.label} $name",
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Checkbox(
                                    checked = ingredient.essential,
                                    onCheckedChange = { checked ->
                                        draft = RecipeEditing.setEssential(draft, index, checked)
                                    },
                                    modifier = Modifier.testTag("ingredient-essential-$index"),
                                )
                                Text("Needed", style = MaterialTheme.typography.bodySmall)
                                TextButton(
                                    onClick = { quantityFor = QuantityTarget.Edit(index) },
                                    modifier = Modifier.testTag("ingredient-qty-$index"),
                                ) { Text("Amount") }
                                TextButton(
                                    onClick = { pickerFor = PickerTarget.Replace(index) },
                                    modifier = Modifier.testTag("ingredient-replace-$index"),
                                ) { Text("Replace") }
                                TextButton(
                                    onClick = { draft = RecipeEditing.remove(draft, index) },
                                    enabled = draft.size > 1,
                                    modifier = Modifier.testTag("ingredient-remove-$index"),
                                ) { Text("Remove") }
                            }
                        }
                    }
                }
                OutlinedButton(
                    onClick = { pickerFor = PickerTarget.Add },
                    modifier = Modifier.testTag("ingredient-add"),
                ) { Text("Add an ingredient") }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Button(
                        onClick = { onSave(draft) },
                        enabled = draft.isNotEmpty() && draft != initialIngredients,
                        modifier = Modifier.testTag("editor-save"),
                    ) { Text("Save ingredients") }
                }
            }
        }
    }

    pickerFor?.let { target ->
        ItemPickerDialog(
            priceItems = priceItems,
            title = when (target) {
                is PickerTarget.Replace -> "Replace with"
                PickerTarget.Add -> "Add which item?"
            },
            onPick = { item ->
                pickerFor = null
                when (target) {
                    is PickerTarget.Replace ->
                        draft = RecipeEditing.substitute(draft, target.index, item)
                    PickerTarget.Add -> quantityFor = QuantityTarget.Add(item)
                }
            },
            onDismiss = { pickerFor = null },
        )
    }

    quantityFor?.let { target ->
        val initial = when (target) {
            is QuantityTarget.Edit -> draft[target.index].quantity
            is QuantityTarget.Add -> Quantity(1.0, target.item.packQuantity.unit)
        }
        QuantityDialog(
            initial = initial,
            onConfirm = { quantity ->
                quantityFor = null
                draft = when (target) {
                    is QuantityTarget.Edit -> RecipeEditing.setQuantity(draft, target.index, quantity)
                    is QuantityTarget.Add -> RecipeEditing.add(draft, target.item, quantity)
                }
            },
            onDismiss = { quantityFor = null },
        )
    }
}

private sealed interface PickerTarget {
    data class Replace(val index: Int) : PickerTarget
    data object Add : PickerTarget
}

private sealed interface QuantityTarget {
    data class Edit(val index: Int) : QuantityTarget
    data class Add(val item: PriceItem) : QuantityTarget
}

/** Pick a price book item by name; the search filters the list as Andrea types. */
@Composable
private fun ItemPickerDialog(
    priceItems: List<PriceItem>,
    title: String,
    onPick: (PriceItem) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val shown = remember(priceItems, query) {
        val q = query.trim().lowercase()
        if (q.isEmpty()) {
            priceItems
        } else {
            priceItems.filter { it.name.lowercase().contains(q) }
        }
    }
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(title, style = MaterialTheme.typography.titleLarge)
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Search the price book") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("picker-search"),
                )
                LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                    items(shown, key = { it.id }) { item ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onPick(item) }
                                .padding(vertical = 8.dp)
                                .testTag("picker-item-${item.id}"),
                        ) {
                            Text(item.name, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "${item.packDescription} · ${Money(item.packPriceCents).format()}",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        }
    }
}

/** Amount plus unit for one ingredient. Units are the app's closed set; any unit may be picked. */
@Composable
private fun QuantityDialog(
    initial: Quantity,
    onConfirm: (Quantity) -> Unit,
    onDismiss: () -> Unit,
) {
    var amountText by remember { mutableStateOf(trimEditorNumber(initial.amount)) }
    var unit by remember { mutableStateOf(initial.unit) }
    var unitMenuOpen by remember { mutableStateOf(false) }
    val parsed = amountText.toDoubleOrNull()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("How much?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { text -> amountText = text.filter { it.isDigit() || it == '.' } },
                    label = { Text("Amount") },
                    singleLine = true,
                    modifier = Modifier.testTag("qty-amount"),
                )
                OutlinedButton(
                    onClick = { unitMenuOpen = true },
                    modifier = Modifier.testTag("qty-unit"),
                ) { Text("Unit: ${unit.label}") }
                DropdownMenu(expanded = unitMenuOpen, onDismissRequest = { unitMenuOpen = false }) {
                    MeasureUnit.entries.forEach { candidate ->
                        DropdownMenuItem(
                            text = { Text(candidate.label) },
                            onClick = {
                                unit = candidate
                                unitMenuOpen = false
                            },
                            modifier = Modifier.testTag("qty-unit-${candidate.name}"),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { parsed?.let { onConfirm(Quantity(it, unit)) } },
                enabled = parsed != null && parsed > 0.0,
                modifier = Modifier.testTag("qty-save"),
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/**
 * The D1 ask (Phase RI-1), shown the moment an edit is saved from a
 * planned night: does this change apply tonight only, or from now on?
 * Tonight only is the default choice, presented first and filled; a
 * tonight-only change lives on that night's plan and never touches the
 * saved recipe, and from now on is the deliberate permanent choice.
 */
@Composable
fun EditScopeDialog(
    onTonightOnly: () -> Unit,
    onFromNowOn: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Save this change for…") },
        text = {
            Text(
                "Tonight only changes this dinner for tonight; the saved recipe keeps " +
                    "its ingredients. From now on makes this the recipe's saved version " +
                    "until you change it again.",
            )
        },
        confirmButton = {
            Button(
                onClick = onTonightOnly,
                modifier = Modifier.testTag("edit-scope-tonight"),
            ) { Text("Tonight only") }
        },
        dismissButton = {
            TextButton(
                onClick = onFromNowOn,
                modifier = Modifier.testTag("edit-scope-always"),
            ) { Text("From now on") }
        },
    )
}

private fun trimEditorNumber(value: Double): String =
    if (value == value.toLong().toDouble()) value.toLong().toString()
    else String.format("%.2f", value).trimEnd('0').trimEnd('.')
