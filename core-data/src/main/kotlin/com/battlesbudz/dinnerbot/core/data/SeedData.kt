package com.battlesbudz.dinnerbot.core.data

import com.battlesbudz.dinnerbot.core.model.HouseholdSettings
import com.battlesbudz.dinnerbot.core.model.MeasureUnit
import com.battlesbudz.dinnerbot.core.model.PriceBookFile
import com.battlesbudz.dinnerbot.core.model.PriceItem
import com.battlesbudz.dinnerbot.core.model.Quantity
import com.battlesbudz.dinnerbot.core.model.Recipe
import com.battlesbudz.dinnerbot.core.model.RecipeIngredient
import com.battlesbudz.dinnerbot.core.model.RecipesFile
import com.battlesbudz.dinnerbot.core.model.Store
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
}
