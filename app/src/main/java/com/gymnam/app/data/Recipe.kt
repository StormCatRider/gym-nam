// ==============================================================================
// PLAIN-ENGLISH GUIDE
//
// WHAT THIS FILE IS: everything about recipes.
//  - Ingredient  = one ingredient with its calories for the AMOUNT used, plus
//                  the words used to find it in your fridge.
//  - Recipe      = YOUR recipe as stored in the database.
//  - RecipeModel = the common shape every recipe screen uses, whether the recipe
//                  is built-in, yours, or from TheMealDB.
//  - RecipeDao   = database actions for your recipes.
// ==============================================================================
package com.gymnam.app.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import org.json.JSONArray
import org.json.JSONObject

// grams = how much the recipe uses. pieces = count when it makes sense (3 eggs).
// keys = words that match it in your fridge, e.g. ["egg", "huevo"].
// staple = pantry basics (oil, salt, garlic): they count for calories but not for
// "you have 4 of 6 ingredients".
// excludes = words that rule a fridge item OUT (chicken excludes "caldo", so
// "Caldo de pollo" is not counted as chicken).
/**
 * One recipe ingredient. Nutrition is for the whole amount used in the recipe.
 * keys = words that recognise it in your fridge (EN + ES, accents removed).
 * staple = oil, garlic, salt…: counts for calories, not for "you have 4 of 6".
 */
data class Ingredient(
    val name: String,
    val emoji: String,
    val grams: Double,
    val pieces: Double = 0.0,
    val kcal: Double,
    val proteinG: Double,
    val carbsG: Double,
    val fatG: Double,
    val keys: List<String>,
    val staple: Boolean = false,
    /** For online recipes: the original "2 cups" text, shown as-is. */
    val measureText: String? = null,
    /** Words that rule a fridge item out (e.g. "caldo" so chicken broth isn't counted as chicken). */
    val excludes: List<String> = emptyList()
) {
    // Ingredient -> JSON (short labels: g, pcs, p, c, f, not).
    fun toJson(): JSONObject = JSONObject().put("name", name).put("emoji", emoji).put("g", grams)
        .put("pcs", pieces).put("kcal", kcal).put("p", proteinG).put("c", carbsG).put("f", fatG)
        .put("keys", JSONArray(keys)).put("not", JSONArray(excludes)).put("staple", staple)
        .apply { measureText?.let { put("measure", it) } }

    companion object {
        // JSON -> Ingredient. Built-in recipes store both "en" and "es" names; your own
        // recipes store a single "name".
        fun fromJson(o: JSONObject, spanish: Boolean = false): Ingredient {
            val keys = o.optJSONArray("keys")?.let { a -> (0 until a.length()).map { a.getString(it) } }.orEmpty()
            val excludes = o.optJSONArray("not")?.let { a -> (0 until a.length()).map { a.getString(it) } }.orEmpty()
            val name = when {
                o.has("name") -> o.getString("name")
                spanish -> o.optString("es", o.optString("en"))
                else -> o.optString("en")
            }
            return Ingredient(
                name = name, emoji = o.optString("emoji", "🥄"), grams = o.optDouble("g", 0.0),
                pieces = o.optDouble("pcs", 0.0), kcal = o.optDouble("kcal", 0.0),
                proteinG = o.optDouble("p", 0.0), carbsG = o.optDouble("c", 0.0), fatG = o.optDouble("f", 0.0),
                keys = keys, staple = o.optBoolean("staple", false),
                measureText = o.optString("measure").takeIf { it.isNotBlank() },
                excludes = excludes
            )
        }

        fun listToJson(list: List<Ingredient>): String = JSONArray().apply { list.forEach { put(it.toJson()) } }.toString()
        fun listFromJson(json: String): List<Ingredient> = runCatching {
            val a = JSONArray(json); (0 until a.length()).map { fromJson(a.getJSONObject(it)) }
        }.getOrDefault(emptyList())
    }
}

// The "...Override" values let you type your own per-serving numbers; when set,
// they win over the numbers added up from the ingredients.
/** Your own recipe. Overrides (per serving) win over the ingredient totals when set. */
@Entity(tableName = "recipes")
data class Recipe(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val emoji: String,
    val servings: Int,
    val minutes: Int,
    val ingredientsJson: String,
    val steps: String,
    val kcalOverride: Double? = null,
    val proteinOverride: Double? = null,
    val carbsOverride: Double? = null,
    val fatOverride: Double? = null,
    val createdAt: Long = System.currentTimeMillis()
) {
    // Per-serving calories = (sum of all ingredient calories) / servings,
    // unless you typed an override. Example: ingredients total 2,000 kcal,
    // 4 servings -> 500 kcal per serving.
    // Steps are stored as text, one step per line.
    fun toModel(): RecipeModel {
        val ings = Ingredient.listFromJson(ingredientsJson)
        val n = servings.coerceAtLeast(1)
        return RecipeModel(
            key = "mine:$id", name = name, emoji = emoji, servings = n, minutes = minutes,
            kcal = kcalOverride ?: (ings.sumOf { it.kcal } / n),
            proteinG = proteinOverride ?: (ings.sumOf { it.proteinG } / n),
            carbsG = carbsOverride ?: (ings.sumOf { it.carbsG } / n),
            fatG = fatOverride ?: (ings.sumOf { it.fatG } / n),
            ingredients = ings,
            steps = steps.lines().map { it.trim() }.filter { it.isNotEmpty() },
            source = RecipeSource.MINE, myId = id
        )
    }
}

// BUILTIN = ships with the app, MINE = yours, ONLINE = TheMealDB.
enum class RecipeSource { BUILTIN, MINE, ONLINE }

// estimated = true for online recipes, whose calories are a best guess from the
// ingredients we could recognise (matchedIngredients of them).
/** What every recipe screen works with, whatever the source. Nutrition is per serving. */
data class RecipeModel(
    val key: String,
    val name: String,
    val emoji: String,
    val servings: Int,
    val minutes: Int,
    val kcal: Double,
    val proteinG: Double,
    val carbsG: Double,
    val fatG: Double,
    val ingredients: List<Ingredient>,
    val steps: List<String>,
    val source: RecipeSource,
    val myId: Long? = null,
    /** Online recipes: calories are estimated from matched ingredients. */
    val estimated: Boolean = false,
    val matchedIngredients: Int = 0
)

@Dao
interface RecipeDao {
    @Query("SELECT * FROM recipes ORDER BY name COLLATE NOCASE")
    fun all(): Flow<List<Recipe>>

    @Query("SELECT * FROM recipes WHERE id = :id")
    suspend fun byId(id: Long): Recipe?

    @Insert suspend fun insert(r: Recipe): Long
    @Update suspend fun update(r: Recipe)
    @Delete suspend fun delete(r: Recipe)
    @Query("SELECT * FROM recipes") suspend fun allNow(): List<Recipe>
    @Insert suspend fun insertAll(l: List<Recipe>)
    @Query("DELETE FROM recipes") suspend fun clear()
}
