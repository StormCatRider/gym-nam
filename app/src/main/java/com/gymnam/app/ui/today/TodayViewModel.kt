// ==============================================================================
// PLAIN-ENGLISH GUIDE
//
// WHAT THIS FILE IS: the brain of the Today tab. It gathers the day's food,
// water, exercise and goals into one "state" that TodayScreen draws, and it
// handles the actions you take there (delete a food, add water, change day...).
// It also computes your logging streak and the "update your goals?" suggestion.
//
// KOTLIN WORDS YOU'LL SEE:
//  - StateFlow = a live value the screen watches (redraws when it changes).
//  - "_day" (with underscore) = the private, changeable version; "day" = the
//    read-only copy the screen can see but not change.
//  - viewModelScope.launch { } = do this in the background, without freezing.
// ==============================================================================
package com.gymnam.app.ui.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gymnam.app.data.AppSettings
import com.gymnam.app.data.ExerciseEntry
import com.gymnam.app.data.FoodDao
import com.gymnam.app.data.MoodDao
import com.gymnam.app.data.MoodEntry
import com.gymnam.app.data.FoodEntry
import com.gymnam.app.data.GoalCalculator
import com.gymnam.app.data.Profile
import com.gymnam.app.data.Meal
import com.gymnam.app.data.MealItem
import com.gymnam.app.data.SavedDao
import com.gymnam.app.data.SavedMeal
import com.gymnam.app.data.SettingsStore
import com.gymnam.app.data.Targets
import com.gymnam.app.data.TrackingDao
import com.gymnam.app.data.WaterEntry
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

// The day's sums: calories and grams of protein, carbs and fat.
data class Totals(val calories: Int = 0, val protein: Double = 0.0, val carbs: Double = 0.0, val fat: Double = 0.0)

// oldLb = weight your goal was based on; trendLb = your 7-day average now.
data class GoalSuggestion(val oldLb: Double, val trendLb: Double, val profile: Profile, val targets: Targets)

// current = days in a row you logged food; best = your record.
data class Streak(val current: Int = 0, val best: Int = 0)

// Everything the Today screen needs, in one package.
data class TodayState(
    val day: LocalDate = LocalDate.now(),
    val byMeal: Map<Meal, List<FoodEntry>> = emptyMap(),
    val totals: Totals = Totals(),
    val targets: Targets = Targets(),
    val waterFlOz: Double = 0.0,
    val waterGoal: Int = 64,
    val exercises: List<ExerciseEntry> = emptyList(),
    val loaded: Boolean = false
) {
    val burned: Int get() = exercises.sumOf { it.kcal }
    // Your budget for the day = calorie goal + exercise calories burned.
    // Example: 2,240 + 300 = 2,540 kcal.
    /** Exercise adds to the day's budget. */
    val goal: Int get() = targets.calories + burned
}

// EXAMPLE: you logged Nov 1, 2, 3, then Nov 5 and today is Nov 5.
//   current: start at today (5) -> 4 is missing -> current = 1.
//   If today isn't logged yet, start at yesterday, so the morning doesn't show 0.
//   best: walk all days in order counting runs: 1-2-3 = 3 days -> best = 3.
/**
 * Current streak = consecutive days with food logged, ending today (or yesterday, so the
 * streak doesn't look broken first thing in the morning). Best = longest run ever.
 */
fun streaks(daysDesc: List<Long>, today: Long): Streak {
    if (daysDesc.isEmpty()) return Streak()
    val set = daysDesc.toHashSet()
    var current = 0
    var d = if (today in set) today else today - 1
    while (d in set) { current++; d-- }
    var best = 0; var run = 0; var prev: Long? = null
    daysDesc.sorted().forEach { day ->
        run = if (prev != null && day == prev!! + 1) run + 1 else 1
        best = maxOf(best, run); prev = day
    }
    return Streak(current, maxOf(best, current))
}

@OptIn(ExperimentalCoroutinesApi::class)
class TodayViewModel(
    private val dao: FoodDao,
    private val savedDao: SavedDao,
    private val tracking: TrackingDao,
    private val settings: SettingsStore,
    private val moodDao: MoodDao
) : ViewModel() {

    // The day being viewed (you can go back to past days with the arrows).
    private val _day = MutableStateFlow(LocalDate.now())
    val day: StateFlow<LocalDate> = _day.asStateFlow()

    // Whenever the viewed day changes, switch to that day's food + water + exercise.
    // flatMapLatest = "forget the old day's feed, follow the new one".
    private val dayData = _day.flatMapLatest { d ->
        val e = d.toEpochDay()
        combine(dao.entriesForDay(e), tracking.waterForDay(e), tracking.exerciseForDay(e)) { food, water, ex ->
            Triple(food, water, ex)
        }
    }

    // Combines the day's data with your settings into one TodayState.
    // groupBy { it.meal } sorts foods into Breakfast / Lunch / Dinner / Snack.
    // WhileSubscribed(5_000) = keep listening 5 s after the screen hides (e.g. a
    // rotation), then pause to save battery.
    val state: StateFlow<TodayState> = combine(dayData, settings.settings, _day) { data, s: AppSettings, d: LocalDate ->
        val (entries, water, ex) = data
        TodayState(
            day = d,
            byMeal = entries.groupBy { it.meal },
            totals = Totals(
                calories = entries.sumOf { it.calories },
                protein = entries.sumOf { it.proteinG },
                carbs = entries.sumOf { it.carbsG },
                fat = entries.sumOf { it.fatG }
            ),
            targets = s.targets,
            waterFlOz = water, waterGoal = s.waterGoalFlOz,
            exercises = ex,
            loaded = true
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TodayState())

    // Live streak, recalculated whenever a day gets its first or last food.
    // ---------- Mood log ("How do you feel?") ----------

    /** The viewed day's mood check-ins (live). */
    val moods: StateFlow<List<MoodEntry>> = _day.flatMapLatest { moodDao.forDay(it.toEpochDay()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * Foods you can mark as "this made me feel bad": the viewed day's foods (newest first),
     * then the day before's (a reaction can show up hours later). No repeats.
     */
    val suspectChoices: StateFlow<List<String>> = _day.flatMapLatest { d ->
        val e = d.toEpochDay()
        combine(dao.entriesForDay(e), dao.entriesForDay(e - 1)) { today, before ->
            (today.sortedByDescending { it.createdAt } + before.sortedByDescending { it.createdAt })
                .map { it.name }.distinct()
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Saves a check-in on the day being viewed (time = now). */
    fun addMood(mood: Int, symptoms: List<String>, foods: List<String>, note: String) = viewModelScope.launch {
        moodDao.insert(MoodEntry(
            epochDay = _day.value.toEpochDay(), mood = mood, symptoms = symptoms.joinToString(","),
            foods = foods.joinToString("\n"), note = note.trim()
        ))
    }

    fun deleteMood(m: MoodEntry) = viewModelScope.launch { moodDao.delete(m) }
    fun restoreMood(m: MoodEntry) = viewModelScope.launch { moodDao.insert(m) }

    val streak: StateFlow<Streak> = dao.loggedDays().map { streaks(it, LocalDate.now().toEpochDay()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Streak())

    // TREND = average of your weigh-ins from the last 7 days, rounded to 0.1 lb.
    // Example: 180, 179.4, 178.8 ... average 174.6 -> 5.4 lb away from the 180 lb your
    // goals were based on -> suggest recalculating with 174.6 lb.
    // No suggestion if: no profile, no recent weights, less than 5 lb change, or you
    // already said "not now" within 5 lb of this weight.
    /**
     * Suggest new goals when your 7-day trend weight is 5+ lb away from the weight your goals were
     * calculated with (and you haven't said "not now" at about this weight).
     */
    val suggestion: StateFlow<GoalSuggestion?> = combine(
        tracking.weightsSince(LocalDate.now().toEpochDay() - 6), settings.settings
    ) { w, s ->
        val p = s.profile ?: return@combine null
        if (w.isEmpty()) return@combine null
        val trend = Math.round(w.sumOf { it.lb } / w.size * 10) / 10.0
        val dismissed = s.dismissedSuggestionLb
        if (kotlin.math.abs(trend - p.weightLb) < 5 || (dismissed != null && kotlin.math.abs(trend - dismissed) < 5)) null
        else {
            val np = p.copy(weightLb = trend)
            GoalSuggestion(p.weightLb, trend, np, GoalCalculator.calculate(np).targets)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    // "Update" saves the new profile/goals; "Not now" remembers this weight so it doesn't nag.
    fun applySuggestion(g: GoalSuggestion) = viewModelScope.launch { settings.saveProfile(g.profile, g.targets) }
    fun dismissSuggestion(g: GoalSuggestion) = viewModelScope.launch { settings.dismissSuggestion(g.trendLb) }

    private var lastToday: LocalDate = LocalDate.now()

    // If the app stayed open past midnight while showing "today", move to the new
    // today automatically. If you were viewing an older day on purpose, stay there.
    /** Called when the screen resumes: if you were looking at "today" and the date changed, follow it. */
    fun onResume() {
        val now = LocalDate.now()
        if (now != lastToday) {
            if (_day.value == lastToday) _day.value = now
            lastToday = now
        }
    }

    // Day arrows. You can't go into the future (nextDay stops at today).
    fun previousDay() { _day.value = _day.value.minusDays(1) }
    fun nextDay() { if (_day.value < LocalDate.now()) _day.value = _day.value.plusDays(1) }
    fun goToToday() { _day.value = LocalDate.now() }
    fun goTo(date: LocalDate) { _day.value = minOf(date, LocalDate.now()) }

    // delete + restore = swipe to delete, with an "Undo" that puts it back.
    fun delete(entry: FoodEntry) = viewModelScope.launch { dao.delete(entry) }
    fun restore(entry: FoodEntry) = viewModelScope.launch { dao.insert(entry) }

    // Each tap adds a new water row; undo removes the most recent one.
    fun addWater(flOz: Double) = viewModelScope.launch {
        tracking.insertWater(WaterEntry(epochDay = _day.value.toEpochDay(), flOz = flOz))
    }
    fun undoWater() = viewModelScope.launch { tracking.undoLastWater(_day.value.toEpochDay()) }

    fun deleteExercise(e: ExerciseEntry) = viewModelScope.launch { tracking.deleteExercise(e) }
    fun restoreExercise(e: ExerciseEntry) = viewModelScope.launch { tracking.insertExercise(e) }

    // Saves all foods of one meal section as a "My meal". If you leave the name
    // empty it uses the meal name, e.g. "Breakfast".
    /** Saves this meal's foods as a reusable "My meal". */
    fun saveMeal(name: String, meal: Meal, entries: List<FoodEntry>) = viewModelScope.launch {
        if (entries.isEmpty()) return@launch
        savedDao.insertMeal(
            SavedMeal(
                name = name.trim().ifBlank { meal.name.lowercase().replaceFirstChar { it.uppercase() } },
                emoji = entries.first().emoji,
                itemsJson = MealItem.toJson(entries.map(MealItem::fromEntry))
            )
        )
    }
}
