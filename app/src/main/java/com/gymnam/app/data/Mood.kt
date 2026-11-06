// ==============================================================================
// PLAIN-ENGLISH GUIDE
// WHAT THIS FILE IS: the mood log ("How do you feel?").
//  - MoodEntry = one check-in: how you felt (1-5), any symptoms, which foods you
//    think caused it ("this made me sick"), a short note, and when.
//  - MoodDao   = the database actions for it.
//  - Mood / Symptom = the fixed choices shown on screen.
//  - suspectCounts() = "possible triggers": foods that show up again and again in
//    the times you felt bad. It's a pattern finder, NOT a diagnosis.
//
// PRIVACY: stays on the phone like everything else; included in your backup zip
// (mood.csv) so you don't lose it, never sent anywhere.
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

/** The 5 moods, from best (5) to worst (1). "level" is what's saved in the database. */
enum class Mood(val level: Int, val emoji: String) {
    GREAT(5, "😄"), GOOD(4, "🙂"), OKAY(3, "😐"), MEH(2, "😕"), SICK(1, "🤢");

    companion object {
        fun of(level: Int) = entries.firstOrNull { it.level == level } ?: OKAY
    }
}

/** Symptoms you can tick. "id" is what's saved (never translated, so it survives a language change). */
enum class Symptom(val id: String, val emoji: String) {
    NAUSEA("nausea", "🤢"), BLOATING("bloating", "🎈"), STOMACH_PAIN("stomach_pain", "🤕"),
    DIARRHEA("diarrhea", "🚽"), HEARTBURN("heartburn", "🔥"), HEADACHE("headache", "🤯"),
    RASH("rash", "🔴"), TIRED("tired", "😴");

    companion object {
        fun of(id: String) = entries.firstOrNull { it.id == id }
    }
}

@Entity(tableName = "mood_entries", indices = [Index("epochDay")])
data class MoodEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val epochDay: Long,
    /** Exact time of the check-in (milliseconds since 1970). */
    val createdAt: Long = System.currentTimeMillis(),
    /** 1 (sick) ... 5 (great); see Mood. */
    val mood: Int,
    /** Symptom ids separated by commas: "nausea,bloating". Empty = none. */
    val symptoms: String = "",
    /**
     * Foods you suspect, one name per line. We save the NAMES (not links to diary rows),
     * so the record still makes sense even if you later delete or edit that food.
     */
    val foods: String = "",
    val note: String = ""
) {
    val moodEnum get() = Mood.of(mood)
    val symptomList get() = symptoms.split(',').mapNotNull { Symptom.of(it.trim()) }
    val foodList get() = foods.split('\n').map { it.trim() }.filter { it.isNotEmpty() }
}

@Dao
interface MoodDao {
    @Insert suspend fun insert(m: MoodEntry): Long
    @Delete suspend fun delete(m: MoodEntry)

    // One day's check-ins, oldest first (live).
    @Query("SELECT * FROM mood_entries WHERE epochDay = :day ORDER BY createdAt")
    fun forDay(day: Long): Flow<List<MoodEntry>>

    // A date range, for the calendar (live). BETWEEN includes both ends.
    @Query("SELECT * FROM mood_entries WHERE epochDay BETWEEN :from AND :to ORDER BY createdAt")
    fun between(from: Long, to: Long): Flow<List<MoodEntry>>

    // Every check-in where you felt bad (mood 1 or 2) and named at least one food (live).
    @Query("SELECT * FROM mood_entries WHERE mood <= 2 AND foods != '' ORDER BY createdAt DESC")
    fun badWithFoods(): Flow<List<MoodEntry>>

    // Used by backup export / restore.
    @Query("SELECT * FROM mood_entries") suspend fun all(): List<MoodEntry>
    @Insert suspend fun insertAll(l: List<MoodEntry>)
    @Query("DELETE FROM mood_entries") suspend fun clear()
}

/** A food and how many "felt bad" check-ins mention it. */
data class Suspect(val food: String, val times: Int)

/**
 * POSSIBLE TRIGGERS: counts how often each food was named when you felt bad.
 * Names are compared without accents or upper/lower case ("Leche" = "leche").
 * Only foods named 2 or more times are returned, most frequent first (max 5).
 * Example: milk named in 3 bad check-ins, bread in 1 -> [milk x3].
 */
fun suspectCounts(bad: List<MoodEntry>): List<Suspect> =
    bad.flatMap { m -> m.foodList.distinctBy { Catalog.normalize(it) } }
        .groupBy { Catalog.normalize(it) }
        .map { (_, names) -> Suspect(names.first(), names.size) }
        .filter { it.times >= 2 }
        .sortedByDescending { it.times }
        .take(5)
