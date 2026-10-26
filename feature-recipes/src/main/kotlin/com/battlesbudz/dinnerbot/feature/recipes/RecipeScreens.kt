package com.battlesbudz.dinnerbot.feature.recipes

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.battlesbudz.dinnerbot.core.model.IngredientNutrition
import com.battlesbudz.dinnerbot.core.model.LabelNutrition
import com.battlesbudz.dinnerbot.core.model.Money
import com.battlesbudz.dinnerbot.core.model.NutritionCalculator
import com.battlesbudz.dinnerbot.core.model.NutritionField
import com.battlesbudz.dinnerbot.core.model.PriceItem
import com.battlesbudz.dinnerbot.core.model.Recipe
import com.battlesbudz.dinnerbot.core.model.RecipeIngredient
import com.battlesbudz.dinnerbot.core.model.RecipePhoto
import com.battlesbudz.dinnerbot.core.model.RecipePhotos
import com.battlesbudz.dinnerbot.core.model.RecipeRating
import java.io.File
import kotlin.math.roundToInt

@Composable
fun RecipeListScreen(
    recipes: List<Recipe>,
    priceItems: List<PriceItem>,
    onRecipeClick: (Recipe) -> Unit,
    modifier: Modifier = Modifier,
    ratings: Map<String, RecipeRating> = emptyMap(),
    /** Household photo files by recipe id, for recipes that have one (Epic issue #10). */
    photoFiles: Map<String, File> = emptyMap(),
    /** Open the recipe import flow (Phase RI-2): share, paste a link, paste text. */
    onImportClick: () -> Unit = {},
    /** Open a blank recipe to fill in by hand (Phase RI-2). */
    onAddOwnClick: () -> Unit = {},
) {
    val itemsById = remember(priceItems) { priceItems.associateBy { it.id } }
    LazyColumn(
        modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("Dinner Bot recipes", style = MaterialTheme.typography.headlineMedium)
            Text(
                "Tap a dinner for ingredients and steps. Costs are estimates from the household price book.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Row(
                modifier = Modifier.padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = onImportClick,
                    modifier = Modifier.testTag("import-recipe"),
                ) { Text("Import a recipe") }
                OutlinedButton(
                    onClick = onAddOwnClick,
                    modifier = Modifier.testTag("add-own-recipe"),
                ) { Text("Add your own recipe") }
            }
        }
        items(recipes, key = { it.id }) { recipe ->
            val perServing = RecipeCostCalculator.costPerServingCents(recipe, recipe.servings, itemsById)
            Card(
                modifier = Modifier.fillMaxWidth().clickable { onRecipeClick(recipe) },
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    photoFiles[recipe.id]?.let { file ->
                        RecipePhotoImage(
                            file = file,
                            contentDescription = "Photo of ${recipe.title}",
                            modifier = Modifier
                                .size(72.dp)
                                .clip(MaterialTheme.shapes.small),
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(recipe.title, style = MaterialTheme.typography.titleMedium)
                        Text(
                            "${recipe.mainProtein} · ${recipe.cuisine} · ${recipe.activeMinutes} min",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Text(
                            "About ${Money(perServing).format()} per serving",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        when (ratings[recipe.id]) {
                            RecipeRating.UP -> Text("👍 Liked", style = MaterialTheme.typography.bodySmall)
                            RecipeRating.DOWN -> Text(
                                "👎 Not for us: out of rotation",
                                style = MaterialTheme.typography.bodySmall,
                            )
                            null -> Unit
                        }
                    }
                    // The price, set big at the right of the row the way
                    // the approved mockups set it (v0.6.0 redesign).
                    Text(
                        Money(perServing).format(),
                        style = MaterialTheme.typography.titleLarge,
                    )
                }
            }
        }
    }
}

/** A small rounded stat pill, the mockups' shape for facts about a dinner. */
@Composable
private fun StatPill(text: String) {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

/**
 * The standard back control (v0.6.0 redesign): a filled pill with a back
 * arrow and its label, big enough to see and tap, sitting at the top of
 * the screen it leaves. the user reported the old text-only back links
 * were easy to miss and could hide under the phone's own bars.
 */
@Composable
fun BackPill(label: String, onBack: () -> Unit, modifier: Modifier = Modifier) {
    Button(
        onClick = onBack,
        modifier = modifier.testTag("back-button"),
    ) {
        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
        Text(label, modifier = Modifier.padding(start = 6.dp))
    }
}

@Composable
fun RecipeDetailScreen(
    recipe: Recipe,
    priceItems: List<PriceItem>,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    rating: RecipeRating? = null,
    onRate: (RecipeRating?) -> Unit = {},
    nutritionByItemId: Map<String, IngredientNutrition> = emptyMap(),
    nutritionByPantrySlug: Map<String, IngredientNutrition> = emptyMap(),
    /**
     * Scanned label facts by item id (v0.6.3): an ingredient whose item
     * carries them is costed from the product label, not the USDA entry.
     */
    labelNutritionByItemId: Map<String, LabelNutrition> = emptyMap(),
    /** The household's photo record for this recipe, if any (Epic issue #10). */
    photo: RecipePhoto? = null,
    /** The photo's file in app-local storage, when it exists on this phone. */
    photoFile: File? = null,
    /** A photo was picked from the phone's photos; the app copies it into local storage. */
    onPhotoPicked: (Uri) -> Unit = {},
    onPhotoRemoved: () -> Unit = {},
    onPhotoCreditChange: (String) -> Unit = {},
    /**
     * The saved recipe (household override applied, won't-eat adaptation
     * NOT applied) whose ingredient list the editor opens with (Phase
     * RI-1): an edit is made against the saved list, so a removable
     * ingredient the adaptation currently strips is still hers to see
     * and change in the editor. Defaults to the displayed recipe.
     */
    savedRecipe: Recipe? = null,
    /** True when the household has a saved edit for this recipe (Phase RI-1). */
    hasHouseholdEdits: Boolean = false,
    /** The editor's finished list, saved as the recipe's household version. */
    onSaveIngredients: (List<RecipeIngredient>) -> Unit = {},
    /**
     * Her edited steps, saved as the recipe's household version
     * (v0.6.3): written onto her own recipes directly, and onto the
     * override for a library recipe, so restore-original brings the
     * seed steps back with everything else.
     */
    onSaveSteps: (List<String>) -> Unit = {},
    /** Restore-original: the household edit is cleared and the seed version stands again. */
    onRestoreOriginal: () -> Unit = {},
    /** Today (ISO), stamped on price book items the editor adds (v0.6.2). */
    editorToday: String = java.time.LocalDate.now().toString(),
    /** A brand-new price book item created inside the ingredient editor (v0.6.2). */
    onCreatePriceItem: (PriceItem) -> Unit = {},
    /** Resolves a barcode scanned in the editor's add form to the item it already belongs to (v0.6.3). */
    existingItemForBarcode: ((String) -> PriceItem?)? = null,
    /** A barcode scanned in the editor's add form, to link to the item it created. */
    onBarcodeLinked: (barcode: String, itemId: String) -> Unit = { _, _ -> },
    /** Label facts captured by a scan in the editor's add form. */
    onLabelFacts: (itemId: String, facts: LabelNutrition) -> Unit = { _, _ -> },
    /**
     * Where an imported recipe came from (Phase RI-2), shown under its
     * title. Null for library and hand-made recipes.
     */
    sourceLink: String? = null,
) {
    val itemsById = remember(priceItems) { priceItems.associateBy { it.id } }
    var servings by remember(recipe.id) { mutableStateOf(recipe.servings) }
    var editingIngredients by remember(recipe.id) { mutableStateOf(false) }
    var editingSteps by remember(recipe.id) { mutableStateOf(false) }
    val total = RecipeCostCalculator.ingredientCostCents(recipe, servings, itemsById)
    val perServing = RecipeCostCalculator.costPerServingCents(recipe, servings, itemsById)

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        BackPill("Back to recipes", onBack)
        Text(recipe.title, style = MaterialTheme.typography.headlineMedium)
        Text(
            "${recipe.mainProtein} · ${recipe.cuisine} · ${recipe.activeMinutes} min active",
            style = MaterialTheme.typography.bodyMedium,
        )
        sourceLink?.let { link ->
            Text(
                "From: $link",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.testTag("recipe-source"),
            )
        }
        if (recipe.dietTags.isNotEmpty()) {
            Text(recipe.dietTags.joinToString(", "), style = MaterialTheme.typography.bodySmall)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatPill("${recipe.activeMinutes} min")
            StatPill("$servings servings")
            StatPill(recipe.mainProtein)
            StatPill("${Money(perServing).format()} per serving")
        }

        // The recipe's photo heads the screen (v0.6.0 redesign), the way
        // it heads the approved mockups. Household photo plumbing is
        // unchanged (Epic issue #10, D-V2-2): the household's own photo
        // of this dinner, attached from the phone's photos through the
        // system photo picker (no permission needed) and stored on this
        // phone only. No photo is bundled with the app; a recipe with
        // none shows a clean placeholder and the add entry.
        val photoPicker = rememberLauncherForActivityResult(
            ActivityResultContracts.PickVisualMedia(),
        ) { picked -> if (picked != null) onPhotoPicked(picked) }
        val pickPhoto = {
            photoPicker.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
            )
        }
        if (photoFile != null) {
            RecipePhotoImage(
                file = photoFile,
                contentDescription = "Photo of ${recipe.title}",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .clip(MaterialTheme.shapes.medium)
                    .testTag("recipe-photo"),
            )
            var creditDraft by remember(photo?.recipeId, photo?.credit) {
                mutableStateOf(photo?.credit ?: RecipePhotos.DEFAULT_CREDIT)
            }
            OutlinedTextField(
                value = creditDraft,
                onValueChange = { creditDraft = it },
                label = { Text("Photo credit") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { onPhotoCreditChange(creditDraft) }) { Text("Save credit") }
                TextButton(onClick = pickPhoto) { Text("Change photo") }
                TextButton(onClick = onPhotoRemoved) { Text("Remove photo") }
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .clip(MaterialTheme.shapes.medium)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "No photo yet",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                "When you cook this dinner, add a photo of it from your phone's photos. " +
                    "It is stored on this phone only.",
                style = MaterialTheme.typography.bodySmall,
            )
            OutlinedButton(
                onClick = pickPhoto,
                modifier = Modifier.testTag("add-recipe-photo"),
            ) { Text("Add a photo") }
        }

        // Thumbs rating: set, change, or clear. The active thumb is the
        // filled button; tapping it again clears the rating.
        Text("Your rating", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (rating == RecipeRating.UP) {
                Button(onClick = { onRate(null) }) { Text("👍 Liked it") }
            } else {
                TextButton(onClick = { onRate(RecipeRating.UP) }) { Text("👍 Liked it") }
            }
            if (rating == RecipeRating.DOWN) {
                Button(onClick = { onRate(null) }) { Text("👎 Not for us") }
            } else {
                TextButton(onClick = { onRate(RecipeRating.DOWN) }) { Text("👎 Not for us") }
            }
        }
        Text(
            when (rating) {
                RecipeRating.UP ->
                    "Thumbs up: this dinner wins ties when Dinner Bot plans a week. Tap it again to clear the rating."
                RecipeRating.DOWN ->
                    "Thumbs down: Dinner Bot will not plan this dinner until the rating changes. " +
                        "It stays in your recipe list."
                null ->
                    "Thumbs down takes a dinner out of rotation. Thumbs up makes it win ties when weeks are planned."
            },
            style = MaterialTheme.typography.bodySmall,
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Servings: ", style = MaterialTheme.typography.titleMedium)
            TextButton(onClick = { if (servings > 1) servings -= 1 }) { Text("-") }
            Text(servings.toString(), style = MaterialTheme.typography.titleLarge)
            TextButton(onClick = { servings += 1 }) { Text("+") }
        }
        Text(
            "Estimated cost: ${Money(total).format()} total, ${Money(perServing).format()} per serving",
            style = MaterialTheme.typography.bodyMedium,
        )

        // Nutrition per serving (Phase V2-6): computed from this recipe as
        // this household cooks it (the recipe handed in is already
        // won't-eat adapted) at the servings picked above. An ingredient
        // whose item carries scanned label facts reads the label
        // (v0.6.3); the rest are estimates from USDA data. A field with
        // no sourced value is marked partial instead of printing a total
        // that pretends to be whole. Since the v0.6.0 redesign this card
        // is the app's per-recipe nutrition home; the weekly summary
        // lives on the Plan screen.
        val nutrition = remember(recipe, servings, nutritionByItemId, nutritionByPantrySlug, labelNutritionByItemId) {
            NutritionCalculator.recipeNutrition(
                recipe, servings, nutritionByItemId, nutritionByPantrySlug, labelNutritionByItemId,
            )
        }
        Card(modifier = Modifier.fillMaxWidth().testTag("recipe-nutrition-card")) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text("Nutrition per serving (estimate)", style = MaterialTheme.typography.titleMedium)
                listOf(
                    NutritionField.CALORIES,
                    NutritionField.PROTEIN,
                    NutritionField.FAT,
                    NutritionField.CARBOHYDRATE,
                    NutritionField.FIBER,
                    NutritionField.SODIUM,
                ).forEach { field ->
                    val marker = if (field in nutrition.incompleteFields) " (partial)" else ""
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("${field.label}:", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "${formatNutritionAmount(nutrition.perServing.value(field), field)}$marker",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
                if (nutrition.isPartial) {
                    val names = nutrition.incompleteFields.sortedBy { it.ordinal }
                        .joinToString(", ") { it.label.lowercase() }
                    Text(
                        if (nutrition.labelFactsUsed) {
                            "Partial data: no $names value for one or more ingredients here, " +
                                "so the $names total is incomplete."
                        } else {
                            "Partial data: USDA FoodData Central has no $names value for one or more " +
                                "ingredients here, so the $names total is incomplete."
                        },
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (nutrition.labelFactsUsed) {
                    Text(
                        "Includes nutrition from product labels, scanned from " +
                            "barcodes in the price book.",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.testTag("recipe-nutrition-label-note"),
                    )
                }
                if (nutrition.closestMatchUsed) {
                    Text(
                        "Includes packaged foods matched to the closest USDA entry. " +
                            "Estimates from USDA FoodData Central data.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                } else if (nutrition.usdaUsed) {
                    Text(
                        "Estimates from USDA FoodData Central data.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                // A label-only total shows no USDA line at all: the label
                // note above already says where its numbers came from.
            }
        }

        Text("Ingredients", style = MaterialTheme.typography.titleMedium)
        // Household editing (Phase RI-1): the user can correct any recipe,
        // built-in library included, and the correction is saved as the
        // recipe's household version, surviving every app update.
        if (hasHouseholdEdits) {
            Text(
                "This recipe has your edits. Your version is the one Dinner Bot " +
                    "cooks, plans, and shops, and it stays yours through app updates.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.testTag("recipe-has-edits"),
            )
            TextButton(
                onClick = onRestoreOriginal,
                modifier = Modifier.testTag("restore-original"),
            ) { Text("Restore the original recipe") }
        }
        OutlinedButton(
            onClick = { editingIngredients = true },
            modifier = Modifier.testTag("edit-ingredients"),
        ) { Text("Edit ingredients") }
        if (editingIngredients) {
            IngredientEditorDialog(
                recipeTitle = recipe.title,
                initialIngredients = (savedRecipe ?: recipe).ingredients,
                priceItems = priceItems,
                onDismiss = { editingIngredients = false },
                onSave = { edited ->
                    editingIngredients = false
                    onSaveIngredients(edited)
                },
                today = editorToday,
                onCreatePriceItem = onCreatePriceItem,
                existingItemForBarcode = existingItemForBarcode,
                onBarcodeLinked = onBarcodeLinked,
                onLabelFacts = onLabelFacts,
            )
        }
        recipe.scaledIngredients(servings).forEach { ingredient ->
            val name = when {
                ingredient.pantryItem != null -> "${ingredient.pantryItem} (pantry)"
                else -> itemsById[ingredient.priceItemId]?.name ?: (ingredient.priceItemId ?: "")
            }
            val qty = ingredient.quantity
            val qtyText = "${trimNumber(qty.amount)} ${qty.unit.label}"
            val noteText = ingredient.note?.let { " — $it" } ?: ""
            Text("$qtyText $name$noteText", style = MaterialTheme.typography.bodyMedium)
        }

        Text("Steps", style = MaterialTheme.typography.titleMedium)
        // Steps editing (v0.6.3): the editor opens with the SAVED
        // recipe's steps (her household version when she has one) and
        // saves through the same door as the ingredients — her own
        // recipes are rewritten directly, a library recipe's steps
        // land on its override, and restore-original brings the seed
        // steps back.
        OutlinedButton(
            onClick = { editingSteps = true },
            modifier = Modifier.testTag("edit-steps"),
        ) { Text("Edit steps") }
        if (editingSteps) {
            StepsEditorDialog(
                initialSteps = (savedRecipe ?: recipe).steps,
                onDismiss = { editingSteps = false },
                onSave = { steps ->
                    editingSteps = false
                    onSaveSteps(steps)
                },
            )
        }
        recipe.steps.forEachIndexed { index, step ->
            Text("${index + 1}. $step", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/**
 * The steps editor (v0.6.3): one step per line, the same shape the
 * import review uses, so how she writes steps never changes between
 * doors. Blank lines drop out; a recipe cannot be saved with no steps
 * at all, so Save stays off while the field holds none.
 */
@Composable
fun StepsEditorDialog(
    initialSteps: List<String>,
    onDismiss: () -> Unit,
    onSave: (List<String>) -> Unit,
) {
    var text by remember { mutableStateOf(initialSteps.joinToString("\n")) }
    val steps = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("edit-steps-dialog"),
        title = { Text("Edit steps") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("One step per line") },
                minLines = 5,
                modifier = Modifier.fillMaxWidth().testTag("edit-steps-field"),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(steps) },
                enabled = steps.isNotEmpty(),
                modifier = Modifier.testTag("edit-steps-save"),
            ) { Text("Save steps") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Whole numbers for kcal and mg, one trimmed decimal for grams; "no data" when USDA has none. */
private fun formatNutritionAmount(value: Double?, field: NutritionField): String {
    if (value == null) return "no data"
    return when (field.unitLabel) {
        "g" -> "${trimNumber(kotlin.math.round(value * 10) / 10.0)} g"
        "mg" -> "${value.roundToInt()} mg"
        else -> "${value.roundToInt()} kcal"
    }
}

private fun trimNumber(value: Double): String =
    if (value == value.toLong().toDouble()) value.toLong().toString()
    else String.format("%.2f", value).trimEnd('0').trimEnd('.')
