// ==============================================================================
// PLAIN-ENGLISH GUIDE
//
// WHAT THIS FILE IS: the brain of the Progress tab: weight chart with the 7-day
// trend line, calories per day vs goal, averages, streak, and day-by-day history,
// for the range you choose (week / month / 3 months / year).
// ==============================================================================
package com.gymnam.app.ui.progress

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gymnam.app.data.DayTotal
import com.gymnam.app.data.FoodDao
import com.gymnam.app.data.SettingsStore
import com.gymnam.app.data.TrackingDao
import com.gymnam.app.data.WeightEntry
import com.gymnam.app.ui.today.Streak
import com.gymnam.app.ui.today.streaks
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate

// The range buttons and how many days each covers.
enum class Range(val days: Int) { WEEK(7), MONTH(30), QUARTER(90), YEAR(365) }

// One dot on a chart: a day and a value.
data class Point(val day: Long, val value: Double)

// One line of the history list: calories, protein, exercise, number of foods.
data class HistoryRow(val day: Long, val kcal: Int, val protein: Int, val burned: Int, val items: Int)

// Everything the Progress screen shows.
data class ProgressState(
    val range: Range = Range.MONTH,
    val from: Long = 0,
    val to: Long = 0,
    val weights: List<Point> = emptyList(),
    val trend: List<Point> = emptyList(),
    val latestLb: Double? = null,
    val trendLb: Double? = null,
    /** Trend at the end of the range minus trend at the start (lb). */
    val changeLb: Double? = null,
    /** Calories eaten per day for every day in range (0 = nothing logged). */
    val kcalByDay: List<Point> = emptyList(),
    val goal: Int = 2000,
    val avgKcal: Int = 0,
    val avgProtein: Int = 0,
    val avgWater: Int = 0,
    val waterGoal: Int = 64,
    val daysLogged: Int = 0,
    val streak: Streak = Streak(),
    val history: List<HistoryRow> = emptyList()
)

// FORMULA: trend(day) = average of all weigh-ins from (day - 6) to day.
// Example: weigh-ins 181, 180, 182, 179 within the same week ->
// trend on the 4th day = (181 + 180 + 182 + 179) / 4 = 180.5 lb.
// Daily weight jumps 2-4 lb from water and food; the trend shows the real direction.
/**
 * 7-day trend: for each weigh-in, the average of all weigh-ins in the 7 days ending that day.
 * Daily weight swings 2–4 lb from water and food; the trend shows the real direction.
 */
fun movingAverage(weights: List<WeightEntry>): List<Point> = weights.map { w ->
    val window = weights.filter { it.epochDay in (w.epochDay - 6)..w.epochDay }
    Point(w.epochDay, window.sumOf { it.lb } / window.size)
}

@OptIn(ExperimentalCoroutinesApi::class)
class ProgressViewModel(
    foodDao: FoodDao,
    tracking: TrackingDao,
    settings: SettingsStore
) : ViewModel() {

    val range = MutableStateFlow(Range.MONTH)

    // When you change the range, reload everything for it. from = first day shown.
    // Weights are loaded 6 extra days earlier so the first trend point already
    // averages a full week.
    val state: StateFlow<ProgressState> = range.flatMapLatest { r ->
        val today = LocalDate.now().toEpochDay()
        val from = today - (r.days - 1)
        combine(
            tracking.weightsSince(from - 6),   // 6 extra days so the first trend point is a full week
            foodDao.dayTotals(from),
            tracking.burnedTotals(from),
            tracking.waterTotals(from),
            combine(settings.settings, foodDao.loggedDays()) { s, d -> s to d }
        ) { weights, totals, burned, water, sd ->
            val (s, logged) = sd
            build(r, from, today, weights, totals, burned.associate { it.epochDay to it.value.toInt() },
                water, s.targets.calories, s.waterGoalFlOz, streaks(logged, today))
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProgressState())

    // Averages count only days you logged food (empty days would make them look low).
    // changeLb = trend at the end - trend at the start of the range
    // (negative = you lost weight). Days with nothing logged show 0 on the chart.
    private fun build(
        r: Range, from: Long, today: Long, weights: List<WeightEntry>, totals: List<DayTotal>,
        burned: Map<Long, Int>, water: List<com.gymnam.app.data.DayValue>, goal: Int, waterGoal: Int, streak: Streak
    ): ProgressState {
        val trendAll = movingAverage(weights)
        val inRange = weights.filter { it.epochDay >= from }
        val trend = trendAll.filter { it.day >= from }
        val byDay = totals.associateBy { it.epochDay }
        val logged = totals.filter { it.items > 0 }
        return ProgressState(
            range = r, from = from, to = today,
            weights = inRange.map { Point(it.epochDay, it.lb) },
            trend = trend,
            latestLb = weights.lastOrNull()?.lb,
            trendLb = trendAll.lastOrNull()?.value,
            changeLb = if (trend.size >= 2) trend.last().value - trend.first().value else null,
            kcalByDay = (from..today).map { d -> Point(d, byDay[d]?.kcal ?: 0.0) },
            goal = goal,
            avgKcal = if (logged.isEmpty()) 0 else (logged.sumOf { it.kcal } / logged.size).toInt(),
            avgProtein = if (logged.isEmpty()) 0 else (logged.sumOf { it.protein } / logged.size).toInt(),
            avgWater = if (water.isEmpty()) 0 else (water.sumOf { it.value } / water.size).toInt(),
            waterGoal = waterGoal,
            daysLogged = logged.size,
            streak = streak,
            history = logged.sortedByDescending { it.epochDay }.map {
                HistoryRow(it.epochDay, it.kcal.toInt(), it.protein.toInt(), burned[it.epochDay] ?: 0, it.items)
            }
        )
    }
}
