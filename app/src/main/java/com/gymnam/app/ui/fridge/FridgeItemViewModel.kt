// ==============================================================================
// PLAIN-ENGLISH GUIDE
//
// WHAT THIS FILE IS: the brain of the "add / edit a fridge item" form
// (name, emoji, quantity, unit, expiry date, optional barcode).
// ==============================================================================
package com.gymnam.app.ui.fridge

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gymnam.app.data.FridgeDao
import com.gymnam.app.data.FridgeItem
import com.gymnam.app.data.FridgeUnit
import com.gymnam.app.data.OpenFoodFacts
import com.gymnam.app.data.SavedDao
import com.gymnam.app.data.SettingsStore
import com.gymnam.app.data.fmtNum
import com.gymnam.app.ui.common.guessEmoji
import com.gymnam.app.ui.common.toNumberOrNull
import kotlinx.coroutines.launch
import java.util.Locale

// What happened with a scanned barcode: nothing yet, looking, found, not found,
// or internet off.
enum class LookupState { NONE, LOOKING, FOUND, NOT_FOUND, OFFLINE }

class FridgeItemViewModel(
    handle: SavedStateHandle,
    private val dao: FridgeDao,
    private val savedDao: SavedDao,
    private val off: OpenFoodFacts,
    private val settings: SettingsStore
) : ViewModel() {

    private val id: Long = handle.get<Long>("id") ?: -1L
    val isEdit = id >= 0
    private var original: FridgeItem? = null
    private var emojiPicked = false
    private var busy = false

    var name by mutableStateOf("")
        private set
    var emoji by mutableStateOf("🥡")
        private set
    var quantity by mutableStateOf("1")
    var unit by mutableStateOf(FridgeUnit.PIECE)
    var expiryDay by mutableStateOf<Long?>(null)
    var barcode by mutableStateOf<String?>(null)
        private set
    var lookup by mutableStateOf(LookupState.NONE)
        private set

    // When editing, fill the form with the item's current values.
    init {
        if (isEdit) viewModelScope.launch {
            dao.byId(id)?.let {
                original = it
                name = it.name; emoji = it.emoji; emojiPicked = true
                quantity = fmtNum(it.quantity); unit = it.unit; expiryDay = it.expiryDay; barcode = it.barcode
            }
        }
    }

    fun updateName(v: String) {
        name = v
        if (!emojiPicked) guessEmoji(v)?.let { emoji = it }
    }

    fun pickEmoji(v: String) { emoji = v; emojiPicked = true }

    // Scanned barcode -> name: try My foods first (offline), then Open Food Facts if
    // internet is ON. Only the name is filled in; you still set quantity and expiry.
    /** Scanned: try My foods first (offline), then Open Food Facts if online is on. */
    fun onBarcode(code: String) = viewModelScope.launch {
        barcode = code
        savedDao.foodByBarcode(code)?.let { f ->
            updateName(f.name); if (!emojiPicked) emoji = f.emoji
            lookup = LookupState.FOUND; return@launch
        }
        if (!settings.internetEnabled()) { lookup = LookupState.OFFLINE; return@launch }
        lookup = LookupState.LOOKING
        val item = runCatching { off.byBarcode(code, Locale.getDefault().language == "es") }.getOrNull()
        if (item != null) {
            updateName(item.brand?.let { "${item.name} ($it)" } ?: item.name)
            lookup = LookupState.FOUND
        } else lookup = LookupState.NOT_FOUND
    }

    // Doesn't save if the name is empty or the quantity isn't a number 0 or more.
    // Editing keeps the original "created" time.
    fun save(onDone: () -> Unit): Boolean {
        val q = quantity.toNumberOrNull()
        if (name.isBlank() || q == null || q < 0 || busy) return busy
        busy = true
        val item = FridgeItem(
            id = if (isEdit) id else 0, name = name.trim(), emoji = emoji, quantity = q, unit = unit,
            expiryDay = expiryDay, barcode = barcode, createdAt = original?.createdAt ?: System.currentTimeMillis()
        )
        viewModelScope.launch {
            if (isEdit) dao.update(item) else dao.insert(item)
            onDone()
        }
        return true
    }

    fun delete(onDone: () -> Unit) {
        val o = original ?: return
        if (busy) return
        busy = true
        viewModelScope.launch { dao.delete(o); onDone() }
    }
}
