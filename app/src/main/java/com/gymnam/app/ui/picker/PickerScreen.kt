// ==============================================================================
// PLAIN-ENGLISH GUIDE
//
// WHAT THIS FILE IS: the food picker screen (+ -> Food). From top to bottom:
//  1. Search bar (the keyboard opens by itself).
//  2. Quick actions (Scan barcode, Type it in) and tabs: All, My foods, My meals, Recent.
//  3. The list:
//     - All, empty search  -> your recent foods (up to 8) and My foods.
//     - All, while typing  -> matches, then the "Search online" section.
//     - My foods / Recent / My meals tabs -> those lists.
//  4. A note that values are estimates, and the Open Food Facts credit (required
//     by its open data license).
// Tapping a food opens PortionSheet; long-pressing one of YOUR foods or meals
// offers to delete it. The brain is PickerViewModel.kt.
// ==============================================================================
package com.gymnam.app.ui.picker

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gymnam.app.R
import com.gymnam.app.data.rememberItemFlags
import com.gymnam.app.ui.common.FlagChips
import com.gymnam.app.data.FoodItem
import com.gymnam.app.data.FoodSource
import com.gymnam.app.data.SavedMeal
import com.gymnam.app.ui.common.EmojiBadge
import com.gymnam.app.ui.common.SoftCard
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.roundToInt

// scannedBarcode = a barcode sent back by the scanner screen;
// consumeBarcode() clears it so it isn't handled twice.
// confirmDelete = the food or meal waiting for "Delete?" confirmation.
@Composable
fun PickerScreen(
    vm: PickerViewModel,
    contentPadding: PaddingValues,
    scannedBarcode: StateFlow<String?>,
    consumeBarcode: () -> Unit,
    onBack: () -> Unit,
    onScan: () -> Unit,
    onManual: (barcode: String?) -> Unit,
    onLogged: () -> Unit
) {
    val query by vm.query.collectAsStateWithLifecycle()
    val tab by vm.tab.collectAsStateWithLifecycle()
    val internetOn by vm.internetOn.collectAsStateWithLifecycle()
    val local by vm.localResults.collectAsStateWithLifecycle()
    val saved by vm.savedFoods.collectAsStateWithLifecycle()
    val meals by vm.meals.collectAsStateWithLifecycle()
    val recents by vm.recents.collectAsStateWithLifecycle()
    val online by vm.online.collectAsStateWithLifecycle()
    val barcode by vm.barcode.collectAsStateWithLifecycle()
    val selected by vm.selected.collectAsStateWithLifecycle()
    val selectedMeal by vm.selectedMeal.collectAsStateWithLifecycle()
    val code by scannedBarcode.collectAsStateWithLifecycle()
    val focus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val haptics = LocalHapticFeedback.current
    var confirmDelete by remember { mutableStateOf<Any?>(null) }

    // When a barcode arrives, look it up (see PickerViewModel.onBarcode).
    // Result coming back from the scanner screen
    LaunchedEffect(code) {
        code?.let { vm.onBarcode(it); consumeBarcode() }
    }
    // Open the keyboard in the search box right away (not when returning from the scanner).
    LaunchedEffect(Unit) { if (code == null) runCatching { focus.requestFocus() } }

    Column(
        Modifier.fillMaxSize()
            .padding(top = contentPadding.calculateTopPadding(), bottom = contentPadding.calculateBottomPadding())
            .imePadding()
    ) {
        // Search bar
        Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 12.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
            TextField(
                value = query, onValueChange = vm::setQuery,
                placeholder = { Text(stringResource(R.string.search_foods)) },
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                trailingIcon = {
                    if (query.isNotEmpty()) IconButton(onClick = { vm.setQuery("") }) { Icon(Icons.Filled.Close, null) }
                },
                singleLine = true,
                shape = CircleShape,
                colors = TextFieldDefaults.colors(
                    focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {
                    focusManager.clearFocus()
                    if (internetOn && local.isEmpty()) vm.searchOnline()
                }),
                modifier = Modifier.weight(1f).focusRequester(focus)
            )
        }

        // Quick actions + tabs
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AssistChip(
                onClick = onScan, label = { Text(stringResource(R.string.scan_barcode)) },
                leadingIcon = { Icon(Icons.Filled.QrCodeScanner, null, Modifier.size(AssistChipDefaults.IconSize)) },
                shape = CircleShape
            )
            AssistChip(
                onClick = { onManual(null) }, label = { Text(stringResource(R.string.manual_entry)) },
                leadingIcon = { Icon(Icons.Filled.Edit, null, Modifier.size(AssistChipDefaults.IconSize)) },
                shape = CircleShape
            )
            PickerTab.entries.forEach { t ->
                FilterChip(
                    selected = tab == t, onClick = { vm.tab.value = t }, shape = CircleShape,
                    label = {
                        Text(stringResource(when (t) {
                            PickerTab.ALL -> R.string.tab_all; PickerTab.MINE -> R.string.tab_mine
                            PickerTab.MEALS -> R.string.tab_meals; PickerTab.RECENT -> R.string.tab_recent
                        }))
                    }
                )
            }
        }

        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            barcodeBanner(barcode, vm, onManual)

            val pick: (FoodItem) -> Unit = { focusManager.clearFocus(); vm.select(it) }
            val longPress: (FoodItem) -> Unit = {
                if (it.source == FoodSource.SAVED) {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress); confirmDelete = it
                }
            }
            // Filters a tab's list by what you typed in the search box.
            fun filtered(list: List<FoodItem>) =
                if (query.isBlank()) list else list.filter { it.name.contains(query.trim(), ignoreCase = true) }

            when (tab) {
                PickerTab.ALL -> if (query.isBlank()) {
                    if (recents.isEmpty() && saved.isEmpty()) item { Hint(stringResource(R.string.empty_start)) }
                    if (recents.isNotEmpty()) {
                        item { Header(stringResource(R.string.recent_header)) }
                        items(recents.take(8), key = { "r" + it.key }) { FoodRow(it, pick, longPress) }
                    }
                    if (saved.isNotEmpty()) {
                        item { Header(stringResource(R.string.mine_header)) }
                        items(saved, key = { "s" + it.key }) { FoodRow(it, pick, longPress) }
                    }
                } else {
                    if (local.isEmpty()) item { Hint(stringResource(R.string.no_results)) }
                    items(local, key = { "l" + it.key }) { FoodRow(it, pick, longPress) }
                    item {
                        OnlineSection(query, internetOn, online, vm::searchOnline, vm::turnOnInternet)
                    }
                    if (online is OnlineState.Results) {
                        val list = (online as OnlineState.Results).items
                        if (list.isEmpty()) item { Hint(stringResource(R.string.online_none)) }
                        items(list, key = { "o" + it.key }) { FoodRow(it, pick, longPress) }
                        item {
                            Text(stringResource(R.string.off_attribution), style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                PickerTab.MINE -> {
                    val list = filtered(saved)
                    if (list.isEmpty()) item { Hint(stringResource(R.string.empty_mine)) }
                    items(list, key = { "m" + it.key }) { FoodRow(it, pick, longPress) }
                }
                PickerTab.RECENT -> {
                    val list = filtered(recents)
                    if (list.isEmpty()) item { Hint(stringResource(R.string.empty_recent)) }
                    items(list, key = { "rc" + it.key }) { FoodRow(it, pick, longPress) }
                }
                PickerTab.MEALS -> {
                    val list = if (query.isBlank()) meals else meals.filter { it.name.contains(query.trim(), true) }
                    if (list.isEmpty()) item { Hint(stringResource(R.string.empty_meals)) }
                    items(list, key = { "ml" + it.id }) { m ->
                        MealRow(m, onClick = { vm.selectMeal(m) }, onLong = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress); confirmDelete = m
                        })
                    }
                }
            }
            item { Text(stringResource(R.string.values_note), style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp)) }
        }
    }

    // A food was tapped: show the portion sheet. After adding, return to Today.
    selected?.let { item ->
        PortionSheet(
            item = item,
            initialMeal = vm.initialMeal,
            onDismiss = { vm.select(null) },
            onLog = { amount, grams, meal, save ->
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                vm.log(item, amount, grams, meal, save) { vm.select(null); onLogged() }
            }
        )
    }
    // A saved meal was tapped: show the meal sheet.
    selectedMeal?.let { m ->
        MealSheet(m, vm.initialMeal, onDismiss = { vm.selectMeal(null) }) { meal ->
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            vm.logMeal(m, meal) { vm.selectMeal(null); onLogged() }
        }
    }
    // The "Delete?" dialog for one of your foods or meals.
    confirmDelete?.let { target ->
        val name = when (target) { is FoodItem -> target.name; is SavedMeal -> target.name; else -> "" }
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text(stringResource(R.string.delete_confirm, name)) },
            confirmButton = {
                TextButton(onClick = {
                    when (target) { is FoodItem -> vm.deleteSaved(target); is SavedMeal -> vm.deleteMeal(target) }
                    confirmDelete = null
                }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text(stringResource(R.string.cancel)) } }
        )
    }
}

// The card shown after a scan: "Looking up 7501055300075..." or "Not found",
// with buttons to type it in, or to turn internet on if it was off.
private fun LazyListScope.barcodeBanner(state: BarcodeState, vm: PickerViewModel, onManual: (String?) -> Unit) {
    when (state) {
        BarcodeState.None -> Unit
        is BarcodeState.Looking -> item {
            SoftCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(12.dp))
                    Text(stringResource(R.string.barcode_looking, state.code))
                }
            }
        }
        is BarcodeState.NotFound -> item {
            SoftCard {
                Text(
                    stringResource(if (state.offline) R.string.barcode_offline else R.string.barcode_notfound, state.code),
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (state.offline) FilledTonalButton(onClick = {
                        vm.turnOnInternet(); vm.clearBarcode(); vm.onBarcode(state.code)
                    }, shape = CircleShape) { Text(stringResource(R.string.turn_on)) }
                    OutlinedButton(onClick = { vm.clearBarcode(); onManual(state.code) }, shape = CircleShape) {
                        Text(stringResource(R.string.add_manually))
                    }
                    TextButton(onClick = vm::clearBarcode) { Text(stringResource(R.string.dismiss)) }
                }
            }
        }
    }
}

// The "Search online" card: a button (nothing is sent until you tap it), a
// spinner while loading, an error with retry, or a hint if internet is off.
@Composable
private fun OnlineSection(
    query: String, internetOn: Boolean, state: OnlineState,
    onSearch: () -> Unit, onTurnOn: () -> Unit
) {
    Column(Modifier.padding(top = 8.dp)) {
        if (!internetOn) {
            SoftCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.CloudOff, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(10.dp))
                    Text(stringResource(R.string.online_off_title), style = MaterialTheme.typography.titleSmall)
                }
                Text(stringResource(R.string.online_off_body), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                Spacer(Modifier.height(8.dp))
                FilledTonalButton(onClick = onTurnOn, shape = CircleShape) { Text(stringResource(R.string.turn_on)) }
            }
        } else when (state) {
            OnlineState.Idle -> OutlinedButton(onClick = onSearch, shape = CircleShape, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Search, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.search_online, query.trim()), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            OnlineState.Loading -> Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.Center) {
                CircularProgressIndicator(Modifier.size(28.dp))
            }
            OnlineState.Failed -> Column {
                Hint(stringResource(R.string.online_failed))
                TextButton(onClick = onSearch) { Text(stringResource(R.string.search_online, query.trim())) }
            }
            is OnlineState.Results -> Header(stringResource(R.string.online_results))
        }
    }
}

// One food in the list: emoji, name, serving, calories, and a small source badge.
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FoodRow(item: FoodItem, onClick: (FoodItem) -> Unit, onLong: (FoodItem) -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).combinedClickable(onClick = { onClick(item) }, onLongClick = { onLong(item) })
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            EmojiBadge(item.emoji)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(item.name, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                FlagChips(rememberItemFlags(item), Modifier.padding(vertical = 2.dp))   // ⚠️ allergy/diet tags
                Row(verticalAlignment = Alignment.CenterVertically) {
                    when (item.source) {
                        FoodSource.SAVED -> Badge(stringResource(R.string.badge_mine))
                        FoodSource.ONLINE -> Badge(stringResource(R.string.badge_online))
                        else -> Unit
                    }
                    Text(
                        item.servingText, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.kcal_value, item.kcal.roundToInt()), style = MaterialTheme.typography.labelLarge)
        }
    }
}

// One saved meal: its foods and total calories.
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MealRow(m: SavedMeal, onClick: () -> Unit, onLong: () -> Unit) {
    val items = remember(m.itemsJson) { m.items() }
    Surface(
        shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).combinedClickable(onClick = onClick, onLongClick = onLong)
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            EmojiBadge(m.emoji)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(m.name, style = MaterialTheme.typography.bodyLarge)
                Text(
                    items.joinToString(" ") { it.emoji } + "  " +
                        stringResource(R.string.items_kcal, items.size, items.sumOf { it.calories }),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun Badge(text: String) {
    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.padding(end = 6.dp)) {
        Text(text, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp))
    }
}

// Header = small section title; Hint = grey helper text. Also used by other screens.
@Composable
internal fun Header(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp, start = 4.dp))
}

@Composable
internal fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 12.dp, horizontal = 4.dp))
}
