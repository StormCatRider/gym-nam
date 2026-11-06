// ==============================================================================
// PLAIN-ENGLISH GUIDE
//
// WHAT THIS FILE IS: the list of questions ("queries") the app can ask the
// database about your food diary. "DAO" = Data Access Object, a menu of
// database actions.
//
// HOW TO READ IT:
//  - @Query("SELECT ...") is written in SQL, the language databases speak.
//    SELECT * FROM food_entries WHERE epochDay = :day
//    = "give me every row of the food diary for this day".
//    ":day" gets replaced with the function's input.
//  - @Insert / @Update / @Delete = add / change / remove a row.
//  - "suspend fun" = a function that may take a moment, so it runs without
//    freezing the screen.
//  - "Flow<...>" = a LIVE answer: when the data changes, the screen gets the new
//    answer automatically (that's why the Today screen updates by itself).
// ==============================================================================
package com.gymnam.app.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface FoodDao {
    // Everything you ate on one day, in the order you logged it (live).
    @Query("SELECT * FROM food_entries WHERE epochDay = :day ORDER BY createdAt")
    fun entriesForDay(day: Long): Flow<List<FoodEntry>>

    // One diary row by its number, or null if it doesn't exist.
    @Query("SELECT * FROM food_entries WHERE id = :id")
    suspend fun byId(id: Long): FoodEntry?

    // SQL in plain words: for each food name keep only its newest row, newest first,
    // max 40. That's the "Recent" list in the food picker.
    /** Latest entry per distinct food name, newest first: powers the "Recent" list. */
    @Query("SELECT * FROM food_entries WHERE id IN (SELECT MAX(id) FROM food_entries GROUP BY name) ORDER BY createdAt DESC LIMIT 40")
    fun recent(): Flow<List<FoodEntry>>

    @Insert
    suspend fun insert(entry: FoodEntry): Long

    @Insert
    suspend fun insertAll(entries: List<FoodEntry>)

    @Query("SELECT * FROM food_entries WHERE epochDay = :day")
    suspend fun entriesForDayNow(day: Long): List<FoodEntry>

    // SUM(...) adds up a column, COUNT(*) counts rows, GROUP BY epochDay = one total
    // per day. Used by the Progress charts and history.
    /** Calories and macros per day, for charts and history. */
    @Query("SELECT epochDay, SUM(calories) AS kcal, SUM(proteinG) AS protein, SUM(carbsG) AS carbs, SUM(fatG) AS fat, COUNT(*) AS items FROM food_entries WHERE epochDay >= :from GROUP BY epochDay ORDER BY epochDay")
    fun dayTotals(from: Long): Flow<List<DayTotal>>

    // DISTINCT = no repeats. Every day you logged anything; used to count your streak.
    /** Every day with at least one food logged, newest first (for streaks). */
    @Query("SELECT DISTINCT epochDay FROM food_entries ORDER BY epochDay DESC")
    fun loggedDays(): Flow<List<Long>>

    // all() and clear() are used by backup export/restore.
    @Query("SELECT * FROM food_entries") suspend fun all(): List<FoodEntry>
    @Query("DELETE FROM food_entries") suspend fun clear()

    @Update
    suspend fun update(entry: FoodEntry)

    @Delete
    suspend fun delete(entry: FoodEntry)
}

// The answer shape of dayTotals(): one day's totals.
data class DayTotal(
    val epochDay: Long,
    val kcal: Double,
    val protein: Double,
    val carbs: Double,
    val fat: Double,
    val items: Int
)
