package com.battlesbudz.dinnerbot.core.data

import com.battlesbudz.dinnerbot.core.model.BulkStock
import com.battlesbudz.dinnerbot.core.model.DietPreset
import com.battlesbudz.dinnerbot.core.model.DietPresetsFile
import com.battlesbudz.dinnerbot.core.model.HouseholdSettings
import com.battlesbudz.dinnerbot.core.model.MeasureUnit
import com.battlesbudz.dinnerbot.core.model.PantryItem
import com.battlesbudz.dinnerbot.core.model.PriceBookFile
import com.battlesbudz.dinnerbot.core.model.PriceItem
import com.battlesbudz.dinnerbot.core.model.Quantity
import com.battlesbudz.dinnerbot.core.model.Recipe
import com.battlesbudz.dinnerbot.core.model.RecipeIngredient
import com.battlesbudz.dinnerbot.core.model.RecipesFile
import com.battlesbudz.dinnerbot.core.model.ReminderSettings
import com.battlesbudz.dinnerbot.core.model.Store
import com.battlesbudz.dinnerbot.core.model.StorePrice
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/** Pure JSON parsing of the canonical seed files. No Android, no database: unit-testable on the JVM. */
object SeedDataParser {
    private val json = Json { ignoreUnknownKeys = true }

    fun parsePriceBook(text: String): List<PriceItem> = json.decodeFromString<PriceBookFile>(text).items
    fun parseRecipes(text: String): List<Recipe> = json.decodeFromString<RecipesFile>(text).recipes
    fun parseDietPresets(text: String): List<DietPreset> =
        json.decodeFromString<DietPresetsFile>(text).presets
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
     * Keep the stored seed recipes identical to the bundled seed file.
     * Recipes are pure seed content (the app never lets the user edit them),
     * so when a new app version ships changed seed recipes (v0.1.1 added the
     * essential/removable ingredient marks), the stored rows are replaced
     * from the asset. This touches ONLY the recipes table: settings, price
     * items (including Andrea's corrections), pantry, stock, and plans are
     * user data and are never touched here. No Room schema change is needed
     * because ingredients live in a JSON column. Returns how many recipes
     * were written (0 when the stored set already matches the bundle).
     */
    suspend fun refreshSeedRecipes(recipesJson: String): Int {
        val bundled = SeedDataParser.parseRecipes(recipesJson)
        val stored = db.recipeDao().getAll().map { with(SeedMapper) { it.toModel() } }
        // Compare by id: the DAO returns rows ordered by title, the bundle
        // is in file order, and list equality would never see them as equal.
        if (stored.associateBy { it.id } == bundled.associateBy { it.id }) return 0
        db.recipeDao().deleteAll()
        db.recipeDao().insertAll(bundled.map { with(SeedMapper) { it.toEntity() } })
        return bundled.size
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
