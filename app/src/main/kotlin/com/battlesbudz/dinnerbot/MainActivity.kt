package com.battlesbudz.dinnerbot

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.battlesbudz.dinnerbot.core.data.DinnerBotDatabase
import com.battlesbudz.dinnerbot.core.data.PlanMapper
import com.battlesbudz.dinnerbot.core.data.RecipeRatingEntity
import com.battlesbudz.dinnerbot.core.data.SeedDataParser
import com.battlesbudz.dinnerbot.core.data.SeedLoader
import com.battlesbudz.dinnerbot.core.data.SeedMapper
import com.battlesbudz.dinnerbot.core.model.BulkStock
import com.battlesbudz.dinnerbot.core.model.Costing
import com.battlesbudz.dinnerbot.core.model.DietPreset
import com.battlesbudz.dinnerbot.core.model.DietPresets
import com.battlesbudz.dinnerbot.core.model.HouseholdSettings
import com.battlesbudz.dinnerbot.core.model.PantryItem
import com.battlesbudz.dinnerbot.core.model.PriceItem
import com.battlesbudz.dinnerbot.core.model.Recipe
import com.battlesbudz.dinnerbot.core.model.RecipeAdaptation
import com.battlesbudz.dinnerbot.core.model.RecipeRating
import com.battlesbudz.dinnerbot.feature.backup.BackupData
import com.battlesbudz.dinnerbot.feature.backup.BackupRecipeRating
import com.battlesbudz.dinnerbot.feature.backup.BackupEngine
import com.battlesbudz.dinnerbot.feature.grocerylist.GroceryRow
import com.battlesbudz.dinnerbot.feature.grocerylist.GroceryListScreen
import com.battlesbudz.dinnerbot.feature.onboarding.HowItWorksScreen
import com.battlesbudz.dinnerbot.feature.onboarding.OnboardingDefaults
import com.battlesbudz.dinnerbot.feature.onboarding.SettingsScreen
import com.battlesbudz.dinnerbot.feature.onboarding.SetupWalkthrough
import com.battlesbudz.dinnerbot.feature.pantry.PantryLogic
import com.battlesbudz.dinnerbot.feature.pantry.PantryScreen
import com.battlesbudz.dinnerbot.feature.planner.PlannerScreen
import com.battlesbudz.dinnerbot.feature.planner.WeekPlanner
import com.battlesbudz.dinnerbot.feature.pricebook.PriceBookScreen
import com.battlesbudz.dinnerbot.feature.pricebook.PriceCorrection
import com.battlesbudz.dinnerbot.feature.recipes.RecipeDetailScreen
import com.battlesbudz.dinnerbot.feature.recipes.RecipeListScreen
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import kotlin.math.roundToInt
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
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                val priceJson = assets.open("pricebook.json").bufferedReader().use { it.readText() }
                val recipesJson = assets.open("recipes.json").bufferedReader().use { it.readText() }
                SeedLoader(database).seedIfEmpty(priceJson, recipesJson)
                SeedLoader(database).refreshSeedRecipes(recipesJson)
                SeedLoader(database).refreshSeedPriceNotes(priceJson)
            }
        }
    }
}

private enum class Tab(val label: String, val icon: ImageVector) {
    RECIPES("Recipes", Icons.Filled.MenuBook),
    PLANNER("Plan", Icons.Filled.CalendarMonth),
    LIST("List", Icons.Filled.ShoppingCart),
    PANTRY("Pantry", Icons.Filled.Inventory2),
    PRICES("Prices", Icons.Filled.AttachMoney),
    SETTINGS("Settings", Icons.Filled.Settings),
}

@Composable
private fun DinnerBotApp(
    database: DinnerBotDatabase,
    loadAsset: (String) -> String,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var loading by remember { mutableStateOf(true) }
    var settings by remember { mutableStateOf<HouseholdSettings?>(null) }
    var recipes by remember { mutableStateOf<List<Recipe>>(emptyList()) }
    var priceItems by remember { mutableStateOf<List<PriceItem>>(emptyList()) }
    var pantryItems by remember { mutableStateOf<List<PantryItem>>(emptyList()) }
    var bulkStocks by remember { mutableStateOf<List<BulkStock>>(emptyList()) }
    var storedPlan by remember { mutableStateOf<PlanMapper.StoredPlan?>(null) }
    var groceryRows by remember { mutableStateOf<List<GroceryRow>>(emptyList()) }
    var ratings by remember { mutableStateOf<Map<String, RecipeRating>>(emptyMap()) }
    var presets by remember { mutableStateOf<List<DietPreset>>(emptyList()) }
    var tab by remember { mutableStateOf(Tab.RECIPES) }
    var openRecipeId by remember { mutableStateOf<String?>(null) }
    var variation by remember { mutableStateOf(0) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var reloadKey by remember { mutableStateOf(0) }
    var showHowItWorks by remember { mutableStateOf(false) }

    LaunchedEffect(reloadKey) {
        loading = true
        val loaded = withContext(Dispatchers.IO) {
            SeedLoader(database).seedIfEmpty(loadAsset("pricebook.json"), loadAsset("recipes.json"))
            SeedLoader(database).refreshSeedRecipes(loadAsset("recipes.json"))
            SeedLoader(database).refreshSeedPriceNotes(loadAsset("pricebook.json"))
            val loadedPresets = runCatching {
                SeedDataParser.parseDietPresets(loadAsset("diet-presets.json"))
            }.getOrDefault(emptyList())
            val s = database.settingsDao().get()?.let { with(SeedMapper) { it.toModel() } }
                ?: OnboardingDefaults.initialSettings()
            SeedLoader(database).seedPantryIfEmpty(OnboardingDefaults.DEFAULT_STAPLES, s.pantryStaples)
            val r = database.recipeDao().getAll().map { with(SeedMapper) { it.toModel() } }
            val p = database.priceItemDao().getAll().map { with(SeedMapper) { it.toModel() } }
            val pantry = database.pantryDao().getAll().map { with(SeedMapper) { it.toModel() } }
            val stocks = database.bulkStockDao().getAll().map { with(SeedMapper) { it.toModel() } }
            val plan = database.planDao().getLatest()?.let { with(PlanMapper) { it.toStoredPlan() } }
            val rows = plan?.let { pl ->
                database.groceryDao().getForPlan(pl.id).map {
                    GroceryRow(it.rowId, with(PlanMapper) { it.toModel() })
                }
            } ?: emptyList()
            val loadedRatings = database.recipeRatingDao().getAll().mapNotNull { entity ->
                runCatching { RecipeRating.valueOf(entity.rating) }.getOrNull()
                    ?.let { entity.recipeId to it }
            }.toMap()
            LoadedState(s, r, p, pantry, stocks, plan, rows, loadedRatings, loadedPresets)
        }
        settings = loaded.settings
        recipes = loaded.recipes
        priceItems = loaded.priceItems
        pantryItems = loaded.pantryItems
        bulkStocks = loaded.bulkStocks
        storedPlan = loaded.plan
        groceryRows = loaded.groceryRows
        ratings = loaded.ratings
        presets = loaded.presets
        loading = false
    }

    fun refresh() { reloadKey += 1 }

    fun persistPlan(plan: WeekPlanner.WeekPlan, keepId: String?, forSettings: HouseholdSettings) {
        val currentSettings = forSettings
        scope.launch {
            withContext(Dispatchers.IO) {
                val id = keepId ?: "plan-${System.currentTimeMillis()}"
                if (keepId == null) {
                    database.planDao().deleteAll()
                    database.groceryDao().deleteAll()
                } else {
                    database.groceryDao().deleteForPlan(id)
                }
                val stored = PlanMapper.StoredPlan(
                    id = id,
                    weekStart = plan.weekStart,
                    budgetCents = currentSettings.weeklyBudgetCents,
                    cashNeededCents = plan.cashNeededCents,
                    foodCostCents = plan.foodCostCents,
                    samsMonthlySavingsCents = plan.samsMonthlySavingsCents,
                    nights = plan.nights,
                    notes = plan.notes,
                    createdAtMillis = System.currentTimeMillis(),
                )
                database.planDao().insert(with(PlanMapper) { stored.toEntity() })
                database.groceryDao().insertAll(plan.lines.map { with(PlanMapper) { it.toEntity(id) } })
            }
            refresh()
        }
    }

    /**
     * The won't-eat rule, applied once for a given settings: removable
     * ingredients stripped, essential-blocked recipes held out of planning.
     * The selected diet presets' won't-eat entries join the household's own
     * list before adapting (Phase V2-2); the household's own entries always
     * stay, and presets never remove any of them.
     */
    fun adaptationFor(forSettings: HouseholdSettings): RecipeAdaptation.Result =
        RecipeAdaptation.adapt(
            recipes,
            DietPresets.effectiveWontEat(forSettings, presets),
            priceItems.associateBy { it.id },
        )

    /**
     * The pool generation and swaps plan from: the adapted recipes narrowed
     * further by the selected presets' tag rules (Phase V2-2). Browsing the
     * recipe list is not narrowed; only generated weeks are.
     */
    fun plannerPool(forSettings: HouseholdSettings): List<Recipe> =
        DietPresets.applyTagFilters(
            adaptationFor(forSettings).plannable,
            DietPresets.selected(forSettings, presets),
        )

    fun generatePlan(forSettings: HouseholdSettings) {
        variation += 1
        val plan = WeekPlanner.generate(
            settings = forSettings,
            recipes = plannerPool(forSettings),
            priceItems = priceItems,
            stockOnHand = PantryLogic.stockMap(bulkStocks),
            pantryOwnedNames = PantryLogic.ownedNames(pantryItems),
            weekStart = currentWeekStart(),
            variation = variation,
            ratings = ratings,
        )
        persistPlan(plan, keepId = null, forSettings = forSettings)
        tab = Tab.PLANNER
    }

    /** Set, change, or clear (null) one recipe's thumbs rating, then reload. */
    fun rateRecipe(recipeId: String, rating: RecipeRating?) {
        scope.launch {
            withContext(Dispatchers.IO) {
                if (rating == null) {
                    database.recipeRatingDao().delete(recipeId)
                } else {
                    database.recipeRatingDao().upsert(
                        RecipeRatingEntity(recipeId, rating.name, System.currentTimeMillis()),
                    )
                }
            }
            refresh()
        }
    }

    // ---- Backup through the system file picker (Storage Access Framework) ----
    var pendingExport by remember { mutableStateOf<String?>(null) }
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        val text = pendingExport
        pendingExport = null
        if (uri != null && text != null) {
            scope.launch {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(text.toByteArray(Charsets.UTF_8))
                    }
                }
                statusMessage = "Backup saved."
            }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val result = withContext(Dispatchers.IO) {
                    runCatching {
                        val text = context.contentResolver.openInputStream(uri)
                            ?.bufferedReader()?.use { it.readText() }
                            ?: throw IllegalArgumentException("That file could not be read.")
                        val data = BackupEngine.import(text)
                        applyBackup(database, data)
                        data
                    }
                }
                statusMessage = result.fold(
                    onSuccess = { "Backup restored: ${it.priceItems.size} prices, ${it.pantryItems.size} pantry items." },
                    onFailure = { it.message ?: "That backup could not be restored." },
                )
                refresh()
            }
        }
    }

    when {
        loading && settings == null -> Box(Modifier.fillMaxSize().padding(16.dp)) {
            Text("Loading Dinner Bot...")
        }
        settings?.onboardingComplete != true -> SetupWalkthrough(
            initial = settings ?: OnboardingDefaults.initialSettings(),
            onFinish = { saved ->
                scope.launch {
                    withContext(Dispatchers.IO) {
                        database.settingsDao().upsert(with(SeedMapper) { saved.toEntity() })
                        SeedLoader(database).seedPantryIfEmpty(
                            OnboardingDefaults.DEFAULT_STAPLES, saved.pantryStaples,
                        )
                    }
                    generatePlan(saved)
                    refresh()
                }
            },
        )
        else -> {
            val currentSettings = settings!!
            // Display and cost the ADAPTED recipes: removable won't-eat
            // ingredients are out everywhere; blocked recipes still browse.
            val adaptation = remember(recipes, priceItems, currentSettings.wontEat, currentSettings.dietPresets, presets) {
                adaptationFor(currentSettings)
            }
            val displayRecipes = adaptation.recipes
            val recipesById = remember(displayRecipes) { displayRecipes.associateBy { it.id } }
            val weekPlanView = remember(storedPlan, groceryRows) {
                storedPlan?.let { sp ->
                    WeekPlanner.WeekPlan(
                        weekStart = sp.weekStart,
                        nights = sp.nights,
                        lines = groceryRows.map { it.line },
                        cashNeededCents = sp.cashNeededCents,
                        foodCostCents = sp.foodCostCents,
                        samsMonthlySavingsCents = sp.samsMonthlySavingsCents,
                        samsGateMet = sp.samsMonthlySavingsCents >= Costing.SAMS_MONTHLY_GATE_CENTS,
                        deferredRestocks = emptyList(),
                        notes = sp.notes,
                    )
                }
            }
            Column(modifier = Modifier.fillMaxSize()) {
                Box(modifier = Modifier.weight(1f)) {
                    when (tab) {
                        Tab.RECIPES -> {
                            val open = openRecipeId?.let { recipesById[it] }
                            if (open != null) {
                                RecipeDetailScreen(
                                    recipe = open,
                                    priceItems = priceItems,
                                    onBack = { openRecipeId = null },
                                    rating = ratings[open.id],
                                    onRate = { rateRecipe(open.id, it) },
                                )
                            } else {
                                RecipeListScreen(
                                    recipes = displayRecipes,
                                    priceItems = priceItems,
                                    onRecipeClick = { openRecipeId = it.id },
                                    ratings = ratings,
                                )
                            }
                        }
                        Tab.PLANNER -> PlannerScreen(
                            plan = weekPlanView,
                            recipesById = recipesById,
                            budgetCents = currentSettings.weeklyBudgetCents,
                            onGenerate = { generatePlan(currentSettings) },
                            onSwap = { day ->
                                val current = weekPlanView
                                if (current != null && storedPlan != null) {
                                    val swapped = WeekPlanner.swapDinner(
                                        settings = currentSettings,
                                        current = current,
                                        day = day,
                                        recipes = plannerPool(currentSettings),
                                        priceItems = priceItems,
                                        stockOnHand = PantryLogic.stockMap(bulkStocks),
                                        pantryOwnedNames = PantryLogic.ownedNames(pantryItems),
                                        ratings = ratings,
                                    )
                                    persistPlan(swapped, keepId = storedPlan!!.id, forSettings = currentSettings)
                                }
                            },
                            onSetNightCooking = { day, cooking, reason ->
                                val current = weekPlanView
                                if (current != null && storedPlan != null) {
                                    val updated = WeekPlanner.setNightCooking(
                                        settings = currentSettings,
                                        current = current,
                                        day = day,
                                        cooking = cooking,
                                        reason = reason,
                                        recipes = plannerPool(currentSettings),
                                        priceItems = priceItems,
                                        stockOnHand = PantryLogic.stockMap(bulkStocks),
                                        pantryOwnedNames = PantryLogic.ownedNames(pantryItems),
                                        ratings = ratings,
                                    )
                                    persistPlan(updated, keepId = storedPlan!!.id, forSettings = currentSettings)
                                }
                            },
                            ratings = ratings,
                            onRate = { recipeId, rating -> rateRecipe(recipeId, rating) },
                            outOfRotationCount = ratings.values.count { it == RecipeRating.DOWN },
                        )
                        Tab.LIST -> GroceryListScreen(
                            priceItems = priceItems,
                            rows = groceryRows,
                            onToggleChecked = { row, checked ->
                                scope.launch {
                                    withContext(Dispatchers.IO) {
                                        database.groceryDao().setChecked(row.rowId, checked)
                                    }
                                    refresh()
                                }
                            },
                            onPriceCorrected = { row, totalCents ->
                                scope.launch {
                                    withContext(Dispatchers.IO) {
                                        correctLinePrice(database, row, totalCents)
                                    }
                                    refresh()
                                }
                            },
                        )
                        Tab.PANTRY -> PantryScreen(
                            pantryItems = pantryItems,
                            bulkPriceItems = PantryLogic.bulkItems(priceItems),
                            stocks = bulkStocks,
                            onTogglePantry = { item ->
                                val toggled = item.copy(owned = !item.owned)
                                scope.launch {
                                    withContext(Dispatchers.IO) {
                                        database.pantryDao().upsert(with(SeedMapper) { toggled.toEntity() })
                                        syncPantryStaples(database, currentSettings)
                                    }
                                    refresh()
                                }
                            },
                            onAddStaple = { name ->
                                val item = PantryLogic.newStaple(name)
                                scope.launch {
                                    withContext(Dispatchers.IO) {
                                        database.pantryDao().upsert(with(SeedMapper) { item.toEntity() })
                                        syncPantryStaples(database, currentSettings)
                                    }
                                    refresh()
                                }
                            },
                            onStockChanged = { stock ->
                                scope.launch {
                                    withContext(Dispatchers.IO) {
                                        if (stock.onHand.amount <= 0.0) {
                                            database.bulkStockDao().delete(stock.itemId)
                                        } else {
                                            database.bulkStockDao().upsert(with(SeedMapper) { stock.toEntity() })
                                        }
                                    }
                                    refresh()
                                }
                            },
                        )
                        Tab.PRICES -> PriceBookScreen(
                            priceItems = priceItems,
                            onItemEdited = { item ->
                                scope.launch {
                                    withContext(Dispatchers.IO) {
                                        database.priceItemDao().upsert(with(SeedMapper) { item.toEntity() })
                                    }
                                    refresh()
                                }
                            },
                        )
                        Tab.SETTINGS -> if (showHowItWorks) {
                            HowItWorksScreen(onDone = { showHowItWorks = false })
                        } else Column(modifier = Modifier.fillMaxSize()) {
                            OutlinedButton(
                                onClick = { showHowItWorks = true },
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                            ) { Text("How Dinner Bot works") }
                            Box(modifier = Modifier.weight(1f)) {
                                SettingsScreen(
                                    initial = currentSettings,
                                    dietPresets = presets,
                                    onSave = { saved ->
                                        scope.launch {
                                            withContext(Dispatchers.IO) {
                                                database.settingsDao().upsert(
                                                    with(SeedMapper) { saved.toEntity() },
                                                )
                                            }
                                            statusMessage = "Settings saved."
                                            refresh()
                                        }
                                    },
                                )
                            }
                            statusMessage?.let {
                                Text(it, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Button(
                                    onClick = {
                                        pendingExport = BackupEngine.export(
                                            BackupData(
                                                settings = currentSettings,
                                                priceItems = priceItems,
                                                pantryItems = pantryItems,
                                                bulkStock = bulkStocks,
                                                recipeRatings = ratings.map { (id, rating) ->
                                                    BackupRecipeRating(id, rating)
                                                },
                                            ),
                                        )
                                        exportLauncher.launch("dinner-bot-backup.json")
                                    },
                                    modifier = Modifier.weight(1f),
                                ) { Text("Export backup") }
                                Button(
                                    onClick = { importLauncher.launch(arrayOf("application/json")) },
                                    modifier = Modifier.weight(1f),
                                ) { Text("Import backup") }
                            }
                        }
                    }
                }
                // Material 3 bottom navigation: six destinations stay
                // reachable with icons, and labels show only on the selected
                // tab so they can never crush or wrap letter by letter the
                // way the old hand-rolled row of full-width buttons did.
                NavigationBar {
                    Tab.entries.forEach { entry ->
                        NavigationBarItem(
                            selected = entry == tab,
                            onClick = { tab = entry },
                            icon = { Icon(entry.icon, contentDescription = entry.label) },
                            label = { Text(entry.label, maxLines = 1) },
                            alwaysShowLabel = false,
                            modifier = Modifier.testTag("tab-${entry.label}"),
                        )
                    }
                }
            }
        }
    }
}

private data class LoadedState(
    val settings: HouseholdSettings,
    val recipes: List<Recipe>,
    val priceItems: List<PriceItem>,
    val pantryItems: List<PantryItem>,
    val bulkStocks: List<BulkStock>,
    val plan: PlanMapper.StoredPlan?,
    val groceryRows: List<GroceryRow>,
    val ratings: Map<String, RecipeRating>,
    val presets: List<DietPreset>,
)

private fun currentWeekStart(): String =
    LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toString()

/** Keep settings.pantryStaples mirroring the pantry table's owned names. */
private suspend fun syncPantryStaples(database: DinnerBotDatabase, settings: HouseholdSettings) {
    val owned = database.pantryDao().getAll().filter { it.owned }.map { it.name }
    if (owned.toSet() != settings.pantryStaples.toSet()) {
        database.settingsDao().upsert(with(SeedMapper) { settings.copy(pantryStaples = owned).toEntity() })
    }
}

/**
 * Andrea tapped a price on the shopping list. Her total for the line becomes
 * the price book's truth: a normal line updates the item's pack price; a
 * rerouted line (bought at a different store) updates that store's per-unit
 * alternate for the item.
 */
private suspend fun correctLinePrice(database: DinnerBotDatabase, row: GroceryRow, totalCents: Int) {
    val entity = database.priceItemDao().getById(row.line.itemId)
    var newPackPrice = totalCents
    if (entity != null) {
        val item = with(SeedMapper) { entity.toModel() }
        if (row.line.store == item.homeStore) {
            val perPack = (totalCents.toDouble() / row.line.packsToBuy.coerceAtLeast(1))
                .roundToInt().coerceAtLeast(1)
            database.priceItemDao().upsert(
                with(SeedMapper) { PriceCorrection.applyPackPrice(item, perPack).toEntity() },
            )
            newPackPrice = perPack
        } else {
            val neededInPackUnit = row.line.needed.convertTo(item.packQuantity.unit).amount
            if (neededInPackUnit > 0.0) {
                val perUnit = totalCents.toDouble() / neededInPackUnit
                database.priceItemDao().upsert(
                    with(SeedMapper) {
                        PriceCorrection.applyAlternatePrice(item, row.line.store, perUnit).toEntity()
                    },
                )
            }
        }
    }
    database.groceryDao().updatePackPrice(row.rowId, newPackPrice)
}

private suspend fun applyBackup(database: DinnerBotDatabase, data: BackupData) {
    database.settingsDao().upsert(with(SeedMapper) { data.settings.toEntity() })
    database.priceItemDao().deleteAll()
    database.priceItemDao().insertAll(data.priceItems.map { with(SeedMapper) { it.toEntity() } })
    database.pantryDao().deleteAll()
    database.pantryDao().insertAll(data.pantryItems.map { with(SeedMapper) { it.toEntity() } })
    database.bulkStockDao().deleteAll()
    data.bulkStock.forEach { database.bulkStockDao().upsert(with(SeedMapper) { it.toEntity() }) }
    // Ratings replace wholesale: a v1 backup has none, which clears them.
    database.recipeRatingDao().deleteAll()
    data.recipeRatings.forEach { saved ->
        database.recipeRatingDao().upsert(
            RecipeRatingEntity(saved.recipeId, saved.rating.name, System.currentTimeMillis()),
        )
    }
    // Plans were priced from the old book; clear them so nothing stale lingers.
    database.planDao().deleteAll()
    database.groceryDao().deleteAll()
}
