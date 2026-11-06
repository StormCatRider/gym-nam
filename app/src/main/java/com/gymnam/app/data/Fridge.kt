// ==============================================================================
// PLAIN-ENGLISH GUIDE
//
// WHAT THIS FILE IS: the Fridge tab's data. One FridgeItem = one thing you have
// ("Eggs, 12 pieces, expires Nov 12"). FridgeDao = the database actions for it.
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

// Each unit knows how many grams it equals, so cooking a recipe can subtract
// "450 g chicken" from "2 lb chicken". PIECE and PACK have no gram value (null),
// so for those the app subtracts pieces, or lets you adjust by hand.
/**
 * How a fridge item is counted. Mass/volume units convert to grams so a recipe's
 * "450 g chicken" can be taken off "2 lb chicken" (1 lb = 453.6 g, 1 oz = 28.35 g).
 * Volume uses water density (1 ml ≈ 1 g): close enough for milk, juice and sauces.
 */
enum class FridgeUnit(val gramsPerUnit: Double?) {
    PIECE(null), PACK(null), G(1.0), OZ(28.35), LB(453.6), ML(1.0)
}

@Entity(tableName = "fridge_items")
data class FridgeItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val emoji: String,
    val quantity: Double,
    val unit: FridgeUnit,
    val expiryDay: Long? = null,      // LocalDate.toEpochDay(); null = no date
    val barcode: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

@Dao
interface FridgeDao {
    // ORDER BY ... = items expiring soonest come first; items with no date go last;
    // then alphabetical (COLLATE NOCASE = ignore upper/lower case).
    /** Soonest expiry first; items without a date at the end. */
    @Query("SELECT * FROM fridge_items ORDER BY expiryDay IS NULL, expiryDay, name COLLATE NOCASE")
    fun all(): Flow<List<FridgeItem>>

    @Query("SELECT * FROM fridge_items")
    suspend fun allNow(): List<FridgeItem>

    @Query("SELECT * FROM fridge_items WHERE id = :id")
    suspend fun byId(id: Long): FridgeItem?

    @Insert suspend fun insert(item: FridgeItem): Long
    @Update suspend fun update(item: FridgeItem)
    @Delete suspend fun delete(item: FridgeItem)
    @Insert suspend fun insertAll(l: List<FridgeItem>)
    @Query("DELETE FROM fridge_items") suspend fun clear()
}
