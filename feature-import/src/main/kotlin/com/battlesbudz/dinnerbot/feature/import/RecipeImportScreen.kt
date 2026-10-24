package com.battlesbudz.dinnerbot.feature.`import`

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.battlesbudz.dinnerbot.core.model.ImportedRecipe
import com.battlesbudz.dinnerbot.core.model.ImportedRecipeIds
import com.battlesbudz.dinnerbot.core.model.PriceItem
import com.battlesbudz.dinnerbot.core.model.Recipe
import com.battlesbudz.dinnerbot.core.model.RecipeOrigin
import com.battlesbudz.dinnerbot.feature.recipes.IngredientEditorDialog
import kotlinx.coroutines.launch

/** How an import flow was opened (Phase RI-2). */
sealed interface ImportStart {
    /** From the Import button: the intake screen, nothing prefilled. */
    data object Blank : ImportStart

    /** From "Add your own recipe": straight to a blank review to fill in. */
    data object Manual : ImportStart

    /** Text that arrived through the system share sheet: a link or recipe text. */
    data class Shared(val text: String) : ImportStart
}

/**
 * The recipe import flow (Phase RI-2): intake (share sheet, paste a
 * link, paste text, or a hand-made blank), the one-shot page fetch for
 * links, and the review screen every import lands on before anything
 * saves. A review abandoned midway saves nothing; import writes no
 * price book item, no pantry row, and no barcode link, ever.
 */
@Composable
fun RecipeImportFlow(
    start: ImportStart,
    priceItems: List<PriceItem>,
    pantryOwnedNames: Set<String>,
    existingImports: List<ImportedRecipe>,
    onSave: (recipe: Recipe, sourceLink: String?, updateExistingId: String?) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    var stage by remember { mutableStateOf<Stage>(Stage.Intake(error = null, link = "", text = "")) }

    fun openReview(draft: ImportedDraft, origin: RecipeOrigin) {
        stage = Stage.Review(
            draft = draft,
            origin = origin,
            rows = ImportReview.buildRows(draft, priceItems, pantryOwnedNames),
        )
    }

    fun fetchAndReview(link: String) {
        stage = Stage.Fetching
        scope.launch {
            val html = RecipePageFetcher.fetchHtml(link)
            val draft = html?.let { RecipeJsonLd.extract(it, link.trim()) }
            if (draft != null) {
                openReview(draft, RecipeOrigin.IMPORTED)
            } else {
                stage = Stage.Intake(
                    error = if (html == null) {
                        "That page could not be read. Some sites block fetching, and " +
                            "there may be no connection. Pasting the recipe text always " +
                            "works, and nothing was saved."
                    } else {
                        "That page did not carry a recipe Dinner Bot could read. " +
                            "Paste the recipe text instead; nothing was saved."
                    },
                    link = link,
                    text = "",
                )
            }
        }
    }

    fun readTextAndReview(text: String) {
        val draft = RecipeTextParser.parse(text)
        if (draft.ingredients.isEmpty() && draft.steps.isEmpty()) {
            stage = Stage.Intake(
                error = "That text did not read as a recipe. Check it has the " +
                    "ingredients in it, or paste a link instead; nothing was saved.",
                link = (stage as? Stage.Intake)?.link ?: "",
                text = text,
            )
        } else {
            openReview(draft, RecipeOrigin.IMPORTED)
        }
    }

    LaunchedEffect(start) {
        when (val s = start) {
            ImportStart.Blank -> Unit
            ImportStart.Manual -> openReview(ImportedDraft.manual(), RecipeOrigin.MANUAL)
            is ImportStart.Shared -> {
                val text = s.text.trim()
                if (RecipePageFetcher.looksLikeUrl(text)) {
                    fetchAndReview(text)
                } else {
                    readTextAndReview(s.text)
                }
            }
        }
    }

    when (val current = stage) {
        is Stage.Fetching -> Column(
            modifier = modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Import a recipe", style = MaterialTheme.typography.headlineMedium)
            Text(
                "Reading the page…",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.testTag("import-fetching"),
            )
        }
        is Stage.Intake -> ImportIntake(
            initialLink = current.link,
            initialText = current.text,
            error = current.error,
            onFetchLink = { fetchAndReview(it) },
            onReadText = { readTextAndReview(it) },
            onCancel = onCancel,
            modifier = modifier,
        )
        is Stage.Review -> ImportReviewScreen(
            stage = current,
            priceItems = priceItems,
            pantryOwnedNames = pantryOwnedNames,
            existingImports = existingImports,
            onSave = onSave,
            onCancel = onCancel,
            modifier = modifier,
        )
    }
}

private sealed interface Stage {
    data class Intake(val error: String?, val link: String, val text: String) : Stage
    data object Fetching : Stage
    data class Review(
        val draft: ImportedDraft,
        val origin: RecipeOrigin,
        val rows: List<ReviewIngredient>,
    ) : Stage
}

@Composable
private fun ImportIntake(
    initialLink: String,
    initialText: String,
    error: String?,
    onFetchLink: (String) -> Unit,
    onReadText: (String) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var link by remember { mutableStateOf(initialLink) }
    var text by remember { mutableStateOf(initialText) }
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Import a recipe", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Bring a recipe in from a link or by pasting its text. You can also " +
                "share a recipe to Dinner Bot from your browser. Everything is " +
                "checked by you before it saves, and nothing is sent anywhere.",
            style = MaterialTheme.typography.bodyMedium,
        )
        error?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.testTag("import-error"),
            )
        }
        Text("Paste a link", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = link,
            onValueChange = { link = it },
            label = { Text("Recipe page link") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("import-link-input"),
        )
        Button(
            onClick = { onFetchLink(link) },
            enabled = link.isNotBlank(),
            modifier = Modifier.testTag("import-link-go"),
        ) { Text("Get the recipe") }
        Text("Or paste the recipe text", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            label = { Text("Recipe text") },
            minLines = 6,
            modifier = Modifier.fillMaxWidth().testTag("import-text-input"),
        )
        Button(
            onClick = { onReadText(text) },
            enabled = text.isNotBlank(),
            modifier = Modifier.testTag("import-text-go"),
        ) { Text("Read the recipe") }
        TextButton(onClick = onCancel, modifier = Modifier.testTag("import-cancel")) {
            Text("Cancel")
        }
    }
}

@Composable
private fun ImportReviewScreen(
    stage: Stage.Review,
    priceItems: List<PriceItem>,
    pantryOwnedNames: Set<String>,
    existingImports: List<ImportedRecipe>,
    onSave: (recipe: Recipe, sourceLink: String?, updateExistingId: String?) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val draft = stage.draft
    val itemsById = remember(priceItems) { priceItems.associateBy { it.id } }
    var title by remember { mutableStateOf(draft.title) }
    var servingsText by remember { mutableStateOf(draft.servings.toString()) }
    var minutesText by remember {
        mutableStateOf(if (draft.activeMinutes > 0) draft.activeMinutes.toString() else "")
    }
    var cuisine by remember { mutableStateOf(draft.cuisine ?: "") }
    var stepsText by remember { mutableStateOf(draft.steps.joinToString("\n")) }
    var rows by remember { mutableStateOf(stage.rows) }
    var editing by remember { mutableStateOf(false) }
    val duplicate = remember(existingImports, draft.sourceLink) {
        ImportReview.findDuplicate(existingImports, draft.sourceLink)
    }
    var updateExisting by remember { mutableStateOf(false) }

    val servings = servingsText.toIntOrNull()?.takeIf { it > 0 } ?: draft.servings
    val minutes = minutesText.toIntOrNull()?.coerceAtLeast(0) ?: 0
    val canSave = title.isNotBlank() && rows.isNotEmpty()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            if (stage.origin == RecipeOrigin.MANUAL) "Your recipe" else "Check this recipe",
            style = MaterialTheme.typography.headlineMedium,
        )
        Text(
            "This is Dinner Bot's reading of the recipe. Fix anything that is " +
                "wrong: a fix changes this recipe only, never another recipe " +
                "and never a price in the book.",
            style = MaterialTheme.typography.bodyMedium,
        )
        draft.sourceLink?.let { link ->
            Text(
                "From: $link",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.testTag("import-source"),
            )
        }
        if (duplicate != null) {
            Column(
                modifier = Modifier.testTag("import-duplicate"),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    "You already have a recipe from this link: ${duplicate.recipe.title}.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (updateExisting) {
                        Button(
                            onClick = { updateExisting = true },
                            modifier = Modifier.testTag("import-duplicate-update"),
                        ) { Text("Update that recipe") }
                    } else {
                        OutlinedButton(
                            onClick = { updateExisting = true },
                            modifier = Modifier.testTag("import-duplicate-update"),
                        ) { Text("Update that recipe") }
                    }
                    if (updateExisting) {
                        OutlinedButton(
                            onClick = { updateExisting = false },
                            modifier = Modifier.testTag("import-duplicate-new"),
                        ) { Text("Save as a new recipe") }
                    } else {
                        Button(
                            onClick = { updateExisting = false },
                            modifier = Modifier.testTag("import-duplicate-new"),
                        ) { Text("Save as a new recipe") }
                    }
                }
            }
        }
        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            label = { Text("Title") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("import-title"),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = servingsText,
                onValueChange = { text -> servingsText = text.filter { it.isDigit() } },
                label = { Text("Servings") },
                singleLine = true,
                modifier = Modifier.weight(1f).testTag("import-servings"),
            )
            OutlinedTextField(
                value = minutesText,
                onValueChange = { text -> minutesText = text.filter { it.isDigit() } },
                label = { Text("Active minutes") },
                singleLine = true,
                modifier = Modifier.weight(1f).testTag("import-minutes"),
            )
        }
        OutlinedTextField(
            value = cuisine,
            onValueChange = { cuisine = it },
            label = { Text("Cuisine") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("import-cuisine"),
        )

        Text("Ingredients", style = MaterialTheme.typography.titleMedium)
        rows.forEachIndexed { index, row ->
            ReviewRow(
                row = row,
                index = index,
                itemsById = itemsById,
                onOpenEditor = { editing = true },
                onApplySuggestion = {
                    rows = rows.mapIndexed { i, r ->
                        if (i == index) {
                            ImportReview.applySuggestion(r, priceItems, pantryOwnedNames)
                        } else {
                            r
                        }
                    }
                },
                onApplyQuantitySuggestion = {
                    rows = rows.mapIndexed { i, r ->
                        if (i == index) {
                            ImportReview.applyQuantitySuggestion(r, priceItems, pantryOwnedNames)
                        } else {
                            r
                        }
                    }
                },
            )
        }
        OutlinedButton(
            onClick = { editing = true },
            modifier = Modifier.testTag("import-edit-ingredients"),
        ) { Text("Edit ingredients") }
        if (editing) {
            IngredientEditorDialog(
                recipeTitle = title.ifBlank { "New recipe" },
                initialIngredients = rows.map { it.ingredient },
                priceItems = priceItems,
                onDismiss = { editing = false },
                onSave = { edited ->
                    editing = false
                    rows = ImportReview.rowsAfterEditorSave(
                        rows, edited, priceItems, pantryOwnedNames,
                    )
                },
            )
        }

        Text("Steps", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = stepsText,
            onValueChange = { stepsText = it },
            label = { Text("One step per line") },
            minLines = 4,
            modifier = Modifier.fillMaxWidth().testTag("import-steps"),
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onCancel, modifier = Modifier.testTag("import-cancel")) {
                Text("Cancel")
            }
            Button(
                onClick = {
                    val steps = stepsText.lines().map { it.trim() }.filter { it.isNotEmpty() }
                    val ingredients = rows.map { it.ingredient }
                    val target = if (updateExisting) duplicate else null
                    val id = target?.recipe?.id ?: ImportedRecipeIds.newId(
                        stage.origin, title.trim(), System.currentTimeMillis(),
                    )
                    val recipe = ImportReview.buildRecipe(
                        id = id,
                        title = title.trim(),
                        servings = servings,
                        activeMinutes = minutes,
                        cuisine = cuisine.trim().ifBlank { "Other" },
                        steps = steps,
                        ingredients = ingredients,
                        origin = stage.origin,
                        priceItems = priceItems,
                    )
                    onSave(recipe, draft.sourceLink, target?.recipe?.id)
                },
                enabled = canSave,
                modifier = Modifier.testTag("import-save"),
            ) { Text(if (updateExisting && duplicate != null) "Update recipe" else "Save recipe") }
        }
    }
}

@Composable
private fun ReviewRow(
    row: ReviewIngredient,
    index: Int,
    itemsById: Map<String, PriceItem>,
    onOpenEditor: () -> Unit,
    onApplySuggestion: () -> Unit,
    onApplyQuantitySuggestion: () -> Unit,
) {
    val ingredient = row.ingredient
    val name = when {
        ingredient.priceItemId != null -> itemsById[ingredient.priceItemId]?.name
            ?: ingredient.priceItemId
        else -> "${ingredient.pantryItem} (as written)"
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpenEditor() }
            .padding(vertical = 4.dp)
            .testTag("import-row-$index"),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        if (row.sourceLine.isNotBlank()) {
            Text(row.sourceLine, style = MaterialTheme.typography.bodySmall)
        }
        Text(
            "${trimImportNumber(ingredient.quantity.amount)} " +
                "${ingredient.quantity.unit.label} $name",
            style = MaterialTheme.typography.bodyLarge,
            textDecoration = if (row.pantryCovered) TextDecoration.LineThrough else null,
        )
        when {
            row.pantryCovered -> Text(
                "Already in your pantry",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.testTag("import-row-$index-covered"),
            )
            row.matched -> Text(
                "In the price book",
                style = MaterialTheme.typography.bodySmall,
            )
            else -> Text(
                "No match in the price book: it stays as written and prices at nothing " +
                    "until it is in the book.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.testTag("import-row-$index-unmatched"),
            )
        }
        val parsed = row.parsed
        if (parsed != null && !parsed.quantityResolved) {
            Text(
                "The amount did not convert; it stays as written above.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        row.suggestion?.let { item ->
            TextButton(
                onClick = onApplySuggestion,
                modifier = Modifier.testTag("import-suggestion-$index"),
            ) { Text("Did you mean ${item.name}? Use it") }
        }
        row.quantitySuggestion?.let { quantity ->
            TextButton(
                onClick = onApplyQuantitySuggestion,
                modifier = Modifier.testTag("import-qty-suggestion-$index"),
            ) {
                Text(
                    "Read the amount as ${trimImportNumber(quantity.amount)} " +
                        quantity.unit.label + "?",
                )
            }
        }
    }
}

private fun trimImportNumber(value: Double): String =
    if (value == value.toLong().toDouble()) value.toLong().toString()
    else String.format("%.2f", value).trimEnd('0').trimEnd('.')
