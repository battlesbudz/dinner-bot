package com.battlesbudz.dinnerbot

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
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
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.battlesbudz.dinnerbot.core.data.BarcodeLinkEntity
import com.battlesbudz.dinnerbot.core.data.CookCheckoffEntity
import com.battlesbudz.dinnerbot.core.data.DinnerBotDatabase
import com.battlesbudz.dinnerbot.core.data.PlanMapper
import com.battlesbudz.dinnerbot.core.data.RecipeRatingEntity
import com.battlesbudz.dinnerbot.core.data.ReconciliationMapper
import com.battlesbudz.dinnerbot.core.data.SeedDataParser
import com.battlesbudz.dinnerbot.core.data.SeedLoader
import com.battlesbudz.dinnerbot.core.data.SeedMapper
import com.battlesbudz.dinnerbot.core.model.BarcodeLink
import com.battlesbudz.dinnerbot.core.model.BulkStock
import com.battlesbudz.dinnerbot.core.model.CookCheckoff
import com.battlesbudz.dinnerbot.core.model.Costing
import com.battlesbudz.dinnerbot.core.model.DietPreset
import com.battlesbudz.dinnerbot.core.model.DietPresets
import com.battlesbudz.dinnerbot.core.model.HouseholdSettings
import com.battlesbudz.dinnerbot.core.model.IngredientNutrition
import com.battlesbudz.dinnerbot.core.model.PantryItem
import com.battlesbudz.dinnerbot.core.model.Perishables
import com.battlesbudz.dinnerbot.core.model.PossibleOut
import com.battlesbudz.dinnerbot.core.model.PriceItem
import com.battlesbudz.dinnerbot.core.model.RanOutEvent
import com.battlesbudz.dinnerbot.core.model.RanOutSource
import com.battlesbudz.dinnerbot.core.model.RanOutState
import com.battlesbudz.dinnerbot.core.model.Recipe
import com.battlesbudz.dinnerbot.core.model.RecipeAdaptation
import com.battlesbudz.dinnerbot.core.model.RecipePhoto
import com.battlesbudz.dinnerbot.core.model.RecipePhotos
import com.battlesbudz.dinnerbot.core.model.RecipeRating
import com.battlesbudz.dinnerbot.core.model.ReconciledOut
import com.battlesbudz.dinnerbot.feature.backup.BackupBarcodeLink
import com.battlesbudz.dinnerbot.feature.backup.BackupData
import com.battlesbudz.dinnerbot.feature.backup.BackupRecipeRating
import com.battlesbudz.dinnerbot.feature.backup.BackupEngine
import com.battlesbudz.dinnerbot.feature.backup.BackupShare
import com.battlesbudz.dinnerbot.feature.grocerylist.GroceryRow
import com.battlesbudz.dinnerbot.feature.grocerylist.GroceryListScreen
import com.battlesbudz.dinnerbot.feature.grocerylist.ReconciliationMerge
import com.battlesbudz.dinnerbot.feature.nutrition.NutritionScreen
import com.battlesbudz.dinnerbot.feature.nutrition.NutritionWeekInput
import com.battlesbudz.dinnerbot.feature.onboarding.HowItWorksScreen
import com.battlesbudz.dinnerbot.feature.onboarding.OnboardingDefaults
import com.battlesbudz.dinnerbot.feature.onboarding.SettingsScreen
import com.battlesbudz.dinnerbot.feature.onboarding.SetupWalkthrough
import com.battlesbudz.dinnerbot.feature.pantry.PantryLogic
import com.battlesbudz.dinnerbot.feature.pantry.PantryScreen
import com.battlesbudz.dinnerbot.feature.pantry.ReconciliationWriteBack
import com.battlesbudz.dinnerbot.feature.planner.FortnightPlannerScreen
import com.battlesbudz.dinnerbot.feature.planner.PlannerScreen
import com.battlesbudz.dinnerbot.feature.planner.Reconciliation
import com.battlesbudz.dinnerbot.feature.planner.ReconciliationScreen
import com.battlesbudz.dinnerbot.feature.planner.WeekPlanner
import com.battlesbudz.dinnerbot.feature.pricebook.PriceBookScreen
import com.battlesbudz.dinnerbot.feature.pricebook.PriceCorrection
import com.battlesbudz.dinnerbot.feature.recipes.CookScreen
import com.battlesbudz.dinnerbot.feature.recipes.RecipeDetailScreen
import com.battlesbudz.dinnerbot.feature.recipes.RecipeListScreen
import com.battlesbudz.dinnerbot.feature.reminders.ReminderPlanner
import com.battlesbudz.dinnerbot.feature.reminders.ReminderScheduler
import com.battlesbudz.dinnerbot.feature.reminders.ReminderWeek
import java.io.File
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
                val nutritionJson = assets.open("nutrition.json").bufferedReader().use { it.readText() }
                SeedLoader(database).seedIfEmpty(priceJson, recipesJson)
                SeedLoader(database).refreshSeedRecipes(recipesJson)
                SeedLoader(database).refreshSeedPriceNotes(priceJson)
                SeedLoader(database).addMissingSeedPriceItems(priceJson)
                SeedLoader(database).refreshSeedNutrition(nutritionJson)
            }
        }
    }
}

private enum class Tab(val label: String, val icon: ImageVector) {
    RECIPES("Recipes", Icons.Filled.MenuBook),
    PLANNER("Plan", Icons.Filled.CalendarMonth),
    LIST("List", Icons.Filled.ShoppingCart),
    NUTRITION("Nutrition", Icons.Filled.PieChart),
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
    // Household recipe photos (Epic issue #10): the records, and the
    // photo files that actually exist on this phone, by recipe id.
    var recipePhotos by remember { mutableStateOf<Map<String, RecipePhoto>>(emptyMap()) }
    var photoFiles by remember { mutableStateOf<Map<String, File>>(emptyMap()) }
    var barcodeLinks by remember { mutableStateOf<List<BarcodeLink>>(emptyList()) }
    var nutritionEntries by remember { mutableStateOf<List<IngredientNutrition>>(emptyList()) }
    // Sunday reconciliation state (Phase V2-7): ran-out records and cook
    // check-offs are user data, loaded with everything else.
    var ranOutEvents by remember { mutableStateOf<List<RanOutEvent>>(emptyList()) }
    var cookCheckoffs by remember { mutableStateOf<List<CookCheckoff>>(emptyList()) }
    var cookTarget by remember { mutableStateOf<CookTarget?>(null) }
    var reconFlow by remember { mutableStateOf<ReconFlow?>(null) }
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
            SeedLoader(database).addMissingSeedPriceItems(loadAsset("pricebook.json"))
            SeedLoader(database).refreshSeedNutrition(loadAsset("nutrition.json"))
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
            val loadedPhotos = database.recipePhotoDao().getAll()
                .map { with(SeedMapper) { it.toModel() } }
            val loadedPhotoById = loadedPhotos.associateBy { it.recipeId }
            // Only files that exist on this phone render; a record whose
            // file is gone simply shows no photo.
            val loadedPhotoFiles = loadedPhotos
                .associate { it.recipeId to RecipePhotoStore.fileFor(context, it) }
                .filterValues { it.exists() }
            val loadedLinks = database.barcodeLinkDao().getAll()
                .map { with(SeedMapper) { it.toModel() } }
            val loadedNutrition = database.ingredientNutritionDao().getAll()
                .map { with(SeedMapper) { it.toModel() } }
            val loadedRanOut = database.ranOutDao().getAll()
                .map { with(ReconciliationMapper) { it.toModel() } }
            val loadedCheckoffs = database.cookCheckoffDao().getAll()
                .map { with(ReconciliationMapper) { it.toModel() } }
            LoadedState(
                s, r, p, pantry, stocks, plan, rows, plan2, rows2,
                loadedRatings, loadedPresets, loadedLinks, loadedNutrition,
                loadedRanOut, loadedCheckoffs, loadedPhotoById, loadedPhotoFiles,
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
        recipePhotos = loaded.photos
        photoFiles = loaded.photoFiles
        presets = loaded.presets
        barcodeLinks = loaded.barcodeLinks
        nutritionEntries = loaded.nutritionEntries
        ranOutEvents = loaded.ranOutEvents
        cookCheckoffs = loaded.cookCheckoffs
        loading = false
    }

    fun refresh() { reloadKey += 1 }

    /**
     * Re-read the Sunday gate's inputs from the store and adopt them.
     * A generate request must judge the week that is actually stored;
     * in-memory state can lag the database, and gating on a stale copy
     * would skip (or invent) the reconciliation.
     */
    suspend fun reloadGateInputs() {
        val snapshot = withContext(Dispatchers.IO) {
            val recentPlans = database.planDao().getRecent(2)
                .map { with(PlanMapper) { it.toStoredPlan() } }
            val first = recentPlans.firstOrNull()
            val second = recentPlans.getOrNull(1)?.takeIf { candidate ->
                first != null && runCatching {
                    LocalDate.parse(candidate.weekStart) ==
                        LocalDate.parse(first.weekStart).plusWeeks(1)
                }.getOrDefault(false)
            }
            GateSnapshot(
                plan = first,
                plan2 = second,
                ranOutEvents = database.ranOutDao().getAll()
                    .map { with(ReconciliationMapper) { it.toModel() } },
                cookCheckoffs = database.cookCheckoffDao().getAll()
                    .map { with(ReconciliationMapper) { it.toModel() } },
                pantryItems = database.pantryDao().getAll()
                    .map { with(SeedMapper) { it.toModel() } },
                bulkStocks = database.bulkStockDao().getAll()
                    .map { with(SeedMapper) { it.toModel() } },
            )
        }
        storedPlan = snapshot.plan
        storedPlan2 = snapshot.plan2
        ranOutEvents = snapshot.ranOutEvents
        cookCheckoffs = snapshot.cookCheckoffs
        pantryItems = snapshot.pantryItems
        bulkStocks = snapshot.bulkStocks
    }

    fun persistPlan(plan: WeekPlanner.WeekPlan, keepId: String?, forSettings: HouseholdSettings) {
        val currentSettings = forSettings
        scope.launch {
            withContext(Dispatchers.IO) {
                val id = keepId ?: "plan-${System.currentTimeMillis()}"
                if (keepId == null) {
                    database.planDao().deleteAll()
                    database.groceryDao().deleteAll()
                    // A fresh generation replaces the plans, and the cook
                    // check-offs were working state for the replaced plans
                    // (Phase V2-7). Ran-out records are NOT cleared: the
                    // Sunday flow consumes them.
                    database.cookCheckoffDao().deleteAll()
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
                    // Same replacement rule as a one-week generation: the
                    // replaced plans' cook check-offs go with them.
                    database.cookCheckoffDao().deleteAll()
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

    // ------------------------------------------------------------------
    // Sunday reconciliation (Phase V2-7)
    //
    // Starting a new week is gated: when a finished prior week has
    // anything to reconcile (ran-out marks waiting, or cooked dinners
    // that used things up), the three-list flow opens BEFORE planning,
    // its write-back corrects the pantry and stock state, and only then
    // is the week planned, with the reconciled set merged into its list
    // by the dedup rules. "Reconcile later" plans on last known state
    // and leaves everything pending for the next attempt.
    // ------------------------------------------------------------------

    /** Stored plans whose week has finished and no reconciliation has consumed yet. */
    fun finishedSourcePlans(forSettings: HouseholdSettings): List<Reconciliation.SourcePlan> {
        val currentStart = currentWeekStart()
        return listOfNotNull(storedPlan, storedPlan2)
            .filter { it.weekStart < currentStart && it.id !in forSettings.reconciledPlanIds }
            .sortedBy { it.weekStart }
            .map { Reconciliation.SourcePlan(it.id, it.weekStart, it.nights) }
    }

    fun possibleOutsFor(
        sources: List<Reconciliation.SourcePlan>,
        forSettings: HouseholdSettings,
    ): List<PossibleOut> = Reconciliation.inferPossibleOuts(
        sourcePlans = sources,
        recipesById = adaptationFor(forSettings).recipes.associateBy { it.id },
        checkoffs = cookCheckoffs,
        priceItems = priceItems,
        pantryItems = pantryItems,
        stockOnHand = PantryLogic.stockMap(bulkStocks),
        pendingEvents = ranOutEvents,
        recordedEvents = ranOutEvents,
    )

    /** The full Sunday session a new week is gated on, or null when nothing is pending. */
    fun fullReconSession(forSettings: HouseholdSettings): Reconciliation.Session? {
        val sources = finishedSourcePlans(forSettings)
        if (sources.isEmpty()) return null
        val pending = ranOutEvents.filter { it.state == RanOutState.PENDING }
        val possibles = possibleOutsFor(sources, forSettings)
        if (!Reconciliation.hasPendingReconciliation(sources, pending, possibles)) return null
        return Reconciliation.buildSession(
            Reconciliation.CheckKind.FULL, sources, ranOutEvents, possibles,
            pantryItems, priceItems, PantryLogic.stockMap(bulkStocks),
        )
    }

    /**
     * The lighter check gating week 2's fresh top-up: due once week 2 has
     * begun, while its fortnight stands, and only when something is
     * pending from week 1's cooking. Completing it marks the fortnight
     * consumed, so it is offered once.
     */
    fun topUpReconSession(forSettings: HouseholdSettings): Reconciliation.Session? {
        val week1 = storedPlan ?: return null
        val week2 = storedPlan2 ?: return null
        if (week2.id in forSettings.reconciledPlanIds) return null
        val week2Start = runCatching { LocalDate.parse(week2.weekStart) }.getOrNull() ?: return null
        if (LocalDate.now().isBefore(week2Start)) return null
        val sources = listOf(Reconciliation.SourcePlan(week1.id, week1.weekStart, week1.nights))
        val pending = ranOutEvents.filter { it.state == RanOutState.PENDING }
        val possibles = possibleOutsFor(sources, forSettings)
        if (!Reconciliation.hasPendingReconciliation(sources, pending, possibles)) return null
        return Reconciliation.buildSession(
            Reconciliation.CheckKind.TOP_UP, sources, ranOutEvents, possibles,
            pantryItems, priceItems, PantryLogic.stockMap(bulkStocks),
        )
    }

    /**
     * Merge a completed reconciliation's ran-out set into a planned week
     * (the dedup rules): staple lines are added, ingredient lines only
     * where a planned dinner uses the item, and nothing is ever doubled.
     */
    fun mergeResolvedIntoWeek(
        plan: WeekPlanner.WeekPlan,
        resolvedOuts: List<ReconciledOut>,
        forSettings: HouseholdSettings,
    ): WeekPlanner.WeekPlan {
        if (resolvedOuts.none { it.priceItemId != null }) return plan
        val recipesByIdLocal = adaptationFor(forSettings).recipes.associateBy { it.id }
        val dinners = plan.nights.filterNot { it.notCooking }.mapNotNull { recipesByIdLocal[it.recipeId] }
        val (needs, _) = Costing.aggregateNeeds(dinners, forSettings.householdSize, priceItems.associateBy { it.id })
        val result = ReconciliationMerge.merge(
            lines = plan.lines,
            resolvedOuts = resolvedOuts,
            priceItems = priceItems,
            plannedItemIds = needs.map { it.item.id }.toSet(),
            plannedNeedsBase = needs.associate { it.item.id to it.neededBase },
            samsGateMet = plan.samsGateMet,
            samsMonthlySavingsCents = plan.samsMonthlySavingsCents,
        )
        if (result.addedNames.isEmpty() && result.cashDeltaCents == 0) return plan
        val notes = if (result.addedNames.isNotEmpty()) {
            plan.notes + ("From Sunday's check, these ran out and are back on the list: " +
                result.addedNames.joinToString(", ") + ".")
        } else {
            plan.notes
        }
        return plan.copy(
            lines = result.lines,
            cashNeededCents = plan.cashNeededCents + result.cashDeltaCents,
            notes = notes,
        )
    }

    /** Plan one week from an explicit pantry/stock state, with the reconciled set merged in. */
    fun doGenerateWeek(
        forSettings: HouseholdSettings,
        planPantry: List<PantryItem>,
        planStocks: List<BulkStock>,
        resolvedOuts: List<ReconciledOut>,
    ) {
        variation += 1
        val plan = WeekPlanner.generate(
            settings = forSettings,
            recipes = plannerPool(forSettings),
            priceItems = priceItems,
            stockOnHand = PantryLogic.stockMap(planStocks),
            pantryOwnedNames = PantryLogic.ownedNames(planPantry),
            weekStart = currentWeekStart(),
            variation = variation,
            ratings = ratings,
        )
        persistPlan(mergeResolvedIntoWeek(plan, resolvedOuts, forSettings), keepId = null, forSettings = forSettings)
        tab = Tab.PLANNER
    }

    /** Plan two weeks from an explicit pantry/stock state, with the reconciled set merged into week 1. */
    fun doGenerateFortnight(
        forSettings: HouseholdSettings,
        planPantry: List<PantryItem>,
        planStocks: List<BulkStock>,
        resolvedOuts: List<ReconciledOut>,
    ) {
        variation += 1
        val week1Start = currentWeekStart()
        val fortnight = WeekPlanner.generateFortnight(
            settings = forSettings,
            recipes = plannerPool(forSettings),
            priceItems = priceItems,
            stockOnHand = PantryLogic.stockMap(planStocks),
            pantryOwnedNames = PantryLogic.ownedNames(planPantry),
            week1Start = week1Start,
            week2Start = LocalDate.parse(week1Start).plusWeeks(1).toString(),
            variation = variation,
            ratings = ratings,
        )
        val mergedWeek1 = mergeResolvedIntoWeek(fortnight.week1, resolvedOuts, forSettings)
        persistFortnight(
            WeekPlanner.composeFortnight(mergedWeek1, fortnight.week2, priceItems),
            keepIds = null,
            forSettings = forSettings,
        )
        tab = Tab.PLANNER
    }

    /** The Plan tab's generate buttons: the Sunday gate first, planning second. */
    fun requestGenerateWeek(forSettings: HouseholdSettings) {
        scope.launch {
            reloadGateInputs()
            val session = fullReconSession(forSettings)
            if (session != null) {
                reconFlow = ReconFlow(session, ReconTarget.WEEK)
            } else {
                doGenerateWeek(forSettings, pantryItems, bulkStocks, emptyList())
            }
        }
    }

    fun requestGenerateFortnight(forSettings: HouseholdSettings) {
        scope.launch {
            reloadGateInputs()
            val session = fullReconSession(forSettings)
            if (session != null) {
                reconFlow = ReconFlow(session, ReconTarget.FORTNIGHT)
            } else {
                doGenerateFortnight(forSettings, pantryItems, bulkStocks, emptyList())
            }
        }
    }

    /** A stored plan as the planner's view type, for the top-up merge. */
    fun storedWeekView(plan: PlanMapper.StoredPlan, rows: List<GroceryRow>): WeekPlanner.WeekPlan =
        WeekPlanner.WeekPlan(
            weekStart = plan.weekStart,
            nights = plan.nights,
            lines = rows.map { it.line },
            cashNeededCents = plan.cashNeededCents,
            foodCostCents = plan.foodCostCents,
            samsMonthlySavingsCents = plan.samsMonthlySavingsCents,
            samsGateMet = plan.samsMonthlySavingsCents >= Costing.SAMS_MONTHLY_GATE_CENTS,
            deferredRestocks = emptyList(),
            notes = plan.notes,
        )

    /**
     * The top-up check completed: week 2 is repriced from the write-back
     * state (its dinners unchanged) and the reconciled set merges into
     * its lines, so the top-up shop carries what ran out during week 1.
     */
    fun applyTopUpOutcome(
        forSettings: HouseholdSettings,
        writeBack: ReconciliationWriteBack.Result,
        resolvedOuts: List<ReconciledOut>,
    ) {
        val week1 = storedPlan ?: return
        val week2 = storedPlan2 ?: return
        val view1 = storedWeekView(week1, groceryRows)
        val view2 = storedWeekView(week2, groceryRows2)
        val owned = PantryLogic.ownedNames(writeBack.pantryItems)
        val stock = PantryLogic.stockMap(writeBack.bulkStocks)
        val adaptedById = adaptationFor(forSettings).recipes.associateBy { it.id }
        val dinners = (view1.nights + view2.nights)
            .filterNot { it.notCooking }
            .mapNotNull { adaptedById[it.recipeId] }
        val gate = WeekPlanner.fortnightGateForDinners(forSettings, dinners, priceItems)
        val stock1 = WeekPlanner.projectedStockAfter(
            forSettings, view1, adaptationFor(forSettings).recipes, priceItems, stock, owned, gate,
        )
        val repriced2 = WeekPlanner.recost(
            settings = forSettings,
            current = view2,
            recipes = adaptationFor(forSettings).recipes,
            priceItems = priceItems,
            stockOnHand = stock1,
            pantryOwnedNames = owned,
            gateOverride = gate,
        )
        val merged2 = mergeResolvedIntoWeek(repriced2, resolvedOuts, forSettings)
        persistFortnight(
            WeekPlanner.composeFortnight(view1, merged2, priceItems),
            keepIds = week1.id to week2.id,
            forSettings = forSettings,
        )
    }

    /**
     * A replaced dinner's cook check-offs clear with it: they recorded
     * cooking the dinner that was planned then, and leaving them would
     * let Sunday's inference treat the replacement dinner as cooked
     * (Codex review, PR #20).
     */
    fun clearNightCheckoffs(planId: String, day: String) {
        scope.launch {
            withContext(Dispatchers.IO) {
                database.cookCheckoffDao().deleteForPlanAndDay(planId, day)
            }
            refresh()
        }
    }

    /**
     * A reconciliation finished: persist its records, land the write-back
     * (pantry flips and stock zeroing) BEFORE anything is planned, mark
     * the consumed plans, and only then plan (or, for the top-up check,
     * update week 2's list).
     */
    fun completeReconciliation(flow: ReconFlow, choices: Reconciliation.SessionChoices) {
        val forSettings = settings ?: return
        val outcome = Reconciliation.resolveSession(
            flow.session, choices, priceItems, LocalDate.now().toString(),
        )
        val writeBack = ReconciliationWriteBack.apply(pantryItems, bulkStocks, outcome.resolvedOuts, priceItems)
        reconFlow = null
        scope.launch {
            withContext(Dispatchers.IO) {
                // A decided candidate retires its carried pending row;
                // the outcome row written below replaces it.
                val decidedKeys = choices.confirmedPossibleKeys + choices.dismissedPossibleKeys
                if (decidedKeys.isNotEmpty()) {
                    database.ranOutDao().deletePendingInferred(decidedKeys.toList())
                }
                outcome.eventsToSave.forEach { event ->
                    database.ranOutDao().upsert(with(ReconciliationMapper) { event.toEntity() })
                }
                writeBack.pantryItems.forEach {
                    database.pantryDao().upsert(with(SeedMapper) { it.toEntity() })
                }
                val standingIds = writeBack.bulkStocks.map { it.itemId }.toSet()
                bulkStocks.map { it.itemId }.filter { it !in standingIds }.forEach {
                    database.bulkStockDao().delete(it)
                }
                writeBack.bulkStocks.forEach {
                    database.bulkStockDao().upsert(with(SeedMapper) { it.toEntity() })
                }
                val consumed = outcome.reconciledPlanIds +
                    if (flow.target == ReconTarget.TOP_UP) listOfNotNull(storedPlan2?.id) else emptyList()
                val newSettings = forSettings.copy(
                    reconciledPlanIds = (forSettings.reconciledPlanIds + consumed).distinct(),
                )
                database.settingsDao().upsert(with(SeedMapper) { newSettings.toEntity() })
                syncPantryStaples(database, newSettings)
            }
            when (flow.target) {
                ReconTarget.WEEK ->
                    doGenerateWeek(forSettings, writeBack.pantryItems, writeBack.bulkStocks, outcome.resolvedOuts)
                ReconTarget.FORTNIGHT ->
                    doGenerateFortnight(forSettings, writeBack.pantryItems, writeBack.bulkStocks, outcome.resolvedOuts)
                ReconTarget.TOP_UP ->
                    applyTopUpOutcome(forSettings, writeBack, outcome.resolvedOuts)
            }
            refresh()
        }
    }

    /**
     * "Reconcile later": pantry and stock state stay untouched, the week
     * plans on last known state, and the ran-out list still waits at the
     * top of the next attempt. Nothing is marked reconciled.
     */
    fun skipReconciliation(flow: ReconFlow) {
        val forSettings = settings ?: return
        reconFlow = null
        // Deferring must not lose the inferred candidates (Codex review,
        // PR #20): generating the replacement plan deletes the source
        // plan and its cook check-offs, which are the only evidence the
        // inference had. The session's possibles are carried forward as
        // pending inferred events, and the next session presents them
        // again in its second list. Her own marks are already persisted
        // rows and need no carry.
        val pendingKeys = ranOutEvents
            .filter { it.state == RanOutState.PENDING }
            .map { it.itemKey }.toSet()
        val carries = flow.session.possibleOuts
            .filter { it.itemKey !in pendingKeys }
            .map { possible ->
                RanOutEvent(
                    itemKey = possible.itemKey,
                    displayName = possible.displayName,
                    stapleSide = possible.stapleSide,
                    priceItemId = possible.priceItemId,
                    bulkItemId = possible.bulkItemId,
                    pantryStapleName = possible.pantryStapleName,
                    dateMarked = LocalDate.now().toString(),
                    source = RanOutSource.INFERRED,
                    state = RanOutState.PENDING,
                    cyclePlanId = flow.session.sourcePlanIds.firstOrNull(),
                )
            }
        scope.launch {
            if (carries.isNotEmpty()) {
                withContext(Dispatchers.IO) {
                    carries.forEach { event ->
                        database.ranOutDao().upsert(with(ReconciliationMapper) { event.toEntity() })
                    }
                }
            }
            when (flow.target) {
                ReconTarget.WEEK -> doGenerateWeek(forSettings, pantryItems, bulkStocks, emptyList())
                ReconTarget.FORTNIGHT -> doGenerateFortnight(forSettings, pantryItems, bulkStocks, emptyList())
                ReconTarget.TOP_UP -> Unit
            }
            refresh()
        }
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
     * Attach a household photo to a recipe (Epic issue #10): the picked
     * image is copied into app-local storage by [RecipePhotoStore] and
     * its reference saved, keeping any credit the recipe already had.
     */
    fun attachRecipePhoto(recipeId: String, picked: Uri) {
        scope.launch {
            val saved = withContext(Dispatchers.IO) {
                runCatching {
                    val credit = database.recipePhotoDao().getByRecipeId(recipeId)?.credit
                        ?: RecipePhotos.DEFAULT_CREDIT
                    RecipePhotoStore.savePickedPhoto(
                        context, recipeId, picked, credit, System.currentTimeMillis(),
                    )?.also { photo ->
                        database.recipePhotoDao().upsert(with(SeedMapper) { photo.toEntity() })
                    }
                }.getOrNull()
            }
            statusMessage = if (saved != null) "Photo added." else "That photo could not be added."
            refresh()
        }
    }

    /** Remove a recipe's photo: the record and the file. */
    fun removeRecipePhoto(recipeId: String) {
        scope.launch {
            withContext(Dispatchers.IO) {
                val existing = database.recipePhotoDao().getByRecipeId(recipeId)
                if (existing != null) {
                    RecipePhotoStore.deleteFile(
                        context, with(SeedMapper) { existing.toModel() },
                    )
                    database.recipePhotoDao().delete(recipeId)
                }
            }
            refresh()
        }
    }

    /** Save the credit stored with a recipe's photo. */
    fun setRecipePhotoCredit(recipeId: String, credit: String) {
        scope.launch {
            withContext(Dispatchers.IO) {
                val existing = database.recipePhotoDao().getByRecipeId(recipeId)
                    ?: return@withContext
                database.recipePhotoDao().upsert(
                    existing.copy(
                        credit = RecipePhotos.creditOrDefault(credit),
                        updatedAtMillis = System.currentTimeMillis(),
                    ),
                )
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

    // The import confirmation (Epic issue #9): importing replaces the
    // data on this phone, and the wording says so before the picker
    // opens, not after the damage is done.
    var showImportConfirm by remember { mutableStateOf(false) }

    /**
     * The household's backup as JSON. Export saves this text and share
     * sends this text: one file, two doors, exactly the D-V2-1 design.
     */
    fun currentBackupJson(): String {
        val current = settings ?: OnboardingDefaults.initialSettings()
        return BackupEngine.export(
            BackupData(
                settings = current,
                priceItems = priceItems,
                pantryItems = pantryItems,
                bulkStock = bulkStocks,
                recipeRatings = ratings.map { (id, rating) ->
                    BackupRecipeRating(id, rating)
                },
                barcodeLinks = barcodeLinks.map { link ->
                    BackupBarcodeLink(link.barcode, link.itemId)
                },
                ranOutEvents = ranOutEvents,
                cookCheckoffs = cookCheckoffs,
            ),
        )
    }

    /**
     * Share the backup file through the system share sheet (Epic issue
     * #9). The JSON is written to the app cache's share directory, a
     * FileProvider URI is handed to the sheet with a read grant, and the
     * receiving phone imports the file like any other backup.
     */
    fun shareBackup() {
        val text = currentBackupJson()
        scope.launch {
            val uri = withContext(Dispatchers.IO) {
                runCatching {
                    val file = BackupShare.shareFileIn(context.cacheDir)
                    file.parentFile?.mkdirs()
                    file.writeText(text, Charsets.UTF_8)
                    FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        file,
                    )
                }.getOrNull()
            }
            if (uri == null) {
                statusMessage = "That backup could not be prepared for sharing."
            } else {
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = BackupShare.MIME_TYPE
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_SUBJECT, BackupShare.SHARE_SUBJECT)
                    putExtra(Intent.EXTRA_TEXT, BackupShare.SHARE_TEXT)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(send, BackupShare.CHOOSER_TITLE))
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
            // Nutrition entries split the way the calculator reads them
            // (Phase V2-6): priced items by id, pantry staples by name slug.
            val nutritionByItemId = remember(nutritionEntries) {
                nutritionEntries.filterNot { it.pantryItem }.associateBy { it.itemId }
            }
            val nutritionByPantrySlug = remember(nutritionEntries) {
                nutritionEntries.filter { it.pantryItem }.associateBy { it.itemId }
            }
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
                    val activeFlow = reconFlow
                    if (activeFlow != null) {
                        // The Sunday gate (Phase V2-7) takes the screen:
                        // reconciliation runs before any planning, and the
                        // tabs wait until it completes or is skipped.
                        ReconciliationScreen(
                            session = activeFlow.session,
                            onComplete = { choices -> completeReconciliation(activeFlow, choices) },
                            onSkip = { skipReconciliation(activeFlow) },
                        )
                    } else when (tab) {
                        Tab.RECIPES -> {
                            val open = openRecipeId?.let { recipesById[it] }
                            if (open != null) {
                                RecipeDetailScreen(
                                    recipe = open,
                                    priceItems = priceItems,
                                    onBack = { openRecipeId = null },
                                    rating = ratings[open.id],
                                    onRate = { rateRecipe(open.id, it) },
                                    nutritionByItemId = nutritionByItemId,
                                    nutritionByPantrySlug = nutritionByPantrySlug,
                                    photo = recipePhotos[open.id],
                                    photoFile = photoFiles[open.id],
                                    onPhotoPicked = { attachRecipePhoto(open.id, it) },
                                    onPhotoRemoved = { removeRecipePhoto(open.id) },
                                    onPhotoCreditChange = { setRecipePhotoCredit(open.id, it) },
                                )
                            } else {
                                RecipeListScreen(
                                    recipes = displayRecipes,
                                    priceItems = priceItems,
                                    onRecipeClick = { openRecipeId = it.id },
                                    ratings = ratings,
                                    photoFiles = photoFiles,
                                )
                            }
                        }
                        Tab.PLANNER -> {
                            val target = cookTarget
                            val targetPlan = target?.let { t ->
                                listOfNotNull(storedPlan, storedPlan2).firstOrNull { it.id == t.planId }
                            }
                            val targetNight = targetPlan?.nights?.firstOrNull { it.day == target?.day }
                            val targetRecipe = targetNight?.let { recipesById[it.recipeId] }
                            if (target != null && targetPlan != null && targetNight != null && targetRecipe != null) {
                                // The cook view (Phase V2-7): tonight's
                                // dinner with its check-offs and ran-out
                                // marks, persisted per planned dinner.
                                CookScreen(
                                    dayLabel = DAY_LABELS[targetNight.day] ?: targetNight.day,
                                    recipe = targetRecipe,
                                    priceItems = priceItems,
                                    householdSize = currentSettings.householdSize,
                                    checkedKeys = cookCheckoffs
                                        .filter { it.planId == target.planId && it.day == target.day && it.checked }
                                        .map { it.itemKey }.toSet(),
                                    ranOutKeys = ranOutEvents
                                        .filter { it.state == RanOutState.PENDING }
                                        .map { it.itemKey }.toSet(),
                                    onToggleChecked = { key, checked ->
                                        scope.launch {
                                            withContext(Dispatchers.IO) {
                                                database.cookCheckoffDao().upsert(
                                                    CookCheckoffEntity(target.planId, target.day, key, checked),
                                                )
                                            }
                                            refresh()
                                        }
                                    },
                                    onToggleRanOut = { event, marked ->
                                        scope.launch {
                                            withContext(Dispatchers.IO) {
                                                if (marked) {
                                                    val already = database.ranOutDao()
                                                        .getByState(RanOutState.PENDING.name)
                                                        .any { it.itemKey == event.itemKey }
                                                    if (!already) {
                                                        database.ranOutDao().upsert(
                                                            with(ReconciliationMapper) { event.toEntity() },
                                                        )
                                                    }
                                                } else {
                                                    database.ranOutDao()
                                                        .deletePendingCookingMark(event.itemKey)
                                                }
                                            }
                                            refresh()
                                        }
                                    },
                                    today = LocalDate.now().toString(),
                                    planId = target.planId,
                                    pantryOwnedNames = PantryLogic.ownedNames(pantryItems),
                                    onBack = { cookTarget = null },
                                )
                            } else if (fortnightView != null && storedPlan != null && storedPlan2 != null) {
                            FortnightPlannerScreen(
                                plan = fortnightView,
                                recipesById = recipesById,
                                budgetCents = currentSettings.weeklyBudgetCents,
                                onRegenerateTwoWeeks = { requestGenerateFortnight(currentSettings) },
                                onPlanOneWeek = { requestGenerateWeek(currentSettings) },
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
                                    clearNightCheckoffs(
                                        if (weekIndex == 1) storedPlan!!.id else storedPlan2!!.id, day,
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
                                    clearNightCheckoffs(
                                        if (weekIndex == 1) storedPlan!!.id else storedPlan2!!.id, day,
                                    )
                                },
                                ratings = ratings,
                                onRate = { recipeId, rating -> rateRecipe(recipeId, rating) },
                                outOfRotationCount = ratings.values.count { it == RecipeRating.DOWN },
                                onCookNight = { weekIndex, day ->
                                    val planId = if (weekIndex == 1) storedPlan?.id else storedPlan2?.id
                                    if (planId != null) cookTarget = CookTarget(planId, day)
                                },
                                onStartTopUpCheck = topUpReconSession(currentSettings)?.let { session ->
                                    { reconFlow = ReconFlow(session, ReconTarget.TOP_UP) }
                                },
                                photoFiles = photoFiles,
                            )
                            } else PlannerScreen(
                            plan = weekPlanView,
                            recipesById = recipesById,
                            budgetCents = currentSettings.weeklyBudgetCents,
                            onGenerate = { requestGenerateWeek(currentSettings) },
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
                                    clearNightCheckoffs(storedPlan!!.id, day)
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
                                    clearNightCheckoffs(storedPlan!!.id, day)
                                }
                            },
                            ratings = ratings,
                            onRate = { recipeId, rating -> rateRecipe(recipeId, rating) },
                            outOfRotationCount = ratings.values.count { it == RecipeRating.DOWN },
                            onGenerateTwoWeeks = { requestGenerateFortnight(currentSettings) },
                            onCookNight = { day ->
                                storedPlan?.let { cookTarget = CookTarget(it.id, day) }
                            },
                            photoFiles = photoFiles,
                        )
                        }
                        Tab.LIST -> {
                            // Two-week plan: the list is the big trip (week
                            // 1's rows plus week 2's shelf-stable and frozen
                            // rows) and week 2's fresh top-up, split by the
                            // same category rule the planner used.
                            val priceById = priceItems.associateBy { it.id }
                            val isFortnightList = fortnightView != null
                            // A week 2 line a reconciliation added shops
                            // with the top-up (Phase V2-7), like the fresh
                            // lines: the big trip predates the check.
                            fun isTopUpRow(row: GroceryRow): Boolean =
                                row.line.reconciliationAdded ||
                                    Perishables.isTopUpLine(row.line, priceById[row.line.itemId])
                            val topUpRows = if (isFortnightList) {
                                groceryRows2.filter { isTopUpRow(it) }
                            } else {
                                emptyList()
                            }
                            val bigTripRows = if (isFortnightList) {
                                groceryRows + groceryRows2.filterNot { isTopUpRow(it) }
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
                                        // Buying a reconciled ran-out item
                                        // restocks it (Phase V2-7): its
                                        // record leaves the confirmed set.
                                        if (checked) {
                                            database.ranOutDao().markRestockedForItem(row.line.itemId)
                                        }
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
                        Tab.NUTRITION -> NutritionScreen(
                            recipes = displayRecipes,
                            byItemId = nutritionByItemId,
                            byPantrySlug = nutritionByPantrySlug,
                            weeks = listOfNotNull(
                                storedPlan?.let {
                                    NutritionWeekInput("This week's dinners", it.nights)
                                },
                                storedPlan2?.let {
                                    NutritionWeekInput("Next week's dinners", it.nights)
                                },
                            ),
                            householdSize = currentSettings.householdSize,
                        )
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
                                        // Marking a staple owned again is
                                        // how buying it back is recorded; a
                                        // reconciled ran-out record for it
                                        // restocks (Phase V2-7).
                                        if (toggled.owned) {
                                            database.ranOutDao().markRestockedForStaple(item.name)
                                        }
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
                                            // Stock coming back in restocks a
                                            // reconciled ran-out record for the
                                            // item (Phase V2-7).
                                            database.ranOutDao().markRestockedForItem(stock.itemId)
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
                            // Backup and sharing (Epic issue #9, D-V2-1):
                            // the backup file IS the sharing. Export saves
                            // it, Share sends the same file through the
                            // system share sheet, and Import restores a
                            // file on this phone, replacing what is here.
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text(
                                    "Backup and sharing",
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                Text(
                                    "Export saves your Dinner Bot backup as a file. Share sends " +
                                        "the same file through an app you already use — a message, " +
                                        "a drive folder, email — so someone else in the household " +
                                        "can import it on their phone. There is no Dinner Bot " +
                                        "account or server; the file is the sharing.",
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    Button(
                                        onClick = {
                                            pendingExport = currentBackupJson()
                                            exportLauncher.launch(BackupShare.FILE_NAME)
                                        },
                                        modifier = Modifier.weight(1f),
                                    ) { Text("Export backup") }
                                    Button(
                                        onClick = { shareBackup() },
                                        modifier = Modifier.weight(1f),
                                    ) { Text("Share backup") }
                                }
                                OutlinedButton(
                                    onClick = { showImportConfirm = true },
                                    modifier = Modifier.fillMaxWidth(),
                                ) { Text("Import backup") }
                            }
                            if (showImportConfirm) {
                                AlertDialog(
                                    onDismissRequest = { showImportConfirm = false },
                                    title = { Text("Import a backup?") },
                                    text = { Text(BackupShare.IMPORT_REPLACES_MESSAGE) },
                                    confirmButton = {
                                        TextButton(
                                            onClick = {
                                                showImportConfirm = false
                                                importLauncher.launch(arrayOf("application/json"))
                                            },
                                        ) { Text("Choose backup file") }
                                    },
                                    dismissButton = {
                                        TextButton(
                                            onClick = { showImportConfirm = false },
                                        ) { Text("Cancel") }
                                    },
                                )
                            }
                        }
                    }
                }
                // Material 3 bottom navigation: seven destinations stay
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
    val nutritionEntries: List<IngredientNutrition>,
    val ranOutEvents: List<RanOutEvent>,
    val cookCheckoffs: List<CookCheckoff>,
    val photos: Map<String, RecipePhoto>,
    val photoFiles: Map<String, File>,
)

/** The Sunday gate's inputs, re-read from the store at request time. */
private data class GateSnapshot(
    val plan: PlanMapper.StoredPlan?,
    val plan2: PlanMapper.StoredPlan?,
    val ranOutEvents: List<RanOutEvent>,
    val cookCheckoffs: List<CookCheckoff>,
    val pantryItems: List<PantryItem>,
    val bulkStocks: List<BulkStock>,
)

private fun currentWeekStart(): String =
    LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toString()

/** Night code to shown label, for the cook view's header (Phase V2-7). */
private val DAY_LABELS = mapOf(
    "MON" to "Monday", "TUE" to "Tuesday", "WED" to "Wednesday", "THU" to "Thursday",
    "FRI" to "Friday", "SAT" to "Saturday", "SUN" to "Sunday",
)

/** The planned dinner whose cook view is open (Phase V2-7). */
private data class CookTarget(val planId: String, val day: String)

/** What a completed Sunday reconciliation goes on to do (Phase V2-7). */
private enum class ReconTarget { WEEK, FORTNIGHT, TOP_UP }

/** A Sunday reconciliation in progress: its prepared lists and its target. */
private data class ReconFlow(val session: Reconciliation.Session, val target: ReconTarget)

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
    // Reconciliation state replaces wholesale (Phase V2-7): a pre-v4
    // backup has no ran-out records or cook check-offs, which clears them.
    database.ranOutDao().deleteAll()
    data.ranOutEvents.forEach { saved ->
        database.ranOutDao().upsert(with(ReconciliationMapper) { saved.toEntity() })
    }
    database.cookCheckoffDao().deleteAll()
    data.cookCheckoffs.forEach { saved ->
        database.cookCheckoffDao().upsert(with(ReconciliationMapper) { saved.toEntity() })
    }
    // Plans were priced from the old book; clear them so nothing stale lingers.
    database.planDao().deleteAll()
    database.groceryDao().deleteAll()
}
