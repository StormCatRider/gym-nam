// ==============================================================================
// PLAIN-ENGLISH GUIDE
//
// WHAT THIS FILE IS: talks to Open Food Facts, a free public food database on the
// internet, for barcode scans and "Search online".
//
// PRIVACY: every request first passes "gate()". If you turned internet OFF in
// Settings, the app stops right there and never connects. Only your search words
// or the barcode number are sent: no name, account, email or location.
// ==============================================================================
package com.gymnam.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

// A custom error meaning "internet is off". Screens catch it and show a friendly
// message instead of crashing.
class OfflineException : Exception("Internet is turned off in Gym & Ñam settings")

/**
 * Open Food Facts (free, open database, no API key).
 * EVERY call passes through [gate] first. When the in-app internet switch is off,
 * the request is never made: no DNS lookup, no connection, nothing.
 *
 * Only the search words or barcode are sent. No account, ID, email or location.
 */
class OpenFoodFacts(private val settings: SettingsStore) {

    // The privacy gate: no internet permission from you -> stop.
    private suspend fun gate() {
        if (!settings.internetEnabled()) throw OfflineException()
    }

    // Builds the web address with your words (URLEncoder makes spaces/accents safe for
    // a URL), downloads the answer, and turns each product into a FoodItem.
    /** Text search. OFF limits search to ~10 requests/minute, so the app only searches when you tap "Search online". */
    suspend fun search(query: String, spanish: Boolean): List<FoodItem> {
        gate()
        val q = URLEncoder.encode(query.trim(), "UTF-8")
        val url = "https://world.openfoodfacts.org/cgi/search.pl?search_terms=$q&search_simple=1" +
            "&action=process&json=1&page_size=30&fields=$FIELDS"
        val json = get(url)
        val arr = json.optJSONArray("products") ?: return emptyList()
        return (0 until arr.length()).mapNotNull { parse(arr.getJSONObject(it), spanish) }
    }

    // Looks up one barcode. filter(Char::isDigit) keeps only the digits.
    // status 1 = found. Returns null if the product isn't in their database.
    /** Barcode lookup (EAN-13, UPC-A, EAN-8, UPC-E). Null if not in the database or no nutrition data. */
    suspend fun byBarcode(code: String, spanish: Boolean): FoodItem? {
        gate()
        val clean = code.filter(Char::isDigit)
        val json = get("https://world.openfoodfacts.org/api/v2/product/$clean.json?fields=$FIELDS")
        if (json.optInt("status", 0) != 1) return null
        return json.optJSONObject("product")?.let { parse(it, spanish, clean) }
    }

    // The actual download. Gives up after 10 s trying to connect / 15 s reading.
    // HTTP codes 200-299 mean success; anything else counts as an error.
    // "finally" = always close the connection, even if something failed.
    private suspend fun get(url: String): JSONObject = withContext(Dispatchers.IO) {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 15_000
            // OFF asks apps to identify themselves; this is generic and holds no personal data.
            setRequestProperty("User-Agent", "GymNam/0.2 (Android; personal calorie app)")
            setRequestProperty("Accept", "application/json")
        }
        try {
            if (conn.responseCode !in 200..299) throw java.io.IOException("HTTP ${conn.responseCode}")
            JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
        } finally {
            conn.disconnect()
        }
    }

    // Turns Open Food Facts' answer into a FoodItem.
    //  - Calories: uses kcal per 100 g; if only kilojoules exist, kJ / 4.184 = kcal.
    //  - Name: tries your language first, then the general name, then the other language.
    //  - Serving: uses the product's serving size if given, otherwise 100 g.
    //  - "?:" means "if the left side is empty, use the right side".
    private fun parse(p: JSONObject, spanish: Boolean, code: String? = null): FoodItem? {
        val n = p.optJSONObject("nutriments") ?: return null
        val kcal100 = n.optDouble("energy-kcal_100g").takeUnless { it.isNaN() }
            ?: n.optDouble("energy_100g").takeUnless { it.isNaN() }?.let { it / 4.184 } // kJ → kcal
            ?: return null
        val name = listOf(
            if (spanish) "product_name_es" else "product_name_en", "product_name",
            if (spanish) "product_name_en" else "product_name_es"
        ).map { p.optString(it).trim() }.firstOrNull { it.isNotEmpty() } ?: return null
        val brand = p.optString("brands").split(',').firstOrNull()?.trim()?.takeIf { it.isNotEmpty() }

        val sq = p.optDouble("serving_quantity").takeIf { !it.isNaN() && it > 0 }
        val servingG = sq ?: 100.0
        val label = if (sq != null) p.optString("serving_size").ifBlank { "${fmtNum(sq)} g" } else "100 g"
        val k = servingG / 100.0
        fun g(key: String) = n.optDouble(key).takeUnless { it.isNaN() } ?: 0.0

        return FoodItem(
            key = "off:${code ?: p.optString("code")}",
            name = name,
            emoji = com.gymnam.app.ui.common.guessEmoji(name) ?: "🛒",
            servingLabel = label,
            servingG = servingG,
            kcal = kcal100 * k,
            proteinG = g("proteins_100g") * k,
            carbsG = g("carbohydrates_100g") * k,
            fatG = g("fat_100g") * k,
            source = FoodSource.ONLINE,
            barcode = code ?: p.optString("code").takeIf { it.isNotBlank() },
            brand = brand
        )
    }

    private companion object {
        // Only ask for the columns we actually use (smaller, faster download).
        const val FIELDS = "code,product_name,product_name_en,product_name_es,brands,nutriments,serving_quantity,serving_size"
    }
}
