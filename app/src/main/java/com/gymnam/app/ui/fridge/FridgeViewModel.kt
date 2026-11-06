// ==============================================================================
// PLAIN-ENGLISH GUIDE
//
// WHAT THIS FILE IS: the brain of the Fridge tab, which has two sections:
//  - FRIDGE: your items, soonest-to-expire first.
//  - RECIPES: built-in + your recipes, scored against what's in your fridge, with
//    sort chips, plus online recipe search (TheMealDB).
// ==============================================================================
package com.gymnam.app.ui.fridge

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gymnam.app.data.BuiltInRecipes
import com.gymnam.app.data.Coverage
import com.gymnam.app.data.FoodDao
import com.gymnam.app.data.FridgeDao
import com.gymnam.app.data.FridgeItem
import com.gymnam.app.data.FridgeMatch
import com.gymnam.app.data.MealDb
import com.gymnam.app.data.MealSummary
import com.gymnam.app.data.OfflineException
import com.gymnam.app.data.RecipeDao
import com.gymnam.app.data.RecipeModel
import com.gymnam.app.data.SettingsStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.Locale

enum class FridgeSection { FRIDGE, RECIPES }

// The sort chips:
//  HAVE     = most ingredients you already have
//  EXPIRING = uses something expiring soon
//  FITS     = fits the calories you have left today
//  PROTEIN  = most protein per serving
//  QUICK    = shortest cooking time
//  LIGHT    = fewest calories
/** The sort chips on the Recipes list ("let me sort in the app"). */
enum class RecipeSort { HAVE, EXPIRING, FITS, PROTEIN, QUICK, LIGHT }

// A recipe plus its fridge score ("4 of 6").
data class RecipeRow(val recipe: RecipeModel, val coverage: Coverage)

sealed interface OnlineRecipes {
    data object Idle : OnlineRecipes
    data object Loading : OnlineRecipes
    data class Results(val list: List<MealSummary>) : OnlineRecipes
    data object Failed : OnlineRecipes
}

@OptIn(ExperimentalCoroutinesApi::class)
class FridgeViewModel(
    private val fridgeDao: FridgeDao,
    recipeDao: RecipeDao,
    foodDao: FoodDao,
    builtIn: BuiltInRecipes,
    private val mealDb: MealDb,
    private val settings: SettingsStore
) : ViewModel() {

    private val spanish = Locale.getDefault().language == "es"
    val today: Long get() = LocalDate.now().toEpochDay()

    val section = MutableStateFlow(FridgeSection.FRIDGE)
    val sort = MutableStateFlow(RecipeSort.HAVE)

    val fridge: StateFlow<List<FridgeItem>> = fridgeDao.all()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val internetOn: StateFlow<Boolean> = settings.settings.map { it.internetEnabled }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    // Calories left today = goal - eaten (exercise not included here).
    /** Calories left today (goal − eaten), for the "Fits calories left" sort. */
    val kcalLeft: StateFlow<Int> = combine(
        foodDao.entriesForDay(LocalDate.now().toEpochDay()), settings.settings
    ) { entries, s -> s.targets.calories - entries.sumOf { it.calories } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 2000)

    // Your own recipes first, then the built-in ones.
    private val allRecipes = combine(
        flow { emit(builtIn.all(spanish)) },
        recipeDao.all().map { list -> list.map { it.toModel() } }
    ) { built, mine -> mine + built }

    // Recomputed whenever the fridge, the sort chip, or calories left change:
    // score every recipe against the fridge, then sort.
    val recipes: StateFlow<List<RecipeRow>> = combine(allRecipes, fridge, sort, kcalLeft) { list, items, s, left ->
        val rows = list.map { RecipeRow(it, FridgeMatch.coverage(it, items, today)) }
        sortRows(rows, s, left)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // Counts recipes where you have 100% of the (non-staple) ingredients.
    /** "You can make N recipes right now" (every non-staple ingredient found). */
    val canMakeNow: StateFlow<Int> = recipes.map { rows -> rows.count { it.coverage.total > 0 && it.coverage.ratio >= 1.0 } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    // "compareByDescending ... thenBy ..." = sort by the first rule; ties are broken by
    // the next rule. FITS shows recipes under your calories left first (best fridge
    // match, then most protein), then the rest from lightest up.
    // Recipes with no time listed go last in QUICK.
    private fun sortRows(rows: List<RecipeRow>, s: RecipeSort, left: Int): List<RecipeRow> = when (s) {
        RecipeSort.HAVE -> rows.sortedWith(compareByDescending<RecipeRow> { it.coverage.ratio }
            .thenByDescending { it.coverage.have }.thenBy { it.recipe.kcal })
        RecipeSort.EXPIRING -> rows.sortedWith(compareByDescending<RecipeRow> { it.coverage.usesExpiring }
            .thenByDescending { it.coverage.ratio })
        RecipeSort.FITS -> {
            val (fits, over) = rows.partition { it.recipe.kcal <= left }
            fits.sortedWith(compareByDescending<RecipeRow> { it.coverage.ratio }.thenByDescending { it.recipe.proteinG }) +
                over.sortedBy { it.recipe.kcal }
        }
        RecipeSort.PROTEIN -> rows.sortedByDescending { it.recipe.proteinG }
        RecipeSort.QUICK -> rows.sortedBy { if (it.recipe.minutes > 0) it.recipe.minutes else Int.MAX_VALUE }
        RecipeSort.LIGHT -> rows.sortedBy { it.recipe.kcal }
    }

    // --- Fridge actions ---
    fun delete(item: FridgeItem) = viewModelScope.launch { fridgeDao.delete(item) }
    fun restore(item: FridgeItem) = viewModelScope.launch { fridgeDao.insert(item) }

    // --- Online recipes (TheMealDB) ---
    val onlineQuery = MutableStateFlow("")
    private val _online = MutableStateFlow<OnlineRecipes>(OnlineRecipes.Idle)
    val online: StateFlow<OnlineRecipes> = _online.asStateFlow()

    fun turnOnInternet() = viewModelScope.launch { settings.setInternet(true) }

    // Same pattern as the food picker: only when you tap search; quiet if internet is off.
    fun searchOnline() {
        val q = onlineQuery.value.trim()
        if (q.isEmpty()) return
        viewModelScope.launch {
            _online.value = OnlineRecipes.Loading
            _online.value = try {
                OnlineRecipes.Results(mealDb.search(q))
            } catch (e: OfflineException) {
                OnlineRecipes.Idle
            } catch (e: Exception) {
                OnlineRecipes.Failed
            }
        }
    }
}
