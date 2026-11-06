// ==============================================================================
// PLAIN-ENGLISH GUIDE
//
// WHAT THIS FILE IS: your body profile and the math that turns it into a daily
// calorie and macro goal. Inputs are imperial (lb, in); the formulas need
// metric, so they are converted inside.
//
// THE FORMULAS (same ones shown in the app's "How we calculated" box):
//  1) Convert:  W (kg) = weight lb / 2.2046      H (cm) = height in x 2.54
//  2) BMR = calories your body burns at complete rest, per day.
//     Mifflin-St Jeor (1990):  BMR = 10*W + 6.25*H - 5*A + s
//         A = age (years); s = +5 for men, -161 for women.
//     Katch-McArdle (needs body fat %):  BMR = 370 + 21.6*LBM
//         LBM = lean body mass (kg) = W x (1 - body fat as a decimal)
//  3) TDEE = Total Daily Energy Expenditure = BMR x activity factor
//         sedentary 1.2 | light 1.375 | moderate 1.55 | active 1.725 | very active 1.9
//  4) Goal adjustment = rate (lb/week) x 3,500 kcal per lb / 7 days
//         (minus to lose, plus to gain; 1 lb/week = 500 kcal/day)
//  5) Safety floor: never below 1,500 kcal (men) / 1,200 (women),
//     and never below BMR when losing. Result rounded to the nearest 10.
//  6) Macros:  protein (g) = g-per-lb x body weight lb
//              fat (g)     = larger of (25% of calories / 9) or (0.3 g x lb)
//              carbs (g)   = calories left / 4   (at least 50 g)
//     Energy per gram: protein 4 kcal, carbs 4 kcal, fat 9 kcal.
//
// WORKED EXAMPLE: man, 30 yr, 69 in, 180 lb, moderate, lose 1 lb/week, 0.8 g/lb
//   W = 81.65 kg, H = 175.26 cm
//   BMR  = 10(81.65) + 6.25(175.26) - 5(30) + 5 = 1,767 kcal
//   TDEE = 1,767 x 1.55 = 2,739 kcal
//   Goal = 2,739 - 500 = 2,239 -> floor is 1,767, so OK -> rounded = 2,240 kcal
//   Protein 0.8 x 180 = 144 g | Fat max(62.2, 54) = 62 g |
//   Carbs (2,240 - 144x4 - 62x9) / 4 = 277 g
// ==============================================================================
package com.gymnam.app.data

import org.json.JSONObject
import kotlin.math.max
import kotlin.math.roundToInt

enum class Sex { MALE, FEMALE }
enum class GoalType { LOSE, MAINTAIN, GAIN }
enum class Formula { MIFFLIN, KATCH }

// Each level carries its multiplier ("factor") used in TDEE = BMR x factor.
/** Physical activity level (PAL) multipliers commonly used with BMR to estimate TDEE. */
enum class ActivityLevel(val factor: Double) {
    SEDENTARY(1.2),      // desk job, little exercise
    LIGHT(1.375),        // light exercise 1–3 days/week
    MODERATE(1.55),      // moderate exercise 3–5 days/week
    ACTIVE(1.725),       // hard exercise 6–7 days/week
    VERY_ACTIVE(1.9)     // physical job + hard training
}

// Your answers from the setup screens. The values after "=" are the starting
// defaults shown before you type anything.
/** Everything the setup wizard asks. Imperial in, metric only inside the formulas. */
data class Profile(
    val sex: Sex = Sex.MALE,
    val age: Int = 30,
    val heightIn: Double = 69.0,
    val weightLb: Double = 180.0,
    val bodyFatPct: Double? = null,
    val activity: ActivityLevel = ActivityLevel.MODERATE,
    val goal: GoalType = GoalType.MAINTAIN,
    /** lb per week; positive number, direction comes from [goal]. */
    val rateLbWeek: Double = 0.0,
    val formula: Formula = Formula.MIFFLIN,
    val proteinPerLb: Double = 0.8
) {
    // The profile is saved in Settings as one JSON text; toJson writes it and
    // fromJson reads it back (returns null if it's missing or damaged).
    fun toJson(): String = JSONObject().put("sex", sex.name).put("age", age).put("heightIn", heightIn)
        .put("weightLb", weightLb).put("bodyFat", bodyFatPct ?: JSONObject.NULL).put("activity", activity.name)
        .put("goal", goal.name).put("rate", rateLbWeek).put("formula", formula.name).put("proteinPerLb", proteinPerLb)
        .toString()

    companion object {
        fun fromJson(s: String?): Profile? = s?.let {
            runCatching {
                val o = JSONObject(it)
                Profile(
                    Sex.valueOf(o.getString("sex")), o.getInt("age"), o.getDouble("heightIn"), o.getDouble("weightLb"),
                    o.optDouble("bodyFat").takeUnless { d -> d.isNaN() }, ActivityLevel.valueOf(o.getString("activity")),
                    GoalType.valueOf(o.getString("goal")), o.getDouble("rate"), Formula.valueOf(o.getString("formula")),
                    o.getDouble("proteinPerLb")
                )
            }.getOrNull()
        }
    }
}

// The final goal PLUS every step of the math, so the app can show its work.
// clampedToFloor = true when the safety floor raised your number.
/** The result plus every intermediate number, so the app can show its work. */
data class GoalResult(
    val weightKg: Double, val heightCm: Double, val leanKg: Double?,
    val bmr: Int, val tdee: Int, val adjustment: Int, val raw: Int,
    val calories: Int, val floor: Int, val clampedToFloor: Boolean,
    val targets: Targets, val formulaUsed: Formula
)

// "object" = there is exactly one GoalCalculator; you just call
// GoalCalculator.calculate(profile). "const val" = a fixed number.
/**
 * Daily calorie and macro targets.
 *
 * BMR, Mifflin-St Jeor (1990):    BMR = 10·W + 6.25·H − 5·A + s      (s = +5 men, −161 women)
 * BMR, Katch-McArdle:             BMR = 370 + 21.6·LBM,   LBM = W·(1 − BF)
 *     W = weight (kg) = lb ÷ 2.2046,  H = height (cm) = in × 2.54,  A = age (years),  BF = body fat (decimal)
 * TDEE = BMR × activity factor (1.2 … 1.9)
 * Goal adjustment = ± rate (lb/week) × 3,500 kcal/lb ÷ 7 days  (≈ ±500 kcal/day per lb/week)
 * Safety floor: never below 1,500 kcal (men) / 1,200 kcal (women), and never below BMR when losing.
 *
 * Macros: protein = g/lb × body weight (lb); fat = 25% of calories ÷ 9 kcal/g, but at least 0.3 g/lb;
 *         carbs = what's left ÷ 4 kcal/g.
 */
object GoalCalculator {
    const val KG_PER_LB = 1 / 2.2046
    const val CM_PER_IN = 2.54
    const val KCAL_PER_LB_FAT = 3500.0

    fun calculate(p: Profile): GoalResult {
        val kg = p.weightLb * KG_PER_LB
        val cm = p.heightIn * CM_PER_IN
        // Katch-McArdle is used only if you chose it AND entered a body fat %.
        // Otherwise it falls back to Mifflin-St Jeor.
        val useKatch = p.formula == Formula.KATCH && p.bodyFatPct != null
        val lean = p.bodyFatPct?.let { kg * (1 - it / 100.0) }
        // "lean!!" = "I'm sure this is not empty" (true here, because useKatch checked it).
        val bmr = if (useKatch) 370 + 21.6 * lean!!
        else 10 * kg + 6.25 * cm - 5 * p.age + if (p.sex == Sex.MALE) 5 else -161
        val tdee = bmr * p.activity.factor
        val adj = when (p.goal) {
            GoalType.LOSE -> -p.rateLbWeek * KCAL_PER_LB_FAT / 7
            GoalType.GAIN -> p.rateLbWeek * KCAL_PER_LB_FAT / 7
            GoalType.MAINTAIN -> 0.0
        }
        val raw = tdee + adj
        // max(a, b) = the bigger of the two. So the floor is 1,500/1,200, or your BMR
        // if you are losing and your BMR is higher.
        val floor = max(if (p.sex == Sex.MALE) 1500.0 else 1200.0, if (p.goal == GoalType.LOSE) bmr else 0.0)
        val calories = roundTo10(max(raw, floor))

        // roundToInt() = round to a whole number. coerceAtLeast(50) = never below 50.
        val protein = (p.proteinPerLb * p.weightLb).roundToInt()
        val fat = max(calories * 0.25 / 9, 0.3 * p.weightLb).roundToInt()
        val carbs = ((calories - protein * 4 - fat * 9) / 4.0).roundToInt().coerceAtLeast(50)

        return GoalResult(
            weightKg = kg, heightCm = cm, leanKg = lean,
            bmr = bmr.roundToInt(), tdee = tdee.roundToInt(), adjustment = adj.roundToInt(), raw = raw.roundToInt(),
            calories = calories, floor = floor.roundToInt(), clampedToFloor = raw < floor,
            targets = Targets(calories, protein, carbs, fat),
            formulaUsed = if (useKatch) Formula.KATCH else Formula.MIFFLIN
        )
    }

    // 2,238.6 -> 2,240 (nearest 10).
    private fun roundTo10(d: Double) = ((d / 10).roundToInt() * 10)
}
