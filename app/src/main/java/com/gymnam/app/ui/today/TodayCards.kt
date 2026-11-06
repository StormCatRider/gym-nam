// ==============================================================================
// PLAIN-ENGLISH GUIDE
//
// WHAT THIS FILE IS: two cards on the Today screen.
//  - WaterCard: glasses (8 fl oz each), a blue progress bar, and +8 / +12 / +16
//    fl oz buttons. Example: goal 64 fl oz -> 8 glasses.
//  - ExerciseCard: today's workouts and the total calories they added.
//    Long-press a workout to delete it.
// ==============================================================================
package com.gymnam.app.ui.today

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.gymnam.app.R
import com.gymnam.app.data.LocalMetric
import com.gymnam.app.data.Units
import com.gymnam.app.data.ExerciseEntry
import com.gymnam.app.ui.common.EmojiBadge
import com.gymnam.app.ui.common.SoftCard
import kotlin.math.roundToInt

private val WaterBlue = Color(0xFF8EC5EC)

// frac = water drunk / goal (0 to 1) -> how full the blue bar is, animated.
/** Water: one tap adds a glass. Glasses are 8 fl oz each, so a 64 fl oz goal = 8 glasses. */
@Composable
fun WaterCard(flOz: Double, goal: Int, onAdd: (Double) -> Unit, onUndo: () -> Unit) {
    val metric = LocalMetric.current
    val frac by animateFloatAsState((flOz / goal.coerceAtLeast(1)).toFloat().coerceIn(0f, 1f), tween(700), label = "water")
    SoftCard(padding = 16.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("💧", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.width(10.dp))
            Text(stringResource(R.string.water), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text(stringResource(R.string.water_of_goal, Units.water(flOz, metric), Units.water(goal.toDouble(), metric)), style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (flOz > 0) IconButton(onClick = onUndo) {
                Icon(Icons.AutoMirrored.Filled.Undo, stringResource(R.string.undo), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        // Glasses
        // Number of glass icons = goal / 8 fl oz (between 1 and 16). Full ones show 🥛.
        // One glass = 8 fl oz (imperial) or 250 ml (metric).
        val glass = Units.glassFlOz(metric)
        val glasses = (goal / glass).roundToInt().coerceIn(1, 16)
        val full = (flOz / glass).toInt()
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            repeat(glasses) { i -> Text(if (i < full) "🥛" else "◌", style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        Box(Modifier.fillMaxWidth().height(8.dp).clip(CircleShape).background(WaterBlue.copy(alpha = 0.25f))) {
            Box(Modifier.fillMaxWidth(frac).fillMaxHeight().clip(CircleShape).background(WaterBlue))
        }
        Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(if (metric) "ml" else "fl oz", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            // Quick buttons: +8/+12/+16 fl oz, or +250/+330/+500 ml (saved as fl oz either way).
            (if (metric) listOf(250, 330, 500) else listOf(8, 12, 16)).forEach { n ->
                FilledTonalButton(onClick = { onAdd(Units.flOzFrom(n.toDouble(), metric)) }, shape = CircleShape) { Text("+$n") }
            }
        }
    }
}

// Shows each entry with its emoji, minutes, and "+kcal". Entries imported from
// Health Connect are labeled "Health Connect".
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ExerciseCard(list: List<ExerciseEntry>, onAdd: () -> Unit, onLongPress: (ExerciseEntry) -> Unit) {
    SoftCard(Modifier.animateContentSize(), padding = 16.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("🏃", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.width(10.dp))
            Text(stringResource(R.string.exercise), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            if (list.isNotEmpty()) Text(stringResource(R.string.burned_kcal, list.sumOf { it.kcal }),
                style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            IconButton(onClick = onAdd) { Icon(Icons.Filled.Add, stringResource(R.string.add), tint = MaterialTheme.colorScheme.primary) }
        }
        if (list.isEmpty()) Text(stringResource(R.string.exercise_empty), style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp, bottom = 4.dp))
        list.forEach { e ->
            Row(
                Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium)
                    .combinedClickable(onClick = {}, onLongClick = { onLongPress(e) })
                    .padding(vertical = 8.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                EmojiBadge(e.emoji)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(e.name, style = MaterialTheme.typography.bodyLarge)
                    if (e.minutes > 0) Text(stringResource(R.string.minutes_short, e.minutes), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (e.source == "hc") Text("Health Connect", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text("+${e.kcal} kcal", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}
