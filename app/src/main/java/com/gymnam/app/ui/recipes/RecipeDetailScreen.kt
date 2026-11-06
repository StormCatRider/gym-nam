// ==============================================================================
// PLAIN-ENGLISH GUIDE
//
// WHAT THIS FILE IS: one recipe's page:
//  - header (emoji, name, servings, time, source),
//  - nutrition per serving ("estimated" for online recipes),
//  - ingredients, each marked ✓ (in your fridge) or ✗ (missing), plus
//    "you have 4 of 6",
//  - the steps,
//  - buttons: "I cooked this", "Save to My recipes" (built-in / online),
//    "Edit" and "Delete" (your own recipes).
// "I cooked this" opens CookSheet: how many servings you ate, which meal, and
// whether to subtract the ingredients from the fridge (with a preview such as
// "Eggs 12 -> 9 pcs").
// ==============================================================================
package com.gymnam.app.ui.recipes

import androidx.compose.animation.core.animateIntAsState
import androidx.compose.foundation.horizontalScroll
import com.gymnam.app.ui.theme.isAppDark
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.outlined.BookmarkAdd
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gymnam.app.R
import com.gymnam.app.data.FridgeUnit
import com.gymnam.app.data.Meal
import com.gymnam.app.data.RecipeModel
import com.gymnam.app.data.RecipeSource
import com.gymnam.app.data.fmtNum
import com.gymnam.app.ui.common.SoftCard
import com.gymnam.app.ui.common.labelRes
import com.gymnam.app.ui.common.mealForNow
import com.gymnam.app.ui.fridge.Pill
import com.gymnam.app.ui.fridge.formatQty
import com.gymnam.app.ui.theme.macroColors
import kotlinx.coroutines.launch
import java.time.LocalTime
import kotlin.math.roundToInt

@Composable
fun RecipeDetailScreen(
    vm: RecipeDetailViewModel,
    snackbar: SnackbarHostState,
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
    onCooked: () -> Unit
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val fridge by vm.fridgeView.collectAsStateWithLifecycle()
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    var cooking by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val savedMsg = stringResource(R.string.recipe_saved)

    Column(
        Modifier.fillMaxSize()
            .padding(top = contentPadding.calculateTopPadding(), bottom = contentPadding.calculateBottomPadding())
    ) {
        val ready = state as? DetailState.Ready
        Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
            Spacer(Modifier.weight(1f))
            ready?.recipe?.let { r ->
                if (r.source == RecipeSource.MINE) {
                    IconButton(onClick = { r.myId?.let(onEdit) }) { Icon(Icons.Filled.Edit, stringResource(R.string.edit)) }
                    IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Outlined.Delete, stringResource(R.string.delete)) }
                } else {
                    TextButton(onClick = {
                        vm.saveToMine { id ->
                            scope.launch { snackbar.showSnackbar(savedMsg) }
                            onEdit(id)
                        }
                    }) {
                        Icon(Icons.Outlined.BookmarkAdd, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.save_to_my_recipes))
                    }
                }
            }
        }

        when (val s = state) {
            DetailState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            DetailState.Offline -> Message(stringResource(R.string.online_off_title), stringResource(R.string.online_recipes_off_body))
            DetailState.Failed -> Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                Message("🌐", stringResource(R.string.online_failed))
                FilledTonalButton(onClick = vm::load, shape = CircleShape) { Text(stringResource(R.string.retry)) }
            }
            DetailState.Gone -> LaunchedEffect(Unit) { onBack() }
            is DetailState.Ready -> {
                Column(
                    Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) { RecipeBody(s.recipe, fridge) }
                Button(
                    onClick = { haptics.performHapticFeedback(HapticFeedbackType.LongPress); cooking = true },
                    shape = CircleShape, contentPadding = PaddingValues(vertical = 16.dp),
                    modifier = Modifier.fillMaxWidth().padding(16.dp)
                ) { Text("👩‍🍳  " + stringResource(R.string.cook_and_log), style = MaterialTheme.typography.titleMedium) }

                if (cooking) CookSheet(s.recipe, fridge, onDismiss = { cooking = false }) { servings, meal, subtract ->
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    vm.cook(servings, meal, subtract) { cooking = false; onCooked() }
                }
            }
        }
    }

    if (confirmDelete) {
        val name = (state as? DetailState.Ready)?.recipe?.name.orEmpty()
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.delete_confirm, name)) },
            confirmButton = { TextButton(onClick = { confirmDelete = false; vm.delete {} }) { Text(stringResource(R.string.delete)) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) } }
        )
    }
}

// A centered message for loading problems: offline, failed, or recipe deleted.
@Composable
private fun Message(title: String, body: String) {
    Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Text(body, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
    }
}

// The recipe content itself (header, nutrition, ingredients, steps).
@Composable
private fun RecipeBody(r: RecipeModel, fridge: FridgeView) {
    val macros = macroColors(isAppDark())
    // Header
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(Modifier.size(72.dp), shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
            Box(contentAlignment = Alignment.Center) { Text(r.emoji, fontSize = 36.sp) }
        }
        Spacer(Modifier.width(14.dp))
        Column {
            Text(r.name, style = MaterialTheme.typography.headlineSmall)
            Text(
                buildList {
                    add(stringResource(R.string.servings_count, r.servings))
                    if (r.minutes > 0) add(stringResource(R.string.minutes_short, r.minutes))
                }.joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    // Nutrition per serving
    SoftCard {
        Text(stringResource(R.string.per_serving), style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            (if (r.estimated) "≈ " else "") + stringResource(R.string.kcal_value, r.kcal.roundToInt()),
            style = MaterialTheme.typography.displaySmall
        )
        Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill("${stringResource(R.string.protein)} ${r.proteinG.roundToInt()} g", macros.protein.copy(alpha = 0.3f))
            Pill("${stringResource(R.string.carbs)} ${r.carbsG.roundToInt()} g", macros.carbs.copy(alpha = 0.3f))
            Pill("${stringResource(R.string.fat)} ${r.fatG.roundToInt()} g", macros.fat.copy(alpha = 0.3f))
        }
        if (r.estimated) Text(
            stringResource(R.string.estimated_note, r.matchedIngredients, r.ingredients.size),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp)
        )
    }

    // Ingredients with fridge status
    SoftCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.ingredients), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            fridge.coverage?.takeIf { it.total > 0 }?.let {
                Pill("🧊 ${it.have}/${it.total}",
                    if (it.ratio >= 1.0) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)
            }
        }
        r.ingredients.forEach { ing ->
            val match = fridge.matches[ing.name]
            Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.Top) {
                Text(
                    when { match != null -> "✅"; ing.staple -> "🧂"; else -> "▫️" },
                    modifier = Modifier.width(28.dp)
                )
                Text(ing.emoji, modifier = Modifier.width(28.dp))
                Column(Modifier.weight(1f)) {
                    Text(ing.name, style = MaterialTheme.typography.bodyLarge)
                    match?.let {
                        Text(stringResource(R.string.in_fridge, it.name, formatQty(it.quantity, it.unit)),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
                Text(
                    ing.measureText ?: buildString {
                        if (ing.pieces > 0) append(fmtNum(ing.pieces)).append(" · ")
                        append(fmtNum(ing.grams)).append(" g")
                    },
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    // Steps
    if (r.steps.isNotEmpty()) SoftCard {
        Text(stringResource(R.string.steps), style = MaterialTheme.typography.titleMedium)
        r.steps.forEachIndexed { i, step ->
            Row(Modifier.padding(top = 10.dp)) {
                Surface(Modifier.size(26.dp), shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                    Box(contentAlignment = Alignment.Center) { Text("${i + 1}", style = MaterialTheme.typography.labelLarge) }
                }
                Spacer(Modifier.width(10.dp))
                Text(step, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            }
        }
    }
    Spacer(Modifier.size(8.dp))
}

// Calories shown update with servings: e.g. 1.5 x 480 = 720 kcal.
// The subtraction preview lists each fridge item: current amount -> amount after.
/** How many servings you ate, which meal, and whether to take ingredients out of the fridge. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CookSheet(
    r: RecipeModel, fridge: FridgeView, onDismiss: () -> Unit,
    onConfirm: (servings: Double, meal: Meal, subtract: Boolean) -> Unit
) {
    var servings by remember { mutableStateOf(1.0) }
    var meal by remember { mutableStateOf(mealForNow(LocalTime.now().hour)) }
    var subtract by remember { mutableStateOf(fridge.plan.isNotEmpty()) }
    val kcal by animateIntAsState((r.kcal * servings).roundToInt(), label = "kcal")

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
            Text("${r.emoji}  ${r.name}", style = MaterialTheme.typography.titleLarge)

            Text(stringResource(R.string.servings_eaten), style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                FilledTonalIconButton(onClick = { servings = (servings - 0.5).coerceAtLeast(0.5) }) {
                    Icon(Icons.Filled.Remove, stringResource(R.string.step_down))
                }
                Text(fmtNum(servings), style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center,
                    modifier = Modifier.width(72.dp))
                FilledTonalIconButton(onClick = { servings += 0.5 }) { Icon(Icons.Filled.Add, stringResource(R.string.step_up)) }
                Spacer(Modifier.weight(1f))
                Text(stringResource(R.string.kcal_value, kcal), style = MaterialTheme.typography.headlineSmall)
            }

            Row(Modifier.padding(top = 12.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Meal.entries.forEach { m ->
                    FilterChip(meal == m, { meal = m }, shape = CircleShape, label = { Text(stringResource(m.labelRes())) })
                }
            }

            Row(Modifier.fillMaxWidth().padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.take_from_fridge), style = MaterialTheme.typography.bodyLarge)
                    Text(stringResource(R.string.take_from_fridge_hint, r.servings), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = subtract, onCheckedChange = { subtract = it }, enabled = fridge.plan.isNotEmpty())
            }
            if (fridge.plan.isEmpty()) Text(stringResource(R.string.nothing_to_take), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
            if (subtract) fridge.plan.forEach { s ->
                val left = (s.item.quantity - s.amount).coerceAtLeast(0.0)
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(s.item.emoji, modifier = Modifier.width(28.dp))
                    Text(s.item.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Text(
                        "${formatQty(s.item.quantity, s.item.unit)} → " +
                            if (left <= 0.001) stringResource(R.string.used_up) else formatQty(roundQty(left, s.item.unit), s.item.unit),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (left <= 0.001) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Button(
                onClick = { onConfirm(servings, meal, subtract) },
                shape = CircleShape, contentPadding = PaddingValues(vertical = 16.dp),
                modifier = Modifier.fillMaxWidth().padding(top = 20.dp)
            ) { Text(stringResource(R.string.add_to_meal, stringResource(meal.labelRes())), style = MaterialTheme.typography.titleMedium) }
        }
    }
}

/** Pounds and ounces to 2 decimals, grams/ml to whole numbers, pieces to halves. */
private fun roundQty(q: Double, u: FridgeUnit): Double = when (u) {
    FridgeUnit.LB, FridgeUnit.OZ -> (q * 100).roundToInt() / 100.0
    FridgeUnit.G, FridgeUnit.ML -> q.roundToInt().toDouble()
    else -> (q * 2).roundToInt() / 2.0
}
