// ==============================================================================
// PLAIN-ENGLISH GUIDE
//
// WHAT THIS FILE IS: a food you CAN pick (not one you already ate). It can come
// from the built-in list, your saved foods, your recent foods, or the internet.
// The numbers (kcal, protein, carbs, fat) are for ONE serving.
// The math helpers here scale those numbers to the amount you actually eat.
// ==============================================================================
package com.gymnam.app.data

import kotlin.math.roundToInt

// Where the food came from: built-in list, My foods, Recent, or online (Open Food Facts).
enum class FoodSource { CATALOG, SAVED, RECENT, ONLINE }

// A "?" after a type (Long?, String?) means the value is optional: it can be
// "null" = empty / not known. "= null" sets "empty" as the default.
/**
 * A food you can pick, from any source. Nutrition is stored PER SERVING.
 * servingG = grams in one serving, or 0 when unknown (then only "servings" can be logged).
 */
data class FoodItem(
    val key: String,
    val name: String,
    val emoji: String,
    val servingLabel: String,
    val servingG: Double,
    val kcal: Double,
    val proteinG: Double,
    val carbsG: Double,
    val fatG: Double,
    val source: FoodSource,
    val savedId: Long? = null,
    val barcode: String? = null,
    val brand: String? = null,
    /** Online products only: the allergens Open Food Facts lists, e.g. ["en:milk", "en:gluten"]. */
    val allergenTags: List<String> = emptyList()
) {
    // True when we know how many grams one serving weighs (so you can log in grams).
    val hasGrams get() = servingG > 0

    /** "1 cup (158 g)" or "1 serving" */
    val servingText: String
        get() = if (hasGrams && !servingLabel.trim().endsWith(" g"))
            "$servingLabel (${fmtNum(servingG)} g)" else servingLabel

    // How many servings did you eat?
    //   in grams:   amount / grams-per-serving   (e.g. 150 g / 50 g = 3 servings)
    //   in servings: the amount itself            (e.g. 2 servings = 2)
    /** Factor relative to one serving. */
    fun factor(amount: Double, inGrams: Boolean): Double =
        if (inGrams && hasGrams) amount / servingG else amount

    // Calories eaten = calories per serving x number of servings, rounded.
    fun kcalFor(amount: Double, inGrams: Boolean) = (kcal * factor(amount, inGrams)).roundToInt()
}

// Pretty number for the screen: 2.0 -> "2", 2.25 -> "2.3" (one decimal).
fun fmtNum(d: Double): String =
    if (d % 1.0 == 0.0) d.toLong().toString() else ((d * 10).roundToInt() / 10.0).toString()
