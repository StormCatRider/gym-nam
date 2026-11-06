// ==============================================================================
// PLAIN-ENGLISH GUIDE
// WHAT THIS FILE IS: the allergy & diet warnings (the red "⚠️ Peanuts" tags).
//
// HOW IT WORKS: each allergy/diet has a list of KEYWORDS in English and Spanish.
// If a food's NAME (or a recipe ingredient's name) contains one of them, the app
// shows a warning. Example: allergy "Milk" -> keywords cheese, leche, yogurt...
// so "Quesadilla de pollo" and "Greek yogurt" get "⚠️ Milk".
//
// "EXCLUDES" are phrases removed BEFORE checking, to avoid false alarms:
//   "Peanut butter" is not dairy, "Almond milk" is not milk, "Turkey bacon" is not pork.
//
// LIMITS (also shown to the user): it can only read the NAME. It cannot see hidden
// ingredients in packaged foods or restaurant dishes, so it may miss things.
// ALWAYS CHECK THE LABEL. This is a helper, not a medical tool.
//
// The list of allergens follows the 9 major food allergens in U.S. law (FALCPA 2004 +
// FASTER Act 2021): milk, egg, fish, crustacean shellfish, tree nuts, peanuts, wheat,
// soy, sesame. Mollusks (clams, squid...) are included with shellfish because many
// people with shellfish allergy avoid both.
//
// SCANNED / ONLINE PRODUCTS: Open Food Facts also lists the allergens printed on the
// product's label ("allergens_tags"); checkItem() uses those too.
//
// TO ADD A KEYWORD: add it (lowercase, no accents) to the right list below.
// ==============================================================================
package com.gymnam.app.data

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import com.gymnam.app.R

/** What you picked on the Me page. Read anywhere with LocalDiet.current. */
data class DietPrefs(
    val allergies: Set<String> = emptySet(),
    val diets: Set<String> = emptySet(),
    /** Extra words typed under "Other", comma-separated: "kiwi, mustard". */
    val other: String = ""
) {
    val isEmpty get() = allergies.isEmpty() && diets.isEmpty() && other.isBlank()
}

/** Set by MainActivity from your settings, so every screen can check foods. */
val LocalDiet = staticCompositionLocalOf { DietPrefs() }

/**
 * One allergy or diet. nameRes = its name ("Milk", "Vegetarian"); chipRes = the warning
 * text on a food ("Milk", "Not vegetarian"); custom = the word, for "Other" allergies.
 */
data class Flag(val id: String, val emoji: String, val nameRes: Int, val chipRes: Int, val isDiet: Boolean, val custom: String? = null)

private class Rule(val flag: Flag, val keys: List<String>, val excludes: List<String>)

object Allergens {
    // ---------------- keyword lists (lowercase, no accents) ----------------
    private val PEANUT = listOf("peanut", "cacahuate", "cacahuete", "mani")
    private val TREE_NUT = listOf("almond", "walnut", "cashew", "pecan", "pistachio", "hazelnut", "macadamia",
        "brazil nut", "pine nut", "praline", "marzipan", "nut", "mixed nuts", "nut butter",
        "almendra", "nuez", "nueces", "nuez de la india", "pistache", "pistacho", "avellana", "pinon", "maranon")
    private val TREE_NUT_X = listOf("nuez moscada", "coconut")
    private val MILK = listOf("milk", "cheese", "butter", "buttermilk", "yogurt", "yoghurt", "cream", "whey", "casein",
        "ghee", "kefir", "curd", "latte", "cappuccino", "mozzarella", "parmesan", "cheddar", "ricotta", "feta", "brie",
        "paneer", "custard", "ice cream", "milkshake", "alfredo", "pizza", "quesadilla", "lasagna", "mac and cheese",
        "queso", "leche", "lacteo", "mantequilla", "crema", "yogur", "requeson", "jocoque", "nata", "dulce de leche",
        "cajeta", "flan", "helado", "licuado", "atole",
        "caesar", "cesar")
    private val MILK_X = listOf("peanut butter", "almond butter", "nut butter", "apple butter", "cocoa butter",
        "mantequilla de mani", "mantequilla de cacahuate", "crema de cacahuate", "almond milk", "oat milk", "soy milk",
        "coconut milk", "coconut cream", "rice milk", "cashew milk", "leche de almendra", "leche de avena",
        "leche de soya", "leche de soja", "leche de coco", "leche de arroz", "cream of wheat", "dairy free",
        "sin lacteos", "vegan cheese", "queso vegano", "non dairy", "butternut", "creamy", "butter lettuce", "lechuga mantequilla", "crema de almendra", "te helado")
    private val EGG = listOf("egg", "huevo", "mayonnaise", "mayo", "mayonesa", "meringue", "merengue", "omelet",
        "omelette", "frittata", "french toast", "pan frances", "flan", "custard", "eggnog", "rompope", "quiche",
        "egg noodles", "aioli")
    private val EGG_X = listOf("egg free", "eggless", "sin huevo", "vegan mayo", "mayonesa vegana")
    private val WHEAT = listOf("wheat", "trigo", "flour", "harina", "bread", "toast", "pan", "pasta", "spaghetti", "noodle",
        "macaroni", "couscous", "barley", "cebada", "rye", "centeno", "bagel", "croissant", "flour tortilla",
        "tortilla de harina", "cracker", "galleta", "cookie", "cake", "pastel", "muffin", "biscuit", "waffle",
        "pancake", "hotcake", "donut", "doughnut", "dona", "brownie", "pretzel", "pita", "naan", "seitan", "bulgur",
        "semolina", "farro", "spelt", "beer", "cerveza", "soy sauce", "salsa de soya", "pizza", "burrito",
        "sandwich", "torta", "bolillo", "telera", "concha", "pan dulce", "empanada", "lasagna", "ravioli",
        "tortellini", "dumpling", "gyoza", "ramen", "udon", "bran flakes", "raisin bran", "cupcake", "cheesecake",
        "crouton", "cruton", "caesar salad", "ensalada cesar", "wrap")
    private val WHEAT_X = listOf("gluten free", "sin gluten", "corn flour", "harina de maiz", "rice flour",
        "harina de arroz", "almond flour", "harina de almendra", "rice noodles", "fideos de arroz", "corn tortilla",
        "tortilla de maiz", "rice cake", "pan fried", "pan seared", "masa harina", "pancake syrup",
        "miel para hot cakes", "pasta de tomate", "tomato paste", "root beer", "spaghetti squash",
        "oat protein pancakes", "hot cakes de avena", "lettuce wrap", "wrap de lechuga")
    private val SHELLFISH = listOf("shrimp", "prawn", "crab", "lobster", "crawfish", "crayfish", "scallop", "clam",
        "mussel", "oyster", "squid", "calamari", "octopus", "camaron", "gamba", "langosta", "langostino", "cangrejo",
        "jaiba", "almeja", "mejillon", "ostion", "ostra", "callo de hacha", "pulpo", "calamar")
    private val FISH = listOf("fish", "salmon", "tuna", "cod", "tilapia", "trout", "sardine", "anchovy", "halibut",
        "mackerel", "catfish", "haddock", "pollock", "mahi", "swordfish", "snapper", "sea bass", "fish sauce", "sushi",
        "surimi", "worcestershire", "caesar", "cesar", "pescado", "atun", "bacalao", "trucha", "sardina", "anchoa",
        "mojarra", "huachinango", "robalo", "pez espada", "sierra")
    // "Tuna (fruta)" = prickly pear in Spanish, not the fish.
    private val FISH_X = listOf("tuna fruta", "tunas fruta", "prickly pear", "sushi rice", "arroz para sushi",
        "fish crackers", "galletas de pescadito")
    private val SOY = listOf("soy", "soya", "soja", "tofu", "edamame", "tempeh", "miso", "soy sauce", "salsa de soya")
    private val SESAME = listOf("sesame", "ajonjoli", "tahini", "tahina", "hummus", "halva")

    private val MEAT = listOf("chicken", "beef", "pork", "turkey", "ham", "bacon", "sausage", "steak", "meat", "lamb",
        "veal", "chorizo", "jerky", "pepperoni", "salami", "prosciutto", "hot dog", "burger", "hamburger", "meatball",
        "brisket", "rib", "sirloin", "ribeye", "filet mignon", "gelatin", "lard", "wings", "drumstick",
        "pollo", "res", "cerdo", "puerco", "pavo", "jamon", "tocino", "salchicha", "carne", "cordero", "ternera",
        "carnitas", "barbacoa", "pastor", "bistec", "arrachera", "cecina", "chicharron", "birria", "suadero",
        "longaniza", "pechuga", "muslo", "alitas", "pozole", "cochinita", "grenetina", "pastrami", "bologna", "bison",
        "venison", "duck", "pato", "mortadela", "bolonia", "lengua", "tripa", "menudo", "pancita", "chistorra",
        "costilla", "milanesa", "albondiga", "picadillo", "machaca", "tinga")
    private val VEG_X = listOf("veggie burger", "plant based burger", "vegetarian", "vegetariano", "vegetariana", "vegan", "vegano", "vegana",
        "plant based", "black bean burger", "soy chorizo", "chorizo de soya", "vegetable broth", "caldo de verduras",
        "meatless", "sin carne", "coconut meat", "carne de coco", "hamburger bun", "hot dog bun",
        "pan para hamburguesa", "pan para hot dog", "milanesa de soya")
    private val PORK = listOf("pork", "ham", "bacon", "sausage", "chorizo", "pepperoni", "salami", "prosciutto",
        "carnitas", "pastor", "chicharron", "pork rinds", "lard", "cerdo", "puerco", "jamon", "tocino", "salchicha",
        "longaniza", "hot dog", "manteca de cerdo", "pozole", "cochinita")
    private val PORK_X = listOf("turkey bacon", "turkey ham", "turkey sausage", "chicken sausage", "beef sausage",
        "jamon de pavo", "tocino de pavo", "salchicha de pavo", "chorizo de pavo", "pozole de pollo",
        "beef pepperoni", "soy chorizo", "chorizo de soya", "veggie sausage", "chicken hot dog", "salchicha de pollo",
        "pozole blanco de pollo", "hot dog bun", "pan para hot dog", "para hot dog", "chicharrones de harina",
        "wheat chicharrones", "duritos")
    private val LACTOSE_X = listOf("lactose free", "sin lactosa", "deslactosada", "deslactosado")

    private val RULES: List<Rule> = listOf(
        Rule(Flag("peanut", "🥜", R.string.allergen_peanut, R.string.allergen_peanut, false), PEANUT, emptyList()),
        Rule(Flag("tree_nut", "🌰", R.string.allergen_tree_nut, R.string.allergen_tree_nut, false), TREE_NUT, TREE_NUT_X),
        Rule(Flag("milk", "🥛", R.string.allergen_milk, R.string.allergen_milk, false), MILK, MILK_X),
        Rule(Flag("egg", "🥚", R.string.allergen_egg, R.string.allergen_egg, false), EGG, EGG_X),
        Rule(Flag("wheat", "🌾", R.string.allergen_wheat, R.string.allergen_wheat, false), WHEAT, WHEAT_X),
        Rule(Flag("shellfish", "🦐", R.string.allergen_shellfish, R.string.allergen_shellfish, false), SHELLFISH, emptyList()),
        Rule(Flag("fish", "🐟", R.string.allergen_fish, R.string.allergen_fish, false), FISH, FISH_X),
        Rule(Flag("soy", "🫘", R.string.allergen_soy, R.string.allergen_soy, false), SOY, emptyList()),
        Rule(Flag("sesame", "🥯", R.string.allergen_sesame, R.string.allergen_sesame, false), SESAME, emptyList()),
        // Diets: same idea, the warning says what conflicts ("Not vegetarian").
        Rule(Flag("vegetarian", "🥕", R.string.diet_vegetarian, R.string.conflict_vegetarian, true),
            MEAT + FISH + SHELLFISH, VEG_X + FISH_X),
        Rule(Flag("vegan", "🌱", R.string.diet_vegan, R.string.conflict_vegan, true),
            MEAT + FISH + SHELLFISH + MILK + EGG + listOf("honey", "miel"), VEG_X + FISH_X + MILK_X + EGG_X + listOf("honeydew")),
        Rule(Flag("no_pork", "🐷", R.string.diet_no_pork, R.string.conflict_no_pork, true), PORK, PORK_X),
        Rule(Flag("lactose_free", "🥛", R.string.diet_lactose_free, R.string.conflict_lactose_free, true),
            MILK, MILK_X + LACTOSE_X),
    )

    /** Allergies offered on the Me page, in order. */
    val allergyFlags: List<Flag> get() = RULES.filter { !it.flag.isDiet }.map { it.flag }
    /** Diets offered on the Me page. */
    val dietFlags: List<Flag> get() = RULES.filter { it.flag.isDiet }.map { it.flag }

    /**
     * Which of YOUR allergies/diets this food name may conflict with. Empty list = no warning.
     * Example: "Peanut butter toast" with allergies {peanut, milk} -> [Peanuts]
     * (not Milk, because "peanut butter" is removed before the milk check).
     */
    fun check(name: String, p: DietPrefs): List<Flag> {
        if (p.isEmpty || name.isBlank()) return emptyList()
        val base = " " + Catalog.normalize(name).replace(',', ' ').replace('/', ' ') + " "
        val out = RULES.filter { r ->
            (r.flag.id in p.allergies || r.flag.id in p.diets) && matchesAny(strip(base, r.excludes), r.keys)
        }.map { it.flag }.toMutableList()
        // "Other" allergies you typed yourself.
        p.other.split(',').map { Catalog.normalize(it) }.filter { it.length >= 2 }.forEach { w ->
            if (matchesAny(base, listOf(w)))
                out += Flag("other:$w", "⚠️", R.string.allergen_other, R.string.allergen_other, false, custom = w)
        }
        return out
    }

    /**
     * Open Food Facts allergen tags -> our allergy ids. These come from the product's label,
     * so for scanned/online products they're more reliable than the name alone.
     */
    private val TAGS = mapOf(
        "en:milk" to "milk", "en:peanuts" to "peanut", "en:nuts" to "tree_nut", "en:eggs" to "egg",
        "en:gluten" to "wheat", "en:fish" to "fish", "en:crustaceans" to "shellfish", "en:molluscs" to "shellfish",
        "en:soybeans" to "soy", "en:sesame-seeds" to "sesame"
    )

    /** Which diets an allergen tag breaks (milk isn't vegan or lactose-free, fish isn't vegetarian...). */
    private val TAG_DIETS = mapOf(
        "milk" to listOf("vegan", "lactose_free"), "egg" to listOf("vegan"),
        "fish" to listOf("vegetarian", "vegan"), "shellfish" to listOf("vegetarian", "vegan")
    )

    /** check() for a food from the list or online: name keywords + the product's own allergen tags. */
    fun checkItem(name: String, tags: List<String>, p: DietPrefs): List<Flag> {
        val byName = check(name, p)
        if (tags.isEmpty() || p.isEmpty) return byName
        val ids = tags.mapNotNull { TAGS[it] }.toSet()
        val hit = (ids + ids.flatMap { TAG_DIETS[it].orEmpty() }).filter { it in p.allergies || it in p.diets }
        val extra = RULES.map { it.flag }.filter { it.id in hit && byName.none { b -> b.id == it.id } }
        return byName + extra
    }

    /** Removes the "excludes" phrases from the name (padded with spaces). */
    private fun strip(base: String, excludes: List<String>): String {
        var s = base
        excludes.forEach { x -> s = s.replace(Regex(" " + Regex.escape(x) + "(es|s)? "), " ") }
        return s
    }

    /**
     * Same rules as the fridge matching (data/FridgeMatch.kt):
     *  - several-word keys must appear as a phrase ("soy sauce");
     *  - one-word keys must be a whole word, plural allowed ("egg" -> "eggs", but NOT "eggplant");
     *  - keys of 5+ letters also match the start of a word ("cheese" -> "cheeseburger").
     */
    private fun matchesAny(base: String, keys: List<String>): Boolean {
        val words = base.trim().split(' ').filter { it.isNotEmpty() }
        return keys.any { k ->
            if (k.contains(' ')) Regex(" " + Regex.escape(k) + "(es|s)? ").containsMatchIn(base)
            else words.any { w -> w == k || w == k + "s" || w == k + "es" || (k.length >= 5 && w.startsWith(k)) }
        }
    }
}

/** Warnings for a whole recipe: its name plus every ingredient's name (no repeats). */
@Composable
fun rememberRecipeFlags(r: RecipeModel): List<Flag> {
    val p = LocalDiet.current
    return remember(r.key, r.name, r.ingredients, p) {
        (listOf(r.name) + r.ingredients.map { it.name }).flatMap { Allergens.check(it, p) }.distinctBy { it.id }
    }
}

/** Warnings for a pickable food (name + online allergen tags). */
@Composable
fun rememberItemFlags(item: FoodItem): List<Flag> {
    val p = LocalDiet.current
    return remember(item.key, item.name, p) { Allergens.checkItem(item.name, item.allergenTags, p) }
}

/** Warnings for one food name, re-checked only when the name or your choices change. */
@Composable
fun rememberFlags(name: String): List<Flag> {
    val p = LocalDiet.current
    return remember(name, p) { Allergens.check(name, p) }
}
