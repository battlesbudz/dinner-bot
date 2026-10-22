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
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.battlesbudz.dinnerbot.core.data.DinnerBotDatabase
import com.battlesbudz.dinnerbot.core.data.PlanMapper
import com.battlesbudz.dinnerbot.core.data.SeedLoader
import com.battlesbudz.dinnerbot.core.data.SeedMapper
import com.battlesbudz.dinnerbot.core.model.BulkStock
import com.battlesbudz.dinnerbot.core.model.Costing
import com.battlesbudz.dinnerbot.core.model.HouseholdSettings
import com.battlesbudz.dinnerbot.core.model.PantryItem
import com.battlesbudz.dinnerbot.core.model.PriceItem
import com.battlesbudz.dinnerbot.core.model.Recipe
import com.battlesbudz.dinnerbot.feature.backup.BackupData
import com.battlesbudz.dinnerbot.feature.backup.BackupEngine
import com.battlesbudz.dinnerbot.feature.grocerylist.GroceryRow
import com.battlesbudz.dinnerbot.feature.grocerylist.GroceryListScreen
import com.battlesbudz.dinnerbot.feature.onboarding.OnboardingDefaults
import com.battlesbudz.dinnerbot.feature.onboarding.OnboardingForm
import com.battlesbudz.dinnerbot.feature.onboarding.SettingsScreen
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
            }
        }
    }
}

private enum class Tab(val label: String) {
    RECIPES("Recipes"), PLANNER("Plan"), LIST("List"),
    PANTRY("Pantry"), PRICES("Prices"), SETTINGS("Settings"),
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
    var tab by remember { mutableStateOf(Tab.RECIPES) }
    var openRecipeId by remember { mutableStateOf<String?>(null) }
    var variation by remember { mutableStateOf(0) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var reloadKey by remember { mutableStateOf(0) }

    LaunchedEffect(reloadKey) {
        loading = true
        val loaded = withContext(Dispatchers.IO) {
            SeedLoader(database).seedIfEmpty(loadAsset("pricebook.json"), loadAsset("recipes.json"))
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
            LoadedState(s, r, p, pantry, stocks, plan, rows)
        }
        settings = loaded.settings
        recipes = loaded.recipes
        priceItems = loaded.priceItems
        pantryItems = loaded.pantryItems
        bulkStocks = loaded.bulkStocks
        storedPlan = loaded.plan
        groceryRows = loaded.groceryRows
        loading = false
    }

    fun refresh() { reloadKey += 1 }

    fun persistPlan(plan: WeekPlanner.WeekPlan, keepId: String?) {
        val currentSettings = settings ?: return
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

    fun generatePlan() {
        val currentSettings = settings ?: return
        variation += 1
        val plan = WeekPlanner.generate(
            settings = currentSettings,
            recipes = recipes,
            priceItems = priceItems,
            stockOnHand = PantryLogic.stockMap(bulkStocks),
            pantryOwnedNames = PantryLogic.ownedNames(pantryItems),
            weekStart = currentWeekStart(),
            variation = variation,
        )
        persistPlan(plan, keepId = null)
        tab = Tab.PLANNER
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
        settings?.onboardingComplete != true -> OnboardingForm(
            initial = settings ?: OnboardingDefaults.initialSettings(),
            onSave = { saved ->
                scope.launch {
                    withContext(Dispatchers.IO) {
                        database.settingsDao().upsert(with(SeedMapper) { saved.toEntity() })
                        SeedLoader(database).seedPantryIfEmpty(
                            OnboardingDefaults.DEFAULT_STAPLES, saved.pantryStaples,
                        )
                    }
                    refresh()
                }
            },
        )
        else -> {
            val currentSettings = settings!!
            val recipesById = remember(recipes) { recipes.associateBy { it.id } }
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
                                )
                            } else {
                                RecipeListScreen(
                                    recipes = recipes,
                                    priceItems = priceItems,
                                    onRecipeClick = { openRecipeId = it.id },
                                )
                            }
                        }
                        Tab.PLANNER -> PlannerScreen(
                            plan = weekPlanView,
                            recipesById = recipesById,
                            budgetCents = currentSettings.weeklyBudgetCents,
                            onGenerate = { generatePlan() },
                            onSwap = { day ->
                                val current = weekPlanView
                                if (current != null && storedPlan != null) {
                                    val swapped = WeekPlanner.swapDinner(
                                        settings = currentSettings,
                                        current = current,
                                        day = day,
                                        recipes = recipes,
                                        priceItems = priceItems,
                                        stockOnHand = PantryLogic.stockMap(bulkStocks),
                                        pantryOwnedNames = PantryLogic.ownedNames(pantryItems),
                                    )
                                    persistPlan(swapped, keepId = storedPlan!!.id)
                                }
                            },
                        )
                        Tab.LIST -> GroceryListScreen(
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
                        Tab.SETTINGS -> Column(modifier = Modifier.fillMaxSize()) {
                            Box(modifier = Modifier.weight(1f)) {
                                SettingsScreen(
                                    initial = currentSettings,
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
                Surface(shadowElevation = 8.dp) {
                    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp)) {
                        Tab.entries.forEach { entry ->
                            if (entry == tab) {
                                Button(onClick = { tab = entry }, modifier = Modifier.weight(1f)) {
                                    Text(entry.label)
                                }
                            } else {
                                TextButton(onClick = { tab = entry }, modifier = Modifier.weight(1f)) {
                                    Text(entry.label)
                                }
                            }
                        }
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
    // Plans were priced from the old book; clear them so nothing stale lingers.
    database.planDao().deleteAll()
    database.groceryDao().deleteAll()
}
