// ==============================================================================
// PLAIN-ENGLISH GUIDE
// WHAT THIS FILE IS: the Calendar page (📅 button on Today). Shows what you ate on
// any past day.
//  - The month grid: each day has a small dot:
//        accent color = you logged food and stayed within your budget
//        red          = you went over your budget (goal + exercise)
//        no dot       = nothing logged
//    and a tiny 😕/🤢 if you logged feeling bad that day.
//  - Tap a day to see its foods (by meal), totals, water, exercise and moods below.
//  - "Open this day" jumps to Today showing that day, where you can edit it.
// The brain is CalendarViewModel.kt.
// ==============================================================================
package com.gymnam.app.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gymnam.app.R
import com.gymnam.app.data.LocalMetric
import com.gymnam.app.data.Meal
import com.gymnam.app.data.Mood
import com.gymnam.app.data.Units
import com.gymnam.app.data.rememberFlags
import com.gymnam.app.ui.common.FlagChips
import com.gymnam.app.ui.common.SoftCard
import com.gymnam.app.ui.common.emoji
import com.gymnam.app.ui.common.formatAmount
import com.gymnam.app.ui.common.labelRes
import com.gymnam.app.ui.nav.InnerTopBar
import com.gymnam.app.ui.theme.isAppDark
import com.gymnam.app.ui.theme.macroColors
import com.gymnam.app.ui.today.moodLabel
import java.text.NumberFormat
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Locale

@Composable
fun CalendarScreen(vm: CalendarViewModel, contentPadding: PaddingValues, onBack: () -> Unit, onOpenDay: (LocalDate) -> Unit) {
    val month by vm.month.collectAsStateWithLifecycle()
    val selected by vm.selected.collectAsStateWithLifecycle()
    val info by vm.monthInfo.collectAsStateWithLifecycle()
    val d by vm.detail.collectAsStateWithLifecycle()
    val over = macroColors(isAppDark()).over
    val nf = remember { NumberFormat.getIntegerInstance() }
    val metric = LocalMetric.current

    Column(Modifier.fillMaxSize()) {
        InnerTopBar(stringResource(R.string.calendar_title), onBack)
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = contentPadding.calculateBottomPadding() + 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                SoftCard(padding = 12.dp) {
                    // Month name with < > arrows (can't go past this month).
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { vm.shiftMonth(-1) }) {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, stringResource(R.string.prev_month))
                        }
                        Text(
                            month.month.getDisplayName(TextStyle.FULL_STANDALONE, Locale.getDefault())
                                .replaceFirstChar { it.titlecase(Locale.getDefault()) } + " " + month.year,
                            style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center, modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { vm.shiftMonth(1) }, enabled = month < YearMonth.now()) {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, stringResource(R.string.next_month))
                        }
                    }
                    MonthGrid(month, selected, info, over, onSelect = vm::select)
                    // Legend
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically) {
                        Dot(MaterialTheme.colorScheme.primary); Text(" " + stringResource(R.string.cal_within), style = MaterialTheme.typography.labelSmall)
                        Spacer(Modifier.width(14.dp))
                        Dot(over); Text(" " + stringResource(R.string.cal_over), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            // ----- The selected day -----
            item {
                SoftCard {
                    val today = LocalDate.now()
                    Text(
                        when (d.day) {
                            today -> stringResource(R.string.today)
                            today.minusDays(1) -> stringResource(R.string.yesterday)
                            else -> d.day.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL))
                        },
                        style = MaterialTheme.typography.titleMedium
                    )
                    if (d.kcal == 0 && d.moods.isEmpty() && d.exercises.isEmpty()) {
                        Text(stringResource(R.string.cal_nothing), style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
                    } else {
                        Text(
                            stringResource(R.string.cal_kcal_of, nf.format(d.kcal), nf.format(d.budget)),
                            style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold,
                            color = if (d.kcal > d.budget) over else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                        Text(
                            "${stringResource(R.string.protein)} ${d.protein.toInt()} g · ${stringResource(R.string.carbs)} ${d.carbs.toInt()} g · " +
                                "${stringResource(R.string.fat)} ${d.fat.toInt()} g · 💧 ${Units.water(d.waterFlOz, metric)}",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    FilledTonalButton(onClick = { onOpenDay(d.day) }, shape = CircleShape, modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
                        Text(stringResource(R.string.cal_open_day))
                    }
                }
            }

            // Foods by meal
            Meal.entries.forEach { meal ->
                val list = d.byMeal[meal].orEmpty()
                if (list.isNotEmpty()) item(key = "meal-${meal.name}-${d.day}") {
                    SoftCard(padding = 14.dp) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(meal.emoji() + "  " + stringResource(meal.labelRes()), style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.weight(1f))
                            Text(nf.format(list.sumOf { it.calories }) + " kcal", style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        list.forEach { e ->
                            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(e.emoji, fontSize = 18.sp)
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(e.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    FlagChips(rememberFlags(e.name))
                                    Text(formatAmount(e.amount, e.unit), style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Text(nf.format(e.calories), style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                }
            }

            // Exercise and moods
            if (d.exercises.isNotEmpty() || d.moods.isNotEmpty()) item(key = "extra-${d.day}") {
                SoftCard(padding = 14.dp) {
                    d.exercises.forEach { ex ->
                        Text("${ex.emoji}  ${ex.name} · ${ex.minutes} min · +${ex.kcal} kcal", style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(vertical = 3.dp))
                    }
                    d.moods.forEach { m ->
                        val foods = if (m.foodList.isEmpty()) "" else " · " + stringResource(R.string.mood_from, m.foodList.joinToString(", "))
                        Text("${m.moodEnum.emoji}  ${moodLabel(m.moodEnum)}$foods", style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(vertical = 3.dp))
                    }
                }
            }
        }
    }
}

/**
 * The month grid: 7 columns (weeks start on Sunday or Monday depending on your phone's
 * region), one row per week. Blank cells pad the first week.
 */
@Composable
private fun MonthGrid(month: YearMonth, selected: LocalDate, info: Map<Long, DayInfo>, over: Color, onSelect: (LocalDate) -> Unit) {
    val firstDow = remember { WeekFields.of(Locale.getDefault()).firstDayOfWeek }
    val days = (0..6).map { firstDow.plus(it.toLong()) }
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        days.forEach { dow: DayOfWeek ->
            Text(dow.getDisplayName(TextStyle.NARROW_STANDALONE, Locale.getDefault()), style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
        }
    }
    // How many blank cells before day 1 (e.g. month starts on Wednesday, week starts Sunday -> 3).
    val lead = ((month.atDay(1).dayOfWeek.value - firstDow.value) + 7) % 7
    val total = lead + month.lengthOfMonth()
    val today = LocalDate.now()
    (0 until (total + 6) / 7).forEach { week ->
        Row(Modifier.fillMaxWidth()) {
            (0..6).forEach { col ->
                val n = week * 7 + col - lead + 1
                Box(Modifier.weight(1f).aspectRatio(1f).padding(2.dp), contentAlignment = Alignment.Center) {
                    if (n in 1..month.lengthOfMonth()) {
                        val date = month.atDay(n)
                        val di = info[date.toEpochDay()]
                        val isSel = date == selected
                        val future = date.isAfter(today)
                        Column(
                            Modifier.fillMaxSize().clip(CircleShape)
                                .then(if (isSel) Modifier.background(MaterialTheme.colorScheme.primary) else Modifier)
                                .then(if (date == today && !isSel) Modifier.border(1.5.dp, MaterialTheme.colorScheme.primary, CircleShape) else Modifier)
                                .clickable(enabled = !future) { onSelect(date) },
                            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                n.toString(), style = MaterialTheme.typography.bodyMedium,
                                color = when {
                                    isSel -> MaterialTheme.colorScheme.onPrimary
                                    future -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                    else -> MaterialTheme.colorScheme.onSurface
                                }
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (di?.logged == true) Dot(if (di.over) over else if (isSel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary)
                                // Only "bad" moods get a mark, so the grid stays calm.
                                di?.worstMood?.takeIf { it <= 2 }?.let { Text(Mood.of(it).emoji, fontSize = 8.sp) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Dot(color: Color) {
    Box(Modifier.size(6.dp).clip(CircleShape).background(color))
}
