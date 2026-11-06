// ==============================================================================
// PLAIN-ENGLISH GUIDE
//
// WHAT THIS FILE IS: the Fridge tab. A switch at the top picks one of two views:
//  FRIDGE ITEMS: "Add" and "Scan" buttons, then your items grouped as
//    Expired / Expiring soon (3 days) / Later / No date, each with a colored
//    expiry chip. Tap = edit, long-press = delete (with Undo).
//  RECIPES: "You can make N recipes now", sort chips, "New recipe", the recipe
//    cards (with "4/6 in fridge", calories, protein, time), and online search.
// The brain is FridgeViewModel.kt.
// ==============================================================================
package com.gymnam.app.ui.fridge

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gymnam.app.R
import com.gymnam.app.data.FridgeItem
import com.gymnam.app.data.RecipeSource
import com.gymnam.app.ui.common.EmojiBadge
import com.gymnam.app.ui.common.SoftCard
import com.gymnam.app.ui.picker.Header
import com.gymnam.app.ui.picker.Hint
import com.gymnam.app.ui.theme.macroColors
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FridgeTab(
    vm: FridgeViewModel,
    snackbar: SnackbarHostState,
    contentPadding: PaddingValues,
    onAddItem: () -> Unit,
    onScanItem: () -> Unit,
    onEditItem: (FridgeItem) -> Unit,
    onOpenRecipe: (key: String) -> Unit,
    onNewRecipe: () -> Unit
) {
    val section by vm.section.collectAsStateWithLifecycle()
    val fridge by vm.fridge.collectAsStateWithLifecycle()
    val recipes by vm.recipes.collectAsStateWithLifecycle()
    val sort by vm.sort.collectAsStateWithLifecycle()
    val canMake by vm.canMakeNow.collectAsStateWithLifecycle()
    val kcalLeft by vm.kcalLeft.collectAsStateWithLifecycle()
    val internetOn by vm.internetOn.collectAsStateWithLifecycle()
    val online by vm.online.collectAsStateWithLifecycle()
    val onlineQuery by vm.onlineQuery.collectAsStateWithLifecycle()
    val haptics = LocalHapticFeedback.current
    val focus = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    val deletedMsg = stringResource(R.string.deleted)
    val undoLabel = stringResource(R.string.undo)
    val today = vm.today
    val dark = isSystemInDarkTheme()

    LazyColumn(
        contentPadding = PaddingValues(
            start = 16.dp, end = 16.dp,
            top = contentPadding.calculateTopPadding() + 8.dp,
            bottom = contentPadding.calculateBottomPadding() + 24.dp
        ),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Text(stringResource(R.string.tab_fridge), style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.size(10.dp))
            // The two-part switch: Fridge items | Recipes.
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                FridgeSection.entries.forEachIndexed { i, s ->
                    SegmentedButton(
                        selected = section == s,
                        onClick = { haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove); vm.section.value = s },
                        shape = SegmentedButtonDefaults.itemShape(i, FridgeSection.entries.size)
                    ) {
                        Text(stringResource(if (s == FridgeSection.FRIDGE) R.string.fridge_items else R.string.recipes))
                    }
                }
            }
        }

        // --- FRIDGE ITEMS VIEW ---
        if (section == FridgeSection.FRIDGE) {
            item {
                Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = onAddItem, shape = CircleShape) {
                        Icon(Icons.Filled.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.add_item))
                    }
                    OutlinedButton(onClick = onScanItem, shape = CircleShape) {
                        Icon(Icons.Filled.QrCodeScanner, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.scan_barcode))
                    }
                }
            }
            if (canMake > 0) item {
                Surface(
                    onClick = { vm.sort.value = RecipeSort.HAVE; vm.section.value = FridgeSection.RECIPES },
                    shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "👩‍🍳  " + pluralStringResource(R.plurals.can_make_now, canMake, canMake),
                        style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(16.dp)
                    )
                }
            }
            if (fridge.isEmpty()) item { Hint(stringResource(R.string.fridge_empty)) }

            val groups = fridge.groupBy { expiryGroup(it.expiryDay, today) }
            ExpiryGroup.entries.forEach { g ->
                val list = groups[g].orEmpty()
                if (list.isNotEmpty()) {
                    item(key = "h" + g.name) { Header(stringResource(g.labelRes)) }
                    items(list, key = { "f" + it.id }) { item ->
                        FridgeRow(item, today, dark, onClick = { onEditItem(item) }, onLong = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            vm.delete(item)
                            scope.launch {
                                if (snackbar.showSnackbar(deletedMsg, undoLabel, duration = SnackbarDuration.Short) ==
                                    SnackbarResult.ActionPerformed) vm.restore(item)
                            }
                        })
                    }
                }
            }
        } else {
            item {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    RecipeSort.entries.forEach { s ->
                        FilterChip(sort == s, { vm.sort.value = s }, shape = CircleShape, label = {
                            Text(when (s) {
                                RecipeSort.FITS -> stringResource(R.string.sort_fits, kcalLeft.coerceAtLeast(0))
                                else -> stringResource(s.labelRes())
                            })
                        })
                    }
                }
            }
            item {
                OutlinedButton(onClick = onNewRecipe, shape = CircleShape, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.new_recipe))
                }
            }
            items(recipes, key = { it.recipe.key }) { row ->
                RecipeRowCard(row, kcalLeft) { onOpenRecipe(row.recipe.key) }
            }
            onlineSection(onlineQuery, internetOn, online, vm, onOpenRecipe) { focus.clearFocus() }
        }
    }
}

// Online recipe search (TheMealDB): a search box + button. If internet is off,
// shows a note with a button to turn it on. Tapping a result opens its page.
private fun LazyListScope.onlineSection(
    query: String, internetOn: Boolean, state: OnlineRecipes, vm: FridgeViewModel,
    onOpen: (String) -> Unit, clearFocus: () -> Unit
) {
    item { Header(stringResource(R.string.online_recipes)) }
    item {
        if (!internetOn) {
            SoftCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.CloudOff, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(10.dp))
                    Text(stringResource(R.string.online_off_title), style = MaterialTheme.typography.titleSmall)
                }
                Text(stringResource(R.string.online_recipes_off_body), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                FilledTonalButton(onClick = { vm.turnOnInternet() }, shape = CircleShape, modifier = Modifier.padding(top = 8.dp)) {
                    Text(stringResource(R.string.turn_on))
                }
            }
        } else {
            OutlinedTextField(
                value = query, onValueChange = { vm.onlineQuery.value = it },
                placeholder = { Text(stringResource(R.string.online_recipes_hint)) },
                singleLine = true, shape = CircleShape,
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { clearFocus(); vm.searchOnline() }),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
    when (state) {
        OnlineRecipes.Idle -> Unit
        OnlineRecipes.Loading -> item {
            Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.Center) {
                CircularProgressIndicator(Modifier.size(28.dp))
            }
        }
        OnlineRecipes.Failed -> item { Hint(stringResource(R.string.online_failed)) }
        is OnlineRecipes.Results -> {
            if (state.list.isEmpty()) item { Hint(stringResource(R.string.online_none)) }
            items(state.list, key = { "m" + it.id }) { m ->
                Surface(
                    onClick = { onOpen("mealdb:${m.id}") }, shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()
                ) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        EmojiBadge("🌐")
                        Spacer(Modifier.width(12.dp))
                        Text(m.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
    item {
        Text(stringResource(R.string.online_recipes_note), style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
    }
}

// One fridge item: emoji, name, quantity ("2 lb") and its expiry chip.
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FridgeRow(item: FridgeItem, today: Long, dark: Boolean, onClick: () -> Unit, onLong: () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).combinedClickable(onClick = onClick, onLongClick = onLong)
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            EmojiBadge(item.emoji)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(item.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(formatQty(item.quantity, item.unit), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            item.expiryDay?.let { ExpiryChip(it, today, dark) }
        }
    }
}

// The colored expiry label. d = days left (expiry day - today):
//   d < 0  -> red   "Expired 2 days ago"
//   d = 0  -> red   "Expires today"
//   1 to 3 -> amber "Expires in 2 days"
//   later  -> grey  "Expires in 9 days"
// pluralStringResource picks "1 day" vs "2 days" correctly in each language.
@Composable
fun ExpiryChip(day: Long, today: Long, dark: Boolean) {
    val d = (day - today).toInt()
    val over = macroColors(dark).over
    val (bg, text) = when {
        d < 0 -> over.copy(alpha = 0.25f) to pluralStringResource(R.plurals.expired_days_ago, -d, -d)
        d == 0 -> over.copy(alpha = 0.25f) to stringResource(R.string.expires_today)
        d <= 3 -> Color(0xFFF3C77A).copy(alpha = 0.35f) to pluralStringResource(R.plurals.expires_in_days, d, d)
        else -> MaterialTheme.colorScheme.surfaceVariant to pluralStringResource(R.plurals.expires_in_days, d, d)
    }
    Surface(shape = CircleShape, color = bg) {
        Text(text, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
    }
}

// One recipe card. The pills show: how many ingredients you have, "Uses
// expiring" if it helps clear your fridge, and whether it fits your calories left.
@Composable
private fun RecipeRowCard(row: RecipeRow, kcalLeft: Int, onClick: () -> Unit) {
    val r = row.recipe
    val c = row.coverage
    Surface(onClick = onClick, shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            EmojiBadge(r.emoji, size = 48.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(r.name, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    buildList {
                        add(stringResource(R.string.kcal_value, r.kcal.roundToInt()))
                        add(stringResource(R.string.protein_short, r.proteinG.roundToInt()))
                        if (r.minutes > 0) add(stringResource(R.string.minutes_short, r.minutes))
                    }.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (c.total > 0) Pill(
                        "🧊 ${c.have}/${c.total}",
                        if (c.ratio >= 1.0) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                    )
                    if (c.usesExpiring) Pill("⏰ " + stringResource(R.string.uses_expiring), Color(0xFFF3C77A).copy(alpha = 0.35f))
                    if (r.source == RecipeSource.MINE) Pill(stringResource(R.string.badge_mine), MaterialTheme.colorScheme.primaryContainer)
                    if (r.kcal <= kcalLeft) Pill("✓ " + stringResource(R.string.fits), MaterialTheme.colorScheme.surfaceVariant)
                }
            }
        }
    }
}

// A small rounded colored label.
@Composable
fun Pill(text: String, color: Color) {
    Surface(shape = CircleShape, color = color) {
        Text(text, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
    }
}

// The section headers the fridge list is grouped by.
enum class ExpiryGroup(val labelRes: Int) {
    EXPIRED(R.string.group_expired), SOON(R.string.group_soon), LATER(R.string.group_later), NONE(R.string.group_no_date)
}

// Which section an item goes in: no date / expired / within 3 days / later.
fun expiryGroup(day: Long?, today: Long): ExpiryGroup = when {
    day == null -> ExpiryGroup.NONE
    day < today -> ExpiryGroup.EXPIRED
    day - today <= 3 -> ExpiryGroup.SOON
    else -> ExpiryGroup.LATER
}

// The text for each sort chip, in your language.
fun RecipeSort.labelRes(): Int = when (this) {
    RecipeSort.HAVE -> R.string.sort_have; RecipeSort.EXPIRING -> R.string.sort_expiring
    RecipeSort.FITS -> R.string.sort_fits; RecipeSort.PROTEIN -> R.string.sort_protein
    RecipeSort.QUICK -> R.string.sort_quick; RecipeSort.LIGHT -> R.string.sort_light
}

// Unit names on screen: pcs, packs, g, oz, lb, ml.
@Composable
fun unitLabel(u: com.gymnam.app.data.FridgeUnit): String = stringResource(
    when (u) {
        com.gymnam.app.data.FridgeUnit.PIECE -> R.string.unit_pcs
        com.gymnam.app.data.FridgeUnit.PACK -> R.string.unit_pack
        com.gymnam.app.data.FridgeUnit.G -> R.string.unit_gram
        com.gymnam.app.data.FridgeUnit.OZ -> R.string.unit_oz
        com.gymnam.app.data.FridgeUnit.LB -> R.string.unit_lb
        com.gymnam.app.data.FridgeUnit.ML -> R.string.unit_ml
    }
)

/** "2 lb", "6 pcs", "1.5 packs" */
@Composable
fun formatQty(q: Double, u: com.gymnam.app.data.FridgeUnit): String =
    "${com.gymnam.app.data.fmtNum(q)} ${unitLabel(u)}"
