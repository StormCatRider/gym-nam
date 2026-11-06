// ==============================================================================
// PLAIN-ENGLISH GUIDE
//
// WHAT THIS FILE IS: the brain of the first-time setup (and of "Edit my goals"
// in Settings). It remembers your answers step by step, checks they make sense,
// and at the end calculates and saves your goals (data/Profile.kt has the math).
//
// STEPS: 3 tour pages -> theme -> about you -> activity -> goal -> formula -> result.
// When opened from Settings the tour and theme pages are skipped.
// ==============================================================================
package com.gymnam.app.ui.setup

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gymnam.app.data.ActivityLevel
import com.gymnam.app.data.Formula
import com.gymnam.app.data.GoalCalculator
import com.gymnam.app.data.GoalResult
import com.gymnam.app.data.GoalType
import com.gymnam.app.data.Profile
import com.gymnam.app.data.SettingsStore
import com.gymnam.app.data.Sex
import com.gymnam.app.data.TrackingDao
import com.gymnam.app.data.Units
import com.gymnam.app.data.WeightEntry
import com.gymnam.app.data.fmtNum
import com.gymnam.app.ui.common.toNumberOrNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate

// UNITS = "Imperial (lb, ft/in) or Metric (kg, cm)?", asked right after the theme.
enum class Step { TOUR1, TOUR2, TOUR3, THEME, UNITS, ABOUT, ACTIVITY, GOAL, FORMULA, RESULT }

class SetupViewModel(
    private val settings: SettingsStore,
    private val tracking: TrackingDao,
    val withTour: Boolean
) : ViewModel() {

    // index = which page you're on (0 = first). "private set" = only this file can
    // change it, through next() and back().
    val steps: List<Step> = if (withTour) Step.entries else Step.entries.filter { it >= Step.ABOUT }
    var index by mutableStateOf(0)
        private set
    val step get() = steps[index]

    // Your answers. Text boxes (age, feet, inches, weight, body fat) are kept as text
    // exactly as typed and only turned into numbers when needed.
    var palette by mutableStateOf("mint")
    // true = metric. The boxes below hold text in the CHOSEN unit (kg or lb, cm or ft+in);
    // profile() converts to lb and inches, which is what the formulas and database use.
    var metric by mutableStateOf(false)
        private set
    var cm by mutableStateOf("")
    var sex by mutableStateOf(Sex.MALE)
    var age by mutableStateOf("")
    var feet by mutableStateOf("")
    var inches by mutableStateOf("")
    var weight by mutableStateOf("")
    var bodyFat by mutableStateOf("")
    var activity by mutableStateOf(ActivityLevel.MODERATE)
    var goal by mutableStateOf(GoalType.LOSE)
    var rate by mutableStateOf(1.0)
    var formula by mutableStateOf(Formula.MIFFLIN)
    var proteinPerLb by mutableStateOf(0.8f)

    // "init" runs once when this brain is created. If you already have a profile
    // (editing later), fill in your previous answers and your latest weigh-in.
    init {
        // Editing later: start from what you entered before, with your latest weigh-in.
        viewModelScope.launch {
            val s = settings.settings.first()
            palette = s.paletteId
            metric = s.metric
            s.profile?.let { p ->
                sex = p.sex; age = p.age.toString()
                fillHeight(p.heightIn)
                weight = fmtNum(Units.weightValue(p.weightLb, metric)); bodyFat = p.bodyFatPct?.let(::fmtNum).orEmpty()
                activity = p.activity; goal = p.goal; rate = if (p.rateLbWeek > 0) p.rateLbWeek else rate
                formula = p.formula; proteinPerLb = p.proteinPerLb.toFloat()
            }
            tracking.latestWeightNow()?.let { weight = fmtNum(Units.weightValue(it.lb, metric)) }
            if (s.profile == null) rate = defaultRate(goal)
        }
    }

    /** Puts a height (in inches) into the boxes of both units: 69 in -> "5" ft "9" in, and "175" cm. */
    private fun fillHeight(inchesTotal: Double) {
        feet = (inchesTotal.toInt() / 12).toString(); inches = fmtNum(Math.round((inchesTotal % 12) * 10) / 10.0)
        cm = Math.round(inchesTotal * Units.CM_PER_IN).toString()
    }

    // Height in inches from what you typed: cm / 2.54, or feet x 12 + inches.
    // Example: 5 ft 9 in = 69 in; 175 cm / 2.54 = 68.9 in.
    private val heightIn get() =
        if (metric) (cm.toNumberOrNull() ?: 0.0) / Units.CM_PER_IN
        else (feet.toNumberOrNull() ?: 0.0) * 12 + (inches.toNumberOrNull() ?: 0.0)

    // Weight in lb from what you typed (kg x 2.20462, or lb as is).
    private val weightLb get() = weight.toNumberOrNull()?.let { Units.lbFrom(it, metric) }

    /**
     * Switch units. Already-typed numbers are converted so nothing is lost
     * (e.g. 180 lb -> 81.6 kg, 5 ft 9 in -> 175 cm), and the choice is saved right away.
     */
    fun chooseMetric(on: Boolean) {
        if (on == metric) return
        val lb = weightLb
        val h = heightIn
        metric = on
        lb?.let { weight = fmtNum(Units.weightValue(it, on)) }
        if (h > 0) fillHeight(h)
        rate = defaultRate(goal)
        viewModelScope.launch { settings.setMetric(on) }
    }

    /** Starting weekly rate: lose 1 lb (or 0.5 kg), gain 0.5 lb (or 0.25 kg). Always stored in lb. */
    private fun defaultRate(g: GoalType): Double = when (g) {
        GoalType.LOSE -> if (metric) 0.5 * Units.LB_PER_KG else 1.0
        GoalType.GAIN -> if (metric) 0.25 * Units.LB_PER_KG else 0.5
        GoalType.MAINTAIN -> 0.0
    }

    // Builds the Profile from your answers, or null if something is out of range:
    //  age 16-100, weight 70-700 lb, height 48-96 in, body fat 3-60 %.
    // If you chose Katch-McArdle without body fat, it falls back to Mifflin-St Jeor.
    // Protein g/lb is cut to one decimal (0.83 -> 0.8).
    fun profile(): Profile? {
        val a = age.toNumberOrNull()?.toInt() ?: return null
        val w = weightLb ?: return null
        val h = heightIn
        if (a !in 16..100 || w !in 70.0..700.0 || h !in 48.0..96.0) return null
        val bf = bodyFat.toNumberOrNull()?.takeIf { it in 3.0..60.0 }
        return Profile(
            sex = sex, age = a, heightIn = h, weightLb = w, bodyFatPct = bf, activity = activity, goal = goal,
            rateLbWeek = if (goal == GoalType.MAINTAIN) 0.0 else rate,
            formula = if (formula == Formula.KATCH && bf == null) Formula.MIFFLIN else formula,
            proteinPerLb = (proteinPerLb * 10).toInt() / 10.0
        )
    }

    // The calculated goal shown on the last page (recomputed as you change answers).
    val result: GoalResult? get() = profile()?.let(GoalCalculator::calculate)

    // Is the Next button allowed on this page? (Only the about-you and formula pages have checks.)
    /** Can we leave this step? */
    fun valid(): Boolean = when (step) {
        Step.ABOUT -> (age.toNumberOrNull()?.toInt() ?: 0) in 16..100 &&
            (weightLb ?: 0.0) in 70.0..700.0 && heightIn in 48.0..96.0
        Step.FORMULA -> formula == Formula.MIFFLIN || (bodyFat.toNumberOrNull() ?: 0.0) in 3.0..60.0
        else -> true
    }

    fun next() { if (index < steps.lastIndex && valid()) index++ }
    fun back() { if (index > 0) index-- }

    fun choosePalette(id: String) { palette = id; viewModelScope.launch { settings.setPalette(id) } }

    // Picking a goal sets a sensible starting rate: lose 1 lb/week, gain 0.5 lb/week.
    fun chooseGoal(g: GoalType) {
        goal = g
        rate = defaultRate(g)
    }

    // Saves profile + goals and also logs today's weight, so the Progress chart has
    // a first point right away.
    /** Save targets; also logs today's weight so the Progress chart starts right away. */
    fun finish(onDone: () -> Unit) {
        val p = profile() ?: return
        val r = GoalCalculator.calculate(p)
        viewModelScope.launch {
            settings.saveProfile(p, r.targets)
            tracking.upsertWeight(WeightEntry(epochDay = LocalDate.now().toEpochDay(), lb = p.weightLb))
            onDone()
        }
    }
}
