// ==============================================================================
// PLAIN-ENGLISH GUIDE
//
// WHAT THIS FILE IS: the built-in food list (assets/foods.csv, 1,000+ foods)
// and its search box logic.
//
// THE CSV FILE: one food per line, columns separated by ";".
//  0 English name; 1 Spanish name; 2 emoji; 3 category; 4 kcal per 100 g;
//  5 protein; 6 carbs; 7 fat (grams per 100 g); 8 serving name EN;
//  9 serving name ES; 10 grams in one serving.
// To fix a food's numbers, edit tools/foods (the source) and regenerate.
// ==============================================================================
package com.gymnam.app.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.text.Normalizer

/**
 * The 1,000+ built-in foods (assets/foods.csv), kept in memory (~100 KB).
 * Values are per 100 g; we convert to per-serving FoodItems on search.
 */
class Catalog(private val context: Context) {

    // One line of the CSV, already split into its columns.
    private class Row(
        val en: String, val es: String, val emoji: String,
        val kcal: Double, val p: Double, val c: Double, val f: Double,
        val servingEn: String, val servingEs: String, val servingG: Double,
        val keyEn: String, val keyEs: String
    )

    private val mutex = Mutex()
    private var rows: List<Row>? = null

    // Reads the CSV the first time it's needed and keeps it in memory.
    // drop(1) skips the first line (the column titles). split(';') cuts a line into
    // columns. Lines with fewer than 11 columns are ignored instead of crashing.
    private suspend fun load(): List<Row> = mutex.withLock {
        rows ?: withContext(Dispatchers.IO) {
            context.assets.open("foods.csv").bufferedReader(Charsets.UTF_8).useLines { lines ->
                lines.drop(1).mapNotNull { line ->
                    val t = line.split(';')
                    if (t.size < 11) return@mapNotNull null
                    Row(
                        en = t[0], es = t[1], emoji = t[2],
                        kcal = t[4].toDouble(), p = t[5].toDouble(), c = t[6].toDouble(), f = t[7].toDouble(),
                        servingEn = t[8], servingEs = t[9], servingG = t[10].toDouble(),
                        keyEn = normalize(t[0]), keyEs = normalize(t[1])
                    )
                }.toList()
            }
        }.also { rows = it }
    }

    // Example: you type "pollo asado". The app keeps foods whose English OR Spanish
    // name contains BOTH words, then ranks them:
    //   score 0 = name starts with what you typed
    //   score 1 = a word in the name starts with it
    //   score 2 = it's somewhere in the middle
    // Ties go to the shorter name ("Egg" before "Egg salad sandwich").
    /**
     * Every word of the query must appear in the English OR Spanish name.
     * Ranked: name starts with query > a word starts with it > contains; then shorter names.
     */
    suspend fun search(query: String, spanish: Boolean, limit: Int = 60): List<FoodItem> {
        val q = normalize(query)
        if (q.isBlank()) return emptyList()
        val words = q.split(' ').filter { it.isNotBlank() }
        return withContext(Dispatchers.Default) {
            load().asSequence()
                .mapNotNull { r ->
                    val inEn = words.all { r.keyEn.contains(it) }
                    val inEs = words.all { r.keyEs.contains(it) }
                    if (!inEn && !inEs) return@mapNotNull null
                    val key = if (spanish && inEs || !inEn) r.keyEs else r.keyEn
                    val score = when {
                        key.startsWith(q) -> 0
                        key.contains(" $q") || key.contains(",$q") -> 1
                        else -> 2
                    }
                    Triple(r, score, key.length)
                }
                .sortedWith(compareBy({ it.second }, { it.third }))
                .take(limit)
                .map { (r, _, _) -> r.toItem(spanish) }
                .toList()
        }
    }

    // The CSV stores values per 100 g. This converts them to ONE serving:
    // k = serving grams / 100.  Example: 1 egg = 50 g -> k = 0.5 -> 143 kcal x 0.5 = 72 kcal.
    private fun Row.toItem(spanish: Boolean): FoodItem {
        val k = servingG / 100.0
        return FoodItem(
            key = "cat:$en",
            name = if (spanish) es else en,
            emoji = emoji,
            servingLabel = if (spanish) servingEs else servingEn,
            servingG = servingG,
            kcal = kcal * k, proteinG = p * k, carbsG = c * k, fatG = f * k,
            source = FoodSource.CATALOG
        )
    }

    companion object {
        // Makes text easy to compare: lowercase, remove accents (á -> a, ñ -> n), turn odd
        // symbols into spaces and squeeze double spaces. "Plátano" -> "platano".
        /** Lowercase, strip accents (á→a, ñ→n), collapse spaces, so "platano" finds "Plátano". */
        fun normalize(s: String): String =
            Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD)
                .replace(Regex("\\p{Mn}+"), "")
                .replace(Regex("[^a-z0-9%/ ,]"), " ")
                .replace(Regex("\\s+"), " ")
                .trim()
    }
}
