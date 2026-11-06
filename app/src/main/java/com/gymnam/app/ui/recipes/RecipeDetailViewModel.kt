// ==============================================================================
// PLAIN-ENGLISH GUIDE
//
// WHAT THIS FILE IS: the brain of the recipe page. It loads the recipe (built-in,
// yours, or online), compares it with your fridge, and handles "I cooked this"
// (log the servings you ate + subtract ingredients from the fridge).
// ==============================================================================
package com.gymnam.app.ui.recipes

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gymnam.app.data.AmountUnit
import com.gymnam.app.data.BuiltInRecipes
import com.gymnam.app.data.Coverage
import com.gymnam.app.data.FoodDao
import com.gymnam.app.data.FoodEntry
import com.gymnam.app.data.FridgeDao
import com.gymnam.app.data.FridgeItem
import com.gymnam.app.data.FridgeMatch
import com.gymnam.app.data.Ingredient
import com.gymnam.app.data.Meal
import com.gymnam.app.data.MealDb
import com.gymnam.app.data.OfflineException
import com.gymnam.app.data.Recipe
import com.gymnam.app.data.RecipeDao
import com.gymnam.app.data.RecipeModel
import com.gymnam.app.data.Subtraction
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.Locale
import kotlin.math.roundToInt

// The page can be: loading, ready (showing a recipe), offline (internet is OFF
// for an online recipe), failed (download error), or gone (recipe was deleted).
sealed interface DetailState {
    data object Loading : DetailState
    data class Ready(val recipe: RecipeModel) : DetailState
    data object Offline : DetailState
    data object Failed : DetailState
    data object Gone : DetailState
}

// coverage = "you have 4 of 6"; plan = what cooking will subtract; matches = which
// fridge item each ingredient will use (shown as a ✓ next to it).
data class FridgeView(
    val coverage: Coverage? = null,
    val plan: List<Subtraction> = emptyList(),
    /** Which fridge item each ingredient will use (by ingredient name). */
    val matches: Map<String, FridgeItem> = emptyMap()
)

class RecipeDetailViewModel(
    handle: SavedStateHandle,
    private val recipeDao: RecipeDao,
    private val fridgeDao: FridgeDao,
    private val foodDao: FoodDao,
    private val builtIn: BuiltInRecipes,
    private val mealDb: MealDb
) : ViewModel() {

    // key says which recipe and where it comes from:
    // "builtin:b12", "mine:5" or "mealdb:52772".
    val key: String = handle.get<String>("key").orEmpty()
    private val spanish = Locale.getDefault().language == "es"

    private val _state = MutableStateFlow<DetailState>(DetailState.Loading)
    val state: StateFlow<DetailState> = _state.asStateFlow()

    // Recomputed live when the recipe or your fridge changes.
    val fridgeView: StateFlow<FridgeView> = combine(_state, fridgeDao.all()) { s, items ->
        val r = (s as? DetailState.Ready)?.recipe ?: return@combine FridgeView()
        val today = LocalDate.now().toEpochDay()
        FridgeView(
            coverage = FridgeMatch.coverage(r, items, today),
            plan = FridgeMatch.plan(r, items, today),
            matches = r.ingredients.mapNotNull { ing -> FridgeMatch.find(ing, items, today)?.let { ing.name to it } }.toMap()
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FridgeView())

    init { load() }

    // Loads the recipe according to the start of its key (see above).
    fun load() {
        viewModelScope.launch {
            when {
                key.startsWith("builtin:") ->
                    _state.value = builtIn.all(spanish).firstOrNull { it.key == key }?.let { DetailState.Ready(it) } ?: DetailState.Gone
                key.startsWith("mine:") -> {
                    val id = key.removePrefix("mine:").toLongOrNull() ?: -1
                    // Live: edits show up immediately; deleting closes the screen.
                    recipeDao.all().collect { list ->
                        _state.value = list.firstOrNull { it.id == id }?.toModel()?.let { DetailState.Ready(it) } ?: DetailState.Gone
                    }
                }
                key.startsWith("mealdb:") -> {
                    _state.value = DetailState.Loading
                    _state.value = try {
                        mealDb.recipe(key.removePrefix("mealdb:"))?.let { DetailState.Ready(it) } ?: DetailState.Failed
                    } catch (e: OfflineException) { DetailState.Offline } catch (e: Exception) { DetailState.Failed }
                }
                else -> _state.value = DetailState.Gone
            }
        }
    }

    // 1. Adds a diary line: e.g. 1.5 servings x 480 kcal = 720 kcal.
    // 2. If "subtract from fridge" is on: for each planned item, re-read its current
    //    amount (it may have changed), subtract, and delete it if nothing is left
    //    (0.001 avoids tiny leftovers from decimal math).
    /**
     * Log the servings you ate, and (optionally) take the whole recipe's ingredients out of the fridge.
     * Items that reach zero are removed.
     */
    fun cook(servings: Double, meal: Meal, subtract: Boolean, onDone: () -> Unit) {
        val r = (state.value as? DetailState.Ready)?.recipe ?: return
        val plan = fridgeView.value.plan
        viewModelScope.launch {
            foodDao.insert(
                FoodEntry(
                    epochDay = LocalDate.now().toEpochDay(), meal = meal, name = r.name, emoji = r.emoji,
                    amount = servings, unit = AmountUnit.SERVING,
                    calories = (r.kcal * servings).roundToInt(),
                    proteinG = r.proteinG * servings, carbsG = r.carbsG * servings, fatG = r.fatG * servings
                )
            )
            if (subtract) plan.forEach { s ->
                val fresh = fridgeDao.byId(s.item.id) ?: return@forEach
                val left = fresh.quantity - s.amount
                if (left <= 0.001) fridgeDao.delete(fresh) else fridgeDao.update(fresh.copy(quantity = left))
            }
            onDone()
        }
    }

    // Copies a built-in/online recipe into My recipes so you can edit it.
    /** Copy a built-in or online recipe into My recipes so you can edit it. */
    fun saveToMine(onSaved: (Long) -> Unit) {
        val r = (state.value as? DetailState.Ready)?.recipe ?: return
        viewModelScope.launch {
            val id = recipeDao.insert(
                Recipe(
                    name = r.name, emoji = r.emoji, servings = r.servings, minutes = r.minutes,
                    ingredientsJson = Ingredient.listToJson(r.ingredients),
                    steps = r.steps.joinToString("\n")
                )
            )
            onSaved(id)
        }
    }

    fun delete(onDone: () -> Unit) {
        val id = (state.value as? DetailState.Ready)?.recipe?.myId ?: return
        viewModelScope.launch {
            recipeDao.byId(id)?.let { recipeDao.delete(it) }
            onDone()
        }
    }
}
