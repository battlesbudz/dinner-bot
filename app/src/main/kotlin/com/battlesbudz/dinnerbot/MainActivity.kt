package com.battlesbudz.dinnerbot

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.battlesbudz.dinnerbot.core.data.BarcodeLinkEntity
import com.battlesbudz.dinnerbot.core.data.CookCheckoffEntity
import com.battlesbudz.dinnerbot.core.data.DinnerBotDatabase
import com.battlesbudz.dinnerbot.core.data.KeepPromptEntity
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
import com.battlesbudz.dinnerbot.core.model.EffectiveRecipes
import com.battlesbudz.dinnerbot.core.model.GuessPricer
import com.battlesbudz.dinnerbot.core.model.HouseholdSettings
import com.battlesbudz.dinnerbot.core.model.ImportedRecipe
import com.battlesbudz.dinnerbot.core.model.IngredientNutrition
import com.battlesbudz.dinnerbot.core.model.LabelNutrition
import com.battlesbudz.dinnerbot.core.model.PantryItem
import com.battlesbudz.dinnerbot.core.model.PossibleOut
import com.battlesbudz.dinnerbot.core.model.PremiumFeature
import com.battlesbudz.dinnerbot.core.model.PremiumLocalState
import com.battlesbudz.dinnerbot.core.model.PremiumRules
import com.battlesbudz.dinnerbot.core.model.PriceItem
import com.battlesbudz.dinnerbot.core.model.RanOutEvent
import com.battlesbudz.dinnerbot.core.model.RanOutKeys
import com.battlesbudz.dinnerbot.core.model.RanOutSource
import com.battlesbudz.dinnerbot.core.model.RanOutState
import com.battlesbudz.dinnerbot.core.model.Recipe
import com.battlesbudz.dinnerbot.core.model.RecipePricing
import com.battlesbudz.dinnerbot.core.model.RecipeAdaptation
import com.battlesbudz.dinnerbot.core.model.RecipeIngredient
import com.battlesbudz.dinnerbot.core.model.RecipeOrigin
import com.battlesbudz.dinnerbot.core.model.RecipeOverride
import com.battlesbudz.dinnerbot.core.model.RecipePhoto
import com.battlesbudz.dinnerbot.core.model.RecipePhotos
import com.battlesbudz.dinnerbot.core.model.RecipeRating
import com.battlesbudz.dinnerbot.core.model.ReconciledOut
import com.battlesbudz.dinnerbot.core.model.SavingsHistory
import com.battlesbudz.dinnerbot.core.model.SavingsWeek
import com.battlesbudz.dinnerbot.core.model.ScanSubstitution
import com.battlesbudz.dinnerbot.core.model.TonightOverrides
import com.battlesbudz.dinnerbot.feature.backup.BackupBarcodeLink
import com.battlesbudz.dinnerbot.feature.backup.BackupData
import com.battlesbudz.dinnerbot.feature.backup.BackupImportedRecipe
import com.battlesbudz.dinnerbot.feature.backup.BackupLabelNutrition
import com.battlesbudz.dinnerbot.feature.backup.BackupRecipeOverride
import com.battlesbudz.dinnerbot.feature.backup.BackupRecipeRating
import com.battlesbudz.dinnerbot.feature.backup.BackupEngine
import com.battlesbudz.dinnerbot.feature.backup.BackupScanSubstitution
import com.battlesbudz.dinnerbot.feature.backup.BackupShare
import com.battlesbudz.dinnerbot.feature.barcode.LocalScanGate
import com.battlesbudz.dinnerbot.feature.barcode.ScanGate
import com.battlesbudz.dinnerbot.feature.grocerylist.GroceryRow
import com.battlesbudz.dinnerbot.feature.grocerylist.GroceryListScreen
import com.battlesbudz.dinnerbot.feature.grocerylist.barcode.BarcodeScanLogic
import com.battlesbudz.dinnerbot.feature.grocerylist.ReconciliationMerge
import com.battlesbudz.dinnerbot.feature.`import`.ImportStart
import com.battlesbudz.dinnerbot.feature.`import`.RecipeImportFlow
import com.battlesbudz.dinnerbot.feature.nutrition.NutritionWeekInput
import com.battlesbudz.dinnerbot.feature.nutrition.WeekNutritionCard
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
import com.battlesbudz.dinnerbot.feature.premium.PaywallScreen
import com.battlesbudz.dinnerbot.feature.premium.PremiumBilling
import com.battlesbudz.dinnerbot.feature.premium.PremiumGateDialog
import com.battlesbudz.dinnerbot.feature.premium.PremiumPromptCard
import com.battlesbudz.dinnerbot.feature.premium.PremiumSettingsSection
import com.battlesbudz.dinnerbot.feature.premium.PurchaseResult
import com.battlesbudz.dinnerbot.feature.premium.RevenueCatBilling
import com.battlesbudz.dinnerbot.feature.premium.SavingsHistorySection
import com.battlesbudz.dinnerbot.feature.pricebook.PriceBookScreen
import com.battlesbudz.dinnerbot.feature.pricebook.PriceCorrection
import com.battlesbudz.dinnerbot.feature.recipes.CookScreen
import com.battlesbudz.dinnerbot.feature.recipes.RecipeCostCalculator
import com.battlesbudz.dinnerbot.feature.recipes.RecipeDetailScreen
import com.battlesbudz.dinnerbot.feature.recipes.RecipeListScreen
import com.battlesbudz.dinnerbot.ui.DinnerBotTheme
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

    /**
     * Text shared to Dinner Bot from another app (Phase RI-2): a recipe
     * page link or recipe text, handed to the import flow in Compose.
     * Read once from the launching intent (and from onNewIntent while
     * the app is running, the activity being singleTop) and consumed
     * by the UI the moment the import opens on it.
     */
    private var sharedText by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Edge to edge, explicitly (v0.6.0 redesign): targetSdk 35 draws
        // behind the system bars whether the app asks or not, and the
        // old build never accounted for them — the user found the tabs
        // and back buttons hiding under the phone's back / home /
        // multitask bar. The app now opts in and every surface pads for
        // the bars it draws under (Scaffold insets for the main shell,
        // windowInsetsPadding on the walkthrough and loading states).
        // Auto bar styles (v0.6.1 dark mode): the system icons follow
        // the phone's own dark setting, dark icons on the light scheme
        // and light icons on the dark one, instead of the light style
        // the redesign originally forced.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(
                android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT,
            ),
            navigationBarStyle = SystemBarStyle.auto(
                android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT,
            ),
        )
        sharedText = intent?.sharedTextExtra()
        setContent {
            DinnerBotTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    DinnerBotApp(
                        database = database,
                        loadAsset = { name ->
                            assets.open(name).bufferedReader().use { it.readText() }
                        },
                        sharedText = sharedText,
                        onSharedTextConsumed = { sharedText = null },
                    )
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
                // addMissingSeedPriceItems deliberately runs only in the
                // app's load effect: its count drives the one-time reprice
                // of stored plans when the book gains items (v0.4.3), and
                // this warm-up racing it would consume the count.
                SeedLoader(database).refreshSeedNutrition(nutritionJson)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        sharedText = intent.sharedTextExtra()
    }
}

/** The text another app shared to Dinner Bot, or null when this intent is not a share. */
private fun Intent.sharedTextExtra(): String? =
    if (action == Intent.ACTION_SEND && type == "text/plain") {
        getStringExtra(Intent.EXTRA_TEXT)?.takeIf { it.isNotBlank() }
    } else {
        null
    }

/**
 * The five destinations (v0.6.0 redesign, the user's decision): Recipes,
 * List, Plan in the middle (the most-used screen, his call), Pantry,
 * Settings. The old Prices tab folds into Pantry behind its pill toggle
 * and the old Nutrition tab folds into the recipe screens and the Plan
 * week's card; no feature went away, only the extra tabs did.
 */
private enum class Tab(val label: String, val icon: ImageVector) {
    RECIPES("Recipes", Icons.Filled.MenuBook),
    LIST("List", Icons.Filled.ShoppingCart),
    PLANNER("Plan", Icons.Filled.CalendarMonth),
    PANTRY("Pantry", Icons.Filled.Inventory2),
    SETTINGS("Settings", Icons.Filled.Settings),
}

/**
 * The brand top bar (v0.6.0 redesign): the robot-chef mark and the app
 * name on every screen, and — when a sub-screen is open — a proper
 * back arrow at the top, visible above everything, which runs the same
 * step as the system back button. Scaffold pads it clear of the status
 * bar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DinnerBotTopBar(showBack: Boolean, onBack: () -> Unit) {
    TopAppBar(
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter = painterResource(R.drawable.ic_dinnerbot_mark),
                    contentDescription = null,
                    modifier = Modifier.size(30.dp),
                )
                Text(
                    "Dinner Bot",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        },
        navigationIcon = {
            if (showBack) {
                IconButton(onClick = onBack, modifier = Modifier.testTag("top-back")) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                    )
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    )
}

/**
 * The five-tab bottom bar (v0.6.0 redesign). Labels always show (the
 * mockups label every tab), the selected tab sits in the black pill
 * indicator from the mockups, and NavigationBar itself applies the
 * navigation-bar inset so the row never hides under the phone's own
 * back / home / multitask bar.
 */
@Composable
private fun DinnerBotNavBar(tab: Tab, onSelect: (Tab) -> Unit) {
    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
        Tab.entries.forEach { entry ->
            NavigationBarItem(
                selected = entry == tab,
                onClick = { onSelect(entry) },
                icon = { Icon(entry.icon, contentDescription = entry.label) },
                label = { Text(entry.label, maxLines = 1) },
                alwaysShowLabel = true,
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                    indicatorColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.onSurface,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
                modifier = Modifier.testTag("tab-${entry.label}"),
            )
        }
    }
}

/**
 * The Pantry screen's pill toggle (v0.6.0 redesign): On hand / Price
 * book, the two halves of the merged destination.
 */
@Composable
private fun PantryModeToggle(showPrices: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(
            selected = !showPrices,
            onClick = { onChange(false) },
            label = { Text("On hand") },
            modifier = Modifier.testTag("pantry-toggle-onhand"),
        )
        FilterChip(
            selected = showPrices,
            onClick = { onChange(true) },
            label = { Text("Price book") },
            modifier = Modifier.testTag("pantry-toggle-prices"),
        )
    }
}

@Composable
private fun DinnerBotApp(
    database: DinnerBotDatabase,
    loadAsset: (String) -> String,
    sharedText: String? = null,
    onSharedTextConsumed: () -> Unit = {},
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
    // Household recipe edits (Phase RI-1): the user's saved ingredient
    // lists over library recipes, by recipe id. The recipes state below
    // is always the EFFECTIVE set (seed + these overrides merged on the
    // load path); this map is kept beside it so screens can show which
    // recipes carry her edits and offer restore-original.
    var recipeOverrides by remember { mutableStateOf<Map<String, RecipeOverride>>(emptyMap()) }
    // the user's own recipes (Phase RI-2): imported and hand-made, kept
    // beside the effective set (which already contains them, merged on
    // the load path) so screens can show a recipe's source link and
    // the import flow can check for a duplicate source.
    var importedRecipes by remember { mutableStateOf<List<ImportedRecipe>>(emptyList()) }
    // First-cook keep prompt state (Phase RI-3): the recipe ids already
    // asked about (persisted; the prompt fires once per recipe, ever),
    // and the recipe the prompt is currently up for, if any.
    var keepPromptAskedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var keepPromptRecipeId by remember { mutableStateOf<String?>(null) }
    // Premium state (Phase C3): the on-device freemium record — the
    // cached entitlement, the free import/scan counters, and the
    // periodic-prompt dismissal. The holder is kept beside the delegate
    // because long-lived callbacks (the billing listener, the scan
    // gate) must reach the live state, not the instance captured at
    // their first composition. Premium status itself comes from
    // RevenueCat; this record is its cache, and every gate fails soft
    // on it (see PremiumRules).
    val premiumStateHolder = remember { mutableStateOf(PremiumLocalState()) }
    var premiumState by premiumStateHolder
    // One accumulated record per planned week (Phase C3): plans are
    // replaced on regeneration, so the savings history keeps its own.
    var savingsWeeks by remember { mutableStateOf<List<SavingsWeek>>(emptyList()) }
    // The premium gate prompt currently up, if any, and the paywall.
    val gateFeatureHolder = remember { mutableStateOf<PremiumFeature?>(null) }
    var gateFeature by gateFeatureHolder
    var showPaywall by remember { mutableStateOf(false) }
    var restoreBusy by remember { mutableStateOf(false) }
    var restoreMessage by remember { mutableStateOf<String?>(null) }
    // The billing seam (Phase C3): RevenueCat behind the PremiumBilling
    // interface, unconfigured until a build supplies an API key.
    val billing: PremiumBilling = remember { RevenueCatBilling(context.applicationContext) }
    // The generation disclaimer (Phase RI-3): titles of the
    // not-yet-priced dinners in the week just generated; non-empty
    // shows the pop-up once for that generation.
    var unpricedDisclaimerTitles by remember { mutableStateOf<List<String>>(emptyList()) }
    // A recipe import in progress (Phase RI-2); the flow takes the
    // screen while it runs, like the Sunday gate does.
    var importStart by remember { mutableStateOf<ImportStart?>(null) }
    // Household recipe photos (Epic issue #10): the records, and the
    // photo files that actually exist on this phone, by recipe id.
    var recipePhotos by remember { mutableStateOf<Map<String, RecipePhoto>>(emptyMap()) }
    var photoFiles by remember { mutableStateOf<Map<String, File>>(emptyMap()) }
    var barcodeLinks by remember { mutableStateOf<List<BarcodeLink>>(emptyList()) }
    // Remembered scan substitutions (PR v0.4.4): user data, loaded
    // with everything else; the list screen surfaces the matching one
    // as a suggestion when the same line is scanned again.
    var scanSubstitutions by remember { mutableStateOf<List<ScanSubstitution>>(emptyList()) }
    var nutritionEntries by remember { mutableStateOf<List<IngredientNutrition>>(emptyList()) }
    // Scanned label facts by item id (v0.6.3): an item's own product
    // label, captured by barcode, wins over its USDA entry in recipe
    // and week nutrition wherever the calculator reads it.
    var labelNutritionByItemId by remember { mutableStateOf<Map<String, LabelNutrition>>(emptyMap()) }
    // Sunday reconciliation state (Phase V2-7): ran-out records and cook
    // check-offs are user data, loaded with everything else.
    var ranOutEvents by remember { mutableStateOf<List<RanOutEvent>>(emptyList()) }
    var cookCheckoffs by remember { mutableStateOf<List<CookCheckoff>>(emptyList()) }
    var cookTarget by remember { mutableStateOf<CookTarget?>(null) }
    var reconFlow by remember { mutableStateOf<ReconFlow?>(null) }
    var presets by remember { mutableStateOf<List<DietPreset>>(emptyList()) }
    var tab by remember { mutableStateOf(Tab.RECIPES) }
    // The Pantry tab's pill toggle (v0.6.0 redesign): the price book
    // folded into Pantry. Hoisted here so the system back button can
    // step from the price book back to the on-hand view, like any other
    // sub-screen step.
    var pantryShowPrices by remember { mutableStateOf(false) }
    var openRecipeId by remember { mutableStateOf<String?>(null) }
    var variation by remember { mutableStateOf(0) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var reloadKey by remember { mutableStateOf(0) }
    var staplesAddedAtLoad by remember { mutableStateOf(0) }
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
            val addedStaples = SeedLoader(database)
                .addMissingSeedPriceItems(loadAsset("pricebook.json"))
            SeedLoader(database).refreshSeedNutrition(loadAsset("nutrition.json"))
            // Phase C2: on first install, copy the bundled starter
            // photos into local storage and record them with their
            // credits, so every seed recipe shows its photo through
            // the ordinary photo plumbing below. A failure here must
            // never block loading; the seeder retries on a later start.
            runCatching {
                BundledPhotoSeeder.seedIfNeeded(
                    context = context,
                    database = database,
                    openAsset = { name -> context.assets.open(name) },
                )
            }
            val loadedPresets = runCatching {
                SeedDataParser.parseDietPresets(loadAsset("diet-presets.json"))
            }.getOrDefault(emptyList())
            val s = database.settingsDao().get()?.let { with(SeedMapper) { it.toModel() } }
                ?.copy(
                    stores = database.storeDao().getAll()
                        .map { with(SeedMapper) { it.toModel() } },
                )
                ?: OnboardingDefaults.initialSettings()
            SeedLoader(database).seedPantryIfEmpty(OnboardingDefaults.DEFAULT_STAPLES, s.pantryStaples)
            // THE recipe load path (Phase RI-1, plan section 5.1): stored
            // seed recipes with the household overrides merged over them.
            // Every surface (planner, recipe screens, costing, grocery
            // list, nutrition) consumes this one effective set, so the
            // seed version of a corrected dish can never leak through a
            // second path. Phase RI-2 extends the same merge with
            // the user's own imported and hand-made recipes, which live
            // in their own table and are never seed-touched.
            val storedRecipes = database.recipeDao().getAll().map { with(SeedMapper) { it.toModel() } }
            val loadedOverrides = database.recipeOverrideDao().getAll()
                .map { with(SeedMapper) { it.toModel() } }
                .associateBy { it.recipeId }
            val loadedImported = database.importedRecipeDao().getAll()
                .map { with(SeedMapper) { it.toModel() } }
            val bookOnDisk = database.priceItemDao().getAll().map { with(SeedMapper) { it.toModel() } }
            // The price-book backfill (the user's correction, 2026-10-24):
            // imports saved before the correction hold ingredients with
            // no book item. They gain their items here — exact-name
            // links where the book already has the ingredient, new
            // guess-priced items otherwise — so an old import behaves
            // exactly like a new one. Idempotent: once every ingredient
            // resolves, the pass creates and rewrites nothing.
            val linkResult = GuessPricer.linkRecipes(
                loadedImported, bookOnDisk, LocalDate.now().toString(),
                defaultStoreId = s.firstStore?.id,
            )
            if (linkResult.itemsToAdd.isNotEmpty() || linkResult.recipes != loadedImported) {
                linkResult.itemsToAdd.forEach {
                    database.priceItemDao().upsert(with(SeedMapper) { it.toEntity() })
                }
                linkResult.recipes.forEach {
                    database.importedRecipeDao().upsert(with(SeedMapper) { it.toEntity() })
                }
            }
            val linkedImported = linkResult.recipes
            val r = EffectiveRecipes.mergeUserRecipes(
                EffectiveRecipes.applyOverrides(storedRecipes, loadedOverrides),
                linkedImported.map { it.recipe },
            )
            val p = bookOnDisk + linkResult.itemsToAdd
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
                    GroceryRow(it.rowId, with(PlanMapper) { it.toModel() }, pl.id)
                }
            } ?: emptyList()
            val rows2 = plan2?.let { pl ->
                database.groceryDao().getForPlan(pl.id).map {
                    GroceryRow(it.rowId, with(PlanMapper) { it.toModel() }, pl.id)
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
            val loadedLabelNutrition = database.labelNutritionDao().getAll()
                .map { with(SeedMapper) { it.toModel() } }
                .associateBy { it.itemId }
            val loadedRanOut = database.ranOutDao().getAll()
                .map { with(ReconciliationMapper) { it.toModel() } }
            val loadedCheckoffs = database.cookCheckoffDao().getAll()
                .map { with(ReconciliationMapper) { it.toModel() } }
            val loadedSubstitutions = database.scanSubstitutionDao().getAll()
                .map { with(SeedMapper) { it.toModel() } }
            val loadedKeepPromptAsked = database.keepPromptDao().getAll()
                .map { it.recipeId }.toSet()
            // The premium record (Phase C3): created on first sight
            // with its first-seen stamp, which anchors the periodic
            // prompt's settle-in interval.
            val storedPremium = database.premiumStateDao().get()
                ?.let { with(SeedMapper) { it.toModel() } }
                ?: PremiumLocalState(firstSeenAtMillis = System.currentTimeMillis()).also { fresh ->
                    database.premiumStateDao().upsert(with(SeedMapper) { fresh.toEntity() })
                }
            // A premium update can land while this load is still
            // reading (the billing listener's first report, a counted
            // scan or import, a prompt dismissal): updates write the
            // live holder, and adopting this slightly older snapshot
            // over it would silently undo the update for the session.
            // When the live record differs from the snapshot the live
            // record wins; the snapshot only supplies the first-seen
            // stamp the untouched composition default never had, and
            // the merged record is written back so the disk agrees.
            val livePremium = premiumStateHolder.value
            val loadedPremium = if (livePremium == storedPremium) {
                storedPremium
            } else {
                livePremium.copy(
                    firstSeenAtMillis = if (livePremium.firstSeenAtMillis != 0L) {
                        livePremium.firstSeenAtMillis
                    } else {
                        storedPremium.firstSeenAtMillis
                    },
                ).also { merged ->
                    database.premiumStateDao().upsert(with(SeedMapper) { merged.toEntity() })
                }
            }
            val loadedSavings = database.savingsRecordDao().getAll()
                .map { with(SeedMapper) { it.toModel() } }
            LoadedState(
                s, r, p, pantry, stocks, plan, rows, plan2, rows2,
                loadedRatings, loadedPresets, loadedLinks, loadedNutrition,
                loadedRanOut, loadedCheckoffs, loadedPhotoById, loadedPhotoFiles,
                addedStaples, loadedSubstitutions, loadedOverrides, linkedImported,
                loadedKeepPromptAsked, loadedLabelNutrition,
                loadedPremium, loadedSavings,
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
        labelNutritionByItemId = loaded.labelNutrition
        ranOutEvents = loaded.ranOutEvents
        cookCheckoffs = loaded.cookCheckoffs
        scanSubstitutions = loaded.scanSubstitutions
        recipeOverrides = loaded.recipeOverrides
        importedRecipes = loaded.importedRecipes
        keepPromptAskedIds = loaded.keepPromptAsked
        premiumState = loaded.premiumState
        savingsWeeks = loaded.savingsWeeks
        staplesAddedAtLoad = loaded.staplesAdded
        loading = false
    }

    fun refresh() { reloadKey += 1 }

    // A share arriving while the app is open opens the import on it
    // (Phase RI-2). Consumed at once so a reload never reopens it.
    LaunchedEffect(sharedText) {
        val text = sharedText ?: return@LaunchedEffect
        importStart = ImportStart.Shared(text)
        onSharedTextConsumed()
    }

    // Billing (Phase C3): configure RevenueCat once and let its
    // entitlement reports correct the on-device cache as they arrive.
    // In an unconfigured build this never fires and the cache simply
    // stands, which is the fail-soft rule every gate relies on.
    LaunchedEffect(Unit) {
        billing.start { active -> notePremiumStatus(active) }
    }

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
                    samsMonthlySavingsCents = 0,
                    nights = plan.nights,
                    notes = plan.notes,
                    createdAtMillis = System.currentTimeMillis(),
                )
                database.planDao().insert(with(PlanMapper) { stored.toEntity() })
                database.groceryDao().insertAll(plan.lines.map { with(PlanMapper) { it.toEntity(id) } })
                // The week's savings record (Phase C3): one per week
                // start, refreshed whenever the week is (re)persisted,
                // so swaps and re-plans keep the history honest.
                database.savingsRecordDao().upsert(
                    with(SeedMapper) {
                        SavingsWeek(
                            plan.weekStart,
                            currentSettings.weeklyBudgetCents,
                            plan.cashNeededCents,
                        ).toEntity(System.currentTimeMillis())
                    },
                )
            }
            refresh()
        }
    }

    /**
     * The settings planning actually sees (Phase C3): diet presets are
     * part of Premium, so while Premium is off the saved picks stay in
     * the settings untouched but shape nothing — the planner receives
     * the household's settings with the preset selection emptied. The
     * household's own won't-eat entries are not presets and always
     * apply, Premium or not.
     */
    fun gatedSettings(forSettings: HouseholdSettings): HouseholdSettings =
        if (premiumState.cachedPremium) {
            forSettings
        } else {
            forSettings.copy(dietPresets = emptyList())
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
            DietPresets.effectiveWontEat(gatedSettings(forSettings), presets),
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
            DietPresets.selected(gatedSettings(forSettings), presets),
        )

    /**
     * The generation disclaimer (Phase RI-3, ruling 5): when a week
     * just generated contains a not-yet-priced dinner of the user's, the
     * pop-up fires once for that generation, naming the dinner. The
     * dinner's own card carries the standing note from there.
     */
    fun noteUnpricedDisclaimer(plans: List<WeekPlanner.WeekPlan>) {
        val titles = plans.flatMap { it.unpricedImportedRecipeIds }
            .mapNotNull { id -> recipes.firstOrNull { it.id == id }?.title }
            .distinct()
        if (titles.isNotEmpty()) {
            unpricedDisclaimerTitles = titles
        }
    }

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
        noteUnpricedDisclaimer(listOf(plan))
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
                        samsMonthlySavingsCents = 0,
                        nights = week.nights,
                        notes = week.notes,
                        createdAtMillis = createdAt,
                    )
                    database.planDao().insert(with(PlanMapper) { stored.toEntity() })
                    database.groceryDao().insertAll(week.lines.map { with(PlanMapper) { it.toEntity(id) } })
                    // One savings record per week of the fortnight (Phase C3).
                    database.savingsRecordDao().upsert(
                        with(SeedMapper) {
                            SavingsWeek(
                                week.weekStart,
                                forSettings.weeklyBudgetCents,
                                week.cashNeededCents,
                            ).toEntity(System.currentTimeMillis())
                        },
                    )
                }
            }
            refresh()
        }
    }

    // ------------------------------------------------------------------
    // Premium (Phase C3)
    //
    // Every gate in the app consults the on-device premium record:
    // its cached entitlement for status, its counters for the free
    // import and scan allowances. RevenueCat is the source of truth
    // and corrects the cache through [notePremiumStatus]; between
    // corrections the cache stands (fail soft), so a store outage
    // never locks a premium user out, and no free feature reads
    // billing state at all.

    /** Apply one transform to the premium record, in state and on disk together. */
    fun updatePremiumState(transform: (PremiumLocalState) -> PremiumLocalState) {
        val current = premiumStateHolder.value
        var updated = transform(current)
        // An update can arrive before the first load has stamped the
        // record (the billing listener fires on its own schedule): a
        // persisted record always carries its first-seen stamp.
        if (updated.firstSeenAtMillis == 0L) {
            updated = updated.copy(firstSeenAtMillis = System.currentTimeMillis())
        }
        if (updated == current) return
        premiumStateHolder.value = updated
        scope.launch {
            withContext(Dispatchers.IO) {
                database.premiumStateDao().upsert(with(SeedMapper) { updated.toEntity() })
            }
        }
    }

    /** A premium status report from the store: the cache follows it; silence keeps the cache. */
    fun notePremiumStatus(active: Boolean) {
        updatePremiumState { current ->
            current.copy(
                cachedPremium = PremiumRules.resolvePremium(current.cachedPremium, active),
                premiumCheckedAtMillis = System.currentTimeMillis(),
            )
        }
    }

    /** Count one decoded barcode against the free scan allowance; premium scans are not counted. */
    fun recordScanUse() {
        updatePremiumState { current ->
            if (current.cachedPremium) {
                current
            } else {
                current.copy(counters = PremiumRules.afterScan(current.counters, LocalDate.now()))
            }
        }
    }

    /** Count one saved import against the free monthly cap; premium imports are not counted. */
    fun recordImportUse() {
        updatePremiumState { current ->
            if (current.cachedPremium) {
                current
            } else {
                current.copy(counters = PremiumRules.afterImport(current.counters, LocalDate.now()))
            }
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
        noteUnpricedDisclaimer(listOf(fortnight.week1, fortnight.week2))
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
        val dinners = EffectiveRecipes.resolveDinners(plan.nights, recipesByIdLocal)
        val (needs, _) = Costing.aggregateNeeds(dinners, forSettings.householdSize, priceItems.associateBy { it.id })
        val result = ReconciliationMerge.merge(
            lines = plan.lines,
            resolvedOuts = resolvedOuts,
            priceItems = priceItems,
            plannedItemIds = needs.map { it.item.id }.toSet(),
            plannedNeedsBase = needs.associate { it.item.id to it.neededBase },
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
        noteUnpricedDisclaimer(listOf(plan))
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
        noteUnpricedDisclaimer(listOf(mergedWeek1, fortnight.week2))
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
        // Two-week planning is part of Premium (Phase C3): the gate
        // fires before the Sunday check, so a free user meets the
        // Premium prompt here and one-week planning stays one tap
        // back. An existing fortnight on screen is untouched — only
        // generating a new one is gated.
        if (!premiumState.cachedPremium) {
            gateFeature = PremiumFeature.TWO_WEEK_PLANNING
            return
        }
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
        val stock1 = WeekPlanner.projectedStockAfter(
            forSettings, view1, adaptationFor(forSettings).recipes, priceItems, stock, owned,
        )
        val repriced2 = WeekPlanner.recost(
            settings = forSettings,
            current = view2,
            recipes = adaptationFor(forSettings).recipes,
            priceItems = priceItems,
            stockOnHand = stock1,
            pantryOwnedNames = owned,
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
                persistSettings(database, newSettings)
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

    /**
     * Restore-original (Phase RI-1): the household edit is deleted and
     * the current seed version stands again whole. The recipe's rating
     * and photo are separate rows and are not touched.
     */
    fun restoreRecipeOriginal(recipeId: String) {
        scope.launch {
            withContext(Dispatchers.IO) {
                database.recipeOverrideDao().delete(recipeId)
            }
            refresh()
        }
    }

    /**
     * A from-now-on ingredient edit (Phase RI-2 routing): where the
     * saved version lives depends on the recipe's origin. A library
     * recipe's edit is its override row (Phase RI-1); an imported or
     * hand-made recipe is the user's own data, so the edit rewrites her
     * recipe's own ingredient list in its table. Either way the saved
     * recipe is what every surface cooks from then on.
     */
    fun saveFromNowOn(recipeId: String, ingredients: List<RecipeIngredient>) {
        val recipe = recipes.firstOrNull { it.id == recipeId } ?: return
        scope.launch {
            withContext(Dispatchers.IO) {
                // A from-now-on edit can name an ingredient the book
                // has never seen; it joins the book guess-priced,
                // exactly as at import (the user's correction,
                // 2026-10-24) — everything is in the pricing book.
                // Library recipes link too since v0.6.2: before, their
                // override path skipped linking entirely, so an
                // override could point at nothing priceable.
                val book = database.priceItemDao().getAll()
                    .map { with(SeedMapper) { it.toModel() } }
                val (linked, created) = GuessPricer.linkRecipe(
                    recipe.copy(ingredients = ingredients),
                    book, LocalDate.now().toString(),
                    defaultStoreId = database.storeDao().getAll().firstOrNull()?.id,
                )
                created.forEach {
                    database.priceItemDao().upsert(with(SeedMapper) { it.toEntity() })
                }
                if (recipe.origin == RecipeOrigin.LIBRARY) {
                    // An ingredients save never wipes a steps edit
                    // (v0.6.3): the override's two halves are saved
                    // independently, so carry the stored steps over.
                    val existingSteps = database.recipeOverrideDao()
                        .getByRecipeId(recipeId)
                        ?.let { with(SeedMapper) { it.toModel() } }?.steps
                    database.recipeOverrideDao().upsert(
                        with(SeedMapper) {
                            RecipeOverride(
                                recipeId, linked.ingredients, System.currentTimeMillis(),
                                steps = existingSteps,
                            ).toEntity()
                        },
                    )
                } else {
                    val stored = database.importedRecipeDao().getById(recipeId)
                        ?.let { with(SeedMapper) { it.toModel() } }
                    if (stored != null) {
                        database.importedRecipeDao().upsert(
                            with(SeedMapper) {
                                stored.copy(
                                    recipe = stored.recipe.copy(
                                        ingredients = linked.ingredients,
                                    ),
                                ).toEntity()
                            },
                        )
                    }
                }
            }
            refresh()
        }
    }

    /**
     * A from-now-on steps edit (v0.6.3), routed exactly like the
     * ingredient edit above: a library recipe's steps land on its
     * override row, with the row's ingredient list carried over
     * untouched (or the recipe's current saved list when this is her
     * first edit of the recipe); an imported or hand-made recipe is
     * rewritten in its own table. Restore-original deletes the
     * override row whole, so the seed steps come back with everything
     * else.
     */
    fun saveStepsFromNowOn(recipeId: String, steps: List<String>) {
        val recipe = recipes.firstOrNull { it.id == recipeId } ?: return
        scope.launch {
            withContext(Dispatchers.IO) {
                if (recipe.origin == RecipeOrigin.LIBRARY) {
                    val existing = database.recipeOverrideDao().getByRecipeId(recipeId)
                        ?.let { with(SeedMapper) { it.toModel() } }
                    database.recipeOverrideDao().upsert(
                        with(SeedMapper) {
                            RecipeOverride(
                                recipeId = recipeId,
                                ingredients = existing?.ingredients ?: recipe.ingredients,
                                updatedAtMillis = System.currentTimeMillis(),
                                steps = steps,
                            ).toEntity()
                        },
                    )
                } else {
                    val stored = database.importedRecipeDao().getById(recipeId)
                        ?.let { with(SeedMapper) { it.toModel() } }
                    if (stored != null) {
                        database.importedRecipeDao().upsert(
                            with(SeedMapper) {
                                stored.copy(
                                    recipe = stored.recipe.copy(steps = steps),
                                ).toEntity()
                            },
                        )
                    }
                }
            }
            refresh()
        }
    }

    /**
     * A brand-new price book item created by hand (v0.6.2): the shared
     * add-an-ingredient form confirms into whatever door opened it —
     * the ingredient editor in recipe creation, editing, the cook
     * view, and import review, or the price book itself — and lands
     * here. The item is written to the book immediately, the scan
     * flow's precedent, so it exists (and rides in backups) even if
     * the flow that created it is cancelled afterwards.
     */
    fun createPriceItem(item: PriceItem) {
        scope.launch {
            withContext(Dispatchers.IO) {
                database.priceItemDao().upsert(with(SeedMapper) { item.toEntity() })
            }
            refresh()
        }
    }

    /**
     * Store the label facts captured for an item (v0.6.3): from a scan
     * in the shared add form, the shopping list's link picker, or a
     * rescan of an already-known barcode — a rescan replaces the
     * stored facts wholesale, because the freshest label is the
     * truest one.
     */
    fun saveLabelNutrition(itemId: String, facts: LabelNutrition) {
        scope.launch {
            withContext(Dispatchers.IO) {
                database.labelNutritionDao().upsert(
                    with(SeedMapper) { facts.copy(itemId = itemId).toEntity() },
                )
            }
            refresh()
        }
    }

    /**
     * Link a barcode to an item (v0.6.3): the write the add-form scan
     * makes, identical to the one the shopping list flow teaches with.
     */
    fun linkBarcode(barcode: String, itemId: String) {
        scope.launch {
            withContext(Dispatchers.IO) {
                database.barcodeLinkDao().upsert(
                    BarcodeLinkEntity(barcode, itemId, System.currentTimeMillis()),
                )
            }
            refresh()
        }
    }

    /**
     * Save a reviewed import (Phase RI-2): a new user recipe, or an
     * update in place of the earlier import from the same source (her
     * ratings and photos key off the recipe id, so they carry over an
     * update untouched).
     *
     * the user's correction (2026-10-24) supersedes the RI-2 rule that
     * the import created nothing: every ingredient still unmatched is
     * linked into the price book first — an exact-name item when one
     * exists, a new guess-priced item otherwise — because everything
     * is in the pricing book. No pantry row or barcode link is created.
     */
    fun saveImportedRecipe(recipe: Recipe, sourceLink: String?, updateExistingId: String?) {
        // The import cap (Phase C3): a NEW import — not a hand-made
        // recipe (manual entry is always free) and not an update of an
        // import already counted — spends one of the month's free
        // imports. When they are gone the save waits here, the review
        // stays open behind the Premium prompt, and nothing is lost.
        val isNewImport = recipe.origin == RecipeOrigin.IMPORTED && updateExistingId == null
        if (isNewImport &&
            !PremiumRules.canImport(premiumState.cachedPremium, premiumState.counters, LocalDate.now())
        ) {
            gateFeature = PremiumFeature.RECIPE_IMPORT
            return
        }
        // Reserve the import against the cap BEFORE the save starts:
        // the state update is synchronous, so a second save tapped
        // while this one is still writing sees the allowance already
        // spent and meets the gate instead of slipping a second
        // recipe past it. The count stays spent even if the write
        // were to fail — the safe direction for a quota.
        if (isNewImport) recordImportUse()
        scope.launch {
            withContext(Dispatchers.IO) {
                val existing = updateExistingId?.let { database.importedRecipeDao().getById(it) }
                val book = database.priceItemDao().getAll().map { with(SeedMapper) { it.toModel() } }
                val (linked, created) = GuessPricer.linkRecipe(
                    recipe, book, LocalDate.now().toString(),
                    defaultStoreId = database.storeDao().getAll().firstOrNull()?.id,
                )
                created.forEach {
                    database.priceItemDao().upsert(with(SeedMapper) { it.toEntity() })
                }
                val record = ImportedRecipe(
                    recipe = linked,
                    sourceLink = sourceLink,
                    createdAtMillis = existing?.createdAtMillis ?: System.currentTimeMillis(),
                )
                database.importedRecipeDao().upsert(with(SeedMapper) { record.toEntity() })
            }
            importStart = null
            tab = Tab.RECIPES
            openRecipeId = recipe.id
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
                scanSubstitutions = scanSubstitutions.map { record ->
                    BackupScanSubstitution(
                        originalItemId = record.originalItemId,
                        substituteItemId = record.substituteItemId,
                        recipeId = record.recipeId,
                        substitutePriceCents = record.substitutePriceCents,
                        createdAtMillis = record.createdAtMillis,
                    )
                },
                recipeOverrides = recipeOverrides.values.map { override ->
                    BackupRecipeOverride(
                        override.recipeId, override.ingredients, override.steps,
                    )
                },
                importedRecipes = importedRecipes.map { imported ->
                    BackupImportedRecipe(
                        imported.recipe, imported.sourceLink, imported.createdAtMillis,
                    )
                },
                // Label facts ride from format v9 (v0.6.3), but only
                // for items the book in this same file carries — the
                // import validation holds the file to that, and an
                // orphaned fact must never make her own backup
                // unreadable.
                labelNutrition = labelNutritionByItemId.values
                    .filter { facts -> priceItems.any { it.id == facts.itemId } }
                    .map { facts ->
                        BackupLabelNutrition(
                            itemId = facts.itemId,
                            barcode = facts.barcode,
                            per100g = facts.per100g,
                            perServing = facts.perServing,
                            servingSizeText = facts.servingSizeText,
                            servingGrams = facts.servingGrams,
                        )
                    },
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

    // The scan gate every barcode surface consults (Phase C3): the
    // allowance lives in the premium record, the prompt in the gate
    // state, and the camera simply never opens when the month's free
    // scans are spent. Recreated when the premium record changes so
    // the answers it gives are never stale.
    val scanGate = remember(premiumState) {
        ScanGate(
            canStartScan = {
                val state = premiumStateHolder.value
                when {
                    state.cachedPremium -> true
                    PremiumRules.scansRemaining(state.counters, LocalDate.now()) > 0 -> true
                    else -> {
                        gateFeatureHolder.value = PremiumFeature.BARCODE_SCAN
                        false
                    }
                }
            },
            onScanCompleted = { recordScanUse() },
        )
    }

    CompositionLocalProvider(LocalScanGate provides scanGate) {
    when {
        loading && settings == null -> Box(
            Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars).padding(16.dp),
        ) {
            Text("Loading Dinner Bot...")
        }
        settings?.onboardingComplete != true -> Box(
            Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars),
        ) {
            SetupWalkthrough(
            initial = settings ?: OnboardingDefaults.initialSettings(),
            onFinish = { saved ->
                scope.launch {
                    withContext(Dispatchers.IO) {
                        persistSettings(database, saved)
                        SeedLoader(database).seedPantryIfEmpty(
                            OnboardingDefaults.DEFAULT_STAPLES, saved.pantryStaples,
                        )
                    }
                    generatePlan(saved)
                    refresh()
                }
            },
            )
        }
        else -> {
            val currentSettings = settings!!
            // Display and cost the ADAPTED recipes: removable won't-eat
            // ingredients are out everywhere; blocked recipes still browse.
            val adaptation = remember(
                recipes, priceItems, currentSettings.wontEat, currentSettings.dietPresets,
                presets, premiumState.cachedPremium,
            ) {
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
            // The add-form scan's already-in-the-book check (v0.6.3):
            // a barcode resolves through the links learned on this
            // phone to the item it belongs to, if any.
            val existingItemForBarcode: (String) -> PriceItem? = { code ->
                barcodeLinks.firstOrNull { it.barcode == code }
                    ?.let { link -> priceItems.firstOrNull { it.id == link.itemId } }
            }
            val weekPlanView = remember(storedPlan, groceryRows) {
                storedPlan?.let { sp ->
                    WeekPlanner.WeekPlan(
                        weekStart = sp.weekStart,
                        nights = sp.nights,
                        lines = groceryRows.map { it.line },
                        cashNeededCents = sp.cashNeededCents,
                        foodCostCents = sp.foodCostCents,
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
            // The stored plans' not-yet-priced dinners of the user's
            // (Phase RI-3): computed live from the price book as it
            // stands, so a dinner's not-accurate note and the cash
            // card's guess label clear themselves the day its last
            // ingredient prices for real. Priced-ness is never stored.
            val unpricedDisplayIds = remember(recipes, priceItems, storedPlan, storedPlan2) {
                val itemsById = priceItems.associateBy { it.id }
                val plannedIds = (storedPlan?.nights.orEmpty() + storedPlan2?.nights.orEmpty())
                    .filterNot { it.notCooking }.map { it.recipeId }.toSet()
                recipes
                    .filter {
                        it.id in plannedIds &&
                            RecipePricing.isUnpricedUserRecipe(it, itemsById)
                    }
                    .map { it.id }.toSet()
            }
            /**
             * A recost rebuilds a week's lines from costing alone, which
             * would drop the lines Sunday reconciliation merged into the
             * plan: those are confirmed purchases, not costing output.
             * Fold any merged line the recost did not reproduce back in,
             * with its cash, so repricing never silently un-buys a
             * ran-out item.
             */
            fun keepMergedLines(
                current: WeekPlanner.WeekPlan,
                repriced: WeekPlanner.WeekPlan,
            ): WeekPlanner.WeekPlan {
                val kept = current.lines.filter { line ->
                    line.reconciliationAdded &&
                        repriced.lines.none { it.itemId == line.itemId }
                }
                if (kept.isEmpty()) return repriced
                return repriced.copy(
                    lines = repriced.lines + kept,
                    cashNeededCents = repriced.cashNeededCents +
                        kept.sumOf { it.packsToBuy * it.estimatedPackPriceCents },
                )
            }

            /**
             * The same protection for scan substitutions (PR v0.4.4): a
             * recost knows nothing about a this-week swap, so without
             * this fold repricing would quietly put the planned line
             * back and drop the item the user actually bought. The pure
             * fold lives with the scan logic; this applies its lines
             * and money deltas.
             */
            fun keepSubstitutedLines(
                current: WeekPlanner.WeekPlan,
                repriced: WeekPlanner.WeekPlan,
            ): WeekPlanner.WeekPlan {
                val fold = BarcodeScanLogic.foldSubstitutedLines(
                    current.lines, repriced.lines, priceItems.associateBy { it.id },
                )
                if (fold.lines == repriced.lines) return repriced
                return repriced.copy(
                    lines = fold.lines,
                    cashNeededCents = repriced.cashNeededCents + fold.cashDeltaCents,
                    foodCostCents = repriced.foodCostCents + fold.foodDeltaCents,
                )
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
                    val repriced1 = WeekPlanner.recost(
                        settings = currentSettings,
                        current = view,
                        recipes = adaptation.recipes,
                        priceItems = priceItems,
                        stockOnHand = stock,
                        pantryOwnedNames = owned,
                    )
                    val merged1 = keepMergedLines(view, repriced1)
                    val kept1 = keepSubstitutedLines(view, merged1)
                    val stock1 = WeekPlanner.projectedStockAfter(
                        currentSettings, repriced1, adaptation.recipes, priceItems, stock, owned,
                    )
                    // Merged bulk packs folded back into week 1 are real
                    // packs coming home, but the projection derives stock
                    // from recipe needs and cannot see them; week 2 must
                    // not buy the same pack twice (Codex review, PR #23).
                    // Scan substitutions reconcile here too (PR v0.4.4):
                    // the read uses the SUBSTITUTED week 1 lines, so the
                    // projection stops crediting the displaced item's
                    // purchase and counts the substitute's leftover.
                    val stockForWeek2 = WeekPlanner.stockWithMergedPacks(
                        stock1, kept1.lines, repriced1.lines, priceItems,
                        stockOnHand = stock,
                    )
                    val repriced2 = WeekPlanner.recost(
                        settings = currentSettings,
                        current = view2,
                        recipes = adaptation.recipes,
                        priceItems = priceItems,
                        stockOnHand = stockForWeek2,
                        pantryOwnedNames = owned,
                    )
                    val kept2 = keepSubstitutedLines(view2, keepMergedLines(view2, repriced2))
                    val changed = kept1.lines != view.lines ||
                        kept1.cashNeededCents != view.cashNeededCents ||
                        kept1.notes != view.notes ||
                        kept2.lines != view2.lines ||
                        kept2.cashNeededCents != view2.cashNeededCents ||
                        kept2.notes != view2.notes
                    if (changed) {
                        persistFortnight(
                            WeekPlanner.composeFortnight(kept1, kept2, priceItems),
                            keepIds = sp.id to sp2.id,
                            forSettings = currentSettings,
                        )
                    }
                    return
                }
                val repriced = keepSubstitutedLines(
                    view,
                    keepMergedLines(
                        view,
                        WeekPlanner.recost(
                            settings = currentSettings,
                            current = view,
                            recipes = adaptation.recipes,
                            priceItems = priceItems,
                            stockOnHand = PantryLogic.stockMap(bulkStocks),
                            pantryOwnedNames = PantryLogic.ownedNames(forPantryItems),
                        ),
                    ),
                )
                if (repriced.lines != view.lines ||
                    repriced.cashNeededCents != view.cashNeededCents ||
                    repriced.notes != view.notes
                ) {
                    persistPlan(repriced, keepId = sp.id, forSettings = currentSettings)
                }
            }

            /**
             * A tonight-only ingredient edit (Phase RI-1, ruling D1):
             * the edited list lands on that night's plan record only, and
             * the plan is repriced in place (same dinners, tonight's
             * version of the edited one) so tonight's grocery list and
             * cash reflect it immediately. The saved recipe is never
             * touched, and no other night changes. Reconciliation-merged
             * and scan-substituted lines are folded back exactly as a
             * pantry reprice folds them.
             */
            fun applyTonightEdits(
                planId: String,
                day: String,
                ingredients: List<RecipeIngredient>,
            ) {
                val edits = TonightOverrides(ingredients)
                fun edited(view: WeekPlanner.WeekPlan): WeekPlanner.WeekPlan =
                    view.copy(
                        nights = view.nights.map { night ->
                            if (night.day == day) night.copy(tonightEdits = edits) else night
                        },
                    )
                val sp = storedPlan ?: return
                val view = weekPlanView ?: return
                val sp2 = storedPlan2
                val view2 = weekPlanView2
                if (sp2 != null && view2 != null && fortnightView != null) {
                    val editedView1 = if (sp.id == planId) edited(view) else view
                    val editedView2 = if (sp2.id == planId) edited(view2) else view2
                    val owned = PantryLogic.ownedNames(pantryItems)
                    val stock = PantryLogic.stockMap(bulkStocks)
                    val dinners = EffectiveRecipes.resolveDinners(
                        editedView1.nights + editedView2.nights, recipesById,
                    )
                    val repriced1 = WeekPlanner.recost(
                        settings = currentSettings,
                        current = editedView1,
                        recipes = adaptation.recipes,
                        priceItems = priceItems,
                        stockOnHand = stock,
                        pantryOwnedNames = owned,
                    )
                    val kept1 = keepSubstitutedLines(
                        editedView1, keepMergedLines(editedView1, repriced1),
                    )
                    val stock1 = WeekPlanner.projectedStockAfter(
                        currentSettings, repriced1, adaptation.recipes, priceItems, stock, owned,
                    )
                    val stockForWeek2 = WeekPlanner.stockWithMergedPacks(
                        stock1, kept1.lines, repriced1.lines, priceItems,
                        stockOnHand = stock,
                    )
                    val repriced2 = WeekPlanner.recost(
                        settings = currentSettings,
                        current = editedView2,
                        recipes = adaptation.recipes,
                        priceItems = priceItems,
                        stockOnHand = stockForWeek2,
                        pantryOwnedNames = owned,
                    )
                    val kept2 = keepSubstitutedLines(
                        editedView2, keepMergedLines(editedView2, repriced2),
                    )
                    persistFortnight(
                        WeekPlanner.composeFortnight(kept1, kept2, priceItems),
                        keepIds = sp.id to sp2.id,
                        forSettings = currentSettings,
                    )
                    return
                }
                if (sp.id != planId) return
                val editedView = edited(view)
                val repriced = keepSubstitutedLines(
                    editedView,
                    keepMergedLines(
                        editedView,
                        WeekPlanner.recost(
                            settings = currentSettings,
                            current = editedView,
                            recipes = adaptation.recipes,
                            priceItems = priceItems,
                            stockOnHand = PantryLogic.stockMap(bulkStocks),
                            pantryOwnedNames = PantryLogic.ownedNames(pantryItems),
                        ),
                    ),
                )
                persistPlan(repriced, keepId = sp.id, forSettings = currentSettings)
            }

            /**
             * Uncross one pantry-covered line (Phase RI-3, the user's
             * weekly-list ruling): the item becomes a buy line for this
             * week at its book price. The pantry itself is never
             * written — the week's recost simply runs with the item in
             * its uncrossed set, and the buy line it produces carries
             * the uncrossed flag, so every later reprice of the week
             * keeps it bought. The reconciliation-merged and
             * scan-substituted folds run exactly as a tonight-only
             * edit's reprice runs them.
             */
            fun applyUncross(planId: String, itemId: String) {
                fun uncrossedFor(view: WeekPlanner.WeekPlan, target: Boolean): Set<String> =
                    view.lines.filter { it.uncrossed }.map { it.itemId }.toSet() +
                        (if (target) setOf(itemId) else emptySet())
                val sp = storedPlan ?: return
                val view = weekPlanView ?: return
                val sp2 = storedPlan2
                val view2 = weekPlanView2
                if (sp2 != null && view2 != null && fortnightView != null) {
                    val owned = PantryLogic.ownedNames(pantryItems)
                    val stock = PantryLogic.stockMap(bulkStocks)
                    val dinners = EffectiveRecipes.resolveDinners(
                        view.nights + view2.nights, recipesById,
                    )
                    val repriced1 = WeekPlanner.recost(
                        settings = currentSettings,
                        current = view,
                        recipes = adaptation.recipes,
                        priceItems = priceItems,
                        stockOnHand = stock,
                        pantryOwnedNames = owned,
                        uncrossedItemIds = uncrossedFor(view, sp.id == planId),
                    )
                    val kept1 = keepSubstitutedLines(
                        view, keepMergedLines(view, repriced1),
                    )
                    val stock1 = WeekPlanner.projectedStockAfter(
                        currentSettings, repriced1, adaptation.recipes, priceItems, stock, owned,
                    )
                    val stockForWeek2 = WeekPlanner.stockWithMergedPacks(
                        stock1, kept1.lines, repriced1.lines, priceItems,
                        stockOnHand = stock,
                    )
                    val repriced2 = WeekPlanner.recost(
                        settings = currentSettings,
                        current = view2,
                        recipes = adaptation.recipes,
                        priceItems = priceItems,
                        stockOnHand = stockForWeek2,
                        pantryOwnedNames = owned,
                        uncrossedItemIds = uncrossedFor(view2, sp2.id == planId),
                    )
                    val kept2 = keepSubstitutedLines(
                        view2, keepMergedLines(view2, repriced2),
                    )
                    persistFortnight(
                        WeekPlanner.composeFortnight(kept1, kept2, priceItems),
                        keepIds = sp.id to sp2.id,
                        forSettings = currentSettings,
                    )
                    return
                }
                if (sp.id != planId) return
                val repriced = keepSubstitutedLines(
                    view,
                    keepMergedLines(
                        view,
                        WeekPlanner.recost(
                            settings = currentSettings,
                            current = view,
                            recipes = adaptation.recipes,
                            priceItems = priceItems,
                            stockOnHand = PantryLogic.stockMap(bulkStocks),
                            pantryOwnedNames = PantryLogic.ownedNames(pantryItems),
                            uncrossedItemIds = uncrossedFor(view, true),
                        ),
                    ),
                )
                persistPlan(repriced, keepId = sp.id, forSettings = currentSettings)
            }

            // v0.4.3 upgrade (Codex review, PR #23): when this load added
            // the newly priced staples to the book, reprice the stored
            // week once so the active plan reflects them immediately —
            // an unowned staple's buy line appears and the stale
            // "not priced" note goes — instead of waiting for the next
            // pantry toggle or regeneration.
            LaunchedEffect(staplesAddedAtLoad, storedPlan?.id) {
                if (staplesAddedAtLoad > 0 && storedPlan != null) {
                    repriceStoredPlan(pantryItems)
                }
            }
            // The generation disclaimer (Phase RI-3): shown once when
            // a week just generated contains a not-yet-priced dinner.
            if (unpricedDisclaimerTitles.isNotEmpty()) {
                AlertDialog(
                    onDismissRequest = { unpricedDisclaimerTitles = emptyList() },
                    modifier = Modifier.testTag("unpriced-disclaimer"),
                    title = { Text("One dinner is not priced yet") },
                    text = {
                        Text(
                            "This week includes " +
                                unpricedDisclaimerTitles.joinToString(", ") +
                                ", whose ingredients do not all have prices yet. " +
                                "Its cost in this week's total is a guess from similar " +
                                "items, marked as a guess wherever it shows. After the " +
                                "first shop, when those items are scanned or their " +
                                "prices are corrected, the dinner is priced for real.",
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = { unpricedDisclaimerTitles = emptyList() }) {
                            Text("Got it")
                        }
                    },
                )
            }
            // The first-cook keep prompt (Phase RI-3): asked once per
            // recipe of the user's, right after its first cook completes.
            // A thumbs up keeps it in rotation (the UP rating); a
            // thumbs down takes it out through the same rating; closing
            // the prompt records nothing and it never asks again.
            val promptRecipe = keepPromptRecipeId?.let { id ->
                recipes.firstOrNull { it.id == id }
            }
            if (promptRecipe != null) {
                AlertDialog(
                    onDismissRequest = { keepPromptRecipeId = null },
                    modifier = Modifier.testTag("keep-prompt"),
                    title = { Text("Did you like ${promptRecipe.title}?") },
                    text = {
                        Text(
                            "You cooked ${promptRecipe.title} for the first time. " +
                                "A thumbs up keeps it in your dinner rotations. " +
                                "A thumbs down takes it out of generated weeks; " +
                                "it stays in your recipes either way.",
                        )
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                rateRecipe(promptRecipe.id, RecipeRating.UP)
                                keepPromptRecipeId = null
                            },
                            modifier = Modifier.testTag("keep-prompt-up"),
                        ) { Text("👍 Keep it") }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = {
                                rateRecipe(promptRecipe.id, RecipeRating.DOWN)
                                keepPromptRecipeId = null
                            },
                            modifier = Modifier.testTag("keep-prompt-down"),
                        ) { Text("👎 Not for us") }
                    },
                )
            }
            // The premium gate prompt (Phase C3): shown when a gate
            // stops an action — imports or scans spent, two-week
            // planning or a diet preset or the savings history asked
            // for. "See Premium plans" swaps it for the paywall; "Not
            // now" returns to the free path, which every gate leaves
            // fully working.
            val activeGate = gateFeature
            if (activeGate != null) {
                PremiumGateDialog(
                    feature = activeGate,
                    onSeePremium = {
                        gateFeature = null
                        showPaywall = true
                    },
                    onDismiss = { gateFeature = null },
                )
            }
            // ---- Back navigation (v0.6.0 redesign) ----
            // the user: the system back button used to leave the app from
            // anywhere, because sub-screens were state, not destinations,
            // and nothing handled back. Now every sub-screen is one back
            // step, in the order a person would unwind them; only a tab's
            // root screen lets back do Android's usual thing and leave.
            // The top bar's back arrow runs the same steps.
            //
            // Sub-screen state survives tab switches on purpose (go look
            // at the list, come back, the recipe is still open), so each
            // flag counts as "open" only on the tab that shows it: back
            // on another tab's root screen must behave like root back,
            // never clear a hidden screen's state (Codex review, PR #33).
            // The import flow and the Sunday check take over the whole
            // screen whatever the tab, so those two always count.
            val recipeDetailOpen = tab == Tab.RECIPES && openRecipeId != null
            val cookOpen = tab == Tab.PLANNER && cookTarget != null
            val howItWorksOpen = tab == Tab.SETTINGS && showHowItWorks
            val priceBookOpen = tab == Tab.PANTRY && pantryShowPrices
            val subScreenOpen = importStart != null || reconFlow != null || showPaywall ||
                recipeDetailOpen || cookOpen || howItWorksOpen || priceBookOpen
            fun goBackOneStep() {
                when {
                    showPaywall -> showPaywall = false
                    importStart != null -> importStart = null
                    reconFlow != null -> skipReconciliation(reconFlow!!)
                    howItWorksOpen -> showHowItWorks = false
                    cookOpen -> cookTarget = null
                    recipeDetailOpen -> openRecipeId = null
                    priceBookOpen -> pantryShowPrices = false
                }
            }
            BackHandler(enabled = subScreenOpen) { goBackOneStep() }

            // The week's nutrition inputs, shared by the Plan screens'
            // nutrition card (the old Nutrition tab's weekly half).
            val nutritionWeeks = listOfNotNull(
                storedPlan?.let { NutritionWeekInput("This week's dinners", it.nights) },
                storedPlan2?.let { NutritionWeekInput("Next week's dinners", it.nights) },
            )
            // The Plan screens' extra sections (Phase C3): the
            // periodic Premium prompt card, when the cadence rules in
            // PremiumRules allow it, over the savings history section.
            // The prompt is an in-app card only — never a notification —
            // and dismissing it here is what starts its interval.
            val planBelowSection: @Composable () -> Unit = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (PremiumRules.shouldShowPeriodicPrompt(
                            premiumState,
                            onboardingComplete = currentSettings.onboardingComplete,
                            hasAnyPlan = savingsWeeks.isNotEmpty() || storedPlan != null,
                            nowMillis = System.currentTimeMillis(),
                        )
                    ) {
                        PremiumPromptCard(
                            onSeePremium = { showPaywall = true },
                            onDismiss = {
                                updatePremiumState {
                                    it.copy(lastPromptDismissedAtMillis = System.currentTimeMillis())
                                }
                            },
                        )
                    }
                    SavingsHistorySection(
                        report = SavingsHistory.report(savingsWeeks, LocalDate.now()),
                        isPremium = premiumState.cachedPremium,
                        onSeePremium = { showPaywall = true },
                    )
                }
            }
            // Per-serving estimates for the Plan night cards, from the
            // same calculator the recipe screens use, at the servings
            // this household cooks.
            val priceItemsByIdForCost = remember(priceItems) { priceItems.associateBy { it.id } }
            val perServingByRecipeId = remember(displayRecipes, priceItemsByIdForCost, currentSettings) {
                displayRecipes.associate { recipe ->
                    recipe.id to RecipeCostCalculator.costPerServingCents(
                        recipe, currentSettings.householdSize, priceItemsByIdForCost,
                    )
                }
            }

            // The app shell (v0.6.0 redesign): a Scaffold so the top bar
            // and the bottom tab bar own their system-bar insets and the
            // content is padded clear of both — nothing hides under the
            // status bar or the phone's back / home / multitask bar.
            Scaffold(
                topBar = {
                    DinnerBotTopBar(showBack = subScreenOpen, onBack = { goBackOneStep() })
                },
                bottomBar = {
                    DinnerBotNavBar(tab = tab, onSelect = { entry -> tab = entry })
                },
            ) { innerPadding ->
                Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                    val activeImport = importStart
                    val activeFlow = reconFlow
                    if (showPaywall) {
                        // The paywall (Phase C3) takes the screen while
                        // it is up, above whatever flow summoned it; the
                        // flow's state is untouched, so closing the
                        // paywall lands exactly where the user left off.
                        PaywallScreen(
                            billing = billing,
                            isPremium = premiumState.cachedPremium,
                            onPremiumChanged = { active -> notePremiumStatus(active) },
                            onClose = { showPaywall = false },
                        )
                    } else if (activeImport != null) {
                        // A recipe import in progress (Phase RI-2) takes
                        // the screen until it saves or is cancelled.
                        RecipeImportFlow(
                            start = activeImport,
                            priceItems = priceItems,
                            pantryOwnedNames = PantryLogic.ownedNames(pantryItems),
                            existingImports = importedRecipes,
                            onSave = { recipe, sourceLink, updateExistingId ->
                                saveImportedRecipe(recipe, sourceLink, updateExistingId)
                            },
                            onCancel = { importStart = null },
                            editorToday = LocalDate.now().toString(),
                            onCreatePriceItem = { item -> createPriceItem(item) },
                            existingItemForBarcode = existingItemForBarcode,
                            onBarcodeLinked = { barcode, itemId -> linkBarcode(barcode, itemId) },
                            onLabelFacts = { itemId, facts -> saveLabelNutrition(itemId, facts) },
                        )
                    } else if (activeFlow != null) {
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
                                    savedRecipe = recipes.firstOrNull { it.id == open.id },
                                    hasHouseholdEdits = EffectiveRecipes.hasOverride(
                                        open.id, recipeOverrides,
                                    ),
                                    onSaveIngredients = { edited ->
                                        saveFromNowOn(open.id, edited)
                                    },
                                    onSaveSteps = { steps ->
                                        saveStepsFromNowOn(open.id, steps)
                                    },
                                    onRestoreOriginal = { restoreRecipeOriginal(open.id) },
                                    editorToday = LocalDate.now().toString(),
                                    onCreatePriceItem = { item -> createPriceItem(item) },
                                    existingItemForBarcode = existingItemForBarcode,
                                    onBarcodeLinked = { barcode, itemId ->
                                        linkBarcode(barcode, itemId)
                                    },
                                    onLabelFacts = { itemId, facts ->
                                        saveLabelNutrition(itemId, facts)
                                    },
                                    nutritionByItemId = nutritionByItemId,
                                    nutritionByPantrySlug = nutritionByPantrySlug,
                                    labelNutritionByItemId = labelNutritionByItemId,
                                    sourceLink = importedRecipes
                                        .firstOrNull { it.recipe.id == open.id }?.sourceLink,
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
                                    onImportClick = { importStart = ImportStart.Blank },
                                    onAddOwnClick = { importStart = ImportStart.Manual },
                                )
                            }
                        }
                        Tab.PLANNER -> {
                            val target = cookTarget
                            val targetPlan = target?.let { t ->
                                listOfNotNull(storedPlan, storedPlan2).firstOrNull { it.id == t.planId }
                            }
                            val targetNight = targetPlan?.nights?.firstOrNull { it.day == target?.day }
                            // Tonight's dinner resolves per night (Phase
                            // RI-1): the saved (adapted) recipe with the
                            // night's tonight-only edits applied last.
                            val targetRecipe = targetNight?.let { night ->
                                recipesById[night.recipeId]?.let { saved ->
                                    EffectiveRecipes.applyTonight(saved, night)
                                }
                            }
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
                                            // The first-cook keep prompt
                                            // (Phase RI-3, ruling 7): when
                                            // this toggle completes the
                                            // dinner — every ingredient
                                            // checked off — and the dinner
                                            // is one of the user's own
                                            // recipes, never rated and
                                            // never asked about, ask once
                                            // whether it stays in rotation.
                                            // The asked record is written
                                            // as the prompt fires, so a
                                            // dismissal records nothing
                                            // more and never repeats.
                                            if (checked) {
                                                val allKeys = targetRecipe.ingredients
                                                    .map { RanOutKeys.forIngredient(it) }.toSet()
                                                val nowChecked = cookCheckoffs
                                                    .filter {
                                                        it.planId == target.planId &&
                                                            it.day == target.day && it.checked
                                                    }
                                                    .map { it.itemKey }.toSet() + key
                                                if (allKeys.isNotEmpty() &&
                                                    allKeys.all { it in nowChecked } &&
                                                    targetRecipe.origin != RecipeOrigin.LIBRARY &&
                                                    targetRecipe.id !in ratings &&
                                                    targetRecipe.id !in keepPromptAskedIds
                                                ) {
                                                    keepPromptAskedIds =
                                                        keepPromptAskedIds + targetRecipe.id
                                                    keepPromptRecipeId = targetRecipe.id
                                                    withContext(Dispatchers.IO) {
                                                        database.keepPromptDao().upsert(
                                                            KeepPromptEntity(
                                                                targetRecipe.id,
                                                                System.currentTimeMillis(),
                                                            ),
                                                        )
                                                    }
                                                }
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
                                    onSaveTonightOnly = { edited ->
                                        applyTonightEdits(target.planId, target.day, edited)
                                    },
                                    onSaveFromNowOn = { edited ->
                                        saveFromNowOn(targetNight.recipeId, edited)
                                    },
                                    onCreatePriceItem = { item -> createPriceItem(item) },
                                    existingItemForBarcode = existingItemForBarcode,
                                    onBarcodeLinked = { barcode, itemId ->
                                        linkBarcode(barcode, itemId)
                                    },
                                    onLabelFacts = { itemId, facts ->
                                        saveLabelNutrition(itemId, facts)
                                    },
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
                                unpricedRecipeIds = unpricedDisplayIds,
                                perServingCentsByRecipeId = perServingByRecipeId,
                                weekNutrition = {
                                    WeekNutritionCard(
                                        recipesById = recipesById,
                                        byItemId = nutritionByItemId,
                                        byPantrySlug = nutritionByPantrySlug,
                                        weeks = nutritionWeeks,
                                        householdSize = currentSettings.householdSize,
                                        labelByItemId = labelNutritionByItemId,
                                    )
                                },
                                belowPlanSection = planBelowSection,
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
                            unpricedRecipeIds = unpricedDisplayIds,
                            perServingCentsByRecipeId = perServingByRecipeId,
                            weekNutrition = {
                                WeekNutritionCard(
                                    recipesById = recipesById,
                                    byItemId = nutritionByItemId,
                                    byPantrySlug = nutritionByPantrySlug,
                                    weeks = nutritionWeeks,
                                    householdSize = currentSettings.householdSize,
                                    labelByItemId = labelNutritionByItemId,
                                )
                            },
                            belowPlanSection = planBelowSection,
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
                                BarcodeScanLogic.isTopUpPurchase(
                                    row.line, priceById[row.line.itemId],
                                )
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
                            stores = currentSettings.stores,
                            rows = bigTripRows,
                            topUpRows = topUpRows,
                            twoWeekTrip = isFortnightList,
                            onUncross = { row -> applyUncross(row.planId, row.line.itemId) },
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
                                        // A swapped row's frozen portion moves
                                        // with the corrected rate too.
                                        (groceryRows + groceryRows2)
                                            .filter {
                                                it.line.itemId == corrected.id &&
                                                    it.line.store == corrected.homeStore
                                            }
                                            .forEach { row ->
                                                if (row.line.substitutedForItemId != null) {
                                                    database.groceryDao().updatePackPriceAndPortion(
                                                        row.rowId, corrected.packPriceCents,
                                                        BarcodeScanLogic.correctedSwapPortion(
                                                            row.line, corrected,
                                                            corrected.packPriceCents,
                                                        ),
                                                    )
                                                } else {
                                                    database.groceryDao()
                                                        .updatePackPrice(row.rowId, corrected.packPriceCents)
                                                }
                                            }
                                    }
                                    refresh()
                                }
                            },
                            scanSubstitutions = scanSubstitutions,
                            substitutionRecipeContext = run {
                                // The dinner context of each listed item,
                                // from the plans actually stored: the one
                                // cooking night that uses it, when there
                                // is exactly one across the stored weeks.
                                // A swapped line keys by its primary
                                // displaced origin: the dinner uses that
                                // item, and the remembered-swap memory is
                                // paired to it.
                                val contexts = mutableMapOf<Pair<String, String>, String?>()
                                (bigTripRows + topUpRows).forEach { row ->
                                    val rootId = BarcodeScanLogic.primaryOriginOf(row.line)
                                        ?: row.line.itemId
                                    val key = row.planId to rootId
                                    if (key !in contexts) {
                                        val plan = listOfNotNull(storedPlan, storedPlan2)
                                            .firstOrNull { it.id == row.planId }
                                        contexts[key] = plan?.let {
                                            BarcodeScanLogic.recipeContextFor(
                                                rootId, it.nights, recipesById,
                                            )
                                        }
                                    }
                                }
                                contexts
                            },
                            onScanNewItem = { item, barcode ->
                                scope.launch {
                                    withContext(Dispatchers.IO) {
                                        saveScannedNewItem(database, item, barcode)
                                    }
                                    refresh()
                                }
                            },
                            onLabelFactsSeen = { itemId, facts ->
                                saveLabelNutrition(itemId, facts)
                            },
                            onSubstitute = { row, substitute, barcode, shelfPriceCents ->
                                // The recipe contexts are read from the
                                // stored plan the row belongs to, one per
                                // origin the new line will displace (the
                                // row's own origins plus the row's item),
                                // so each remembered record names its
                                // dinner when exactly one dinner uses
                                // that item.
                                val plan = listOfNotNull(storedPlan, storedPlan2)
                                    .firstOrNull { it.id == row.planId }
                                val origins = BarcodeScanLogic
                                    .displacedOriginsOf(row.line.substitutedForItemId) +
                                    row.line.itemId
                                val recipeContextByItemId: Map<String, String?> =
                                    origins.associateWith { origin ->
                                        plan?.let {
                                            BarcodeScanLogic.recipeContextFor(
                                                origin, it.nights, recipesById,
                                            )
                                        }
                                    }
                                scope.launch {
                                    withContext(Dispatchers.IO) {
                                        applyScanSubstitution(
                                            database, row, substitute, barcode,
                                            shelfPriceCents, recipeContextByItemId,
                                        )
                                    }
                                    refresh()
                                }
                            },
                            )
                        }
                        // Pantry and the price book are one destination
                        // now (v0.6.0 redesign): the pill toggle at the
                        // top switches between what's on hand and the
                        // book. Both screens keep their exact behavior;
                        // the toggle is the only thing new here.
                        Tab.PANTRY -> Column(modifier = Modifier.fillMaxSize()) {
                            PantryModeToggle(
                                showPrices = pantryShowPrices,
                                onChange = { pantryShowPrices = it },
                            )
                            Box(modifier = Modifier.weight(1f)) {
                                if (!pantryShowPrices) {
                                    PantryScreen(
                            pantryItems = pantryItems,
                            bulkPriceItems = PantryLogic.bulkItems(priceItems, bulkStocks),
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
                                } else {
                                    PriceBookScreen(
                                        priceItems = priceItems,
                                        stores = currentSettings.stores,
                                        today = LocalDate.now().toString(),
                                        onItemAdded = { item -> createPriceItem(item) },
                                        existingItemForBarcode = existingItemForBarcode,
                                        onBarcodeLinked = { barcode, itemId ->
                                            linkBarcode(barcode, itemId)
                                        },
                                        onLabelFacts = { itemId, facts ->
                                            saveLabelNutrition(itemId, facts)
                                        },
                                        onItemEdited = { item ->
                                            scope.launch {
                                                withContext(Dispatchers.IO) {
                                                    database.priceItemDao().upsert(
                                                        with(SeedMapper) { item.toEntity() },
                                                    )
                                                }
                                                refresh()
                                            }
                                        },
                                    )
                                }
                            }
                        }
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
                                    dietPresetsLocked = !premiumState.cachedPremium,
                                    onDietPresetLockedTap = {
                                        gateFeature = PremiumFeature.DIET_PRESETS
                                    },
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
                                                persistSettings(database, saved)
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
                            // Premium (Phase C3): status, the paywall,
                            // subscription management, and restore —
                            // quiet and always reachable from Settings.
                            PremiumSettingsSection(
                                isPremium = premiumState.cachedPremium,
                                billingConfigured = billing.isConfigured,
                                onSeePremium = { showPaywall = true },
                                onRestore = {
                                    restoreBusy = true
                                    restoreMessage = null
                                    scope.launch {
                                        restoreMessage = when (val result = billing.restorePurchases()) {
                                            PurchaseResult.Purchased ->
                                                "Your earlier purchase is restored. Premium is on."
                                            PurchaseResult.Cancelled -> null
                                            is PurchaseResult.Failed -> result.message
                                            PurchaseResult.Unavailable ->
                                                "Purchases are not available yet in this build."
                                        }
                                        restoreBusy = false
                                    }
                                },
                                restoreBusy = restoreBusy,
                                restoreMessage = restoreMessage,
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                            )
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
    /** Price book items the seed refresh added during this load (v0.4.3 staples). */
    val staplesAdded: Int,
    /** Remembered scan substitutions (PR v0.4.4). */
    val scanSubstitutions: List<ScanSubstitution>,
    /** Household recipe edits by recipe id (Phase RI-1); the recipes above already carry them merged. */
    val recipeOverrides: Map<String, RecipeOverride>,
    /** the user's own imported and hand-made recipes (Phase RI-2), also merged into the recipes above. */
    val importedRecipes: List<ImportedRecipe>,
    /** Recipe ids whose first-cook keep prompt has already fired (Phase RI-3); it never fires twice. */
    val keepPromptAsked: Set<String>,
    /** Scanned label facts by item id (v0.6.3). */
    val labelNutrition: Map<String, LabelNutrition>,
    /** The on-device premium record: cached entitlement, counters, prompt cadence (Phase C3). */
    val premiumState: PremiumLocalState,
    /** One savings record per planned week, oldest first (Phase C3). */
    val savingsWeeks: List<SavingsWeek>,
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

/**
 * Persist the household settings AND the user's stores (Phase C1): the
 * stores live in their own table, replaced wholesale in chosen order, so
 * every settings save path keeps them in step with the form's state.
 */
private suspend fun persistSettings(database: DinnerBotDatabase, settings: HouseholdSettings) {
    database.settingsDao().upsert(with(SeedMapper) { settings.toEntity() })
    database.storeDao().deleteAll()
    database.storeDao().insertAll(
        settings.stores.mapIndexed { index, store -> with(SeedMapper) { store.toEntity(index) } },
    )
}

/** Keep settings.pantryStaples mirroring the pantry table's owned names. */
private suspend fun syncPantryStaples(database: DinnerBotDatabase, settings: HouseholdSettings) {
    val owned = database.pantryDao().getAll().filter { it.owned }.map { it.name }
    if (owned.toSet() != settings.pantryStaples.toSet()) {
        persistSettings(database, settings.copy(pantryStaples = owned))
    }
}

/**
 * the user tapped a price on the shopping list. The total for the line becomes
 * the price book's truth: a normal line updates the item's pack price; a
 * rerouted line (bought at a different store) updates that store's per-unit
 * alternate for the item.
 */
private suspend fun correctLinePrice(database: DinnerBotDatabase, row: GroceryRow, totalCents: Int) {
    val entity = database.priceItemDao().getById(row.line.itemId)
    val isSwapLine = row.line.substitutedForItemId != null
    var rowPrice = totalCents
    var correctedItem: PriceItem? = null
    if (entity != null) {
        val item = with(SeedMapper) { entity.toModel() }
        correctedItem = item
        if (row.line.store == item.homeStore) {
            val perPack = (totalCents.toDouble() / row.line.packsToBuy.coerceAtLeast(1))
                .roundToInt().coerceAtLeast(1)
            database.priceItemDao().upsert(
                with(SeedMapper) { PriceCorrection.applyPackPrice(item, perPack).toEntity() },
            )
            rowPrice = perPack
        } else {
            val neededInPackUnit = Costing.amountInPackUnit(row.line.needed, item)
            if (neededInPackUnit > 0.0) {
                val perUnit = totalCents.toDouble() / neededInPackUnit
                database.priceItemDao().upsert(
                    with(SeedMapper) {
                        PriceCorrection.applyAlternatePrice(item, row.line.store, perUnit).toEntity()
                    },
                )
            }
            if (isSwapLine) {
                // The book's alternate still derives from total over
                // need, but the ROW of a swap prices per pack at any
                // store (it was built from a scanned pack): storing the
                // line total here would multiply it by the pack count
                // wherever the row's cash is computed (Codex, PR #24).
                rowPrice = (totalCents.toDouble() / row.line.packsToBuy.coerceAtLeast(1))
                    .roundToInt().coerceAtLeast(1)
            }
        }
    }
    // A swapped row's portion is frozen at its scanned rate (the fold
    // carries the line whole), so the correction recomputes it from
    // the corrected per-pack price; a planner line's portion is
    // rebuilt at the next reprice and needs no write here (PR v0.4.4).
    val item = correctedItem
    if (item != null && isSwapLine) {
        database.groceryDao().updatePackPriceAndPortion(
            row.rowId, rowPrice,
            BarcodeScanLogic.correctedSwapPortion(row.line, item, rowPrice),
        )
    } else {
        database.groceryDao().updatePackPrice(row.rowId, rowPrice)
    }
}

/**
 * A product the price book did not have, added from a scan (PR v0.4.4):
 * the item is saved to the book as an ordinary item and the scanned
 * barcode is linked to it, so the next scan of the same package recalls
 * the item with the price typed here.
 */
private suspend fun saveScannedNewItem(database: DinnerBotDatabase, item: PriceItem, barcode: String) {
    database.priceItemDao().upsert(with(SeedMapper) { item.toEntity() })
    database.barcodeLinkDao().upsert(
        BarcodeLinkEntity(barcode, item.id, System.currentTimeMillis()),
    )
}

/**
 * A this-week-only scan substitution (PR v0.4.4, the user's drumsticks
 * case). Writes, in order:
 *
 * 1. The substitute's price, store-aware like every scan write: the
 *    pack price when the line shops at the item's home store, that
 *    store's per-unit alternate otherwise. A substitute the book did
 *    not have joins the book here, at the typed price. The ORIGINAL
 *    item's price is never touched.
 * 2. The barcode's link, when the scan carried one.
 * 3. The row surgery: the planned row comes off this week's list and
 *    the substitute's row goes on, marked with what it replaced —
 *    merged into the week's existing line for the substitute when
 *    another dinner already buys it — and the stored plan's cash and
 *    food totals move by the exact difference. The recipe and future
 *    weeks are not touched.
 * 4. The remembered record, so the same swap is suggested next time.
 */
private suspend fun applyScanSubstitution(
    database: DinnerBotDatabase,
    row: GroceryRow,
    substitute: PriceItem,
    barcode: String?,
    shelfPriceCents: Int,
    recipeContextByItemId: Map<String, String?>,
) {
    val stored = database.priceItemDao().getById(substitute.id)
        ?.let { with(SeedMapper) { it.toModel() } }
    val base = stored ?: substitute
    val priced = if (row.line.store == base.homeStore) {
        BarcodeScanLogic.scanCorrection(base, shelfPriceCents)
    } else {
        PriceCorrection.applyAlternatePrice(
            base,
            row.line.store,
            shelfPriceCents.toDouble() / base.packQuantity.amount,
            source = BarcodeScanLogic.SCAN_SOURCE,
        )
    }
    database.priceItemDao().upsert(with(SeedMapper) { priced.toEntity() })
    if (barcode != null) {
        database.barcodeLinkDao().upsert(
            BarcodeLinkEntity(barcode, priced.id, System.currentTimeMillis()),
        )
    }
    val newLine = BarcodeScanLogic.substituteLine(row.line, priced, shelfPriceCents)
    // The week's totals move by the exact line movements below.
    var cashDelta = -(row.line.packsToBuy * row.line.estimatedPackPriceCents)
    var foodDelta = -(row.line.portionCostCents ?: 0)
    // When another dinner this week already buys the substitute, the
    // swap merges into that line instead of inserting a second one
    // (the write-time twin of the reprice fold's merge): one line,
    // packs enough for both dinners, both portions, the swap's marker.
    val mergeTarget = database.groceryDao().getForPlan(row.planId)
        .filter { it.rowId != row.rowId }
        .firstOrNull {
            // Same item, same store, same effective trip (the list's
            // own section rule: a plain perishable line and a
            // reconciliation line both shop the Week 2 top-up, while
            // a reconciliation line for a shelf-stable item does not
            // shop with the big trip). A line differing on any of
            // those is a separate purchase, and merging into it would
            // mislabel the store section, the price, or the schedule
            // (Codex, PR #24). A line already carrying a swap never
            // absorbs another.
            it.itemId == priced.id && it.store == row.line.store &&
                it.substitutedForItemId == null &&
                BarcodeScanLogic.isTopUpPurchase(
                    with(PlanMapper) { it.toModel() }, priced,
                ) == BarcodeScanLogic.isTopUpPurchase(newLine, priced)
        }
    database.groceryDao().deleteRow(row.rowId)
    if (mergeTarget != null) {
        val existingLine = with(PlanMapper) { mergeTarget.toModel() }
        val merged = BarcodeScanLogic.mergeSwapIntoLine(existingLine, newLine, priced)
        database.groceryDao().deleteRow(mergeTarget.rowId)
        database.groceryDao().insert(with(PlanMapper) { merged.toEntity(row.planId) })
        cashDelta += merged.packsToBuy * merged.estimatedPackPriceCents -
            existingLine.packsToBuy * existingLine.estimatedPackPriceCents
        foodDelta += (merged.portionCostCents ?: 0) - (existingLine.portionCostCents ?: 0)
    } else {
        database.groceryDao().insert(with(PlanMapper) { newLine.toEntity(row.planId) })
        cashDelta += newLine.packsToBuy * newLine.estimatedPackPriceCents
        foodDelta += newLine.portionCostCents ?: 0
    }
    val planEntity = database.planDao().getById(row.planId)
    if (planEntity != null) {
        val plan = with(PlanMapper) { planEntity.toStoredPlan() }
        val updated = plan.copy(
            cashNeededCents = plan.cashNeededCents + cashDelta,
            foodCostCents = plan.foodCostCents + foodDelta,
        )
        database.planDao().insert(with(PlanMapper) { updated.toEntity() })
    }
    // One remembered record per displaced origin, each with its own
    // dinner context: a swap from an already-swapped line pairs the
    // substitute with the original dinner's item, not the intermediate
    // swap, and a swap from a write-time merged line pairs it with BOTH
    // dinners the row stood for.
    val stamp = System.currentTimeMillis()
    BarcodeScanLogic.displacedOriginsOf(newLine.substitutedForItemId).forEach { origin ->
        database.scanSubstitutionDao().insert(
            with(SeedMapper) {
                ScanSubstitution(
                    planId = row.planId.ifEmpty { null },
                    originalItemId = origin,
                    substituteItemId = priced.id,
                    recipeId = recipeContextByItemId[origin],
                    substitutePriceCents = shelfPriceCents,
                    createdAtMillis = stamp,
                ).toEntity()
            },
        )
    }
}

private suspend fun applyBackup(database: DinnerBotDatabase, data: BackupData) {
    persistSettings(database, data.settings)
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
    // Remembered scan substitutions replace wholesale (PR v0.4.4): a
    // pre-v5 backup has none, which clears them. The plan the swap
    // happened in is not carried by the file, so restored records have
    // none; the pairing memory is what matters.
    database.scanSubstitutionDao().deleteAll()
    data.scanSubstitutions.forEach { saved ->
        database.scanSubstitutionDao().insert(
            with(SeedMapper) {
                ScanSubstitution(
                    planId = null,
                    originalItemId = saved.originalItemId,
                    substituteItemId = saved.substituteItemId,
                    recipeId = saved.recipeId,
                    substitutePriceCents = saved.substitutePriceCents,
                    createdAtMillis = saved.createdAtMillis,
                ).toEntity()
            },
        )
    }
    // Household recipe edits replace wholesale (Phase RI-1): a pre-v6
    // backup has none, which clears them — the same semantics as the
    // ratings and links above, since importing replaces this phone's
    // data. Tonight-only edits ride on plans and do not restore, like
    // the plans themselves.
    database.recipeOverrideDao().deleteAll()
    data.recipeOverrides.forEach { saved ->
        database.recipeOverrideDao().upsert(
            with(SeedMapper) {
                RecipeOverride(
                    saved.recipeId, saved.ingredients, System.currentTimeMillis(),
                    steps = saved.steps,
                ).toEntity()
            },
        )
    }
    // Scanned label facts replace wholesale (v0.6.3, format v9): a
    // pre-v9 backup has none, which clears them — the same semantics
    // as every other household section above.
    database.labelNutritionDao().deleteAll()
    data.labelNutrition.forEach { saved ->
        database.labelNutritionDao().upsert(
            with(SeedMapper) {
                LabelNutrition(
                    itemId = saved.itemId,
                    barcode = saved.barcode,
                    per100g = saved.per100g,
                    perServing = saved.perServing,
                    servingSizeText = saved.servingSizeText,
                    servingGrams = saved.servingGrams,
                    updatedAtMillis = System.currentTimeMillis(),
                ).toEntity()
            },
        )
    }
    // the user's own recipes replace wholesale (Phase RI-2): a pre-v7
    // backup has none, which clears them — the same semantics as every
    // other household section, since importing replaces this phone's
    // data.
    database.importedRecipeDao().deleteAll()
    data.importedRecipes.forEach { saved ->
        database.importedRecipeDao().upsert(
            with(SeedMapper) {
                ImportedRecipe(
                    saved.recipe, saved.sourceLink, saved.createdAtMillis,
                ).toEntity()
            },
        )
    }
    // Plans were priced from the old book; clear them so nothing stale lingers.
    database.planDao().deleteAll()
    database.groceryDao().deleteAll()
}
