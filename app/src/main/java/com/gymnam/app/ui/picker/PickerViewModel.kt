// ==============================================================================
// PLAIN-ENGLISH GUIDE
//
// WHAT THIS FILE IS: the brain of the food picker (the screen you get from the
// big + button -> "Search food"). It handles:
//  - typing in the search box (your foods first, then the built-in list),
//  - the tabs All / My foods / My meals / Recent,
//  - "Search online" (Open Food Facts), only when you tap it,
//  - barcode results,
//  - logging the chosen food with the amount you picked.
// ==============================================================================
package com.gymnam.app.ui.picker

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gymnam.app.data.AmountUnit
import com.gymnam.app.data.Catalog
import com.gymnam.app.data.FoodDao
import com.gymnam.app.data.FoodEntry
import com.gymnam.app.data.FoodItem
import com.gymnam.app.data.FoodSource
import com.gymnam.app.data.Meal
import com.gymnam.app.data.OfflineException
import com.gymnam.app.data.OpenFoodFacts
import com.gymnam.app.data.SavedDao
import com.gymnam.app.data.SavedFood
import com.gymnam.app.data.SavedMeal
import com.gymnam.app.data.SettingsStore
import com.gymnam.app.data.fmtNum
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.Locale
import kotlin.math.roundToInt

enum class PickerTab { ALL, MINE, MEALS, RECENT }

// The online search can be: not started, loading, showing results, or failed.
// "sealed" = only these four possibilities exist, so the screen handles them all.
sealed interface OnlineState {
    data object Idle : OnlineState
    data object Loading : OnlineState
    data class Results(val items: List<FoodItem>) : OnlineState
    data object Failed : OnlineState
}

// Barcode lookup: nothing, looking it up, or not found (and whether internet was off).
sealed interface BarcodeState {
    data object None : BarcodeState
    data class Looking(val code: String) : BarcodeState
    /** offline = internet switch is off, so we couldn't look it up. */
    data class NotFound(val code: String, val offline: Boolean) : BarcodeState
}

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class PickerViewModel(
    handle: SavedStateHandle,
    private val foodDao: FoodDao,
    private val savedDao: SavedDao,
    private val catalog: Catalog,
    private val off: OpenFoodFacts,
    private val settings: SettingsStore
) : ViewModel() {

    // Which day and meal you're adding to. They come from the screen you came from
    // (e.g. "+" on Lunch of Nov 3). If missing: today, and Snack.
    val day: Long = handle.get<Long>("day")?.takeIf { it > 0 } ?: LocalDate.now().toEpochDay()
    val initialMeal: Meal = handle.get<String>("meal")
        ?.let { runCatching { Meal.valueOf(it) }.getOrNull() } ?: Meal.SNACK
    // True if the phone is in Spanish; used to search and show Spanish names.
    private val spanish = Locale.getDefault().language == "es"

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()
    val tab = MutableStateFlow(PickerTab.ALL)

    val internetOn: StateFlow<Boolean> = settings.settings.map { it.internetEnabled }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val savedFoods: StateFlow<List<FoodItem>> = savedDao.foods().map { l -> l.map { it.toItem() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val meals: StateFlow<List<SavedMeal>> = savedDao.meals()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val recents: StateFlow<List<FoodItem>> = foodDao.recent().map { l -> l.map { it.toRecentItem() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // Search as you type. debounce(120) waits 0.12 s after your last key so we don't
    // search on every single letter. Your saved foods whose names contain every word
    // come first, then the built-in list results.
    /** Your foods first, then the built-in list. Recomputed as you type. */
    val localResults: StateFlow<List<FoodItem>> =
        combine(_query.debounce(120), savedFoods) { q, saved -> q to saved }
            .mapLatest { (q, saved) ->
                if (q.isBlank()) emptyList()
                else {
                    val words = Catalog.normalize(q).split(' ').filter { it.isNotBlank() }
                    val mine = saved.filter { s -> Catalog.normalize(s.name).let { n -> words.all { n.contains(it) } } }
                    mine + catalog.search(q, spanish)
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _online = MutableStateFlow<OnlineState>(OnlineState.Idle)
    val online: StateFlow<OnlineState> = _online.asStateFlow()

    private val _barcode = MutableStateFlow<BarcodeState>(BarcodeState.None)
    val barcode: StateFlow<BarcodeState> = _barcode.asStateFlow()

    private val _selected = MutableStateFlow<FoodItem?>(null)
    val selected: StateFlow<FoodItem?> = _selected.asStateFlow()

    private val _selectedMeal = MutableStateFlow<SavedMeal?>(null)
    val selectedMeal: StateFlow<SavedMeal?> = _selectedMeal.asStateFlow()

    // Typing something new clears old online results (they'd be for the old words).
    init {
        // A new query invalidates old online results.
        viewModelScope.launch {
            _query.drop(1).distinctUntilChanged().collect { _online.value = OnlineState.Idle }
        }
    }

    fun setQuery(q: String) { _query.value = q }
    fun select(item: FoodItem?) { _selected.value = item }
    fun selectMeal(m: SavedMeal?) { _selectedMeal.value = m }
    fun clearBarcode() { _barcode.value = BarcodeState.None }
    fun turnOnInternet() = viewModelScope.launch { settings.setInternet(true) }

    // Only runs when you tap "Search online" (Open Food Facts allows ~10 searches per
    // minute, and this way nothing is sent unless you choose to). Needs 2+ letters.
    /** Explicit button only: respects OFF's rate limits and your privacy. */
    fun searchOnline() {
        val q = _query.value.trim()
        if (q.length < 2) return
        viewModelScope.launch {
            _online.value = OnlineState.Loading
            _online.value = try {
                OnlineState.Results(off.search(q, spanish))
            } catch (e: OfflineException) {
                OnlineState.Idle
            } catch (e: Exception) {
                OnlineState.Failed
            }
        }
    }

    // Order of lookup:
    //  1. My foods with that barcode (works offline).
    //  2. If internet is OFF -> "not found (offline)", with a button to turn it on.
    //  3. Ask Open Food Facts. Found -> open it; not found -> offer to type it in.
    /** Saved foods with this barcode work offline; otherwise ask Open Food Facts if allowed. */
    fun onBarcode(code: String) = viewModelScope.launch {
        savedDao.foodByBarcode(code)?.let { _barcode.value = BarcodeState.None; select(it.toItem()); return@launch }
        if (!internetOn.value) { _barcode.value = BarcodeState.NotFound(code, offline = true); return@launch }
        _barcode.value = BarcodeState.Looking(code)
        val item = try { off.byBarcode(code, spanish) } catch (e: Exception) { null }
        if (item != null) { _barcode.value = BarcodeState.None; select(item) }
        else _barcode.value = BarcodeState.NotFound(code, offline = false)
    }

    // Saves the food to your diary.
    // f = number of servings eaten (see FoodItem.factor). Calories = per-serving x f.
    // If we know the grams, the diary stores grams ("240 g") because it's clearer
    // later than "1 serving". Brand names are added in parentheses.
    // saveToMine = also add it to My foods (for foods that aren't already there).
    fun log(item: FoodItem, amount: Double, inGrams: Boolean, meal: Meal, saveToMine: Boolean, onDone: () -> Unit) {
        val f = item.factor(amount, inGrams)
        // Store grams whenever we know them: "240 g" is clearer later than "1 serving".
        val (amt, unit) = when {
            inGrams && item.hasGrams -> amount to AmountUnit.GRAM
            item.hasGrams -> amount * item.servingG to AmountUnit.GRAM
            else -> amount to AmountUnit.SERVING
        }
        val entry = FoodEntry(
            epochDay = day, meal = meal,
            name = item.brand?.let { "${item.name} ($it)" } ?: item.name,
            emoji = item.emoji, amount = amt, unit = unit,
            calories = (item.kcal * f).roundToInt().coerceAtLeast(0),
            proteinG = item.proteinG * f, carbsG = item.carbsG * f, fatG = item.fatG * f
        )
        viewModelScope.launch {
            foodDao.insert(entry)
            if (saveToMine && item.source != FoodSource.SAVED) {
                savedDao.insertFood(
                    SavedFood(
                        name = entry.name, emoji = item.emoji, servingName = item.servingLabel,
                        servingG = item.servingG, kcal = item.kcal, proteinG = item.proteinG,
                        carbsG = item.carbsG, fatG = item.fatG, barcode = item.barcode
                    )
                )
            }
            onDone()
        }
    }

    // Logs every food of a saved meal at once, into the chosen day and meal.
    fun logMeal(m: SavedMeal, meal: Meal, onDone: () -> Unit) = viewModelScope.launch {
        foodDao.insertAll(m.items().map {
            FoodEntry(
                epochDay = day, meal = meal, name = it.name, emoji = it.emoji, amount = it.amount,
                unit = it.unit, calories = it.calories, proteinG = it.proteinG, carbsG = it.carbsG, fatG = it.fatG
            )
        })
        onDone()
    }

    fun deleteSaved(item: FoodItem) = viewModelScope.launch {
        val id = item.savedId ?: return@launch
        // Room deletes by primary key, so rebuilding the row from the item is enough.
        savedDao.deleteFood(
            SavedFood(id = id, name = item.name, emoji = item.emoji, servingName = item.servingLabel,
                servingG = item.servingG, kcal = item.kcal, proteinG = item.proteinG,
                carbsG = item.carbsG, fatG = item.fatG, barcode = item.barcode)
        )
    }

    fun deleteMeal(m: SavedMeal) = viewModelScope.launch { savedDao.deleteMeal(m) }

    // Turns a past diary line into a pickable food: if you logged grams, one
    // "serving" = that many grams; otherwise per-serving numbers = total / amount.
    /** Recent entry → pickable item, per serving as you last logged it. */
    private fun FoodEntry.toRecentItem(): FoodItem {
        val grams = unit == AmountUnit.GRAM
        val a = amount.takeIf { it > 0 } ?: 1.0
        return if (grams) FoodItem(
            key = "recent:$id", name = name, emoji = emoji, servingLabel = "${fmtNum(a)} g",
            servingG = a, kcal = calories.toDouble(), proteinG = proteinG, carbsG = carbsG, fatG = fatG,
            source = FoodSource.RECENT
        ) else FoodItem(
            key = "recent:$id", name = name, emoji = emoji,
            servingLabel = if (spanish) "1 porción" else "1 serving",
            servingG = 0.0, kcal = calories / a, proteinG = proteinG / a, carbsG = carbsG / a, fatG = fatG / a,
            source = FoodSource.RECENT
        )
    }
}
