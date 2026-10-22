package com.battlesbudz.dinnerbot

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
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
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.battlesbudz.dinnerbot.core.data.BarcodeLinkEntity
import com.battlesbudz.dinnerbot.core.data.DinnerBotDatabase
import com.battlesbudz.dinnerbot.core.data.PlanMapper
import com.battlesbudz.dinnerbot.core.data.RecipeRatingEntity
import com.battlesbudz.dinnerbot.core.data.SeedDataParser
import com.battlesbudz.dinnerbot.core.data.SeedLoader
import com.battlesbudz.dinnerbot.core.data.SeedMapper
import com.battlesbudz.dinnerbot.core.model.BarcodeLink
import com.battlesbudz.dinnerbot.core.model.BulkStock
import com.battlesbudz.dinnerbot.core.model.Costing
import com.battlesbudz.dinnerbot.core.model.DietPreset
import com.battlesbudz.dinnerbot.core.model.DietPresets
import com.battlesbudz.dinnerbot.core.model.HouseholdSettings
import com.battlesbudz.dinnerbot.core.model.PantryItem
import com.battlesbudz.dinnerbot.core.model.Perishables
import com.battlesbudz.dinnerbot.core.model.PriceItem
import com.battlesbudz.dinnerbot.core.model.Recipe
import com.battlesbudz.dinnerbot.core.model.RecipeAdaptation
import com.battlesbudz.dinnerbot.core.model.RecipeRating
import com.battlesbudz.dinnerbot.feature.backup.BackupBarcodeLink
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
import com.battlesbudz.dinnerbot.feature.planner.FortnightPlannerScreen
import com.battlesbudz.dinnerbot.feature.planner.PlannerScreen
import com.battlesbudz.dinnerbot.feature.planner.WeekPlanner
import com.battlesbudz.dinnerbot.feature.pricebook.PriceBookScreen
import com.battlesbudz.dinnerbot.feature.pricebook.PriceCorrection
import com.battlesbudz.dinnerbot.feature.recipes.RecipeDetailScreen
import com.battlesbudz.dinnerbot.feature.recipes.RecipeListScreen
import com.battlesbudz.dinnerbot.feature.reminders.ReminderPlanner
import com.battlesbudz.dinnerbot.feature.reminders.ReminderScheduler
import com.battlesbudz.dinnerbot.feature.reminders.ReminderWeek
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
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
    var storedPlan2 by remember { mutableStateOf<PlanMapper.StoredPlan?>(null) }
    var groceryRows2 by remember { mutableStateOf<List<GroceryRow>>(emptyList()) }
    var ratings by remember { mutableStateOf<Map<String, RecipeRating>>(emptyMap()) }
    var barcodeLinks by remember { mutableStateOf<List<BarcodeLink>>(emptyList()) }
    var presets by remember { mutableStateOf<List<DietPreset>>(emptyList()) }
    var tab by remember { mutableStateOf(Tab.RECIPES) }
    var openRecipeId by remember { mutableStateOf<String?>(null) }
    var variation by remember { mutableStateOf(0) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var reloadKey by remember { mutableStateOf(0) }
    var showHowItWorks by remember { mutableStateOf(false) }

    // ---- Notification permission (Phase V2-4) ----
    // The effective state the settings form and the reminder planner
    // read: granted outright on Android versions with no notification
    // runtime permission. It is requested only at the moment a reminder
    // is switched on in Settings; the launcher result lands here, and
    // entering Settings re-reads it (it may have changed in Android's
    // own settings screens).
    var notificationsGranted by remember { mutableStateOf(hasNotificationPermission(context)) }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> notificationsGranted = granted }
    LaunchedEffect(tab) {
        if (tab == Tab.SETTINGS) notificationsGranted = hasNotificationPermission(context)
    }

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
            val recentPlans = database.planDao().getRecent(2)
                .map { with(PlanMapper) { it.toStoredPlan() } }
            val plan = recentPlans.firstOrNull()
            // A fortnight is stored as two plans (Phase V2-3) in the
            // existing plan storage: week 2's start is week 1's plus 7
            // days. Anything else in the second slot is not a pair.
            val plan2 = recentPlans.getOrNull(1)?.takeIf { second ->
                plan != null && runCatching {
                    LocalDate.parse(second.weekStart) == LocalDate.parse(plan.weekStart).plusWeeks(1)
                }.getOrDefault(false)
            }
            val rows = plan?.let { pl ->
                database.groceryDao().getForPlan(pl.id).map {
                    GroceryRow(it.rowId, with(PlanMapper) { it.toModel() })
                }
            } ?: emptyList()
            val rows2 = plan2?.let { pl ->
                database.groceryDao().getForPlan(pl.id).map {
                    GroceryRow(it.rowId, with(PlanMapper) { it.toModel() })
                }
            } ?: emptyList()
            val loadedRatings = database.recipeRatingDao().getAll().mapNotNull { entity ->
                runCatching { RecipeRating.valueOf(entity.rating) }.getOrNull()
                    ?.let { entity.recipeId to it }
            }.toMap()
            val loadedLinks = database.barcodeLinkDao().getAll()
                .map { with(SeedMapper) { it.toModel() } }
            LoadedState(
                s, r, p, pantry, stocks, plan, rows, plan2, rows2,
                loadedRatings, loadedPresets, loadedLinks,
            )
        }
        settings = loaded.settings
        recipes = loaded.recipes
        priceItems = loaded.priceItems
        pantryItems = loaded.pantryItems
        bulkStocks = loaded.bulkStocks
        storedPlan = loaded.plan
        groceryRows = loaded.groceryRows
        storedPlan2 = loaded.plan2
        groceryRows2 = loaded.groceryRows2
        ratings = loaded.ratings
        presets = loaded.presets
        barcodeLinks = loaded.barcodeLinks
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

    /**
     * Persist a fortnight (Phase V2-3) as two plans in the existing plan
     * storage: week 1 and week 2, each with its own grocery rows and
     * check-offs. No schema change: a plan has always been a week, and a
     * fortnight is two of them a week apart. A fresh generation clears the
     * old plans exactly like a one-week generation does.
     */
    fun persistFortnight(
        fortnight: WeekPlanner.FortnightPlan,
        keepIds: Pair<String, String>?,
        forSettings: HouseholdSettings,
    ) {
        scope.launch {
            withContext(Dispatchers.IO) {
                val base = "plan-${System.currentTimeMillis()}"
                val id1 = keepIds?.first ?: "$base-w1"
                val id2 = keepIds?.second ?: "$base-w2"
                if (keepIds == null) {
                    database.planDao().deleteAll()
                    database.groceryDao().deleteAll()
                } else {
                    database.groceryDao().deleteForPlan(id1)
                    database.groceryDao().deleteForPlan(id2)
                }
                val now = System.currentTimeMillis()
                listOf(
                    Triple(id1, fortnight.week1, now),
                    Triple(id2, fortnight.week2, now + 1),
                ).forEach { (id, week, createdAt) ->
                    val stored = PlanMapper.StoredPlan(
                        id = id,
                        weekStart = week.weekStart,
                        budgetCents = forSettings.weeklyBudgetCents,
                        cashNeededCents = week.cashNeededCents,
                        foodCostCents = week.foodCostCents,
                        samsMonthlySavingsCents = week.samsMonthlySavingsCents,
                        nights = week.nights,
                        notes = week.notes,
                        createdAtMillis = createdAt,
                    )
                    database.planDao().insert(with(PlanMapper) { stored.toEntity() })
                    database.groceryDao().insertAll(week.lines.map { with(PlanMapper) { it.toEntity(id) } })
                }
            }
            refresh()
        }
    }

    /** Plan two weeks on one trip (Phase V2-3). */
    fun generateTwoWeekPlan(forSettings: HouseholdSettings) {
        variation += 1
        val week1Start = currentWeekStart()
        val fortnight = WeekPlanner.generateFortnight(
            settings = forSettings,
            recipes = plannerPool(forSettings),
            priceItems = priceItems,
            stockOnHand = PantryLogic.stockMap(bulkStocks),
            pantryOwnedNames = PantryLogic.ownedNames(pantryItems),
            week1Start = week1Start,
            week2Start = LocalDate.parse(week1Start).plusWeeks(1).toString(),
            variation = variation,
            ratings = ratings,
        )
        persistFortnight(fortnight, keepIds = null, forSettings = forSettings)
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

    /**
     * Reminders follow the stored state (Phase V2-4). Every reload after
     * a plan change (generate, swap, night-off toggle, reprice), a pantry
     * or stock change, a settings save, or a permission change replans
     * the reminder set from the adapted recipes (what the household
     * actually cooks) and replaces the scheduled work wholesale, so a
     * reminder can never outlive the plan it was made from. WorkManager
     * keeps the requests on the phone and across reboots.
     */
    LaunchedEffect(
        settings, storedPlan, storedPlan2, recipes, priceItems, bulkStocks, presets, notificationsGranted,
    ) {
        val currentSettings = settings ?: return@LaunchedEffect
        val weeks = listOfNotNull(storedPlan, storedPlan2).mapNotNull { plan ->
            runCatching { LocalDate.parse(plan.weekStart) }.getOrNull()
                ?.let { ReminderWeek(it, plan.nights) }
        }
        val planned = ReminderPlanner.plan(
            settings = currentSettings.reminders,
            householdSize = currentSettings.householdSize,
            weeks = weeks,
            recipesById = adaptationFor(currentSettings).recipes.associateBy { it.id },
            itemsById = priceItems.associateBy { it.id },
            stockOnHand = PantryLogic.stockMap(bulkStocks),
            now = LocalDateTime.now(),
            notificationsGranted = notificationsGranted,
        )
        ReminderScheduler.reschedule(context, planned)
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
            val weekPlanView2 = remember(storedPlan2, groceryRows2) {
                storedPlan2?.let { sp ->
                    WeekPlanner.WeekPlan(
                        weekStart = sp.weekStart,
                        nights = sp.nights,
                        lines = groceryRows2.map { it.line },
                        cashNeededCents = sp.cashNeededCents,
                        foodCostCents = sp.foodCostCents,
                        samsMonthlySavingsCents = sp.samsMonthlySavingsCents,
                        samsGateMet = sp.samsMonthlySavingsCents >= Costing.SAMS_MONTHLY_GATE_CENTS,
                        deferredRestocks = emptyList(),
                        notes = sp.notes,
                    )
                }
            }
            // The stored fortnight as the engine's type, whenever both
            // weeks are present; the sections are recomposed from the two
            // weeks so the view always matches what was planned.
            val fortnightView = remember(weekPlanView, weekPlanView2, priceItems) {
                val w1 = weekPlanView
                val w2 = weekPlanView2
                if (w1 != null && w2 != null) {
                    WeekPlanner.composeFortnight(w1, w2, priceItems)
                } else {
                    null
                }
            }
            /**
             * Pantry ownership feeds costing: when a staple is marked owned,
             * added, or marked not owned, the current week is repriced in
             * place (same dinners, new costing) and persisted, so its list
             * and cash total immediately reflect what is already home.
             * Skipped when the reprice changes nothing.
             */
            fun repriceStoredPlan(forPantryItems: List<PantryItem>) {
                val sp = storedPlan ?: return
                val view = weekPlanView ?: return
                val sp2 = storedPlan2
                val view2 = weekPlanView2
                if (sp2 != null && view2 != null) {
                    // Fortnight: reprice week 1 against the real stock,
                    // then week 2 against the stock the repriced week 1
                    // leaves behind, holding the horizon gate the pair
                    // was planned under. Same dinners throughout.
                    val owned = PantryLogic.ownedNames(forPantryItems)
                    val stock = PantryLogic.stockMap(bulkStocks)
                    val dinners = (view.nights + view2.nights)
                        .filterNot { it.notCooking }
                        .mapNotNull { recipesById[it.recipeId] }
                    val gate = WeekPlanner.fortnightGateForDinners(currentSettings, dinners, priceItems)
                    val repriced1 = WeekPlanner.recost(
                        settings = currentSettings,
                        current = view,
                        recipes = adaptation.recipes,
                        priceItems = priceItems,
                        stockOnHand = stock,
                        pantryOwnedNames = owned,
                        gateOverride = gate,
                    )
                    val stock1 = WeekPlanner.projectedStockAfter(
                        currentSettings, repriced1, adaptation.recipes, priceItems, stock, owned, gate,
                    )
                    val repriced2 = WeekPlanner.recost(
                        settings = currentSettings,
                        current = view2,
                        recipes = adaptation.recipes,
                        priceItems = priceItems,
                        stockOnHand = stock1,
                        pantryOwnedNames = owned,
                        gateOverride = gate,
                    )
                    val changed = repriced1.lines != view.lines ||
                        repriced1.cashNeededCents != view.cashNeededCents ||
                        repriced1.notes != view.notes ||
                        repriced2.lines != view2.lines ||
                        repriced2.cashNeededCents != view2.cashNeededCents ||
                        repriced2.notes != view2.notes
                    if (changed) {
                        persistFortnight(
                            WeekPlanner.composeFortnight(repriced1, repriced2, priceItems),
                            keepIds = sp.id to sp2.id,
                            forSettings = currentSettings,
                        )
                    }
                    return
                }
                val repriced = WeekPlanner.recost(
                    settings = currentSettings,
                    current = view,
                    recipes = adaptation.recipes,
                    priceItems = priceItems,
                    stockOnHand = PantryLogic.stockMap(bulkStocks),
                    pantryOwnedNames = PantryLogic.ownedNames(forPantryItems),
                )
                if (repriced.lines != view.lines ||
                    repriced.cashNeededCents != view.cashNeededCents ||
                    repriced.notes != view.notes
                ) {
                    persistPlan(repriced, keepId = sp.id, forSettings = currentSettings)
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
                        Tab.PLANNER -> if (fortnightView != null && storedPlan != null && storedPlan2 != null) {
                            FortnightPlannerScreen(
                                plan = fortnightView,
                                recipesById = recipesById,
                                budgetCents = currentSettings.weeklyBudgetCents,
                                onRegenerateTwoWeeks = { generateTwoWeekPlan(currentSettings) },
                                onPlanOneWeek = { generatePlan(currentSettings) },
                                onSwap = { weekIndex, day ->
                                    val swapped = WeekPlanner.swapDinnerFortnight(
                                        settings = currentSettings,
                                        current = fortnightView,
                                        weekIndex = weekIndex,
                                        day = day,
                                        recipes = adaptation.recipes,
                                        priceItems = priceItems,
                                        stockOnHand = PantryLogic.stockMap(bulkStocks),
                                        pantryOwnedNames = PantryLogic.ownedNames(pantryItems),
                                        ratings = ratings,
                                        candidatePool = plannerPool(currentSettings),
                                    )
                                    persistFortnight(
                                        swapped,
                                        keepIds = storedPlan!!.id to storedPlan2!!.id,
                                        forSettings = currentSettings,
                                    )
                                },
                                onSetNightCooking = { weekIndex, day, cooking, reason ->
                                    val updated = WeekPlanner.setNightCookingFortnight(
                                        settings = currentSettings,
                                        current = fortnightView,
                                        weekIndex = weekIndex,
                                        day = day,
                                        cooking = cooking,
                                        reason = reason,
                                        recipes = adaptation.recipes,
                                        priceItems = priceItems,
                                        stockOnHand = PantryLogic.stockMap(bulkStocks),
                                        pantryOwnedNames = PantryLogic.ownedNames(pantryItems),
                                        ratings = ratings,
                                        candidatePool = plannerPool(currentSettings),
                                    )
                                    persistFortnight(
                                        updated,
                                        keepIds = storedPlan!!.id to storedPlan2!!.id,
                                        forSettings = currentSettings,
                                    )
                                },
                                ratings = ratings,
                                onRate = { recipeId, rating -> rateRecipe(recipeId, rating) },
                                outOfRotationCount = ratings.values.count { it == RecipeRating.DOWN },
                            )
                        } else PlannerScreen(
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
                                        recipes = adaptation.recipes,
                                        priceItems = priceItems,
                                        stockOnHand = PantryLogic.stockMap(bulkStocks),
                                        pantryOwnedNames = PantryLogic.ownedNames(pantryItems),
                                        ratings = ratings,
                                        candidatePool = plannerPool(currentSettings),
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
                                        recipes = adaptation.recipes,
                                        priceItems = priceItems,
                                        stockOnHand = PantryLogic.stockMap(bulkStocks),
                                        pantryOwnedNames = PantryLogic.ownedNames(pantryItems),
                                        ratings = ratings,
                                        candidatePool = plannerPool(currentSettings),
                                    )
                                    persistPlan(updated, keepId = storedPlan!!.id, forSettings = currentSettings)
                                }
                            },
                            ratings = ratings,
                            onRate = { recipeId, rating -> rateRecipe(recipeId, rating) },
                            outOfRotationCount = ratings.values.count { it == RecipeRating.DOWN },
                            onGenerateTwoWeeks = { generateTwoWeekPlan(currentSettings) },
                        )
                        Tab.LIST -> {
                            // Two-week plan: the list is the big trip (week
                            // 1's rows plus week 2's shelf-stable and frozen
                            // rows) and week 2's fresh top-up, split by the
                            // same category rule the planner used.
                            val priceById = priceItems.associateBy { it.id }
                            val isFortnightList = fortnightView != null
                            val topUpRows = if (isFortnightList) {
                                groceryRows2.filter {
                                    Perishables.isTopUpLine(it.line, priceById[it.line.itemId])
                                }
                            } else {
                                emptyList()
                            }
                            val bigTripRows = if (isFortnightList) {
                                groceryRows + groceryRows2.filterNot {
                                    Perishables.isTopUpLine(it.line, priceById[it.line.itemId])
                                }
                            } else {
                                groceryRows
                            }
                            GroceryListScreen(
                            priceItems = priceItems,
                            rows = bigTripRows,
                            topUpRows = topUpRows,
                            twoWeekTrip = isFortnightList,
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
                            barcodeLinks = barcodeLinks,
                            onBarcodeLinkLearned = { link ->
                                scope.launch {
                                    withContext(Dispatchers.IO) {
                                        database.barcodeLinkDao().upsert(
                                            with(SeedMapper) { link.toEntity() },
                                        )
                                    }
                                    refresh()
                                }
                            },
                            onScanLineCorrected = { row, totalCents ->
                                // A scan from a line, naming that line's item,
                                // is that line's correction: same path as a tap
                                // on its price, the row's store included.
                                scope.launch {
                                    withContext(Dispatchers.IO) {
                                        correctLinePrice(database, row, totalCents)
                                    }
                                    refresh()
                                }
                            },
                            onScanPriceCorrected = { corrected ->
                                scope.launch {
                                    withContext(Dispatchers.IO) {
                                        database.priceItemDao().upsert(
                                            with(SeedMapper) { corrected.toEntity() },
                                        )
                                        // This week's rows for the item at its
                                        // home store show the new pack price,
                                        // the same update a tap correction makes.
                                        (groceryRows + groceryRows2)
                                            .filter {
                                                it.line.itemId == corrected.id &&
                                                    it.line.store == corrected.homeStore
                                            }
                                            .forEach { row ->
                                                database.groceryDao()
                                                    .updatePackPrice(row.rowId, corrected.packPriceCents)
                                            }
                                    }
                                    refresh()
                                }
                            },
                            )
                        }
                        Tab.PANTRY -> PantryScreen(
                            pantryItems = pantryItems,
                            bulkPriceItems = PantryLogic.bulkItems(priceItems),
                            stocks = bulkStocks,
                            onTogglePantry = { item ->
                                val toggled = item.copy(owned = !item.owned)
                                val updatedPantry = pantryItems.map {
                                    if (it.id == item.id) toggled else it
                                }
                                scope.launch {
                                    withContext(Dispatchers.IO) {
                                        database.pantryDao().upsert(with(SeedMapper) { toggled.toEntity() })
                                        syncPantryStaples(database, currentSettings)
                                    }
                                    repriceStoredPlan(updatedPantry)
                                    refresh()
                                }
                            },
                            onAddStaple = { name ->
                                val item = PantryLogic.newStaple(name)
                                val updatedPantry = pantryItems.filterNot { it.id == item.id } + item
                                scope.launch {
                                    withContext(Dispatchers.IO) {
                                        database.pantryDao().upsert(with(SeedMapper) { item.toEntity() })
                                        syncPantryStaples(database, currentSettings)
                                    }
                                    repriceStoredPlan(updatedPantry)
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
                                    notificationsGranted = notificationsGranted,
                                    onRequestNotificationPermission = {
                                        if (Build.VERSION.SDK_INT >= 33) {
                                            notificationPermissionLauncher.launch(
                                                Manifest.permission.POST_NOTIFICATIONS,
                                            )
                                        }
                                    },
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
                                                barcodeLinks = barcodeLinks.map { link ->
                                                    BackupBarcodeLink(link.barcode, link.itemId)
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
    val plan2: PlanMapper.StoredPlan?,
    val groceryRows2: List<GroceryRow>,
    val ratings: Map<String, RecipeRating>,
    val presets: List<DietPreset>,
    val barcodeLinks: List<BarcodeLink>,
)

private fun currentWeekStart(): String =
    LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toString()

/**
 * The effective notification state (Phase V2-4): Android versions before
 * 13 have no notification runtime permission, so notifications are
 * simply available; from 13 on, the permission must have been granted.
 */
private fun hasNotificationPermission(context: Context): Boolean =
    Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED

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
    // Barcode links replace wholesale: a pre-v3 backup has none, which clears them.
    database.barcodeLinkDao().deleteAll()
    data.barcodeLinks.forEach { saved ->
        database.barcodeLinkDao().upsert(
            BarcodeLinkEntity(saved.barcode, saved.itemId, System.currentTimeMillis()),
        )
    }
    // Plans were priced from the old book; clear them so nothing stale lingers.
    database.planDao().deleteAll()
    database.groceryDao().deleteAll()
}
