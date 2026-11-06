// ==============================================================================
// PLAIN-ENGLISH GUIDE
//
// WHAT THIS FILE IS: the shape of ONE line in your food diary
// ("2 eggs, breakfast, 143 kcal, on Nov 5").
//
// KEY IDEAS:
//  - "enum class" = a fixed list of choices. Meal can only be BREAKFAST, LUNCH,
//    DINNER or SNACK. Nothing else is allowed, which prevents typos.
//  - "data class" = a simple record that just holds values, like a row in a
//    spreadsheet. Each "val" inside is one column.
//  - "@Entity" tells the database (Room) to make a table for it, named
//    "food_entries". "@PrimaryKey(autoGenerate = true)" = the database numbers
//    each row 1, 2, 3... by itself.
// ==============================================================================
package com.gymnam.app.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Meal sections shown on the Today screen. Stored by name, so order can change safely. */
enum class Meal { BREAKFAST, LUNCH, DINNER, SNACK }

// GRAM = you typed grams. SERVING = you typed servings/pieces (e.g. 2 eggs).
/** How the amount is measured: grams or servings/pieces (your choice). */
enum class AmountUnit { GRAM, SERVING }

// indices = [Index("epochDay")] makes "show me one day" fast, like a book index.
/**
 * One logged food. Kept flat and self-contained (calories/macros copied in at log time)
 * so editing a saved food later never rewrites your history, and so the CSV export
 * and the future gym app can read it without joins.
 */
@Entity(tableName = "food_entries", indices = [Index("epochDay")])
data class FoodEntry(
    // epochDay = the date stored as a plain number: days since Jan 1, 1970.
    // Nov 5, 2026 is 20,762. Numbers are easy to sort and compare, unlike text dates.
    // "Long" = a whole number that can be very big. "Double" = a number with decimals.
    // "String" = text. "Int" = a normal whole number.
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val epochDay: Long,                 // LocalDate.toEpochDay(), local-time day
    val meal: Meal,
    val name: String,
    val emoji: String,
    val amount: Double,
    val unit: AmountUnit,
    val calories: Int,                  // kcal for the amount eaten
    val proteinG: Double = 0.0,
    val carbsG: Double = 0.0,
    val fatG: Double = 0.0,
    // createdAt = the exact moment you logged it (milliseconds since 1970).
    // "= System.currentTimeMillis()" means: if nobody gives a value, use "right now".
    val createdAt: Long = System.currentTimeMillis()
)
