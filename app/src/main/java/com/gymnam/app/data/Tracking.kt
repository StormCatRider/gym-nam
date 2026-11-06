// ==============================================================================
// PLAIN-ENGLISH GUIDE
//
// WHAT THIS FILE IS: the three tracking tables and their database actions.
//  - WeightEntry   = one weigh-in per day, in pounds (lb).
//  - WaterEntry    = one drink, in US fluid ounces (fl oz).
//  - ExerciseEntry = one workout; its calories are ADDED to your day's budget.
// ==============================================================================
package com.gymnam.app.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

// unique = true on epochDay means only ONE weight per day is allowed.
// Logging again the same day replaces the earlier number (see upsertWeight).
/** One weigh-in per day, in pounds. Logging again the same day replaces it. */
@Entity(tableName = "weight_entries", indices = [Index(value = ["epochDay"], unique = true)])
data class WeightEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val epochDay: Long,
    val lb: Double,
    val createdAt: Long = System.currentTimeMillis()
)

/** A drink of water, in US fluid ounces. */
@Entity(tableName = "water_entries", indices = [Index("epochDay")])
data class WaterEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val epochDay: Long,
    val flOz: Double,
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * Exercise. Burned calories are added to the day's budget.
 * source = "manual" (typed by you) or "hc" (imported from Health Connect), so they can be told apart.
 */
@Entity(tableName = "exercise_entries", indices = [Index("epochDay")])
data class ExerciseEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val epochDay: Long,
    val name: String,
    val emoji: String,
    val minutes: Int,
    val kcal: Int,
    // "manual" = you typed it. "hc" = imported from Health Connect (so a re-sync can
    // replace only the imported ones and never touch yours).
    val source: String = "manual",
    val createdAt: Long = System.currentTimeMillis()
)

// One number per day (e.g. total water on Nov 5), for charts.
/** Sum per day, for charts. */
data class DayValue(val epochDay: Long, val value: Double)

@Dao
interface TrackingDao {
    // Weight
    // "upsert" = update-or-insert: if today already has a weight, replace it.
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertWeight(w: WeightEntry)
    @Delete suspend fun deleteWeight(w: WeightEntry)
    @Query("SELECT * FROM weight_entries WHERE epochDay >= :from ORDER BY epochDay")
    fun weightsSince(from: Long): Flow<List<WeightEntry>>
    // Newest weigh-in (DESC = newest first, LIMIT 1 = only one).
    @Query("SELECT * FROM weight_entries ORDER BY epochDay DESC LIMIT 1")
    fun latestWeight(): Flow<WeightEntry?>
    @Query("SELECT * FROM weight_entries ORDER BY epochDay DESC LIMIT 1")
    suspend fun latestWeightNow(): WeightEntry?
    @Query("SELECT * FROM weight_entries WHERE epochDay = :day LIMIT 1")
    suspend fun weightForDay(day: Long): WeightEntry?

    // Water
    // COALESCE(SUM(flOz), 0) = add up the day's water; if there's none, answer 0
    // instead of "nothing".
    @Insert suspend fun insertWater(w: WaterEntry)
    @Query("SELECT COALESCE(SUM(flOz), 0) FROM water_entries WHERE epochDay = :day")
    fun waterForDay(day: Long): Flow<Double>
    @Query("SELECT COALESCE(SUM(flOz), 0) FROM water_entries WHERE epochDay = :day")
    suspend fun waterNow(day: Long): Double
    // Deletes only the most recent drink of that day (the Undo button).
    @Query("DELETE FROM water_entries WHERE id = (SELECT id FROM water_entries WHERE epochDay = :day ORDER BY createdAt DESC LIMIT 1)")
    suspend fun undoLastWater(day: Long)
    @Query("SELECT epochDay, SUM(flOz) AS value FROM water_entries WHERE epochDay >= :from GROUP BY epochDay ORDER BY epochDay")
    fun waterTotals(from: Long): Flow<List<DayValue>>

    // Exercise
    @Insert suspend fun insertExercise(e: ExerciseEntry)
    @Delete suspend fun deleteExercise(e: ExerciseEntry)
    /** Health Connect's daily activity entry is replaced on every sync. */
    @Query("DELETE FROM exercise_entries WHERE epochDay = :day AND source = 'hc'")
    suspend fun deleteHcExercise(day: Long)
    @Query("SELECT * FROM exercise_entries WHERE epochDay = :day ORDER BY createdAt")
    fun exerciseForDay(day: Long): Flow<List<ExerciseEntry>>
    // Total exercise calories for one day.
    @Query("SELECT COALESCE(SUM(kcal), 0) FROM exercise_entries WHERE epochDay = :day")
    suspend fun burnedNow(day: Long): Int
    @Query("SELECT epochDay, SUM(kcal) AS value FROM exercise_entries WHERE epochDay >= :from GROUP BY epochDay ORDER BY epochDay")
    fun burnedTotals(from: Long): Flow<List<DayValue>>

    // Backup
    @Query("SELECT * FROM weight_entries") suspend fun allWeights(): List<WeightEntry>
    @Query("SELECT * FROM water_entries") suspend fun allWater(): List<WaterEntry>
    @Query("SELECT * FROM exercise_entries") suspend fun allExercise(): List<ExerciseEntry>
    @Insert suspend fun insertWeights(l: List<WeightEntry>)
    @Insert suspend fun insertWaters(l: List<WaterEntry>)
    @Insert suspend fun insertExercises(l: List<ExerciseEntry>)
    @Query("DELETE FROM weight_entries") suspend fun clearWeights()
    @Query("DELETE FROM water_entries") suspend fun clearWater()
    @Query("DELETE FROM exercise_entries") suspend fun clearExercise()
}
