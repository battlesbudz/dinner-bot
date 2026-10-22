package com.battlesbudz.dinnerbot.core.data

import com.battlesbudz.dinnerbot.core.model.BulkStock
import com.battlesbudz.dinnerbot.core.model.HouseholdSettings
import com.battlesbudz.dinnerbot.core.model.MeasureUnit
import com.battlesbudz.dinnerbot.core.model.PantryItem
import com.battlesbudz.dinnerbot.core.model.PriceBookFile
import com.battlesbudz.dinnerbot.core.model.PriceItem
import com.battlesbudz.dinnerbot.core.model.Quantity
import com.battlesbudz.dinnerbot.core.model.Recipe
import com.battlesbudz.dinnerbot.core.model.RecipeIngredient
import com.battlesbudz.dinnerbot.core.model.RecipesFile
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
        dietNotes = dietNotes,
        pantryStaplesJson = json.encodeToString(stringList, pantryStaples),
        onboardingComplete = onboardingComplete,
    )

    fun SettingsEntity.toModel(): HouseholdSettings = HouseholdSettings(
        householdSize = householdSize,
        cookNights = json.decodeFromString(stringList, cookNightsJson),
        weeklyBudgetCents = weeklyBudgetCents,
        wontEat = json.decodeFromString(stringList, wontEatJson),
        dietNotes = dietNotes,
        pantryStaples = json.decodeFromString(stringList, pantryStaplesJson),
        onboardingComplete = onboardingComplete,
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
