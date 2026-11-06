// ==============================================================================
// PLAIN-ENGLISH GUIDE
//
// WHAT THIS FILE IS: the logic that answers "what can I cook with what's in my
// fridge?" and "what gets used up when I cook this?".
//
//  - matches()  : does this fridge item count as this ingredient?
//  - coverage() : "you have 4 of 6 ingredients", and whether any expire soon.
//  - plan()     : the list of fridge changes when you cook (e.g. Eggs 12 -> 9).
// ==============================================================================
package com.gymnam.app.data

// have / total ingredients (pantry staples not counted). ratio = have / total,
// e.g. 4 / 6 = 0.67. Used to sort recipes: best matches first.
/** How well your fridge covers a recipe. Staples are left out of the count. */
data class Coverage(
    val have: Int,
    val total: Int,
    val usesExpiring: Boolean,
    val missing: List<Ingredient>
) {
    val ratio: Double get() = if (total == 0) 1.0 else have.toDouble() / total
}

// One planned change: take `amount` off this fridge item.
/** One planned fridge change when you cook a recipe. */
data class Subtraction(val item: FridgeItem, val amount: Double, val ingredient: Ingredient)

object FridgeMatch {

    // "Expiring soon" = today up to 3 days from now.
    /** Expiring = today through 3 days from now. */
    const val SOON_DAYS = 3

    // EXAMPLES of the rules below:
    //  "huevo" matches "Huevos" (plural OK), "papa" matches "Papas" but NOT "Papaya",
    //  "champinon" (5+ letters) matches "Champiñones" by its start,
    //  "Caldo de pollo" is NOT chicken because chicken excludes "caldo".
    /**
     * Does this fridge item count as this ingredient? (Names compared without accents, any case.)
     *  - A multi-word key must appear as a phrase: "salsa verde", "carne molida".
     *  - A one-word key must equal a word in the name, plural allowed: "huevo" → "Huevos", "papa" → "Papas"
     *    (but not "Papaya"). Keys of 5+ letters may also match the start of a word: "champinon" → "Champiñones".
     *  - Any exclude word rules it out: chicken keys exclude "caldo", so "Caldo de pollo" isn't chicken.
     */
    fun matches(ing: Ingredient, item: FridgeItem): Boolean {
        val name = Catalog.normalize(item.name).replace(',', ' ')   // "Pechuga, cruda" → phrase checks still work
        val words = name.split(' ').filter { it.isNotBlank() }
        if (ing.excludes.any { x -> if (x.contains(' ')) hasPhrase(name, x) else words.any { it.startsWith(x) } }) return false
        return ing.keys.any { key -> if (key.contains(' ')) hasPhrase(name, key) else words.any { wordMatches(key, it) } }
    }

    // A word counts if it equals the key, the key + "s", the key + "es", or (for
    // keys of 5+ letters) starts with the key.
    private fun wordMatches(key: String, word: String) =
        word == key || word == key + "s" || word == key + "es" || (key.length >= 5 && word.startsWith(key))

    // For multi-word keys like "salsa verde": the phrase must appear as whole words.
    // Regex = a text pattern; ( |$) means "followed by a space or the end".
    private fun hasPhrase(name: String, phrase: String) =
        Regex("(^| )" + Regex.escape(phrase) + "(es|s)?( |$)").containsMatchIn(name)

    // Picks the fridge item to use: has quantity left, matches, not expired;
    // if several match, use the one expiring soonest (so nothing goes to waste).
    /** Best fridge item for an ingredient: not expired, soonest expiry first. */
    fun find(ing: Ingredient, fridge: List<FridgeItem>, today: Long): FridgeItem? =
        fridge.filter { it.quantity > 0 && matches(ing, it) && (it.expiryDay ?: Long.MAX_VALUE) >= today }
            .minByOrNull { it.expiryDay ?: Long.MAX_VALUE }

    // Goes through each non-staple ingredient: found -> have + 1, else -> missing list.
    // Flags "uses something expiring soon" so those recipes can be highlighted.
    fun coverage(recipe: RecipeModel, fridge: List<FridgeItem>, today: Long): Coverage {
        val needed = recipe.ingredients.filter { !it.staple }
        var expiring = false
        val missing = mutableListOf<Ingredient>()
        var have = 0
        needed.forEach { ing ->
            val item = find(ing, fridge, today)
            if (item == null) missing += ing
            else {
                have++
                if (item.expiryDay != null && item.expiryDay - today <= SOON_DAYS) expiring = true
            }
        }
        return Coverage(have, needed.size, expiring, missing)
    }

    // How much to subtract, by the fridge item's unit:
    //  - weight/volume units: recipe grams / grams-per-unit
    //    (450 g chicken from a "lb" item = 450 / 453.6 = 0.99 lb)
    //  - pieces: the recipe's piece count (3 eggs)
    //  - packs, or pieces with no count: skipped, you adjust by hand.
    // Never subtracts more than you have (minOf).
    /**
     * What cooking the whole recipe takes out of the fridge.
     * Mass/volume items subtract grams converted to the item's unit; piece items subtract
     * the recipe's piece count; packs (and pieces with no count) are left for you to adjust.
     */
    fun plan(recipe: RecipeModel, fridge: List<FridgeItem>, today: Long): List<Subtraction> =
        recipe.ingredients.mapNotNull { ing ->
            val item = find(ing, fridge, today) ?: return@mapNotNull null
            val amount = when {
                item.unit.gramsPerUnit != null && ing.grams > 0 -> ing.grams / item.unit.gramsPerUnit
                item.unit == FridgeUnit.PIECE && ing.pieces > 0 -> ing.pieces
                else -> null
            } ?: return@mapNotNull null
            Subtraction(item, minOf(amount, item.quantity), ing)
        }
            // If two ingredients hit the same fridge item (e.g. egg + egg white), combine them.
            .groupBy { it.item.id }
            .map { (_, list) -> list.first().copy(amount = minOf(list.sumOf { it.amount }, list.first().item.quantity)) }
}
