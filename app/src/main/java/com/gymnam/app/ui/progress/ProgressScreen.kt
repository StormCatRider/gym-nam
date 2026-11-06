// ==============================================================================
// PLAIN-ENGLISH GUIDE
//
// WHAT THIS FILE IS: the Progress tab, top to bottom:
//  1. Range buttons: 7 days / 30 days / 90 days / 1 year.
//  2. Streak card: current 🔥 streak, best streak, days logged.
//  3. Weight card: latest, trend, change over the range, chart, "Log weight".
//  4. Calories card: chart, average calories and protein vs goal.
//  5. Water card: daily average vs goal.
//  6. History: one row per logged day (tap it to open that day on Today).
// The numbers come from ProgressViewModel.kt.
// ==============================================================================
package com.gymnam.app.ui.progress

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gymnam.app.R
import com.gymnam.app.data.fmtNum
import com.gymnam.app.ui.common.SoftCard
import com.gymnam.app.ui.picker.Header
import com.gymnam.app.ui.theme.macroColors
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun ProgressScreen(
    vm: ProgressViewModel,
    contentPadding: PaddingValues,
    onLogWeight: () -> Unit,
    onOpenDay: (LocalDate) -> Unit
) {
    val s by vm.state.collectAsStateWithLifecycle()
    val range by vm.range.collectAsStateWithLifecycle()
    val over = macroColors(isSystemInDarkTheme()).over
    val dateFmt = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)

    LazyColumn(
        contentPadding = PaddingValues(
            start = 16.dp, end = 16.dp,
            top = contentPadding.calculateTopPadding() + 8.dp,
            bottom = contentPadding.calculateBottomPadding() + 24.dp
        ),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text(stringResource(R.string.tab_progress), style = MaterialTheme.typography.headlineMedium)
            Row(Modifier.padding(top = 10.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Range.entries.forEach { r ->
                    FilterChip(range == r, { vm.range.value = r }, shape = CircleShape, label = {
                        Text(stringResource(when (r) {
                            Range.WEEK -> R.string.range_week; Range.MONTH -> R.string.range_month
                            Range.QUARTER -> R.string.range_quarter; Range.YEAR -> R.string.range_year
                        }))
                    })
                }
            }
        }

        // Streak
        item {
            SoftCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("🔥", style = MaterialTheme.typography.displaySmall)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(pluralStringResource(R.plurals.streak_days, s.streak.current, s.streak.current),
                            style = MaterialTheme.typography.titleLarge)
                        Text(stringResource(R.string.best_streak, s.streak.best), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("${s.daysLogged}/${s.range.days}", style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(R.string.days_logged), style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        // Weight
        item {
            SoftCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("⚖️  " + stringResource(R.string.weight), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    FilledTonalButton(onClick = onLogWeight, shape = CircleShape) {
                        Icon(Icons.Filled.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.log_weight))
                    }
                }
                if (s.latestLb == null) {
                    Text(stringResource(R.string.weight_empty), style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
                } else {
                    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Stat(stringResource(R.string.latest), "${fmtNum(s.latestLb!!)} lb")
                        s.trendLb?.let { Stat(stringResource(R.string.trend_7d), "${fmtNum((it * 10).roundToInt() / 10.0)} lb") }
                        s.changeLb?.let {
                            val v = (it * 10).roundToInt() / 10.0
                            Stat(stringResource(R.string.change), (if (v > 0) "+" else if (v < 0) "−" else "") + "${fmtNum(abs(v))} lb")
                        }
                    }
                    WeightChart(s.weights, s.trend, s.from, s.to)
                    Text(stringResource(R.string.trend_explain), style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
                }
            }
        }

        // Calories
        item {
            SoftCard {
                Text("🔥  " + stringResource(R.string.calories_title), style = MaterialTheme.typography.titleMedium)
                Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Stat(stringResource(R.string.avg_per_day), stringResource(R.string.kcal_value, s.avgKcal))
                    Stat(stringResource(R.string.goal), stringResource(R.string.kcal_value, s.goal))
                    Stat(stringResource(R.string.avg_protein), "${s.avgProtein} g")
                }
                CaloriesChart(s.kcalByDay, s.goal, over)
                if (s.range.days > 60) Text(stringResource(R.string.weekly_avg_note), style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
            }
        }

        // Water
        item {
            SoftCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("💧  " + stringResource(R.string.water), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    Text(stringResource(R.string.water_avg, s.avgWater, s.waterGoal), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        // History
        if (s.history.isNotEmpty()) {
            item { Header(stringResource(R.string.history)) }
            items(s.history, key = { it.day }) { h ->
                Surface(
                    onClick = { onOpenDay(LocalDate.ofEpochDay(h.day)) }, shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()
                ) {
                    Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(LocalDate.ofEpochDay(h.day).format(dateFmt), style = MaterialTheme.typography.bodyLarge)
                            Text(
                                buildList {
                                    add(stringResource(R.string.protein_short, h.protein))
                                    if (h.burned > 0) add("🏃 +${h.burned}")
                                }.joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            stringResource(R.string.kcal_value, h.kcal), style = MaterialTheme.typography.labelLarge,
                            color = if (h.kcal > s.goal + h.burned) over else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

// A small label + big number pair, e.g. "Avg / day" over "2,105".
@Composable
private fun Stat(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium)
    }
}
