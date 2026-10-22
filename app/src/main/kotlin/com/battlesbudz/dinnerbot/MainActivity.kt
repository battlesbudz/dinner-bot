package com.battlesbudz.dinnerbot

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.battlesbudz.dinnerbot.core.data.DinnerBotDatabase
import com.battlesbudz.dinnerbot.core.data.SeedLoader
import com.battlesbudz.dinnerbot.core.data.SeedMapper
import com.battlesbudz.dinnerbot.core.model.HouseholdSettings
import com.battlesbudz.dinnerbot.core.model.PriceItem
import com.battlesbudz.dinnerbot.core.model.Recipe
import com.battlesbudz.dinnerbot.feature.onboarding.OnboardingDefaults
import com.battlesbudz.dinnerbot.feature.onboarding.OnboardingForm
import com.battlesbudz.dinnerbot.feature.recipes.RecipeDetailScreen
import com.battlesbudz.dinnerbot.feature.recipes.RecipeListScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    private val database by lazy { DinnerBotDatabase.create(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    DinnerBotApp(database = database, loadAsset = { name ->
                        assets.open(name).bufferedReader().use { it.readText() }
                    })
                }
            }
        }
        // Seed on first launch, off the main thread.
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                val priceJson = assets.open("pricebook.json").bufferedReader().use { it.readText() }
                val recipesJson = assets.open("recipes.json").bufferedReader().use { it.readText() }
                SeedLoader(database).seedIfEmpty(priceJson, recipesJson)
            }
        }
    }
}

private sealed interface Screen {
    data object Loading : Screen
    data object Onboarding : Screen
    data object RecipeList : Screen
    data class RecipeDetail(val recipeId: String) : Screen
}

@Composable
private fun DinnerBotApp(
    database: DinnerBotDatabase,
    loadAsset: (String) -> String,
) {
    var screen by remember { mutableStateOf<Screen>(Screen.Loading) }
    var settings by remember { mutableStateOf<HouseholdSettings?>(null) }
    var recipes by remember { mutableStateOf<List<Recipe>>(emptyList()) }
    var priceItems by remember { mutableStateOf<List<PriceItem>>(emptyList()) }
    var reloadKey by remember { mutableStateOf(0) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(reloadKey) {
        val loaded = withContext(Dispatchers.IO) {
            // Make sure seed data exists even if the Activity-level seed is still running.
            SeedLoader(database).seedIfEmpty(loadAsset("pricebook.json"), loadAsset("recipes.json"))
            val s = database.settingsDao().get()?.let { with(SeedMapper) { it.toModel() } }
                ?: OnboardingDefaults.initialSettings()
            val r = database.recipeDao().getAll().map { with(SeedMapper) { it.toModel() } }
            val p = database.priceItemDao().getAll().map { with(SeedMapper) { it.toModel() } }
            Triple(s, r, p)
        }
        // In-memory settings win over the database copy while a save is in flight.
        val effective = settings?.takeIf { it.onboardingComplete } ?: loaded.first
        settings = effective
        recipes = loaded.second
        priceItems = loaded.third
        screen = if (effective.onboardingComplete) Screen.RecipeList else Screen.Onboarding
    }

    when (val current = screen) {
        is Screen.Loading -> Box(Modifier.fillMaxSize().padding(16.dp)) {
            Text("Loading Dinner Bot...")
        }
        is Screen.Onboarding -> OnboardingForm(
            initial = settings ?: OnboardingDefaults.initialSettings(),
            onSave = { saved ->
                settings = saved
                screen = Screen.RecipeList
                scope.launch {
                    withContext(Dispatchers.IO) {
                        database.settingsDao().upsert(with(SeedMapper) { saved.toEntity() })
                    }
                    reloadKey += 1
                }
            },
        )
        is Screen.RecipeList -> RecipeListScreen(
            recipes = recipes,
            priceItems = priceItems,
            onRecipeClick = { screen = Screen.RecipeDetail(it.id) },
        )
        is Screen.RecipeDetail -> {
            val recipe = recipes.firstOrNull { it.id == current.recipeId }
            if (recipe == null) {
                LaunchedEffect(current.recipeId) { screen = Screen.RecipeList }
            } else {
                RecipeDetailScreen(
                    recipe = recipe,
                    priceItems = priceItems,
                    onBack = { screen = Screen.RecipeList },
                )
            }
        }
    }
}
