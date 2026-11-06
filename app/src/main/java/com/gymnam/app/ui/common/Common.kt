// ==============================================================================
// PLAIN-ENGLISH GUIDE
//
// WHAT THIS FILE IS: small shared pieces used on many screens, so the app looks
// the same everywhere: the rounded card, the emoji circle, meal names and emojis,
// number formatting, and the emoji guesser.
// ==============================================================================
package com.gymnam.app.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gymnam.app.R
import com.gymnam.app.data.AmountUnit
import com.gymnam.app.data.Meal

// The rounded white card you see all over the app. "content" = whatever you put
// inside it. Change the corner radius or shadow here to restyle every card.
/** Rounded, soft card used everywhere so the app feels consistent. */
@Composable
fun SoftCard(
    modifier: Modifier = Modifier,
    padding: Dp = 18.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        shadowElevation = 1.dp
    ) {
        Column(Modifier.padding(padding), content = content)
    }
}

// An emoji inside a soft colored circle. The emoji is half the circle's size.
/** Emoji inside a soft tinted circle. */
@Composable
fun EmojiBadge(emoji: String, size: Dp = 40.dp, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.size(size),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primaryContainer
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(emoji, fontSize = (size.value * 0.5f).sp)
        }
    }
}

// A friendly "nothing here yet" message with a big emoji.
/** Friendly empty state: a big emoji and a short message. */
@Composable
fun ComingSoon(emoji: String, message: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            EmojiBadge(emoji, size = 96.dp)
            Text(
                message,
                modifier = Modifier.padding(top = 20.dp),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

// "fun Meal.labelRes()" adds a helper to every Meal: BREAKFAST.labelRes() gives the
// text "Breakfast" / "Desayuno" from the strings files (R.string.xxx).
fun Meal.labelRes(): Int = when (this) {
    Meal.BREAKFAST -> R.string.meal_breakfast
    Meal.LUNCH -> R.string.meal_lunch
    Meal.DINNER -> R.string.meal_dinner
    Meal.SNACK -> R.string.meal_snack
}

fun Meal.emoji(): String = when (this) {
    Meal.BREAKFAST -> "🍳"
    Meal.LUNCH -> "🥗"
    Meal.DINNER -> "🍲"
    Meal.SNACK -> "🍎"
}

// Picks the meal from the clock when you tap +:
//  4 AM-10 AM breakfast | 11 AM-3 PM lunch | 4 PM-9 PM dinner | otherwise snack.
/** Default meal by clock time so the + button usually lands in the right section. */
fun mealForNow(hour: Int): Meal = when (hour) {
    in 4..10 -> Meal.BREAKFAST
    in 11..15 -> Meal.LUNCH
    in 16..21 -> Meal.DINNER
    else -> Meal.SNACK
}

// "150 g", "1 serving", "1.5 servings" in your language.
/** "150 g", "1 serving", "1.5 servings" */
@Composable
fun formatAmount(amount: Double, unit: AmountUnit): String {
    val n = if (amount % 1.0 == 0.0) amount.toLong().toString() else "%.1f".format(amount)
    val u = when (unit) {
        AmountUnit.GRAM -> androidx.compose.ui.res.stringResource(R.string.unit_gram)
        AmountUnit.SERVING -> androidx.compose.ui.res.stringResource(
            if (amount == 1.0) R.string.unit_serving else R.string.unit_servings
        )
    }
    return "$n $u"
}

// Reads a number you typed. Spanish keyboards use a comma (1,5), so the comma is
// turned into a dot first. Gives null (empty) if it isn't a number.
/** Accepts "1,5" (Spanish keyboards) as well as "1.5". */
fun String.toNumberOrNull(): Double? = trim().replace(',', '.').toDoubleOrNull()

// The emojis you can choose from in the food editor.
/** Food emojis offered in the picker. */
val FoodEmojis = listOf(
    "🍽️", "🍳", "🥚", "🥓", "🥞", "🥣", "🍞", "🥐", "🌮", "🌯", "🫔", "🍔", "🍕", "🌭",
    "🍗", "🥩", "🍖", "🐟", "🍤", "🍚", "🍝", "🍜", "🍲", "🥗", "🥑", "🥦", "🥕", "🌽",
    "🫘", "🧀", "🥛", "🍎", "🍌", "🍓", "🍊", "🍇", "🥜", "🍪", "🍫", "🍰", "🍦", "☕", "🥤", "🍺"
)

// Looks for keywords in the food name (English or Spanish) and suggests an emoji:
// "Huevos revueltos" contains "huevo" -> 🥚. First match wins.
/** Light keyword → emoji guess (EN + ES), used until you pick one yourself. */
fun guessEmoji(name: String): String? {
    val n = name.lowercase()
    val map = listOf(
        listOf("taco") to "🌮", listOf("burrito") to "🌯", listOf("tamal") to "🫔",
        listOf("egg", "huevo") to "🥚", listOf("bacon", "tocino") to "🥓",
        listOf("pancake", "hotcake") to "🥞", listOf("oat", "avena", "cereal") to "🥣",
        listOf("bread", "pan ", "toast") to "🍞", listOf("burger", "hamburguesa") to "🍔",
        listOf("pizza") to "🍕", listOf("chicken", "pollo") to "🍗",
        listOf("steak", "beef", "carne", "bistec") to "🥩", listOf("fish", "pescado", "salmon", "tuna", "atún") to "🐟",
        listOf("shrimp", "camarón", "camaron") to "🍤", listOf("rice", "arroz") to "🍚",
        listOf("pasta", "spaghetti", "espagueti") to "🍝", listOf("soup", "sopa", "caldo", "pozole") to "🍲",
        listOf("salad", "ensalada") to "🥗", listOf("avocado", "aguacate", "guacamole") to "🥑",
        listOf("broccoli", "brócoli") to "🥦", listOf("bean", "frijol") to "🫘",
        listOf("cheese", "queso") to "🧀", listOf("milk", "leche", "yogurt") to "🥛",
        listOf("apple", "manzana") to "🍎", listOf("banana", "plátano", "platano") to "🍌",
        listOf("strawberr", "fresa") to "🍓", listOf("orange", "naranja") to "🍊",
        listOf("peanut", "cacahuate", "nuts", "nuez") to "🥜", listOf("cookie", "galleta") to "🍪",
        listOf("chocolate") to "🍫", listOf("cake", "pastel") to "🍰", listOf("ice cream", "helado") to "🍦",
        listOf("coffee", "café", "cafe") to "☕", listOf("soda", "refresco", "shake", "licuado") to "🥤",
        listOf("beer", "cerveza") to "🍺", listOf("corn", "elote", "tortilla") to "🌽"
    )
    return map.firstOrNull { (keys, _) -> keys.any { n.contains(it) } }?.second
}
