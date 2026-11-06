// ==============================================================================
// PLAIN-ENGLISH GUIDE
//
// WHAT THIS FILE IS: what you SEE on the Today tab, from top to bottom:
//  1. Day header: app name, 🔥 streak, the date, and < > arrows to change day.
//  2. "Update your goals?" banner (only when your weight trend moved 5+ lb).
//  3. The big calorie ring + protein / carbs / fat bars.
//  4. Water card.
//  5. One card per meal (Breakfast, Lunch, Dinner, Snack) with its foods.
//  6. Exercise card.
// Plus two pop-up dialogs: "Save this meal" and "Delete this food?".
// The numbers come from TodayViewModel (the brain); this file only draws them.
//
// READING SCREEN CODE: each function marked @Composable draws a piece of the
// screen. Things like Modifier.padding(16.dp) adjust size and spacing;
// MaterialTheme.colorScheme.primary = the palette's accent color;
// stringResource(R.string.xxx) = text from strings.xml in your language.
// ==============================================================================
package com.gymnam.app.ui.today

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.BookmarkAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gymnam.app.R
import com.gymnam.app.data.FoodEntry
import com.gymnam.app.data.Meal
import com.gymnam.app.ui.common.EmojiBadge
import com.gymnam.app.ui.common.SoftCard
import com.gymnam.app.ui.common.emoji
import com.gymnam.app.ui.common.formatAmount
import com.gymnam.app.ui.common.labelRes
import com.gymnam.app.ui.theme.macroColors
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.math.roundToInt

// The inputs: vm = the brain; snackbar = the bottom message area; the "on..."
// inputs are actions to run when you tap things (navigation is decided by
// MainScaffold.kt).
// collectAsStateWithLifecycle() = watch a live value and redraw when it changes.
// ON_RESUME -> vm.onResume(): checks if midnight passed while the app was away.
@Composable
fun TodayScreen(
    vm: TodayViewModel,
    snackbar: SnackbarHostState,
    contentPadding: PaddingValues,
    onAddToMeal: (Meal) -> Unit,
    onEdit: (FoodEntry) -> Unit,
    onAddExercise: () -> Unit
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val streak by vm.streak.collectAsStateWithLifecycle()
    val suggestion by vm.suggestion.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.onResume() }
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    // toDelete / toSave = which food or meal has an open dialog (null = no dialog).
    var toDelete by remember { mutableStateOf<FoodEntry?>(null) }
    var toSave by remember { mutableStateOf<Meal?>(null) }
    val savedMsg = stringResource(R.string.meal_saved)
    val macros = macroColors(isSystemInDarkTheme())
    val deletedMsg = stringResource(R.string.deleted)
    val undoLabel = stringResource(R.string.undo)

    // LazyColumn = a scrolling list that only draws what's on screen (smooth on any phone).
    // Each item { } below is one block of the page, in order.
    LazyColumn(
        contentPadding = PaddingValues(
            start = 16.dp, end = 16.dp,
            top = contentPadding.calculateTopPadding() + 8.dp,
            bottom = contentPadding.calculateBottomPadding() + 24.dp
        ),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            DayHeader(state.day, streak.current, vm::previousDay, vm::nextDay, vm::goToToday)
        }
        // The goal-update banner. Shows e.g. "Your 7-day trend is 174.6 lb (was 180 lb).
        // New goal 2,170 kcal instead of 2,240." with "Not now" / "Update goals".
        suggestion?.let { g ->
            item(key = "suggestion") {
                Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("⚖️  " + stringResource(R.string.suggest_title), style = MaterialTheme.typography.titleSmall)
                        Text(
                            stringResource(R.string.suggest_body, com.gymnam.app.data.fmtNum(g.trendLb), com.gymnam.app.data.fmtNum(g.oldLb),
                                state.targets.calories, g.targets.calories),
                            style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp)
                        )
                        Row(Modifier.padding(top = 8.dp)) {
                            TextButton(onClick = { vm.dismissSuggestion(g) }) { Text(stringResource(R.string.not_now)) }
                            Spacer(Modifier.width(8.dp))
                            TextButton(onClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress); vm.applySuggestion(g)
                            }) { Text(stringResource(R.string.update_goals)) }
                        }
                    }
                }
            }
        }
        item {
            // The main card: calorie ring, "1,200 of 2,240 kcal" (or with "+300 burned"),
            // and the three macro bars.
            SoftCard(padding = 24.dp) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CalorieRing(state.totals.calories, state.goal, macros.over)
                }
                Text(
                    if (state.burned > 0)
                        stringResource(R.string.eaten_of_goal_burned, state.totals.calories, state.targets.calories, state.burned)
                    else stringResource(R.string.eaten_of_goal, state.totals.calories, state.goal),
                    modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Spacer(Modifier.height(20.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    MacroBar(stringResource(R.string.protein), state.totals.protein, state.targets.proteinG, macros.protein, Modifier.weight(1f))
                    MacroBar(stringResource(R.string.carbs), state.totals.carbs, state.targets.carbsG, macros.carbs, Modifier.weight(1f))
                    MacroBar(stringResource(R.string.fat), state.totals.fat, state.targets.fatG, macros.fat, Modifier.weight(1f))
                }
            }
        }
        item {
            WaterCard(state.waterFlOz, state.waterGoal,
                onAdd = { oz -> haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove); vm.addWater(oz) },
                onUndo = { haptics.performHapticFeedback(HapticFeedbackType.LongPress); vm.undoWater() })
        }
        // One card per meal. Tap a food = edit it. Long-press = delete (with Undo).
        items(Meal.entries, key = { it.name }) { meal ->
            MealCard(
                meal = meal,
                entries = state.byMeal[meal].orEmpty(),
                onAdd = { onAddToMeal(meal) },
                onSave = { toSave = meal },
                onEdit = onEdit,
                onLongPress = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    toDelete = it
                }
            )
        }
        item {
            ExerciseCard(state.exercises, onAdd = onAddExercise, onLongPress = { e ->
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                vm.deleteExercise(e)
                scope.launch {
                    if (snackbar.showSnackbar(deletedMsg, undoLabel, duration = SnackbarDuration.Short) ==
                        SnackbarResult.ActionPerformed) vm.restoreExercise(e)
                }
            })
        }
    }

    // Dialog: name and save this meal's foods as a "My meal".
    toSave?.let { meal ->
        val mealLabel = stringResource(meal.labelRes())
        var name by remember(meal) { mutableStateOf(mealLabel) }
        AlertDialog(
            onDismissRequest = { toSave = null },
            title = { Text(stringResource(R.string.save_meal)) },
            text = {
                OutlinedTextField(
                    value = name, onValueChange = { name = it }, singleLine = true,
                    label = { Text(stringResource(R.string.meal_name)) }, shape = MaterialTheme.shapes.medium
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.saveMeal(name, meal, state.byMeal[meal].orEmpty())
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    toSave = null
                    scope.launch { snackbar.showSnackbar(savedMsg) }
                }) { Text(stringResource(R.string.save)) }
            },
            dismissButton = { TextButton(onClick = { toSave = null }) { Text(stringResource(R.string.cancel)) } }
        )
    }

    // Dialog: confirm delete. After deleting, the snackbar offers Undo for a few seconds.
    toDelete?.let { entry ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text(stringResource(R.string.delete_confirm, entry.name)) },
            confirmButton = {
                TextButton(onClick = {
                    vm.delete(entry)
                    toDelete = null
                    scope.launch {
                        val r = snackbar.showSnackbar(deletedMsg, undoLabel, duration = SnackbarDuration.Short)
                        if (r == SnackbarResult.ActionPerformed) vm.restore(entry)
                    }
                }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text(stringResource(R.string.cancel)) } }
        )
    }
}

// Shows "Today", "Yesterday", or a date like "Nov 3, 2026" (in your phone's
// format). Tapping the date jumps back to today. The > arrow is disabled on today.
@Composable
private fun DayHeader(day: LocalDate, streak: Int, onPrev: () -> Unit, onNext: () -> Unit, onToday: () -> Unit) {
    val today = LocalDate.now()
    val label = when (day) {
        today -> stringResource(R.string.today)
        today.minusDays(1) -> stringResource(R.string.yesterday)
        else -> day.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.app_name),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                if (streak > 0) {
                    Spacer(Modifier.width(8.dp))
                    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                        Text("🔥 " + pluralStringResource(R.plurals.streak_days, streak, streak),
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
                    }
                }
            }
            Text(
                label,
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.clip(MaterialTheme.shapes.small).clickable(onClick = onToday)
            )
        }
        FilledTonalIconButton(onClick = onPrev) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, stringResource(R.string.prev_day))
        }
        Spacer(Modifier.width(8.dp))
        FilledTonalIconButton(onClick = onNext, enabled = day < today) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, stringResource(R.string.next_day))
        }
    }
}

// One macro bar: name, a bar filled to eaten / target, and "92 / 144 g".
// Example: 92 g of 144 g protein = 64% filled.
@Composable
private fun MacroBar(label: String, value: Double, target: Int, color: Color, modifier: Modifier) {
    val frac by animateFloatAsState(
        (value / target.coerceAtLeast(1)).toFloat().coerceIn(0f, 1f), tween(900), label = "macro"
    )
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(6.dp))
        Box(
            Modifier.fillMaxWidth().height(10.dp).clip(CircleShape)
                .background(color.copy(alpha = 0.22f))
        ) {
            Box(Modifier.fillMaxWidth(frac).fillMaxHeight().clip(CircleShape).background(color))
        }
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(R.string.grams_of, value.roundToInt(), target),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// A meal's card: emoji, name, total kcal, a bookmark button (save as My meal),
// a + button, and the list of foods (or "Nothing yet" if empty).
// animateContentSize() = the card grows/shrinks smoothly as foods are added.
@Composable
private fun MealCard(
    meal: Meal,
    entries: List<FoodEntry>,
    onAdd: () -> Unit,
    onSave: () -> Unit,
    onEdit: (FoodEntry) -> Unit,
    onLongPress: (FoodEntry) -> Unit
) {
    SoftCard(Modifier.animateContentSize(), padding = 16.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(meal.emoji(), style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.width(10.dp))
            Text(stringResource(meal.labelRes()), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            if (entries.isNotEmpty()) {
                Text(
                    stringResource(R.string.kcal_value, entries.sumOf { it.calories }),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (entries.isNotEmpty()) IconButton(onClick = onSave) {
                Icon(Icons.Outlined.BookmarkAdd, stringResource(R.string.save_meal),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onAdd) {
                Icon(Icons.Filled.Add, stringResource(R.string.add), tint = MaterialTheme.colorScheme.primary)
            }
        }
        if (entries.isEmpty()) {
            Text(
                stringResource(R.string.meal_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, top = 2.dp, bottom = 4.dp)
            )
        } else {
            entries.forEach { e -> EntryRow(e, { onEdit(e) }, { onLongPress(e) }) }
        }
    }
}

// One food line: emoji, name, amount ("150 g") and its calories.
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EntryRow(e: FoodEntry, onClick: () -> Unit, onLongClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        EmojiBadge(e.emoji)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(e.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                formatAmount(e.amount, e.unit),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(stringResource(R.string.kcal_value, e.calories), style = MaterialTheme.typography.labelLarge)
    }
}
