// ==============================================================================
// PLAIN-ENGLISH GUIDE
//
// WHAT THIS FILE IS: "My foods" and "My meals".
//  - SavedFood = a food you created (or saved from a scan) with its own numbers.
//  - SavedMeal = a group of foods you log together in one tap ("My usual breakfast").
//  - SavedDao  = the database actions for both.
// ==============================================================================
package com.gymnam.app.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import org.json.JSONArray
import org.json.JSONObject

// Same idea as FoodItem but stored in the database. barcode is optional: if it's
// set, scanning that barcode later finds your food even with internet OFF.
/** "My foods": nutrition per serving. A barcode lets future scans work offline. */
@Entity(tableName = "saved_foods", indices = [Index("barcode")])
data class SavedFood(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val emoji: String,
    val servingName: String,
    val servingG: Double,
    val kcal: Double,
    val proteinG: Double,
    val carbsG: Double,
    val fatG: Double,
    val barcode: String? = null,
    val createdAt: Long = System.currentTimeMillis()
) {
    // Converts it to the common FoodItem shape the food picker understands.
    fun toItem() = FoodItem(
        key = "saved:$id", name = name, emoji = emoji, servingLabel = servingName,
        servingG = servingG, kcal = kcal, proteinG = proteinG, carbsG = carbsG, fatG = fatG,
        source = FoodSource.SAVED, savedId = id, barcode = barcode
    )
}

// itemsJson = the list of foods in the meal, written as JSON text (a standard
// "list of labeled values" format) because a database column holds one value.
/** "My meals": a named group of foods logged together in one tap. */
@Entity(tableName = "saved_meals")
data class SavedMeal(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val emoji: String,
    val itemsJson: String,
    val createdAt: Long = System.currentTimeMillis()
) {
    fun items(): List<MealItem> = MealItem.listFromJson(itemsJson)
}

// A COPY of each food at the moment you saved the meal, so later edits to other
// foods never change your saved meal.
/** One food inside a saved meal (a snapshot, so editing other foods never changes it). */
data class MealItem(
    val name: String, val emoji: String, val amount: Double, val unit: AmountUnit,
    val calories: Int, val proteinG: Double, val carbsG: Double, val fatG: Double
) {
    companion object {
        fun fromEntry(e: FoodEntry) =
            MealItem(e.name, e.emoji, e.amount, e.unit, e.calories, e.proteinG, e.carbsG, e.fatG)

        // List of foods -> JSON text (to store).
        fun toJson(items: List<MealItem>): String = JSONArray().apply {
            items.forEach {
                put(JSONObject().put("name", it.name).put("emoji", it.emoji).put("amount", it.amount)
                    .put("unit", it.unit.name).put("kcal", it.calories).put("p", it.proteinG)
                    .put("c", it.carbsG).put("f", it.fatG))
            }
        }.toString()

        // JSON text -> list of foods (to use). If the text is damaged, runCatching +
        // getOrDefault(emptyList()) return an empty list instead of crashing.
        fun listFromJson(json: String): List<MealItem> = runCatching {
            val a = JSONArray(json)
            (0 until a.length()).map { i ->
                val o = a.getJSONObject(i)
                MealItem(
                    o.optString("name"), o.optString("emoji", "🍽️"), o.optDouble("amount", 1.0),
                    runCatching { AmountUnit.valueOf(o.optString("unit")) }.getOrDefault(AmountUnit.SERVING),
                    o.optInt("kcal"), o.optDouble("p", 0.0), o.optDouble("c", 0.0), o.optDouble("f", 0.0)
                )
            }
        }.getOrDefault(emptyList())
    }
}

// Database actions for My foods / My meals, sorted by name.
@Dao
interface SavedDao {
    @Query("SELECT * FROM saved_foods ORDER BY name COLLATE NOCASE")
    fun foods(): Flow<List<SavedFood>>

    @Query("SELECT * FROM saved_foods WHERE barcode = :code LIMIT 1")
    suspend fun foodByBarcode(code: String): SavedFood?

    @Insert suspend fun insertFood(f: SavedFood): Long
    @Delete suspend fun deleteFood(f: SavedFood)

    @Query("SELECT * FROM saved_meals ORDER BY name COLLATE NOCASE")
    fun meals(): Flow<List<SavedMeal>>

    @Insert suspend fun insertMeal(m: SavedMeal): Long
    @Delete suspend fun deleteMeal(m: SavedMeal)

    // Backup
    @Query("SELECT * FROM saved_foods") suspend fun foodsNow(): List<SavedFood>
    @Query("SELECT * FROM saved_meals") suspend fun mealsNow(): List<SavedMeal>
    @Insert suspend fun insertFoods(l: List<SavedFood>)
    @Insert suspend fun insertMeals(l: List<SavedMeal>)
    @Query("DELETE FROM saved_foods") suspend fun clearFoods()
    @Query("DELETE FROM saved_meals") suspend fun clearMeals()
}
