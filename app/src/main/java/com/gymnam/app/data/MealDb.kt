// ==============================================================================
// PLAIN-ENGLISH GUIDE
//
// WHAT THIS FILE IS: online recipe search through TheMealDB (free website with
// recipes, English only). Blocked unless internet is ON in Settings.
//
// TheMealDB gives ingredients like "2 cups rice" but NO calories. So this file:
//  1. finds the closest food in the built-in list ("rice" -> "White rice, cooked"),
//  2. converts the measure to grams ("2 cups" -> 2 x 158 g = 316 g),
//  3. calculates calories from grams. That's why online recipes say "estimated".
// NOTE: the free key "1" in the web address is for personal use. Publishing the
// app on the Play Store requires TheMealDB's supporter key.
// ==============================================================================
package com.gymnam.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.math.roundToInt

// A search result: just the recipe's id and name (details load when tapped).
data class MealSummary(val id: String, val name: String)

/**
 * TheMealDB (free recipe database, public test key "1", English only). It has no nutrition data,
 * so [toModel] estimates calories by matching each ingredient to the built-in food list and
 * converting its measure ("2 cups", "1 lb", "3 cloves") to grams.
 * Like Open Food Facts, every call is blocked unless the in-app internet switch is on.
 */
class MealDb(private val settings: SettingsStore, private val catalog: Catalog) {

    // Downloads one answer from TheMealDB. First line = the privacy gate (internet OFF -> stop).
    private suspend fun get(path: String): JSONObject {
        if (!settings.internetEnabled()) throw OfflineException()
        return withContext(Dispatchers.IO) {
            val conn = (URL("https://www.themealdb.com/api/json/v1/1/$path").openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000; readTimeout = 15_000
                setRequestProperty("User-Agent", "GymNam/0.3 (Android; personal calorie app)")
            }
            try {
                if (conn.responseCode !in 200..299) throw java.io.IOException("HTTP ${conn.responseCode}")
                JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
            } finally { conn.disconnect() }
        }
    }

    private fun summaries(json: JSONObject): List<MealSummary> {
        val arr = json.optJSONArray("meals") ?: return emptyList()   // "meals": null when nothing found
        return (0 until arr.length()).map { arr.getJSONObject(it) }
            .map { MealSummary(it.getString("idMeal"), it.getString("strMeal")) }
    }

    // Two searches combined: recipes whose NAME has your word, plus recipes that USE
    // it as an ingredient. distinctBy removes duplicates; take(40) keeps 40 max.
    /** Recipes whose NAME matches, plus recipes that USE the word as a main ingredient. */
    suspend fun search(query: String): List<MealSummary> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        val byName = summaries(get("search.php?s=" + URLEncoder.encode(q, "UTF-8")))
        val byIng = summaries(get("filter.php?i=" + URLEncoder.encode(q.replace(' ', '_'), "UTF-8")))
        return (byName + byIng).distinctBy { it.id }.take(40)
    }

    suspend fun recipe(id: String): RecipeModel? {
        val meal = get("lookup.php?i=" + URLEncoder.encode(id, "UTF-8"))
            .optJSONArray("meals")?.optJSONObject(0) ?: return null
        return toModel(meal)
    }

    // TheMealDB stores up to 20 ingredients as strIngredient1..20 and strMeasure1..20.
    // It doesn't say how many servings, so we assume 4.
    // The instructions are one long text; the Regex splits it into steps at line
    // breaks or at "STEP 1", "Step 2"...
    private suspend fun toModel(m: JSONObject): RecipeModel {
        val servings = 4  // TheMealDB doesn't say; most recipes serve about 4
        val ings = (1..20).mapNotNull { i ->
            val name = m.optString("strIngredient$i").trim()
            if (name.isEmpty() || name == "null") return@mapNotNull null
            val measure = m.optString("strMeasure$i").trim().takeIf { it.isNotEmpty() && it != "null" }
            estimate(name, measure.orEmpty())
        }
        val steps = m.optString("strInstructions")
            .split(Regex("\\r?\\n+|(?<=\\.)\\s+(?=STEP|Step)"))
            .map { it.replace(Regex("^(STEP|Step)\\s*\\d+[:.]?\\s*"), "").trim() }
            .filter { it.length > 3 }
        return RecipeModel(
            key = "mealdb:${m.getString("idMeal")}",
            name = m.getString("strMeal"),
            emoji = guessRecipeEmoji(m.optString("strCategory")),
            servings = servings, minutes = 0,
            kcal = ings.sumOf { it.kcal } / servings,
            proteinG = ings.sumOf { it.proteinG } / servings,
            carbsG = ings.sumOf { it.carbsG } / servings,
            fatG = ings.sumOf { it.fatG } / servings,
            ingredients = ings, steps = steps, source = RecipeSource.ONLINE,
            estimated = true, matchedIngredients = ings.count { it.kcal > 0 || it.grams > 0 }
        )
    }

    // One ingredient: find the closest built-in food, measure -> grams, then
    // calories = food's kcal per serving x (grams / serving grams).
    /** One ingredient → grams → calories, using the closest built-in food. */
    private suspend fun estimate(name: String, measure: String): Ingredient {
        val food = catalog.search(name, spanish = false, limit = 1).firstOrNull()
        val grams = measureToGrams(measure, food)
        val per = if (food != null && food.hasGrams) grams / food.servingG else 0.0
        val keys = Catalog.normalize(name).split(' ').filter { it.length > 2 }.ifEmpty { listOf(Catalog.normalize(name)) }
        return Ingredient(
            name = name, emoji = food?.emoji ?: "🥄", grams = grams,
            pieces = Regex("^\\s*(\\d+)\\s*$").find(measure)?.groupValues?.get(1)?.toDouble() ?: 0.0,
            kcal = (food?.kcal ?: 0.0) * per, proteinG = (food?.proteinG ?: 0.0) * per,
            carbsG = (food?.carbsG ?: 0.0) * per, fatG = (food?.fatG ?: 0.0) * per,
            keys = keys, staple = isStaple(name), measureText = measure.ifBlank { null }
        )
    }

    companion object {
        // Pantry basics: counted for calories, not for "you have X of Y".
        private val STAPLE_WORDS = listOf("salt", "pepper", "oil", "water", "garlic", "spice", "cumin", "paprika",
            "oregano", "thyme", "bay", "cinnamon", "stock cube", "vinegar", "sugar")

        fun isStaple(name: String) = Catalog.normalize(name).let { n -> STAPLE_WORDS.any { n.contains(it) } }

        // Turns kitchen measures into grams. Conversions used:
        //  1 kg = 1,000 g | 1 lb = 453.6 g | 1 oz = 28.35 g | 1 ml ~ 1 g | 1 L = 1,000 g
        //  1 cup = that food's own cup weight if known, else 240 g
        //  1 tbsp = 15 g | 1 tsp = 5 g | 1 garlic clove = 3 g | 1 slice = 30 g
        //  1 can = 400 g | 1 handful = 30 g | "to taste", "pinch" = 0 g
        //  a plain number ("2", "2 large") = 2 x the food's usual piece weight (or 100 g)
        // Example: "1/2 lb" -> 0.5 x 453.6 = 226.8 g.
        /**
         * Rough measure → grams. Handles "200g", "1.5 kg", "1/2 lb", "1 ½ cups", "2 tbsp", "3 cloves", "2".
         * A cup uses the food's own cup weight when we know it (rice ≠ spinach), else 240 g.
         * "to taste", "pinch", "garnish" → 0.
         */
        fun measureToGrams(measure: String, food: FoodItem?): Double {
            val m = measure.lowercase().replace(",", ".")
            if (m.isBlank() || listOf("taste", "pinch", "dash", "garnish", "sprinkl", "drizzle").any { m.contains(it) }) return 0.0
            val n = parseQuantity(m) ?: 1.0
            val unitPart = m.replace(Regex("[0-9./½¼¾⅓⅔\\s-]+"), " ").trim()
            fun has(vararg u: String) = u.any { Regex("\\b${it}s?\\b").containsMatchIn(unitPart) }
            val cupG = food?.takeIf { it.servingLabel.contains("cup") && it.hasGrams }?.let {
                it.servingG / (if (it.servingLabel.contains("½")) 0.5 else if (it.servingLabel.contains("¼")) 0.25 else 1.0)
            } ?: 240.0
            return when {
                has("kg") -> n * 1000
                has("g", "gram") -> n
                has("ml") -> n
                has("l", "litre", "liter") -> n * 1000
                has("lb", "pound") -> n * 453.6
                has("oz", "ounce") -> n * 28.35
                has("cup") -> n * cupG
                has("tbsp", "tbs", "tablespoon") -> n * 15
                has("tsp", "teaspoon") -> n * 5
                has("clove") -> n * 3
                has("slice") -> n * 30
                has("can", "tin") -> n * 400
                has("handful") -> n * 30
                unitPart.isEmpty() || has("large", "medium", "small", "whole") ->
                    n * (food?.takeIf { it.hasGrams && !it.servingLabel.contains("cup") }?.servingG ?: 100.0)
                else -> 0.0
            }
        }

        // Reads the number part: "2" -> 2, "1.5" -> 1.5, "1/2" -> 0.5, "1 ½" -> 1.5.
        // Returns null if there's no number (then 1 is assumed).
        private fun parseQuantity(s: String): Double? {
            val fracs = mapOf('½' to 0.5, '¼' to 0.25, '¾' to 0.75, '⅓' to 1.0 / 3, '⅔' to 2.0 / 3)
            var total = 0.0
            var found = false
            Regex("(\\d+(?:\\.\\d+)?)\\s*/\\s*(\\d+)|(\\d+(?:\\.\\d+)?)|([½¼¾⅓⅔])").findAll(s).take(2).forEach { r ->
                found = true
                total += when {
                    r.groupValues[1].isNotEmpty() -> r.groupValues[1].toDouble() / r.groupValues[2].toDouble()
                    r.groupValues[3].isNotEmpty() -> r.groupValues[3].toDouble()
                    else -> fracs[r.groupValues[4][0]] ?: 0.0
                }
            }
            return if (found && total > 0) total else null
        }

        // Picks an emoji from TheMealDB's category.
        fun guessRecipeEmoji(category: String) = when (category.lowercase()) {
            "beef" -> "🥩"; "chicken" -> "🍗"; "seafood" -> "🐟"; "pork" -> "🥓"; "lamb", "goat" -> "🍖"
            "pasta" -> "🍝"; "dessert" -> "🍰"; "breakfast" -> "🍳"; "vegetarian", "vegan" -> "🥗"
            "side" -> "🥔"; "starter" -> "🥟"; else -> "🍽️"
        }

        fun roundKcal(d: Double) = d.roundToInt()
    }
}
