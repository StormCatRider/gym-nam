// ==============================================================================
// PLAIN-ENGLISH GUIDE
//
// WHAT THIS FILE IS: the app's frame and its "map".
//  - The bottom bar: Today · Fridge · (big +) · Progress · Settings.
//  - The + sheet: Food, Water, Exercise, Weight, Fridge item.
//  - The NAVIGATION: every screen has a "route" (like a web address), and this
//    file says which screen opens for each route and with which animation.
//
// HOW NAVIGATION WORKS: nav.navigate("pick?day=20762&meal=LUNCH") opens the food
// picker for Lunch on Nov 5. nav.popBackStack() = go back one screen (like the
// back button). Screens are stacked like cards; going back removes the top card.
// ==============================================================================
package com.gymnam.app.ui.nav

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Kitchen
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.ShowChart
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.gymnam.app.R
import com.gymnam.app.data.Meal
import com.gymnam.app.ui.AppViewModelFactory
import com.gymnam.app.ui.add.FoodEditorScreen
import com.gymnam.app.ui.common.mealForNow
import com.gymnam.app.ui.fridge.FridgeItemScreen
import com.gymnam.app.ui.fridge.FridgeTab
import com.gymnam.app.ui.recipes.RecipeDetailScreen
import com.gymnam.app.ui.recipes.RecipeEditorScreen
import com.gymnam.app.ui.picker.PickerScreen
import com.gymnam.app.ui.progress.ProgressScreen
import com.gymnam.app.ui.quick.ExerciseSheet
import com.gymnam.app.ui.quick.QuickAddViewModel
import com.gymnam.app.ui.quick.WaterSheet
import com.gymnam.app.ui.quick.WeightSheet
import com.gymnam.app.ui.picker.PickerViewModel
import com.gymnam.app.ui.scan.ScannerScreen
import com.gymnam.app.ui.settings.SettingsScreen
import com.gymnam.app.ui.today.TodayScreen
import com.gymnam.app.ui.today.TodayViewModel
import kotlinx.coroutines.launch
import java.time.LocalTime

// All routes in one place. The {id}, {day}... parts are blanks filled in when
// opening a screen; the small functions (food(...), pick(...)) fill them for you.
//   today / fridge / progress / settings = the 4 tabs
//   food       = type or edit a food        pick   = food picker (search)
//   scan       = barcode camera             fridgeItem = add/edit fridge item
//   recipe     = a recipe page              recipeEdit = create/edit my recipe
//   setup      = edit goals (same screens as first setup)
private object Routes {
    const val TODAY = "today"
    const val FRIDGE = "fridge"
    const val PROGRESS = "progress"
    const val SETTINGS = "settings"
    const val FOOD = "food?id={id}&day={day}&meal={meal}&barcode={barcode}"
    fun food(id: Long = -1, day: Long, meal: Meal, barcode: String? = null) =
        "food?id=$id&day=$day&meal=${meal.name}&barcode=${barcode.orEmpty()}"
    const val PICK = "pick?day={day}&meal={meal}"
    fun pick(day: Long, meal: Meal) = "pick?day=$day&meal=${meal.name}"
    const val SCAN = "scan"
    const val FRIDGE_ITEM = "fridgeItem?id={id}&scan={scan}"
    fun fridgeItem(id: Long = -1, scan: Boolean = false) = "fridgeItem?id=$id&scan=$scan"
    const val RECIPE = "recipe?key={key}"
    fun recipe(key: String) = "recipe?key=${android.net.Uri.encode(key)}"
    const val RECIPE_EDIT = "recipeEdit?id={id}"
    const val SETUP = "setup"
    fun recipeEdit(id: Long = -1) = "recipeEdit?id=$id"
}

// The 4 tabs: route, name (from strings.xml) and icon.
private data class Tab(val route: String, val labelRes: Int, val icon: ImageVector)

private val tabs = listOf(
    Tab(Routes.TODAY, R.string.tab_today, Icons.Outlined.Home),
    Tab(Routes.FRIDGE, R.string.tab_fridge, Icons.Outlined.Kitchen),
    Tab(Routes.PROGRESS, R.string.tab_progress, Icons.Outlined.ShowChart),
    Tab(Routes.SETTINGS, R.string.tab_settings, Icons.Outlined.Settings),
)

// remember { } = keep this value while the screen is shown (not reset every redraw).
// showBar = show the bottom bar only on the 4 main tabs, not on inner screens.
// sheetOpen = is the + sheet open? quick = which small sheet is open (water/weight/exercise).
// haptics = the small vibration when you tap tabs and the + button.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScaffold() {
    val nav = rememberNavController()
    val snackbar = remember { SnackbarHostState() }
    // Shared so the + button logs into whichever day you're looking at
    val todayVm: TodayViewModel = viewModel(factory = AppViewModelFactory)
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val showBar = tabs.any { it.route == route }
    var sheetOpen by remember { mutableStateOf(false) }
    // Which quick-add sheet is open: "water", "weight", "exercise" or null
    var quick by remember { mutableStateOf<String?>(null) }
    val quickVm: QuickAddViewModel = viewModel(factory = AppViewModelFactory)
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val loggedMsg = stringResource(R.string.logged)

    // Opens the food picker for the day currently shown on Today.
    fun openFood(meal: Meal) {
        nav.navigate(Routes.pick(day = todayVm.day.value.toEpochDay(), meal = meal))
    }

    // Scaffold = Material Design's page frame: content area + bottom bar + snackbar
    // (the small message that pops up at the bottom, e.g. "Logged ✓ · Undo").
    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            // The bottom bar slides down/out when you open an inner screen, and back up when you return.
            AnimatedVisibility(
                visible = showBar,
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut()
            ) {
                BottomBar(
                    current = route,
                    onTab = { r ->
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        nav.switchTab(r)
                    },
                    onPlus = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        sheetOpen = true
                    }
                )
            }
        }
    ) { inner ->
        // The list of all screens. Each composable(route) { ... } block = one screen.
        NavHost(
            navController = nav,
            startDestination = Routes.TODAY,
            enterTransition = { fadeIn() },
            exitTransition = { fadeOut() }
        ) {
            // Today tab. Tapping a food opens the editor; the exercise + opens the exercise sheet.
            composable(Routes.TODAY) {
                TodayScreen(
                    vm = todayVm, snackbar = snackbar, contentPadding = inner,
                    onAddToMeal = { openFood(it) },
                    onEdit = { e -> nav.navigate(Routes.food(id = e.id, day = e.epochDay, meal = e.meal)) },
                    onAddExercise = { quick = "exercise" }
                )
            }
            // Fridge tab, with its buttons wired to the right screens.
            composable(Routes.FRIDGE) {
                FridgeTab(
                    vm = viewModel(factory = AppViewModelFactory), snackbar = snackbar, contentPadding = inner,
                    onAddItem = { nav.navigate(Routes.fridgeItem()) },
                    onScanItem = { nav.navigate(Routes.fridgeItem(scan = true)) },
                    onEditItem = { nav.navigate(Routes.fridgeItem(id = it.id)) },
                    onOpenRecipe = { nav.navigate(Routes.recipe(it)) },
                    onNewRecipe = { nav.navigate(Routes.recipeEdit()) }
                )
            }
            composable(
                // Add/edit fridge item. navArgument = the blanks in the route, with types and
                // defaults (id -1 = new item). It slides in from the right.
                // The scanner sends the barcode back through savedStateHandle ("barcode").
                Routes.FRIDGE_ITEM,
                arguments = listOf(
                    navArgument("id") { type = NavType.LongType; defaultValue = -1L },
                    navArgument("scan") { type = NavType.BoolType; defaultValue = false },
                ),
                enterTransition = { slideInHorizontally { it } + fadeIn() },
                popExitTransition = { slideOutHorizontally { it } + fadeOut() }
            ) { entry ->
                val handle = entry.savedStateHandle
                FridgeItemScreen(
                    vm = viewModel(factory = AppViewModelFactory), contentPadding = inner,
                    scannedBarcode = handle.getStateFlow<String?>("barcode", null),
                    consumeBarcode = { handle.set<String?>("barcode", null) },
                    startWithScan = entry.arguments?.getBoolean("scan") == true,
                    onScan = { nav.navigate(Routes.SCAN) },
                    onBack = { nav.popBackStack() }
                )
            }
            composable(
                // Recipe page. After "I cooked this", go back and show "Logged".
                Routes.RECIPE,
                arguments = listOf(navArgument("key") { type = NavType.StringType; defaultValue = "" }),
                enterTransition = { slideInHorizontally { it } + fadeIn() },
                popExitTransition = { slideOutHorizontally { it } + fadeOut() }
            ) {
                RecipeDetailScreen(
                    vm = viewModel(factory = AppViewModelFactory), snackbar = snackbar, contentPadding = inner,
                    onBack = { nav.popBackStack() },
                    onEdit = { id -> nav.navigate(Routes.recipeEdit(id)) },
                    onCooked = {
                        nav.popBackStack()
                        scope.launch { snackbar.showSnackbar(loggedMsg) }
                    }
                )
            }
            composable(
                // Recipe editor. After saving, show the saved recipe's page without leaving a
                // duplicate page underneath (so Back goes where you expect).
                Routes.RECIPE_EDIT,
                arguments = listOf(navArgument("id") { type = NavType.LongType; defaultValue = -1L }),
                enterTransition = { slideInVertically { it / 3 } + fadeIn() },
                popExitTransition = { slideOutVertically { it / 3 } + fadeOut() }
            ) {
                RecipeEditorScreen(
                    vm = viewModel(factory = AppViewModelFactory), contentPadding = inner,
                    onBack = { nav.popBackStack() },
                    onSaved = { id ->
                        // Land on the saved recipe's page without stacking a duplicate.
                        val prevKey = nav.previousBackStackEntry?.arguments?.getString("key")
                        nav.popBackStack()
                        if (prevKey != "mine:$id") {
                            if (prevKey != null) nav.popBackStack()
                            nav.navigate(Routes.recipe("mine:$id"))
                        }
                    }
                )
            }
            // Progress tab. Tapping a day in the history opens that day on Today.
            composable(Routes.PROGRESS) {
                ProgressScreen(
                    vm = viewModel(factory = AppViewModelFactory), contentPadding = inner,
                    onLogWeight = { quick = "weight" },
                    onOpenDay = { date -> todayVm.goTo(date); nav.switchTab(Routes.TODAY) }
                )
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(viewModel(factory = AppViewModelFactory), snackbar, inner, onOpenSetup = { nav.navigate(Routes.SETUP) })
            }
            // "Edit my goals" from Settings: the setup screens without the tour.
            composable(Routes.SETUP) {
                val app = androidx.compose.ui.platform.LocalContext.current.applicationContext as com.gymnam.app.GymNamApp
                val vm: com.gymnam.app.ui.setup.SetupViewModel = viewModel(factory = viewModelFactory {
                    // initializer is an "extension" function: it must be imported (see top
                    // of file) and called by its short name, not by its full package path.
                    initializer {
                        com.gymnam.app.ui.setup.SetupViewModel(app.settings, app.db.trackingDao(), withTour = false)
                    }
                })
                com.gymnam.app.ui.setup.SetupScreen(vm, onDone = { nav.popBackStack() }, onCancel = { nav.popBackStack() })
            }
            composable(
                // Manual food editor. After saving, jump straight back to Today.
                Routes.FOOD,
                arguments = listOf(
                    navArgument("id") { type = NavType.LongType; defaultValue = -1L },
                    navArgument("day") { type = NavType.LongType; defaultValue = 0L },
                    navArgument("meal") { type = NavType.StringType; defaultValue = Meal.SNACK.name },
                    navArgument("barcode") { type = NavType.StringType; defaultValue = "" },
                ),
                enterTransition = { slideInHorizontally { it } + fadeIn() },
                popExitTransition = { slideOutHorizontally { it } + fadeOut() }
            ) {
                FoodEditorScreen(
                    viewModel(factory = AppViewModelFactory), inner,
                    onBack = { nav.popBackStack() },
                    onSaved = { nav.popBackStack(Routes.TODAY, inclusive = false) }
                )
            }
            composable(
                // Food picker. It can open the scanner or the manual editor (passing the scanned barcode).
                Routes.PICK,
                arguments = listOf(
                    navArgument("day") { type = NavType.LongType; defaultValue = 0L },
                    navArgument("meal") { type = NavType.StringType; defaultValue = Meal.SNACK.name },
                ),
                enterTransition = { slideInVertically { it / 3 } + fadeIn() },
                popExitTransition = { slideOutVertically { it / 3 } + fadeOut() }
            ) { entry ->
                val vm: PickerViewModel = viewModel(factory = AppViewModelFactory)
                val handle = entry.savedStateHandle
                PickerScreen(
                    vm = vm, contentPadding = inner,
                    scannedBarcode = handle.getStateFlow<String?>("barcode", null),
                    consumeBarcode = { handle.set<String?>("barcode", null) },
                    onBack = { nav.popBackStack() },
                    onScan = { nav.navigate(Routes.SCAN) },
                    onManual = { code -> nav.navigate(Routes.food(day = vm.day, meal = vm.initialMeal, barcode = code)) },
                    onLogged = { nav.popBackStack(Routes.TODAY, inclusive = false) }
                )
            }
            // Barcode camera. When a code is read, hand it to the previous screen and close.
            composable(Routes.SCAN) {
                ScannerScreen(
                    onResult = { code ->
                        nav.previousBackStackEntry?.savedStateHandle?.set("barcode", code)
                        nav.popBackStack()
                    },
                    onBack = { nav.popBackStack() }
                )
            }
        }
    }

    // The + sheet. Each tile first slides the sheet away, THEN opens its screen.
    // Food goes to Today with the meal guessed from the clock (see mealForNow).
    if (sheetOpen) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(onDismissRequest = { sheetOpen = false }, sheetState = sheetState) {
            AddSheet(
                onFood = {
                    scope.launch { sheetState.hide() }.invokeOnCompletion {
                        sheetOpen = false
                        nav.switchTab(Routes.TODAY)
                        openFood(mealForNow(LocalTime.now().hour))
                    }
                },
                onFridge = {
                    scope.launch { sheetState.hide() }.invokeOnCompletion {
                        sheetOpen = false
                        nav.navigate(Routes.fridgeItem())
                    }
                },
                onQuick = { kind ->
                    scope.launch { sheetState.hide() }.invokeOnCompletion { sheetOpen = false; quick = kind }
                }
            )
        }
    }

    // Water / weight / exercise go into the day you're looking at on Today.
    // Water / weight / exercise are saved on the day you're viewing on Today.
    val quickDay = todayVm.day.collectAsStateWithLifecycle().value.toEpochDay()
    when (quick) {
        "water" -> WaterSheet(quickVm, quickDay) { quick = null }
        "weight" -> WeightSheet(quickVm, quickDay) { quick = null }
        "exercise" -> ExerciseSheet(quickVm, quickDay) { quick = null }
    }
}

// Switching tabs the right way: don't pile up screens, and remember where you
// were in each tab (saveState / restoreState), like Instagram's bottom bar.
private fun NavHostController.switchTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

// The bar: first 2 tabs, the big + in the middle, then the last 2 tabs.
@Composable
private fun BottomBar(current: String?, onTab: (String) -> Unit, onPlus: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        shadowElevation = 8.dp
    ) {
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            tabs.take(2).forEach { TabItem(it, current == it.route, onTab, Modifier.weight(1f)) }
            PlusButton(onPlus)
            tabs.drop(2).forEach { TabItem(it, current == it.route, onTab, Modifier.weight(1f)) }
        }
    }
}

// One tab. The colored "pill" behind the selected icon grows with a springy
// animation (dampingRatio 0.6 = a little bounce).
@Composable
private fun TabItem(tab: Tab, selected: Boolean, onTab: (String) -> Unit, modifier: Modifier) {
    val pill by animateFloatAsState(if (selected) 1f else 0f, spring(dampingRatio = 0.6f), label = "pill")
    Column(
        modifier
            .clip(RoundedCornerShape(20.dp))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onTab(tab.route) }
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(contentAlignment = Alignment.Center) {
            Surface(
                modifier = Modifier.width(56.dp).height(32.dp).scale(scaleX = 0.4f + 0.6f * pill, scaleY = 1f),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = pill)
            ) {}
            Icon(
                tab.icon, contentDescription = stringResource(tab.labelRes),
                tint = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(2.dp))
        Text(
            stringResource(tab.labelRes),
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
    }
}

// The big round + button (60 dp, accent color, with a shadow).
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlusButton(onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.padding(horizontal = 6.dp).size(60.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primary,
        shadowElevation = 6.dp
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Add, stringResource(R.string.add),
                tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(32.dp))
        }
    }
}

// The grid of tiles in the + sheet, 3 per row (chunked(3)). Empty spots in the
// last row are filled with blank space so tiles stay the same size.
/** What the + button can add: food, water, exercise, weight, fridge item. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddSheet(onFood: () -> Unit, onFridge: () -> Unit, onQuick: (String) -> Unit) {
    val items = listOf(
        Triple("🍽️", R.string.add_food, true),
        Triple("💧", R.string.add_water, true),
        Triple("🏃", R.string.add_exercise, true),
        Triple("⚖️", R.string.add_weight, true),
        Triple("🧊", R.string.add_fridge_item, true),
    )
    Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 32.dp)) {
        Text(stringResource(R.string.add_what), style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(16.dp))
        items.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { (emoji, label, live) ->
                    Surface(
                        onClick = {
                            when (label) {
                                R.string.add_food -> onFood()
                                R.string.add_fridge_item -> onFridge()
                                R.string.add_water -> onQuick("water")
                                R.string.add_weight -> onQuick("weight")
                                R.string.add_exercise -> onQuick("exercise")
                            }
                        },
                        enabled = live,
                        modifier = Modifier.weight(1f).height(104.dp),
                        shape = MaterialTheme.shapes.large,
                        color = if (live) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                    ) {
                        Column(
                            Modifier.padding(8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(emoji, fontSize = 30.sp)
                            Spacer(Modifier.height(6.dp))
                            Text(stringResource(label), style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
                            if (!live) Text(stringResource(R.string.coming_soon),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}
