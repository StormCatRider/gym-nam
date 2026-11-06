// ==============================================================================
// PLAIN-ENGLISH GUIDE
//
// WHAT THIS FILE IS: the brain of the manual "type a food" form, used both to
// ADD a new food and to EDIT one already in your diary (tap a food on Today).
// ==============================================================================
package com.gymnam.app.ui.add

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gymnam.app.data.AmountUnit
import com.gymnam.app.data.FoodDao
import com.gymnam.app.data.FoodEntry
import com.gymnam.app.data.Meal
import com.gymnam.app.data.SavedDao
import com.gymnam.app.data.SavedFood
import com.gymnam.app.ui.common.guessEmoji
import com.gymnam.app.ui.common.toNumberOrNull
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

// Inputs from navigation: id (-1 = new food, otherwise the diary row to edit),
// day, meal, and an optional barcode (when a scan wasn't found online).
/**
 * Nav args: id (-1 = new), day (epochDay), meal (Meal name).
 * Holds the form state; survives rotation via the ViewModel.
 */
class FoodEditorViewModel(
    handle: SavedStateHandle,
    private val dao: FoodDao,
    private val savedDao: SavedDao
) : ViewModel() {

    private val id: Long = handle.get<Long>("id") ?: -1L
    private val day: Long = handle.get<Long>("day")?.takeIf { it > 0 } ?: java.time.LocalDate.now().toEpochDay()
    val isEdit = id >= 0
    /** Set when you came here from a barcode that wasn't found. */
    val barcode: String? = handle.get<String>("barcode")?.takeIf { it.isNotBlank() }
    // "Also save to My foods" starts checked if you came from an unknown barcode.
    var saveToMine by mutableStateOf(barcode != null)
    private var original: FoodEntry? = null

    // The form's boxes. They hold TEXT exactly as typed; numbers are read on save.
    var name by mutableStateOf("")
        private set
    var emoji by mutableStateOf("🍽️")
        private set
    private var emojiPicked = false
    var meal by mutableStateOf(handle.get<String>("meal")?.let { runCatching { Meal.valueOf(it) }.getOrNull() } ?: Meal.SNACK)
    var amount by mutableStateOf("1")
    var unit by mutableStateOf(AmountUnit.SERVING)
    var calories by mutableStateOf("")
    var protein by mutableStateOf("")
    var carbs by mutableStateOf("")
    var fat by mutableStateOf("")

    // When editing, fill the form with the existing food's values.
    init {
        if (isEdit) viewModelScope.launch {
            dao.byId(id)?.let { e ->
                original = e
                name = e.name; emoji = e.emoji; emojiPicked = true; meal = e.meal
                amount = trim(e.amount); unit = e.unit
                calories = e.calories.toString()
                protein = trimOrBlank(e.proteinG); carbs = trimOrBlank(e.carbsG); fat = trimOrBlank(e.fatG)
            }
        }
    }

    // Typing a name suggests an emoji, until you pick one yourself.
    fun updateName(v: String) {
        name = v
        if (!emojiPicked) guessEmoji(v)?.let { emoji = it }
    }

    fun pickEmoji(v: String) { emoji = v; emojiPicked = true }

    // Atwater factors: calories = 4 x protein g + 4 x carbs g + 9 x fat g.
    // Example: 30 g protein, 20 g carbs, 10 g fat = 120 + 80 + 90 = 290 kcal.
    // Shown as a hint; "use this" copies it into the calories box.
    /** Atwater factors: 4 kcal/g protein, 4 kcal/g carbs, 9 kcal/g fat. */
    val kcalFromMacros: Int?
        get() {
            val p = protein.toNumberOrNull() ?: 0.0
            val c = carbs.toNumberOrNull() ?: 0.0
            val f = fat.toNumberOrNull() ?: 0.0
            val k = 4 * p + 4 * c + 9 * f
            return if (k > 0) k.roundToInt() else null
        }

    fun useMacroKcal() { kcalFromMacros?.let { calories = it.toString() } }

    // busy = a save is in progress; ignores double taps so the food isn't saved twice.
    /** Returns true when saved. Calories fall back to the macro estimate if left blank. */
    private var busy = false

    // Returns false (and doesn't save) if the name is empty or calories are missing
    // and can't be calculated from macros. Amount defaults to 1 if left blank.
    fun save(onDone: () -> Unit): Boolean {
        if (busy) return true
        val kcal = calories.toNumberOrNull()?.roundToInt() ?: kcalFromMacros
        if (name.isBlank() || kcal == null || kcal < 0) return false
        val entry = FoodEntry(
            id = if (isEdit) id else 0,
            epochDay = original?.epochDay ?: day,
            meal = meal,
            name = name.trim(),
            emoji = emoji,
            amount = amount.toNumberOrNull()?.takeIf { it > 0 } ?: 1.0,
            unit = unit,
            calories = kcal,
            proteinG = protein.toNumberOrNull() ?: 0.0,
            carbsG = carbs.toNumberOrNull() ?: 0.0,
            fatG = fat.toNumberOrNull() ?: 0.0,
            createdAt = original?.createdAt ?: System.currentTimeMillis()
        )
        busy = true
        viewModelScope.launch {
            if (isEdit) dao.update(entry) else dao.insert(entry)
            if (!isEdit && saveToMine) {
                // Saved per serving. If you typed grams, one serving = that many grams.
                val grams = entry.unit == AmountUnit.GRAM
                val a = entry.amount
                savedDao.insertFood(
                    SavedFood(
                        name = entry.name, emoji = entry.emoji,
                        servingName = if (grams) "${trim(a)} g" else "1 serving",
                        servingG = if (grams) a else 0.0,
                        kcal = if (grams) entry.calories.toDouble() else entry.calories / a,
                        proteinG = if (grams) entry.proteinG else entry.proteinG / a,
                        carbsG = if (grams) entry.carbsG else entry.carbsG / a,
                        fatG = if (grams) entry.fatG else entry.fatG / a,
                        barcode = barcode
                    )
                )
            }
            onDone()
        }
        return true
    }

    fun delete(onDone: () -> Unit) {
        val e = original ?: return
        if (busy) return
        busy = true
        viewModelScope.launch { dao.delete(e); onDone() }
    }

    // 2.0 -> "2", 2.5 -> "2.5" for the text boxes; trimOrBlank shows 0 as an empty box.
    private fun trim(d: Double) = if (d % 1.0 == 0.0) d.toLong().toString() else d.toString()
    private fun trimOrBlank(d: Double) = if (d == 0.0) "" else trim(d)
}
