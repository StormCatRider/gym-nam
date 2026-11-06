// ==============================================================================
// PLAIN-ENGLISH GUIDE
// WHAT THIS FILE IS: the brain of the Calendar page.
//  - month    = the month shown (starts on the current month).
//  - selected = the day you tapped (starts on today).
//  - monthInfo: for every day of the month, calories eaten vs that day's budget
//    (goal + exercise) and the worst mood logged. Used to color the days.
//  - detail:  everything about the selected day: foods by meal, totals, water,
//    exercise and mood check-ins.
// Everything is "live": if you log something, the calendar updates by itself.
// ==============================================================================
package com.gymnam.app.ui.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gymnam.app.data.ExerciseEntry
import com.gymnam.app.data.FoodDao
import com.gymnam.app.data.FoodEntry
import com.gymnam.app.data.Meal
import com.gymnam.app.data.MoodDao
import com.gymnam.app.data.MoodEntry
import com.gymnam.app.data.SettingsStore
import com.gymnam.app.data.TrackingDao
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.YearMonth

/** One day's dot on the calendar. worstMood = lowest mood level that day (1 = sick), or null. */
data class DayInfo(val kcal: Int, val budget: Int, val worstMood: Int?) {
    val logged get() = kcal > 0
    val over get() = logged && kcal > budget
}

/** Everything shown under the calendar for the selected day. */
data class DayDetail(
    val day: LocalDate,
    val byMeal: Map<Meal, List<FoodEntry>> = emptyMap(),
    val kcal: Int = 0, val protein: Double = 0.0, val carbs: Double = 0.0, val fat: Double = 0.0,
    val budget: Int = 0,
    val waterFlOz: Double = 0.0,
    val exercises: List<ExerciseEntry> = emptyList(),
    val moods: List<MoodEntry> = emptyList()
)

@OptIn(ExperimentalCoroutinesApi::class)
class CalendarViewModel(
    private val food: FoodDao,
    private val tracking: TrackingDao,
    private val moodDao: MoodDao,
    private val settings: SettingsStore
) : ViewModel() {
    val month = MutableStateFlow(YearMonth.now())
    val selected = MutableStateFlow(LocalDate.now())

    // For the month on screen: map of epochDay -> DayInfo.
    val monthInfo: StateFlow<Map<Long, DayInfo>> = month.flatMapLatest { ym ->
        val first = ym.atDay(1).toEpochDay()
        val last = ym.atEndOfMonth().toEpochDay()
        combine(food.dayTotals(first), tracking.burnedTotals(first), moodDao.between(first, last), settings.settings) { totals, burned, moods, s ->
            val burnedBy = burned.associate { it.epochDay to it.value.toInt() }
            val worst = moods.groupBy { it.epochDay }.mapValues { (_, l) -> l.minOf { it.mood } }
            (first..last).associateWith { d ->
                val t = totals.firstOrNull { it.epochDay == d }
                DayInfo(t?.kcal?.toInt() ?: 0, s.targets.calories + (burnedBy[d] ?: 0), worst[d])
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    val detail: StateFlow<DayDetail> = selected.flatMapLatest { d ->
        val e = d.toEpochDay()
        combine(food.entriesForDay(e), tracking.waterForDay(e), tracking.exerciseForDay(e), moodDao.forDay(e), settings.settings) { f, w, ex, m, s ->
            DayDetail(
                day = d, byMeal = f.groupBy { it.meal },
                kcal = f.sumOf { it.calories }, protein = f.sumOf { it.proteinG }, carbs = f.sumOf { it.carbsG }, fat = f.sumOf { it.fatG },
                budget = s.targets.calories + ex.sumOf { it.kcal },
                waterFlOz = w, exercises = ex, moods = m
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DayDetail(LocalDate.now()))

    /** Move one month back (-1) or forward (+1); never past the current month. */
    fun shiftMonth(by: Long) {
        val next = month.value.plusMonths(by)
        if (next <= YearMonth.now()) month.value = next
    }

    fun select(d: LocalDate) { if (!d.isAfter(LocalDate.now())) selected.value = d }
}
