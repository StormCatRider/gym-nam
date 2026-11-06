// ==============================================================================
// PLAIN-ENGLISH GUIDE
//
// WHAT THIS FILE IS: the sheet that slides up after you pick a food:
// choose the amount, see calories update live, pick the meal, tap "Add".
// Also MealSheet: the same idea for a saved "My meal" (logs all its foods).
// ==============================================================================
package com.gymnam.app.ui.picker

import androidx.compose.animation.core.animateIntAsState
import androidx.compose.foundation.horizontalScroll
import com.gymnam.app.ui.theme.isAppDark
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gymnam.app.R
import com.gymnam.app.data.rememberItemFlags
import com.gymnam.app.ui.common.WarningBanner
import com.gymnam.app.data.FoodItem
import com.gymnam.app.data.FoodSource
import com.gymnam.app.data.Meal
import com.gymnam.app.data.SavedMeal
import com.gymnam.app.data.fmtNum
import com.gymnam.app.ui.common.labelRes
import com.gymnam.app.ui.common.toNumberOrNull
import com.gymnam.app.ui.theme.macroColors
import kotlin.math.roundToInt

// remember(item.key) = reset the boxes when a different food is opened.
// "Save to My foods" starts ON for scanned products, so next time they work offline.
// f = servings eaten; the big calorie number = kcal per serving x f (animated).
/** Choose how much, see calories update live, pick the meal, add. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PortionSheet(
    item: FoodItem,
    initialMeal: Meal,
    onDismiss: () -> Unit,
    onLog: (amount: Double, inGrams: Boolean, meal: Meal, saveToMine: Boolean) -> Unit
) {
    var inGrams by remember(item.key) { mutableStateOf(false) }
    var amountText by remember(item.key) { mutableStateOf("1") }
    var meal by remember(item.key) { mutableStateOf(initialMeal) }
    var save by remember(item.key) { mutableStateOf(item.barcode != null && item.source != FoodSource.SAVED) }
    val amount = amountText.toNumberOrNull()?.takeIf { it > 0 } ?: 0.0
    val f = item.factor(amount, inGrams)
    val kcal by animateIntAsState((item.kcal * f).roundToInt(), label = "kcal")
    val macros = macroColors(isAppDark())

    // Switching units keeps the same quantity: 1 serving of 158 g <-> 158 g.
    fun switchUnit(toGrams: Boolean) {
        if (toGrams == inGrams) return
        // Keep the same quantity when flipping units: 1 serving ↔ 158 g
        amountText = if (toGrams) fmtNum(amount * item.servingG) else fmtNum(amount / item.servingG)
        inGrams = toGrams
    }
    // The - / + buttons: 10 g steps in grams, 0.5 steps in servings.
    fun step(dir: Int) {
        val s = if (inGrams) 10.0 else 0.5
        amountText = fmtNum((amount + dir * s).coerceAtLeast(s))
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 28.dp).imePadding()
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(Modifier.size(60.dp), shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                    Box(contentAlignment = Alignment.Center) { Text(item.emoji, fontSize = 30.sp) }
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(item.name, style = MaterialTheme.typography.titleLarge)
                    Text(
                        listOfNotNull(item.brand, item.servingText).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Allergy/diet warning + "check the label" reminder (only if something matches).
            WarningBanner(rememberItemFlags(item), Modifier.padding(top = 12.dp))

            // Big live calorie number + macros
            Text(
                stringResource(R.string.kcal_value, kcal),
                style = MaterialTheme.typography.displaySmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 20.dp)
            )
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                MacroPill(stringResource(R.string.protein), item.proteinG * f, macros.protein)
                MacroPill(stringResource(R.string.carbs), item.carbsG * f, macros.carbs)
                MacroPill(stringResource(R.string.fat), item.fatG * f, macros.fat)
            }

            // Amount
            Row(Modifier.padding(top = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                FilledTonalIconButton(onClick = { step(-1) }) { Icon(Icons.Filled.Remove, stringResource(R.string.step_down)) }
                OutlinedTextField(
                    value = amountText, onValueChange = { amountText = it },
                    singleLine = true, label = { Text(stringResource(R.string.amount)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    shape = MaterialTheme.shapes.medium,
                    textStyle = MaterialTheme.typography.titleMedium.copy(textAlign = TextAlign.Center),
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                )
                FilledTonalIconButton(onClick = { step(1) }) { Icon(Icons.Filled.Add, stringResource(R.string.step_up)) }
            }
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(!inGrams, { switchUnit(false) }, shape = CircleShape,
                    label = { Text(item.servingLabel.removePrefix("1 ").ifBlank { stringResource(R.string.unit_servings) }) })
                if (item.hasGrams) FilterChip(inGrams, { switchUnit(true) }, shape = CircleShape,
                    label = { Text(stringResource(R.string.unit_gram)) })
            }

            // Meal
            Row(
                Modifier.padding(top = 16.dp).horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Meal.entries.forEach { m ->
                    FilterChip(meal == m, { meal = m }, shape = CircleShape, label = { Text(stringResource(m.labelRes())) })
                }
            }

            if (item.source != FoodSource.SAVED) {
                Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(if (item.barcode != null) R.string.save_to_mine_barcode else R.string.save_to_mine),
                        style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f)
                    )
                    Switch(checked = save, onCheckedChange = { save = it })
                }
            }

            Button(
                onClick = { if (amount > 0) onLog(amount, inGrams, meal, save) },
                enabled = amount > 0,
                shape = CircleShape, contentPadding = PaddingValues(vertical = 16.dp),
                modifier = Modifier.fillMaxWidth().padding(top = 18.dp)
            ) { Text(stringResource(R.string.add_to_meal, stringResource(meal.labelRes())), style = MaterialTheme.typography.titleMedium) }
        }
    }
}

// A small colored label with grams, e.g. "P 25 g".
@Composable
private fun MacroPill(label: String, grams: Double, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(shape = CircleShape, color = color.copy(alpha = 0.25f)) {
            Text("${grams.roundToInt()} g", style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp))
        }
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp))
    }
}

// Shows the foods of a saved meal with their calories; pick the meal and add them all.
/** Log a saved meal (all its foods) into a meal section. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MealSheet(m: SavedMeal, initialMeal: Meal, onDismiss: () -> Unit, onLog: (Meal) -> Unit) {
    val items = remember(m.itemsJson) { m.items() }
    var meal by remember { mutableStateOf(initialMeal) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
            Text("${m.emoji}  ${m.name}", style = MaterialTheme.typography.titleLarge)
            Text(
                stringResource(R.string.items_kcal, items.size, items.sumOf { it.calories }),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
            items.forEach {
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Text("${it.emoji}  ${it.name}", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    Text(stringResource(R.string.kcal_value, it.calories), style = MaterialTheme.typography.labelLarge)
                }
            }
            Row(
                Modifier.padding(top = 16.dp).horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Meal.entries.forEach { s ->
                    FilterChip(meal == s, { meal = s }, shape = CircleShape, label = { Text(stringResource(s.labelRes())) })
                }
            }
            Button(
                onClick = { onLog(meal) }, shape = CircleShape, contentPadding = PaddingValues(vertical = 16.dp),
                modifier = Modifier.fillMaxWidth().padding(top = 18.dp)
            ) { Text(stringResource(R.string.add_meal_items, items.size), style = MaterialTheme.typography.titleMedium) }
        }
    }
}
