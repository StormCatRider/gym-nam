// ==============================================================================
// PLAIN-ENGLISH GUIDE
//
// WHAT THIS FILE IS: the brain of the "create / edit my recipe" form.
// You add ingredients by searching foods (your foods + built-in list), and the
// per-serving calories and macros add themselves up. You can also type your own
// totals ("Set totals myself"), which then win over the calculated ones.
// ==============================================================================
package com.gymnam.app.ui.recipes

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gymnam.app.data.Catalog
import com.gymnam.app.data.FoodItem
import com.gymnam.app.data.Ingredient
import com.gymnam.app.data.Recipe
import com.gymnam.app.data.RecipeDao
import com.gymnam.app.data.SavedDao
import com.gymnam.app.data.fmtNum
import com.gymnam.app.ui.common.toNumberOrNull
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Locale

class RecipeEditorViewModel(
    handle: SavedStateHandle,
    private val dao: RecipeDao,
    private val savedDao: SavedDao,
    private val catalog: Catalog
) : ViewModel() {

    private val id: Long = handle.get<Long>("id") ?: -1L
    val isEdit = id >= 0
    private var original: Recipe? = null
    private var busy = false
    private val spanish = Locale.getDefault().language == "es"

    var name by mutableStateOf("")
    var emoji by mutableStateOf("🍲")
    var servings by mutableStateOf("4")
    var minutes by mutableStateOf("")
    var steps by mutableStateOf("")
    // A list the screen watches: adding/removing an ingredient redraws it.
    val ingredients = mutableStateListOf<Ingredient>()

    /** "Set totals myself": per-serving values typed by you win over the ingredient sums. */
    var override by mutableStateOf(false)
    var kcalO by mutableStateOf("")
    var proteinO by mutableStateOf("")
    var carbsO by mutableStateOf("")
    var fatO by mutableStateOf("")

    // Ingredient search
    var query by mutableStateOf("")
        private set
    var results by mutableStateOf<List<FoodItem>>(emptyList())
        private set
    // The running ingredient search; cancelled when you type another letter.
    private var searchJob: Job? = null

    // When editing, fill the form from the saved recipe.
    init {
        if (isEdit) viewModelScope.launch {
            dao.byId(id)?.let { r ->
                original = r
                name = r.name; emoji = r.emoji; servings = r.servings.toString()
                minutes = if (r.minutes > 0) r.minutes.toString() else ""
                steps = r.steps
                ingredients.clear(); ingredients.addAll(Ingredient.listFromJson(r.ingredientsJson))
                if (r.kcalOverride != null) {
                    override = true
                    kcalO = fmtNum(r.kcalOverride); proteinO = fmtNum(r.proteinOverride ?: 0.0)
                    carbsO = fmtNum(r.carbsOverride ?: 0.0); fatO = fmtNum(r.fatOverride ?: 0.0)
                }
            }
        }
    }

    private val servingsN get() = servings.toNumberOrNull()?.toInt()?.coerceAtLeast(1) ?: 1

    // Per-serving totals = sum of each ingredient / servings.
    // Example: 4 ingredients adding to 1,920 kcal, 4 servings -> 480 kcal each.
    /** Per serving, from the ingredients. */
    val calc get() = ingredients.let { l ->
        val n = servingsN.toDouble()
        doubleArrayOf(l.sumOf { it.kcal } / n, l.sumOf { it.proteinG } / n, l.sumOf { it.carbsG } / n, l.sumOf { it.fatG } / n)
    }

    // Waits 0.15 s after your last letter, then searches.
    fun updateQuery(q: String) {
        query = q
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(150)
            val words = Catalog.normalize(q).split(' ').filter { it.isNotBlank() }
            val mine = if (words.isEmpty()) emptyList() else savedDao.foods().first()
                .filter { f -> Catalog.normalize(f.name).let { n -> words.all { n.contains(it) } } }
                .map { it.toItem() }
            results = mine + catalog.search(q, spanish, limit = 30)
        }
    }

    // Converts what you picked into grams and its calories/macros for that amount.
    // If the food's serving is a "piece" (1 medium, 1 large, 1 fillet, 1 clove...)
    // it also remembers how many, so cooking can subtract pieces from the fridge.
    fun addIngredient(item: FoodItem, amount: Double, inGrams: Boolean) {
        val f = item.factor(amount, inGrams)
        val grams = when { inGrams -> amount; item.hasGrams -> amount * item.servingG; else -> 0.0 }
        // A "piece"-type serving (1 medium, 1 piece, 1 large…) also records how many, for the fridge.
        val pieceServing = !inGrams && Regex("^1 (medium|large|small|piece|mediano|grande|chico|pieza|fillet|filete|clove|diente)").containsMatchIn(item.servingLabel)
        ingredients += Ingredient(
            name = item.name, emoji = item.emoji, grams = grams, pieces = if (pieceServing) amount else 0.0,
            kcal = item.kcal * f, proteinG = item.proteinG * f, carbsG = item.carbsG * f, fatG = item.fatG * f,
            keys = keysFor(item.name)
        )
        query = ""; results = emptyList()
    }

    fun removeIngredient(i: Ingredient) { ingredients.remove(i) }
    // Marks an ingredient as a pantry staple (not counted in "you have X of Y").
    fun toggleStaple(i: Ingredient) {
        val idx = ingredients.indexOf(i)
        if (idx >= 0) ingredients[idx] = i.copy(staple = !i.staple)
    }

    // Needs at least a name. Servings default to 1; empty time = 0 (unknown).
    fun save(onDone: (Long) -> Unit): Boolean {
        if (name.isBlank() || busy) return busy
        busy = true
        val r = Recipe(
            id = if (isEdit) id else 0, name = name.trim(), emoji = emoji, servings = servingsN,
            minutes = minutes.toNumberOrNull()?.toInt() ?: 0,
            ingredientsJson = Ingredient.listToJson(ingredients.toList()),
            steps = steps.trim(),
            kcalOverride = if (override) kcalO.toNumberOrNull() else null,
            proteinOverride = if (override) proteinO.toNumberOrNull() ?: 0.0 else null,
            carbsOverride = if (override) carbsO.toNumberOrNull() ?: 0.0 else null,
            fatOverride = if (override) fatO.toNumberOrNull() ?: 0.0 else null,
            createdAt = original?.createdAt ?: System.currentTimeMillis()
        )
        viewModelScope.launch {
            val newId = if (isEdit) { dao.update(r); id } else dao.insert(r)
            onDone(newId)
        }
        return true
    }

    companion object {
        // "Stop words" ignored when making fridge keys: little words (de, the, with) and
        // cooking states (raw, cooked, crudo...).
        private val STOP = setOf("de", "del", "la", "el", "los", "las", "con", "en", "y", "a", "the", "and", "with", "of",
            "raw", "cooked", "crudo", "cruda", "cocido", "cocida", "fresh", "fresco", "fresca", "canned", "lata", "dry", "seco")

        // Fridge keys = first two meaningful words of the name.
        // "Chicken breast, raw" -> chicken, breast.  "Pechuga de pollo" -> pechuga, pollo.
        /** First two meaningful words of the name, e.g. "Chicken breast, raw" → chicken, breast. */
        fun keysFor(name: String): List<String> =
            Catalog.normalize(name).split(' ', ',').filter { it.length > 2 && it !in STOP && it.none(Char::isDigit) }
                .take(2).ifEmpty { listOf(Catalog.normalize(name)) }
    }
}
