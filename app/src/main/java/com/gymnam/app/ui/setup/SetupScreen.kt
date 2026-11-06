// ==============================================================================
// PLAIN-ENGLISH GUIDE
//
// WHAT THIS FILE IS: the setup screens (first launch, or "Edit my goals" later).
// Dots at the top show progress; Back / Next buttons at the bottom.
//  TOUR1-3  three welcome pages (logging, fridge, privacy)
//  THEME    pick a palette (applies instantly)
//  ABOUT    sex, age, height (ft + in), weight (lb)
//  ACTIVITY sedentary ... very active
//  GOAL     lose / maintain / gain and how fast (lb per week)
//  FORMULA  Mifflin-St Jeor or Katch-McArdle (needs body fat %), protein g/lb
//  RESULT   your goal with all the math shown step by step
// The brain is SetupViewModel.kt; the formulas are in data/Profile.kt.
// ==============================================================================
package com.gymnam.app.ui.setup

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gymnam.app.R
import com.gymnam.app.data.ActivityLevel
import com.gymnam.app.data.Formula
import com.gymnam.app.data.GoalResult
import com.gymnam.app.data.GoalType
import com.gymnam.app.data.Profile
import com.gymnam.app.data.Sex
import com.gymnam.app.data.fmtNum
import com.gymnam.app.ui.common.SoftCard
import com.gymnam.app.ui.theme.Palettes
import com.gymnam.app.ui.theme.isAppDark
import com.gymnam.app.data.Units
import java.text.NumberFormat

// onCancel = null on first launch (you can't skip setup); from Settings it's a
// "Cancel" button. AnimatedContent slides between pages.
@Composable
fun SetupScreen(vm: SetupViewModel, onDone: () -> Unit, onCancel: (() -> Unit)?) {
    val haptics = LocalHapticFeedback.current
    // Surface (not just a background color) also sets the TEXT color for everything inside.
    // Before, texts here defaulted to black, which was hard to read in night mode.
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
    Column(Modifier.fillMaxSize().systemBarsPadding().imePadding()) {
        // Progress dots
        Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.Center) {
            vm.steps.indices.forEach { i ->
                Box(
                    Modifier.padding(3.dp).size(if (i == vm.index) 22.dp else 8.dp, 8.dp).clip(CircleShape)
                        .background(if (i <= vm.index) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer)
                )
            }
        }
        AnimatedContent(
            targetState = vm.index, modifier = Modifier.weight(1f),
            transitionSpec = {
                val dir = if (targetState > initialState) 1 else -1
                (slideInHorizontally { it * dir / 3 } + fadeIn()) togetherWith (slideOutHorizontally { -it * dir / 3 } + fadeOut())
            }, label = "setup"
        ) { i ->
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                when (vm.steps[i]) {
                    Step.TOUR1 -> Tour("🍽️", R.string.tour1_title, R.string.tour1_body)
                    Step.TOUR2 -> Tour("🧊", R.string.tour2_title, R.string.tour2_body)
                    Step.TOUR3 -> Tour("🔒", R.string.tour3_title, R.string.tour3_body)
                    Step.THEME -> ThemeStep(vm)
                    Step.UNITS -> UnitsStep(vm)
                    Step.ABOUT -> AboutStep(vm)
                    Step.ACTIVITY -> ActivityStep(vm)
                    Step.GOAL -> GoalStep(vm)
                    Step.FORMULA -> FormulaStep(vm)
                    Step.RESULT -> ResultStep(vm.profile(), vm.result, vm.metric)
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            when {
                vm.index > 0 -> TextButton(onClick = vm::back) { Text(stringResource(R.string.back)) }
                onCancel != null -> TextButton(onClick = onCancel) { Text(stringResource(R.string.cancel)) }
            }
            Spacer(Modifier.weight(1f))
            val last = vm.step == Step.RESULT
            Button(
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    if (last) vm.finish(onDone) else vm.next()
                },
                enabled = vm.valid() && (!last || vm.profile() != null),
                shape = CircleShape, contentPadding = PaddingValues(horizontal = 28.dp, vertical = 14.dp)
            ) { Text(stringResource(if (last) R.string.start else R.string.next), style = MaterialTheme.typography.titleMedium) }
        }
    }
    }
}

// One welcome page: big emoji, title and text.
@Composable
private fun ColumnScope.Tour(emoji: String, title: Int, body: Int) {
    Spacer(Modifier.height(40.dp))
    Surface(Modifier.size(140.dp).align(Alignment.CenterHorizontally), shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
        Box(contentAlignment = Alignment.Center) { Text(emoji, fontSize = 64.sp) }
    }
    Spacer(Modifier.height(16.dp))
    Text(stringResource(title), style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    Text(stringResource(body), style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.fillMaxWidth())
}

@Composable
private fun StepTitle(title: Int, subtitle: Int? = null) {
    Text(stringResource(title), style = MaterialTheme.typography.headlineMedium)
    subtitle?.let { Text(stringResource(it), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
}

// Palette circles; tapping one recolors the app immediately.
@Composable
private fun ThemeStep(vm: SetupViewModel) {
    val dark = isAppDark()
    StepTitle(R.string.setup_theme_title, R.string.setup_theme_body)
    Palettes.forEach { p ->
        val sel = vm.palette == p.id
        Surface(
            onClick = { vm.choosePalette(p.id) }, shape = MaterialTheme.shapes.large,
            color = if (dark) p.cardDark else p.bgLight,
            border = if (sel) BorderStroke(3.dp, if (dark) p.accentDark else p.accentLight) else null,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(36.dp).clip(CircleShape).background(if (dark) p.accentDark else p.accentLight))
                Spacer(Modifier.width(14.dp))
                Text(stringResource(p.nameRes), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f),
                    color = if (dark) MaterialTheme.colorScheme.onSurface else androidx.compose.ui.graphics.Color(0xFF2B2A33))
                if (sel) Text("✓", style = MaterialTheme.typography.titleLarge)
            }
        }
    }
}

// Number boxes; Next stays disabled until age, height and weight are in range.
// The units question: two big choices. Picking one saves it right away (also changeable in Settings).
@Composable
private fun UnitsStep(vm: SetupViewModel) {
    StepTitle(R.string.setup_units_title, R.string.setup_units_body)
    Choice(!vm.metric, { vm.chooseMetric(false) }, "🇺🇸  " + stringResource(R.string.units_imperial), stringResource(R.string.units_imperial_d))
    Choice(vm.metric, { vm.chooseMetric(true) }, "🌍  " + stringResource(R.string.units_metric), stringResource(R.string.units_metric_d))
    Text(stringResource(R.string.units_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun AboutStep(vm: SetupViewModel) {
    val num = KeyboardOptions(keyboardType = KeyboardType.Decimal)
    StepTitle(R.string.setup_about_title, R.string.setup_about_body)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(vm.sex == Sex.MALE, { vm.sex = Sex.MALE }, shape = CircleShape, label = { Text(stringResource(R.string.sex_male)) })
        FilterChip(vm.sex == Sex.FEMALE, { vm.sex = Sex.FEMALE }, shape = CircleShape, label = { Text(stringResource(R.string.sex_female)) })
    }
    Text(stringResource(R.string.sex_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    OutlinedTextField(vm.age, { vm.age = it.filter(Char::isDigit) }, Modifier.fillMaxWidth(), singleLine = true,
        label = { Text(stringResource(R.string.age)) }, suffix = { Text(stringResource(R.string.years)) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), shape = MaterialTheme.shapes.medium)
    // Height: one box in cm (metric), or two boxes, feet + inches (imperial).
    if (vm.metric) {
        OutlinedTextField(vm.cm, { vm.cm = it.filter(Char::isDigit) }, Modifier.fillMaxWidth(), singleLine = true,
            label = { Text(stringResource(R.string.height)) }, suffix = { Text("cm") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), shape = MaterialTheme.shapes.medium)
    } else Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(vm.feet, { vm.feet = it.filter(Char::isDigit) }, Modifier.weight(1f), singleLine = true,
            label = { Text(stringResource(R.string.height)) }, suffix = { Text("ft") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), shape = MaterialTheme.shapes.medium)
        OutlinedTextField(vm.inches, { vm.inches = it }, Modifier.weight(1f), singleLine = true,
            label = { Text(" ") }, suffix = { Text("in") }, keyboardOptions = num, shape = MaterialTheme.shapes.medium)
    }
    OutlinedTextField(vm.weight, { vm.weight = it }, Modifier.fillMaxWidth(), singleLine = true,
        label = { Text(stringResource(R.string.weight)) }, suffix = { Text(Units.weightUnit(vm.metric)) },
        keyboardOptions = num, shape = MaterialTheme.shapes.medium)
}

@Composable
private fun ActivityStep(vm: SetupViewModel) {
    StepTitle(R.string.setup_activity_title)
    val labels = mapOf(
        ActivityLevel.SEDENTARY to (R.string.act_sedentary to R.string.act_sedentary_d),
        ActivityLevel.LIGHT to (R.string.act_light to R.string.act_light_d),
        ActivityLevel.MODERATE to (R.string.act_moderate to R.string.act_moderate_d),
        ActivityLevel.ACTIVE to (R.string.act_active to R.string.act_active_d),
        ActivityLevel.VERY_ACTIVE to (R.string.act_very_active to R.string.act_very_active_d),
    )
    ActivityLevel.entries.forEach { a ->
        val (t, d) = labels.getValue(a)
        Choice(vm.activity == a, { vm.activity = a }, stringResource(t), stringResource(d) + "  ·  ×${a.factor}")
    }
}

// Rate chips: lose 0.5 / 1 / 1.5 / 2 lb per week, or gain 0.25 / 0.5 lb per week.
@Composable
private fun GoalStep(vm: SetupViewModel) {
    StepTitle(R.string.setup_goal_title)
    Choice(vm.goal == GoalType.LOSE, { vm.chooseGoal(GoalType.LOSE) }, "📉  " + stringResource(R.string.goal_lose), null)
    Choice(vm.goal == GoalType.MAINTAIN, { vm.chooseGoal(GoalType.MAINTAIN) }, "⚖️  " + stringResource(R.string.goal_maintain), null)
    Choice(vm.goal == GoalType.GAIN, { vm.chooseGoal(GoalType.GAIN) }, "📈  " + stringResource(R.string.goal_gain), null)
    if (vm.goal != GoalType.MAINTAIN) {
        Text(stringResource(if (vm.metric) R.string.rate_per_week_kg else R.string.rate_per_week), style = MaterialTheme.typography.labelLarge)
        // Chip values are stored in lb/week; the label shows them in your unit (0.5 kg = 1.1 lb).
        val rates = Units.ratePresetsLb(vm.metric, losing = vm.goal == GoalType.LOSE)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            rates.forEach { r ->
                val shown = if (vm.metric) fmtNum(Math.round(r / Units.LB_PER_KG * 100) / 100.0) + " kg" else fmtNum(r) + " lb"
                FilterChip(kotlin.math.abs(vm.rate - r) < 0.001, { vm.rate = r }, shape = CircleShape, label = { Text(shown) })
            }
        }
        Text(
            stringResource(when {
                vm.goal == GoalType.LOSE && vm.metric -> R.string.rate_note_lose_kg
                vm.goal == GoalType.LOSE -> R.string.rate_note_lose
                vm.metric -> R.string.rate_note_gain_kg
                else -> R.string.rate_note_gain
            }),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// Formula choice and the protein slider (g per lb of body weight).
@Composable
private fun FormulaStep(vm: SetupViewModel) {
    StepTitle(R.string.setup_formula_title)
    Choice(vm.formula == Formula.MIFFLIN, { vm.formula = Formula.MIFFLIN }, "Mifflin-St Jeor", stringResource(R.string.formula_mifflin_d))
    Choice(vm.formula == Formula.KATCH, { vm.formula = Formula.KATCH }, "Katch-McArdle", stringResource(R.string.formula_katch_d))
    if (vm.formula == Formula.KATCH) OutlinedTextField(
        vm.bodyFat, { vm.bodyFat = it }, Modifier.fillMaxWidth(), singleLine = true,
        label = { Text(stringResource(R.string.body_fat)) }, suffix = { Text("%") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), shape = MaterialTheme.shapes.medium
    )
    Spacer(Modifier.height(8.dp))
    // The slider always works in g per lb; in metric the text shows g per kg (x 2.20462).
    Text(
        if (vm.metric) stringResource(R.string.protein_per_kg, fmtNum(Units.proteinPerUnit((vm.proteinPerLb * 10).toInt() / 10.0, true)))
        else stringResource(R.string.protein_per_lb, fmtNum((vm.proteinPerLb * 10).toInt() / 10.0)),
        style = MaterialTheme.typography.titleMedium
    )
    Slider(vm.proteinPerLb, { vm.proteinPerLb = (it * 10).toInt() / 10f }, valueRange = 0.6f..1.2f, steps = 5)
    Text(stringResource(if (vm.metric) R.string.protein_per_kg_note else R.string.protein_per_lb_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

// Prints every step, e.g.:
//   W = 180 lb / 2.2046 = 81.6 kg ... BMR = 1,767 ... TDEE = 1,767 x 1.55 = 2,739
//   2,739 - 500 = 2,239 -> 2,240 kcal/day
// and a note if the safety floor raised the number.
/** Shows the math, step by step, with your numbers. */
@Composable
private fun ResultStep(p: Profile?, r: GoalResult?, metric: Boolean) {
    StepTitle(R.string.setup_result_title)
    if (p == null || r == null) { Text(stringResource(R.string.setup_incomplete)); return }
    val nf = NumberFormat.getIntegerInstance()
    SoftCard {
        Text(nf.format(r.calories) + " kcal", style = MaterialTheme.typography.displaySmall)
        Text(stringResource(R.string.per_day), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("${stringResource(R.string.protein)} ${r.targets.proteinG} g")
            Text("${stringResource(R.string.carbs)} ${r.targets.carbsG} g")
            Text("${stringResource(R.string.fat)} ${r.targets.fatG} g")
        }
    }
    SoftCard {
        Text(stringResource(R.string.how_calculated), style = MaterialTheme.typography.titleMedium)
        val kg = fmtNum(Math.round(r.weightKg * 10) / 10.0)
        val cm = fmtNum(Math.round(r.heightCm * 10) / 10.0)
        // Imperial shows the conversion to kg and cm (the formulas need metric); metric shows them directly.
        MathLine(if (metric) stringResource(R.string.calc_units_metric, kg, cm)
                 else stringResource(R.string.calc_units, fmtNum(p.weightLb), kg, fmtNum(p.heightIn), cm))
        if (r.formulaUsed == Formula.KATCH) {
            MathLine(stringResource(R.string.calc_lbm, kg, fmtNum(p.bodyFatPct ?: 0.0), fmtNum(Math.round((r.leanKg ?: 0.0) * 10) / 10.0)))
            MathLine(stringResource(R.string.calc_katch, fmtNum(Math.round((r.leanKg ?: 0.0) * 10) / 10.0), nf.format(r.bmr)))
        } else {
            MathLine(stringResource(R.string.calc_mifflin, kg, cm, p.age, if (p.sex == Sex.MALE) "+ 5" else "− 161", nf.format(r.bmr)))
        }
        MathLine(stringResource(R.string.calc_tdee, nf.format(r.bmr), fmtNum(p.activity.factor), nf.format(r.tdee)))
        if (p.goal != GoalType.MAINTAIN) {
            val sign = if (p.goal == GoalType.LOSE) "−" else "+"
            val adj = (if (r.adjustment < 0) "−" else "+") + nf.format(kotlin.math.abs(r.adjustment))
            // Metric: "0.5 kg/week = 1.1 lb x 3,500 kcal / 7 days" (the 3,500 kcal rule is per lb).
            MathLine(if (metric) stringResource(R.string.calc_goal_metric,
                    sign + fmtNum(Math.round(p.rateLbWeek / Units.LB_PER_KG * 100) / 100.0), fmtNum(Math.round(p.rateLbWeek * 10) / 10.0), adj)
                else stringResource(R.string.calc_goal, sign + fmtNum(p.rateLbWeek), adj))
        }
        if (r.clampedToFloor) Text(stringResource(R.string.calc_floor, nf.format(r.floor)), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 6.dp))
        MathLine(if (metric) stringResource(R.string.calc_protein_metric, fmtNum(Units.proteinPerUnit(p.proteinPerLb, true)), kg, r.targets.proteinG)
                 else stringResource(R.string.calc_protein, fmtNum(p.proteinPerLb), fmtNum(p.weightLb), r.targets.proteinG))
        MathLine(stringResource(R.string.calc_fat, r.targets.fatG))
        MathLine(stringResource(R.string.calc_carbs, r.targets.carbsG))
    }
    Text(stringResource(R.string.setup_disclaimer), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

// One line of the math shown on the result page.
@Composable
private fun MathLine(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 6.dp))
}

// A selectable card with a title and a small description (used for activity, goal, formula).
@Composable
private fun Choice(selected: Boolean, onClick: () -> Unit, title: String, subtitle: String?) {
    Surface(
        onClick = onClick, shape = MaterialTheme.shapes.large,
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}
