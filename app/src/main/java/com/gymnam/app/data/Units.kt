// ==============================================================================
// PLAIN-ENGLISH GUIDE
// WHAT THIS FILE IS: the Imperial <-> Metric switch.
//
// IMPORTANT IDEA: the app always SAVES in imperial (lb, inches, fl oz), exactly as
// before. Metric is only how numbers are SHOWN and TYPED. That way:
//  - switching units never changes or loses your data,
//  - backups and Health Connect keep working the same,
//  - the goal formulas (data/Profile.kt) don't change at all.
//
// CONVERSION FACTORS (exact or standard values):
//   1 kg    = 2.20462 lb        ->  kg = lb / 2.20462     lb = kg x 2.20462
//   1 in    = 2.54 cm (exact)   ->  cm = in x 2.54        in = cm / 2.54
//   1 fl oz = 29.5735 ml        ->  ml = fl oz x 29.5735  fl oz = ml / 29.5735
// EXAMPLES: 180 lb = 81.6 kg | 69 in (5 ft 9 in) = 175 cm | 64 fl oz = 1,893 ml
//
// LocalMetric: a value every screen can read ("is the user in metric?") without
// passing it through every function. MainActivity sets it from your settings.
// ==============================================================================
package com.gymnam.app.data

import androidx.compose.runtime.staticCompositionLocalOf
import java.text.NumberFormat
import kotlin.math.roundToInt

/** true = metric (kg, cm, ml); false = imperial (lb, ft/in, fl oz). Read with LocalMetric.current. */
val LocalMetric = staticCompositionLocalOf { false }

object Units {
    const val LB_PER_KG = 2.20462
    const val CM_PER_IN = 2.54
    const val ML_PER_FLOZ = 29.5735

    // ---------- Weight ----------
    /** lb (stored) -> the number shown, rounded to 0.1. 180.0 -> 81.6 in metric. */
    fun weightValue(lb: Double, metric: Boolean): Double =
        Math.round((if (metric) lb / LB_PER_KG else lb) * 10) / 10.0

    /** Number the user typed (kg or lb) -> lb to store. */
    fun lbFrom(value: Double, metric: Boolean): Double = if (metric) value * LB_PER_KG else value

    fun weightUnit(metric: Boolean) = if (metric) "kg" else "lb"

    /** "176.2 lb" or "79.9 kg". */
    fun weight(lb: Double, metric: Boolean) = "${fmtNum(weightValue(lb, metric))} ${weightUnit(metric)}"

    /** A weight CHANGE, with sign: "−3.4 lb" / "+1.5 kg" (no rounding to 0 surprises). */
    fun weightChange(lb: Double, metric: Boolean): String {
        val v = weightValue(lb, metric)
        val sign = if (v > 0) "+" else if (v < 0) "−" else ""
        return "$sign${fmtNum(kotlin.math.abs(v))} ${weightUnit(metric)}"
    }

    /** Valid range for a typed body weight, in the user's unit (50–800 lb = 23–363 kg). */
    fun weightRange(metric: Boolean) = if (metric) 23.0..363.0 else 50.0..800.0

    // ---------- Height ----------
    /** "5 ft 9 in" or "175 cm". */
    fun height(inches: Double, metric: Boolean): String =
        if (metric) "${(inches * CM_PER_IN).roundToInt()} cm"
        else "${(inches / 12).toInt()} ft ${fmtNum(Math.round((inches % 12) * 10) / 10.0)} in"

    // ---------- Water ----------
    /** fl oz (stored) -> number shown: ml rounded to the nearest 10 ml, or whole fl oz. */
    fun waterValue(flOz: Double, metric: Boolean): Int =
        if (metric) ((flOz * ML_PER_FLOZ) / 10).roundToInt() * 10 else flOz.roundToInt()

    /**
     * Readable water amount:
     *  imperial: "40 fl oz"
     *  metric under 1 liter: "750 ml";  1 liter or more: "1.9 L" (1 L = 1,000 ml, one decimal,
     *  two decimals only for quarter liters like "1.25 L").
     */
    fun water(flOz: Double, metric: Boolean): String {
        if (!metric) return "${NumberFormat.getIntegerInstance().format(flOz.roundToInt())} fl oz"
        val ml = waterValue(flOz, metric)
        return if (ml < 1000) "$ml ml" else "${fmtNum(liters(ml))} L"
    }

    /** ml -> liters for display: 1,250 -> 1.25, 1,893 -> 1.9. */
    private fun liters(ml: Int): Double {
        val l = ml / 1000.0
        val quarter = Math.round(l * 4) / 4.0
        return if (kotlin.math.abs(l - quarter) < 0.005) quarter else Math.round(l * 10) / 10.0
    }

    /** Typed water -> fl oz to store. `liters` = the user picked "L" instead of "ml". */
    fun flOzFrom(value: Double, metric: Boolean, liters: Boolean = false): Double = when {
        !metric -> value
        liters -> value * 1000 / ML_PER_FLOZ
        else -> value / ML_PER_FLOZ
    }

    /** Quick-add water buttons, as fl oz to store, paired with their label. */
    fun waterPresets(metric: Boolean): List<Pair<Double, String>> =
        (if (metric) listOf(250, 330, 500, 750, 1000, 1500).map { it / ML_PER_FLOZ }
         else listOf(8.0, 12.0, 16.0, 20.0, 24.0, 32.0)).map { it to water(it, metric) }

    /** The water goal shown in Settings: fl oz, or liters in metric (64 fl oz -> 1.9). */
    fun waterGoalValue(flOz: Int, metric: Boolean): Double =
        if (metric) Math.round(flOz * ML_PER_FLOZ / 100) / 10.0 else flOz.toDouble()

    /** Typed water goal (fl oz, or liters in metric) -> whole fl oz to store. */
    fun waterGoalFlOz(value: Double, metric: Boolean): Int =
        (if (metric) value * 1000 / ML_PER_FLOZ else value).roundToInt()

    /** One "glass" on the water card: 8 fl oz, or 250 ml (= 8.45 fl oz). */
    fun glassFlOz(metric: Boolean): Double = if (metric) 250 / ML_PER_FLOZ else 8.0

    // ---------- Weekly rate & protein (used in setup) ----------
    /** Rate choices shown in setup. Stored in lb/week. Metric: 0.25 / 0.5 / 0.75 / 1 kg. */
    fun ratePresetsLb(metric: Boolean, losing: Boolean): List<Double> = when {
        metric && losing -> listOf(0.25, 0.5, 0.75, 1.0).map { it * LB_PER_KG }
        metric -> listOf(0.1, 0.25).map { it * LB_PER_KG }
        losing -> listOf(0.5, 1.0, 1.5, 2.0)
        else -> listOf(0.25, 0.5)
    }

    /** Protein is stored per lb. Shown per kg in metric: g/kg = g/lb x 2.20462 (0.8 g/lb = 1.8 g/kg). */
    fun proteinPerUnit(gPerLb: Double, metric: Boolean): Double =
        Math.round((if (metric) gPerLb * LB_PER_KG else gPerLb) * 10) / 10.0
}
