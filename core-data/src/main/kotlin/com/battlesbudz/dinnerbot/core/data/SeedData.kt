package com.battlesbudz.dinnerbot.core.data

import com.battlesbudz.dinnerbot.core.model.BarcodeLink
import com.battlesbudz.dinnerbot.core.model.ScanSubstitution
import com.battlesbudz.dinnerbot.core.model.BulkStock
import com.battlesbudz.dinnerbot.core.model.DietPreset
import com.battlesbudz.dinnerbot.core.model.DietPresetsFile
import com.battlesbudz.dinnerbot.core.model.HouseholdSettings
import com.battlesbudz.dinnerbot.core.model.ImportedRecipe
import com.battlesbudz.dinnerbot.core.model.IngredientNutrition
import com.battlesbudz.dinnerbot.core.model.LabelNutrition
import com.battlesbudz.dinnerbot.core.model.MeasureUnit
import com.battlesbudz.dinnerbot.core.model.NutritionFile
import com.battlesbudz.dinnerbot.core.model.NutritionValues
import com.battlesbudz.dinnerbot.core.model.PantryItem
import com.battlesbudz.dinnerbot.core.model.PriceBookFile
import com.battlesbudz.dinnerbot.core.model.PriceItem
import com.battlesbudz.dinnerbot.core.model.Quantity
import com.battlesbudz.dinnerbot.core.model.Recipe
import com.battlesbudz.dinnerbot.core.model.RecipeIngredient
import com.battlesbudz.dinnerbot.core.model.RecipeOrigin
import com.battlesbudz.dinnerbot.core.model.RecipeOverride
import com.battlesbudz.dinnerbot.core.model.RecipePhoto
import com.battlesbudz.dinnerbot.core.model.RecipesFile
import com.battlesbudz.dinnerbot.core.model.ReminderSettings
import com.battlesbudz.dinnerbot.core.model.Store
import com.battlesbudz.dinnerbot.core.model.StorePrice
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/** Pure JSON parsing of the canonical seed files. No Android, no database: unit-testable on the JVM. */
object SeedDataParser {
    private val json = Json { ignoreUnknownKeys = true }

    fun parsePriceBook(text: String): List<PriceItem> = json.decodeFromString<PriceBookFile>(text).items
    fun parseRecipes(text: String): List<Recipe> = json.decodeFromString<RecipesFile>(text).recipes
    fun parseDietPresets(text: String): List<DietPreset> =
        json.decodeFromString<DietPresetsFile>(text).presets
    fun parseNutrition(text: String): NutritionFile = json.decodeFromString<NutritionFile>(text)
}

/** Pure conversions between core-model types and Room entities. Unit-testable on the JVM. */
object SeedMapper {
    private val json = Json { ignoreUnknownKeys = true }
    private val stringList = ListSerializer(String.serializer())
    private val ingredientList = ListSerializer(RecipeIngredient.serializer())
    private val storePriceList = ListSerializer(StorePrice.serializer())

    fun PriceItem.toEntity(): PriceItemEntity = PriceItemEntity(
        id = id,
        name = name,
        category = category,
        homeStore = homeStore.name,
        packDescription = packDescription,
        packAmount = packQuantity.amount,
        packUnit = packQuantity.unit.name,
        packPriceCents = packPriceCents,
        pricePerUnitCents = pricePerUnitCents,
        checkedDate = checkedDate,
        source = source,
        userCorrected = userCorrected,
        alternatesJson = json.encodeToString(storePriceList, alternates),
        freezeNote = freezeNote,
        staple = staple,
        guessPriced = guessPriced,
    )

    fun PriceItemEntity.toModel(): PriceItem = PriceItem(
        id = id,
        name = name,
        category = category,
        homeStore = Store.valueOf(homeStore),
        packDescription = packDescription,
        packQuantity = Quantity(packAmount, MeasureUnit.valueOf(packUnit)),
        packPriceCents = packPriceCents,
        pricePerUnitCents = pricePerUnitCents,
        checkedDate = checkedDate,
        source = source,
        userCorrected = userCorrected,
        alternates = runCatching { json.decodeFromString(storePriceList, alternatesJson) }
            .getOrDefault(emptyList()),
        freezeNote = freezeNote,
        staple = staple,
        guessPriced = guessPriced,
    )

    fun IngredientNutrition.toEntity(): IngredientNutritionEntity = IngredientNutritionEntity(
        itemId = itemId,
        pantryItem = pantryItem,
        fdcId = fdcId,
        fdcDescription = fdcDescription,
        fdcDataType = fdcDataType,
        closestMatch = closestMatch,
        source = source,
        caloriesKcal = per100g.caloriesKcal,
        proteinG = per100g.proteinG,
        fatG = per100g.fatG,
        carbohydrateG = per100g.carbohydrateG,
        fiberG = per100g.fiberG,
        sugarsG = per100g.sugarsG,
        sodiumMg = per100g.sodiumMg,
        saturatedFatG = per100g.saturatedFatG,
        cholesterolMg = per100g.cholesterolMg,
        gramWeightsJson = json.encodeToString(
            MapSerializer(
                String.serializer(),
                Double.serializer(),
            ),
            gramWeights,
        ),
        gramWeightSource = gramWeightSource,
    )

    fun IngredientNutritionEntity.toModel(): IngredientNutrition = IngredientNutrition(
        itemId = itemId,
        pantryItem = pantryItem,
        fdcId = fdcId,
        fdcDescription = fdcDescription,
        fdcDataType = fdcDataType,
        closestMatch = closestMatch,
        source = source,
        per100g = NutritionValues(
            caloriesKcal = caloriesKcal,
            proteinG = proteinG,
            fatG = fatG,
            carbohydrateG = carbohydrateG,
            fiberG = fiberG,
            sugarsG = sugarsG,
            sodiumMg = sodiumMg,
            saturatedFatG = saturatedFatG,
            cholesterolMg = cholesterolMg,
        ),
        gramWeights = runCatching {
            json.decodeFromString(
                MapSerializer(
                    String.serializer(),
                    Double.serializer(),
                ),
                gramWeightsJson,
            )
        }.getOrDefault(emptyMap()),
        gramWeightSource = gramWeightSource,
    )

    fun BarcodeLink.toEntity(): BarcodeLinkEntity = BarcodeLinkEntity(
        barcode = barcode,
        itemId = itemId,
        createdAtMillis = createdAtMillis,
    )

    fun BarcodeLinkEntity.toModel(): BarcodeLink = BarcodeLink(
        barcode = barcode,
        itemId = itemId,
        createdAtMillis = createdAtMillis,
    )

    fun ScanSubstitution.toEntity(): ScanSubstitutionEntity = ScanSubstitutionEntity(
        id = id,
        planId = planId,
        originalItemId = originalItemId,
        substituteItemId = substituteItemId,
        recipeId = recipeId,
        substitutePriceCents = substitutePriceCents,
        createdAtMillis = createdAtMillis,
    )

    fun ScanSubstitutionEntity.toModel(): ScanSubstitution = ScanSubstitution(
        id = id,
        planId = planId,
        originalItemId = originalItemId,
        substituteItemId = substituteItemId,
        recipeId = recipeId,
        substitutePriceCents = substitutePriceCents,
        createdAtMillis = createdAtMillis,
    )

    fun RecipeOverride.toEntity(): RecipeOverrideEntity = RecipeOverrideEntity(
        recipeId = recipeId,
        ingredientsJson = json.encodeToString(ingredientList, ingredients),
        updatedAtMillis = updatedAtMillis,
        stepsJson = steps?.let { json.encodeToString(stringList, it) },
    )

    fun RecipeOverrideEntity.toModel(): RecipeOverride = RecipeOverride(
        recipeId = recipeId,
        ingredients = json.decodeFromString(ingredientList, ingredientsJson),
        updatedAtMillis = updatedAtMillis,
        steps = stepsJson?.let { json.decodeFromString(stringList, it) },
    )

    fun LabelNutrition.toEntity(): LabelNutritionEntity = LabelNutritionEntity(
        itemId = itemId,
        barcode = barcode,
        per100gJson = json.encodeToString(NutritionValues.serializer(), per100g),
        perServingJson = perServing?.let { json.encodeToString(NutritionValues.serializer(), it) },
        servingSizeText = servingSizeText,
        servingGrams = servingGrams,
        updatedAtMillis = updatedAtMillis,
    )

    fun LabelNutritionEntity.toModel(): LabelNutrition = LabelNutrition(
        itemId = itemId,
        barcode = barcode,
        per100g = json.decodeFromString(NutritionValues.serializer(), per100gJson),
        perServing = perServingJson?.let {
            json.decodeFromString(NutritionValues.serializer(), it)
        },
        servingSizeText = servingSizeText,
        servingGrams = servingGrams,
        updatedAtMillis = updatedAtMillis,
    )

    fun ImportedRecipe.toEntity(): ImportedRecipeEntity = ImportedRecipeEntity(
        id = recipe.id,
        title = recipe.title,
        servings = recipe.servings,
        activeMinutes = recipe.activeMinutes,
        cuisine = recipe.cuisine,
        mainProtein = recipe.mainProtein,
        dietTagsJson = json.encodeToString(stringList, recipe.dietTags),
        ingredientsJson = json.encodeToString(ingredientList, recipe.ingredients),
        stepsJson = json.encodeToString(stringList, recipe.steps),
        sourceUrl = sourceLink,
        origin = recipe.origin.name,
        createdAtMillis = createdAtMillis,
    )

    fun ImportedRecipeEntity.toModel(): ImportedRecipe = ImportedRecipe(
        recipe = Recipe(
            id = id,
            title = title,
            servings = servings,
            activeMinutes = activeMinutes,
            cuisine = cuisine,
            mainProtein = mainProtein,
            dietTags = json.decodeFromString(stringList, dietTagsJson),
            ingredients = json.decodeFromString(ingredientList, ingredientsJson),
            steps = json.decodeFromString(stringList, stepsJson),
            // A row whose origin text is somehow not IMPORTED or MANUAL
            // still reads as a user recipe, never as a library recipe:
            // the table is household data by construction.
            origin = runCatching { RecipeOrigin.valueOf(origin) }
                .getOrDefault(RecipeOrigin.IMPORTED)
                .takeIf { it != RecipeOrigin.LIBRARY } ?: RecipeOrigin.IMPORTED,
        ),
        sourceLink = sourceUrl,
        createdAtMillis = createdAtMillis,
    )

    fun RecipePhoto.toEntity(): RecipePhotoEntity = RecipePhotoEntity(
        recipeId = recipeId,
        fileName = fileName,
        credit = credit,
        updatedAtMillis = updatedAtMillis,
    )

    fun RecipePhotoEntity.toModel(): RecipePhoto = RecipePhoto(
        recipeId = recipeId,
        fileName = fileName,
        credit = credit,
        updatedAtMillis = updatedAtMillis,
    )

    fun PantryItem.toEntity(): PantryItemEntity = PantryItemEntity(id = id, name = name, owned = owned)

    fun PantryItemEntity.toModel(): PantryItem = PantryItem(id = id, name = name, owned = owned)

    fun BulkStock.toEntity(): BulkStockEntity = BulkStockEntity(
        itemId = itemId,
        amount = onHand.amount,
        unit = onHand.unit.name,
    )

    fun BulkStockEntity.toModel(): BulkStock = BulkStock(
        itemId = itemId,
        onHand = Quantity(amount, MeasureUnit.valueOf(unit)),
    )

    fun Recipe.toEntity(): RecipeEntity = RecipeEntity(
        id = id,
        title = title,
        servings = servings,
        activeMinutes = activeMinutes,
        cuisine = cuisine,
        mainProtein = mainProtein,
        dietTagsJson = json.encodeToString(stringList, dietTags),
        ingredientsJson = json.encodeToString(ingredientList, ingredients),
        stepsJson = json.encodeToString(stringList, steps),
    )

    fun RecipeEntity.toModel(): Recipe = Recipe(
        id = id,
        title = title,
        servings = servings,
        activeMinutes = activeMinutes,
        cuisine = cuisine,
        mainProtein = mainProtein,
        dietTags = json.decodeFromString(stringList, dietTagsJson),
        ingredients = json.decodeFromString(ingredientList, ingredientsJson),
        steps = json.decodeFromString(stringList, stepsJson),
    )

    fun HouseholdSettings.toEntity(): SettingsEntity = SettingsEntity(
        id = 1,
        householdSize = householdSize,
        cookNightsJson = json.encodeToString(stringList, cookNights),
        weeklyBudgetCents = weeklyBudgetCents,
        wontEatJson = json.encodeToString(stringList, wontEat),
        dietPresetsJson = json.encodeToString(stringList, dietPresets),
        dietNotes = dietNotes,
        pantryStaplesJson = json.encodeToString(stringList, pantryStaples),
        onboardingComplete = onboardingComplete,
        remindersJson = json.encodeToString(ReminderSettings.serializer(), reminders),
        reconciledPlanIdsJson = json.encodeToString(stringList, reconciledPlanIds),
    )

    fun SettingsEntity.toModel(): HouseholdSettings = HouseholdSettings(
        householdSize = householdSize,
        cookNights = json.decodeFromString(stringList, cookNightsJson),
        weeklyBudgetCents = weeklyBudgetCents,
        wontEat = json.decodeFromString(stringList, wontEatJson),
        dietPresets = runCatching { json.decodeFromString(stringList, dietPresetsJson) }
            .getOrDefault(emptyList()),
        dietNotes = dietNotes,
        pantryStaples = json.decodeFromString(stringList, pantryStaplesJson),
        onboardingComplete = onboardingComplete,
        // A pre-V2-4 row (or a damaged blob) reads as every reminder off.
        reminders = runCatching { json.decodeFromString(ReminderSettings.serializer(), remindersJson) }
            .getOrDefault(ReminderSettings()),
        // A pre-V2-7 row reads as no plan reconciled yet.
        reconciledPlanIds = runCatching { json.decodeFromString(stringList, reconciledPlanIdsJson) }
            .getOrDefault(emptyList()),
    )
}

/** First-launch seeding: fills an empty database from the bundled seed JSON. Called by the app. */
class SeedLoader(private val db: DinnerBotDatabase) {
    data class Result(val priceItemsInserted: Int, val recipesInserted: Int)

    suspend fun seedIfEmpty(priceBookJson: String, recipesJson: String): Result {
        var items = 0
        var recipes = 0
        if (db.priceItemDao().count() == 0) {
            val parsed = SeedDataParser.parsePriceBook(priceBookJson)
            db.priceItemDao().insertAll(parsed.map { with(SeedMapper) { it.toEntity() } })
            items = parsed.size
        }
        if (db.recipeDao().count() == 0) {
            val parsed = SeedDataParser.parseRecipes(recipesJson)
            db.recipeDao().insertAll(parsed.map { with(SeedMapper) { it.toEntity() } })
            recipes = parsed.size
        }
        return Result(items, recipes)
    }

    /**
     * Keep the stored seed recipes in step with the bundled seed file,
     * under Battles's refresh rule (Phase RI-1): a recipe Andrea has
     * changed is NEVER refreshed, and refresh delivers additions only for
     * it. Concretely:
     *
     * - A recipe carrying a household override keeps its STORED row whole:
     *   the bundle's version of it is not written at all, in whole or in
     *   part. Her ingredient list stands in the override table (which this
     *   function never writes), and the merge on the load path cooks it.
     * - A recipe with no override refreshes from the bundle exactly as
     *   before (v0.1.1's essential/removable marks arrived this way).
     * - A bundled recipe the phone does not have yet is added.
     * - A recipe the bundle no longer carries leaves the library, and an
     *   override it leaves behind is orphaned and cleaned up here.
     *
     * This touches ONLY the recipes table and orphaned override rows:
     * settings, price items (including Andrea's corrections), pantry,
     * stock, and plans are user data and are never touched here. Returns
     * how many recipes were written (0 when nothing changed).
     */
    suspend fun refreshSeedRecipes(recipesJson: String): Int {
        val bundled = SeedDataParser.parseRecipes(recipesJson)
        val bundledById = bundled.associateBy { it.id }
        val stored = db.recipeDao().getAll().map { with(SeedMapper) { it.toModel() } }
        val storedById = stored.associateBy { it.id }
        val overrideIds = db.recipeOverrideDao().getAll().map { it.recipeId }.toSet()

        // A bundle that drops a recipe retires it; its override, if any,
        // is orphaned and goes with it. Overrides for recipes the bundle
        // still carries are never touched.
        val orphaned = overrideIds.filter { it !in bundledById }
        orphaned.forEach { db.recipeOverrideDao().delete(it) }

        val result = bundled.map { seed ->
            val kept = storedById[seed.id]
            if (seed.id in overrideIds && kept != null) kept else seed
        }
        // Compare by id: the DAO returns rows ordered by title, the bundle
        // is in file order, and list equality would never see them as equal.
        if (storedById == result.associateBy { it.id }) return 0
        db.recipeDao().deleteAll()
        db.recipeDao().insertAll(result.map { with(SeedMapper) { it.toEntity() } })
        return result.size
    }

    /**
     * Keep the stored freeze notes identical to the bundled price book
     * (Phase V2-2). Freeze notes are seed-owned guidance text on the Sam's
     * bulk items; the refresh writes ONLY the freezeNote column per item,
     * so Andrea's corrected prices and every other user row are untouched.
     * Returns how many stored notes actually changed.
     */
    suspend fun refreshSeedPriceNotes(priceBookJson: String): Int {
        val bundled = SeedDataParser.parsePriceBook(priceBookJson).associateBy { it.id }
        var changed = 0
        for (stored in db.priceItemDao().getAll()) {
            val seedNote = bundled[stored.id]?.freezeNote
            if (stored.freezeNote != seedNote) {
                db.priceItemDao().updateFreezeNote(stored.id, seedNote)
                changed++
            }
        }
        return changed
    }

    /**
     * Add bundled price book items the stored book does not have yet
     * (v0.4.3: the eleven pantry staples became priced items). Only ids
     * absent from the stored table are inserted, so Andrea's corrected
     * prices and every other stored row are untouched; existing rows are
     * never rewritten from the bundle. Runs on every start like the other
     * refreshes, and it also heals a database restored from an older
     * backup that predates the new items. Returns how many items were
     * added.
     */
    suspend fun addMissingSeedPriceItems(priceBookJson: String): Int {
        val bundled = SeedDataParser.parsePriceBook(priceBookJson)
        val storedIds = db.priceItemDao().getAll().map { it.id }.toSet()
        val missing = bundled.filter { it.id !in storedIds }
        if (missing.isEmpty()) return 0
        db.priceItemDao().insertAll(missing.map { with(SeedMapper) { it.toEntity() } })
        return missing.size
    }

    /**
     * Keep the stored nutrition entries identical to the bundled seed file
     * (Phase V2-6). Nutrition entries are pure seed content, loaded and
     * refreshed like the recipes: the app never lets the user edit them,
     * so when the bundled file changes the stored rows are replaced from
     * the asset. This touches ONLY the ingredient_nutrition table; every
     * user table is untouched. Returns how many entries were written
     * (0 when the stored set already matches the bundle).
     */
    suspend fun refreshSeedNutrition(nutritionJson: String): Int {
        val bundled = SeedDataParser.parseNutrition(nutritionJson).entries
        val stored = db.ingredientNutritionDao().getAll().map { with(SeedMapper) { it.toModel() } }
        if (stored.associateBy { it.itemId } == bundled.associateBy { it.itemId }) return 0
        db.ingredientNutritionDao().deleteAll()
        db.ingredientNutritionDao().insertAll(bundled.map { with(SeedMapper) { it.toEntity() } })
        return bundled.size
    }

    /**
     * First-run pantry seeding: every known staple name becomes a pantry row,
     * owned when its name is in [ownedNames]. Ids are slugs of the name.
     */
    suspend fun seedPantryIfEmpty(allStapleNames: List<String>, ownedNames: Collection<String>): Int {
        if (db.pantryDao().count() > 0) return 0
        val owned = ownedNames.map { it.lowercase() }.toSet()
        val rows = allStapleNames.map { name ->
            PantryItemEntity(id = slug(name), name = name, owned = name.lowercase() in owned)
        }
        db.pantryDao().insertAll(rows)
        return rows.size
    }

    companion object {
        fun slug(name: String): String =
            name.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')
    }
}
