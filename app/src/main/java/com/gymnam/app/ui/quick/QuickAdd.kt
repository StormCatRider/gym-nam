// ==============================================================================
// PLAIN-ENGLISH GUIDE
//
// WHAT THIS FILE IS: the three small "quick add" sheets that slide up from the
// bottom (from the + button, the Progress tab, or the Exercise card):
//  - WeightSheet:   type your weight (with -0.2 / +0.2 lb buttons).
//  - WaterSheet:    quick buttons or a custom amount in fl oz.
//  - ExerciseSheet: pick an activity and minutes; calories are estimated for you
//                   (you can overwrite the number).
// QuickAddViewModel at the top is their small shared brain.
// ==============================================================================
package com.gymnam.app.ui.quick

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.gymnam.app.R
import com.gymnam.app.data.ExerciseEntry
import com.gymnam.app.data.TrackingDao
import com.gymnam.app.data.WaterEntry
import com.gymnam.app.data.WeightEntry
import com.gymnam.app.data.LocalMetric
import com.gymnam.app.data.Units
import com.gymnam.app.data.fmtNum
import com.gymnam.app.ui.common.toNumberOrNull
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

// Saves weight / water / exercise into the database for the given day.
class QuickAddViewModel(private val tracking: TrackingDao) : ViewModel() {
    val latestWeight: StateFlow<WeightEntry?> = tracking.latestWeight()
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    // One weight per day: saving again the same day replaces it.
    fun saveWeight(day: Long, lb: Double) = viewModelScope.launch { tracking.upsertWeight(WeightEntry(epochDay = day, lb = lb)) }
    fun addWater(day: Long, flOz: Double) = viewModelScope.launch { tracking.insertWater(WaterEntry(epochDay = day, flOz = flOz)) }
    fun addExercise(day: Long, a: Activity, name: String, minutes: Int, kcal: Int) = viewModelScope.launch {
        tracking.insertExercise(ExerciseEntry(epochDay = day, name = name, emoji = a.emoji, minutes = minutes, kcal = kcal))
    }
}

// MET = "metabolic equivalent": how many times more energy than sitting still.
//   walk 3.5 | run 9.8 | bike 7.5 | weights 5.0 | HIIT 8.0 | swim 8.0 |
//   sports 7.0 | yoga 2.5 | other 5.0  (Compendium of Physical Activities)
/**
 * Activities with their MET (metabolic equivalent) value, from the Compendium of Physical Activities.
 * Estimated kcal = MET × body weight (kg) × hours, where kg = lb ÷ 2.2046.
 * Example: running (9.8 MET), 30 min, 180 lb → 9.8 × 81.6 × 0.5 ≈ 400 kcal.
 */
enum class Activity(val emoji: String, val met: Double, val labelRes: Int) {
    WALK("🚶", 3.5, R.string.act_walk), RUN("🏃", 9.8, R.string.act_run), BIKE("🚴", 7.5, R.string.act_bike),
    WEIGHTS("🏋️", 5.0, R.string.act_weights), HIIT("🔥", 8.0, R.string.act_hiit), SWIM("🏊", 8.0, R.string.act_swim),
    SPORTS("⚽", 7.0, R.string.act_sports), YOGA("🧘", 2.5, R.string.act_yoga), OTHER("💪", 5.0, R.string.act_other);

    // FORMULA: kcal = MET x body weight (kg) x hours,  kg = lb / 2.2046,  hours = min / 60
    // Example: run 30 min at 180 lb -> 9.8 x 81.65 x 0.5 = 400 kcal.
    fun estimate(minutes: Int, lb: Double): Int = (met * (lb / 2.2046) * (minutes / 60.0)).roundToInt()
}

// Starts with your last weight already typed in (LaunchedEffect fills it once
// it loads). The tip under the box reminds you to weigh in at the same time each
// day for a cleaner trend.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeightSheet(vm: QuickAddViewModel, day: Long, onDone: () -> Unit) {
    val latest by vm.latestWeight.collectAsStateWithLifecycle()
    // metric = kg on screen. The number you type is in kg or lb; it's saved as lb.
    val metric = LocalMetric.current
    var text by remember { mutableStateOf("") }
    LaunchedEffect(latest) { if (text.isEmpty()) latest?.let { text = fmtNum(Units.weightValue(it.lb, metric)) } }
    val typed = text.toNumberOrNull()
    val okRange = Units.weightRange(metric)
    ModalBottomSheet(onDismissRequest = onDone, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp).imePadding()) {
            Text("⚖️  " + stringResource(R.string.log_weight), style = MaterialTheme.typography.titleLarge)
            latest?.let {
                Text(stringResource(R.string.last_weight, Units.weight(it.lb, metric), java.time.LocalDate.ofEpochDay(it.epochDay).toString()),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(Modifier.padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                FilledTonalIconButton(onClick = { text = fmtNum(Math.round(((typed ?: 0.0) - 0.2).coerceAtLeast(0.0) * 10) / 10.0) }) {
                    Icon(Icons.Filled.Remove, stringResource(R.string.step_down))
                }
                OutlinedTextField(
                    text, { text = it }, Modifier.weight(1f).padding(horizontal = 8.dp), singleLine = true,
                    suffix = { Text(Units.weightUnit(metric)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    textStyle = MaterialTheme.typography.headlineSmall.copy(textAlign = TextAlign.Center),
                    shape = MaterialTheme.shapes.medium
                )
                FilledTonalIconButton(onClick = { text = fmtNum(Math.round(((typed ?: 0.0) + 0.2) * 10) / 10.0) }) {
                    Icon(Icons.Filled.Add, stringResource(R.string.step_up))
                }
            }
            Text(stringResource(R.string.weight_tip), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
            Button(
                onClick = { typed?.takeIf { it in okRange }?.let { vm.saveWeight(day, Units.lbFrom(it, metric)); onDone() } },
                enabled = typed != null && typed in okRange,
                shape = CircleShape, contentPadding = PaddingValues(vertical = 16.dp),
                modifier = Modifier.fillMaxWidth().padding(top = 18.dp)
            ) { Text(stringResource(R.string.save), style = MaterialTheme.typography.titleMedium) }
        }
    }
}

// Preset buttons add instantly; or type any amount and tap Add.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WaterSheet(vm: QuickAddViewModel, day: Long, onDone: () -> Unit) {
    val metric = LocalMetric.current
    var custom by remember { mutableStateOf("") }
    // Metric only: type the custom amount in ml (false) or liters (true).
    var inLiters by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDone, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp).imePadding()) {
            Text("💧  " + stringResource(R.string.add_water), style = MaterialTheme.typography.titleLarge)
            Row(Modifier.padding(top = 16.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Each preset is (fl oz to save, label): "8 fl oz" ... or "250 ml" ... "1.5 L".
                Units.waterPresets(metric).forEach { (oz, label) ->
                    FilledTonalButton(onClick = { vm.addWater(day, oz); onDone() }, shape = CircleShape) {
                        Text(label)
                    }
                }
            }
            Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    custom, { custom = it }, Modifier.weight(1f), singleLine = true,
                    suffix = { Text(if (!metric) "fl oz" else if (inLiters) "L" else "ml") },
                    label = { Text(stringResource(R.string.amount)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), shape = MaterialTheme.shapes.medium
                )
                Button(
                    onClick = { custom.toNumberOrNull()?.takeIf { it > 0 }?.let { vm.addWater(day, Units.flOzFrom(it, metric, inLiters)); onDone() } },
                    shape = CircleShape, modifier = Modifier.padding(start = 8.dp)
                ) { Text(stringResource(R.string.add)) }
            }
            // Metric: choose whether the box above is in milliliters or liters (1 L = 1,000 ml).
            if (metric) Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(!inLiters, { inLiters = false }, label = { Text("ml") }, shape = CircleShape)
                FilterChip(inLiters, { inLiters = true }, label = { Text("L") }, shape = CircleShape)
            }
        }
    }
}

// Uses your latest weight for the estimate (170 lb if you never logged one).
// The estimate updates as you change activity or minutes, until you type your
// own calories (kcalEdited), which then stay as you typed them.
// filter(Char::isDigit) = the boxes accept only digits.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseSheet(vm: QuickAddViewModel, day: Long, onDone: () -> Unit) {
    val latest by vm.latestWeight.collectAsStateWithLifecycle()
    var activity by remember { mutableStateOf(Activity.WALK) }
    var minutes by remember { mutableStateOf("30") }
    var kcalText by remember { mutableStateOf("") }
    var kcalEdited by remember { mutableStateOf(false) }
    val lb = latest?.lb ?: 170.0
    val metric = LocalMetric.current
    val mins = minutes.toNumberOrNull()?.toInt() ?: 0
    val estimate = activity.estimate(mins, lb)
    LaunchedEffect(estimate) { if (!kcalEdited) kcalText = estimate.toString() }
    val label = stringResource(activity.labelRes)

    ModalBottomSheet(onDismissRequest = onDone, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp).imePadding()) {
            Text("🏃  " + stringResource(R.string.add_exercise), style = MaterialTheme.typography.titleLarge)
            Row(Modifier.padding(top = 14.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Activity.entries.forEach { a ->
                    FilterChip(activity == a, { activity = a; kcalEdited = false }, shape = CircleShape,
                        label = { Text("${a.emoji} ${stringResource(a.labelRes)}") })
                }
            }
            Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(minutes, { minutes = it.filter(Char::isDigit) }, Modifier.weight(1f), singleLine = true,
                    label = { Text(stringResource(R.string.minutes)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), shape = MaterialTheme.shapes.medium)
                OutlinedTextField(kcalText, { kcalText = it.filter(Char::isDigit); kcalEdited = true }, Modifier.weight(1f),
                    singleLine = true, label = { Text(stringResource(R.string.calories_burned)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), shape = MaterialTheme.shapes.medium)
            }
            Text(
                if (latest != null) stringResource(R.string.burn_estimate, fmtNum(activity.met), Units.weight(lb, metric))
                else stringResource(R.string.burn_estimate_default, fmtNum(activity.met), Units.weight(170.0, metric)),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )
            Button(
                onClick = {
                    val k = kcalText.toIntOrNull() ?: 0
                    if (mins > 0 && k >= 0) { vm.addExercise(day, activity, label, mins, k); onDone() }
                },
                enabled = mins > 0,
                shape = CircleShape, contentPadding = PaddingValues(vertical = 16.dp),
                modifier = Modifier.fillMaxWidth().padding(top = 18.dp)
            ) { Text(stringResource(R.string.save), style = MaterialTheme.typography.titleMedium) }
        }
    }
}

