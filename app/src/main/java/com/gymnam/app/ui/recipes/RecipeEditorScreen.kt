// ==============================================================================
// PLAIN-ENGLISH GUIDE
//
// WHAT THIS FILE IS: the "create / edit my recipe" form:
//  name and emoji, servings, minutes, ingredients (add with a search sheet,
//  remove, or mark as pantry staple), steps (one per line), the calculated
//  per-serving nutrition, and the optional "Set totals myself" override.
// The brain is RecipeEditorViewModel.kt.
// ==============================================================================
package com.gymnam.app.ui.recipes

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gymnam.app.R
import com.gymnam.app.data.FoodItem
import com.gymnam.app.data.fmtNum
import com.gymnam.app.ui.common.EmojiBadge
import com.gymnam.app.ui.common.FoodEmojis
import com.gymnam.app.ui.common.SoftCard
import com.gymnam.app.ui.common.toNumberOrNull
import kotlin.math.roundToInt

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RecipeEditorScreen(vm: RecipeEditorViewModel, contentPadding: PaddingValues, onBack: () -> Unit, onSaved: (Long) -> Unit) {
    val haptics = LocalHapticFeedback.current
    var adding by remember { mutableStateOf(false) }
    var showError by remember { mutableStateOf(false) }
    val num = KeyboardOptions(keyboardType = KeyboardType.Decimal)
    val calc = vm.calc

    Column(
        Modifier.fillMaxSize()
            .padding(top = contentPadding.calculateTopPadding(), bottom = contentPadding.calculateBottomPadding())
            .imePadding()
    ) {
        Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
            Text(stringResource(if (vm.isEdit) R.string.edit_recipe else R.string.new_recipe),
                style = MaterialTheme.typography.titleLarge)
        }

        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            SoftCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(Modifier.size(64.dp), shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                        Box(contentAlignment = Alignment.Center) { Text(vm.emoji, fontSize = 32.sp) }
                    }
                    Spacer(Modifier.width(14.dp))
                    OutlinedTextField(
                        vm.name, { vm.name = it }, Modifier.weight(1f), singleLine = true,
                        label = { Text(stringResource(R.string.recipe_name)) },
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        shape = MaterialTheme.shapes.medium
                    )
                }
                LazyRow(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(FoodEmojis) { e ->
                        val sel = e == vm.emoji
                        Surface(
                            Modifier.size(42.dp).clip(CircleShape).clickable { vm.emoji = e }, shape = CircleShape,
                            color = if (sel) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                            border = if (sel) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
                        ) { Box(contentAlignment = Alignment.Center) { Text(e, fontSize = 19.sp) } }
                    }
                }
                Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(vm.servings, { vm.servings = it.filter(Char::isDigit) }, Modifier.weight(1f),
                        label = { Text(stringResource(R.string.servings)) }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), shape = MaterialTheme.shapes.medium)
                    OutlinedTextField(vm.minutes, { vm.minutes = it.filter(Char::isDigit) }, Modifier.weight(1f),
                        label = { Text(stringResource(R.string.minutes)) }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), shape = MaterialTheme.shapes.medium)
                }
            }

            SoftCard {
                Text(stringResource(R.string.ingredients), style = MaterialTheme.typography.titleMedium)
                if (vm.ingredients.isEmpty()) Text(stringResource(R.string.no_ingredients), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                vm.ingredients.forEach { ing ->
                    Row(
                        Modifier.fillMaxWidth().padding(top = 8.dp).clip(MaterialTheme.shapes.small)
                            .combinedClickable(onClick = {}, onLongClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress); vm.toggleStaple(ing)
                            }),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        EmojiBadge(ing.emoji, size = 34.dp)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(ing.name + if (ing.staple) "  🧂" else "", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                (if (ing.grams > 0) "${fmtNum(ing.grams)} g · " else "") +
                                    stringResource(R.string.kcal_value, ing.kcal.roundToInt()),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = { vm.removeIngredient(ing) }) { Icon(Icons.Filled.Close, stringResource(R.string.delete)) }
                    }
                }
                if (vm.ingredients.isNotEmpty()) Text(stringResource(R.string.staple_hint), style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
                OutlinedButton(onClick = { adding = true }, shape = CircleShape, modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
                    Icon(Icons.Filled.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.add_ingredient))
                }
            }

            SoftCard {
                Text(stringResource(R.string.per_serving), style = MaterialTheme.typography.titleMedium)
                Text(
                    "${stringResource(R.string.kcal_value, calc[0].roundToInt())} · " +
                        stringResource(R.string.macro_line, calc[1].roundToInt(), calc[2].roundToInt(), calc[3].roundToInt()),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (vm.override) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                )
                Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.set_totals_myself), modifier = Modifier.weight(1f))
                    Switch(vm.override, { on ->
                        vm.override = on
                        if (on && vm.kcalO.isBlank()) {   // start from the calculated numbers
                            vm.kcalO = calc[0].roundToInt().toString(); vm.proteinO = calc[1].roundToInt().toString()
                            vm.carbsO = calc[2].roundToInt().toString(); vm.fatO = calc[3].roundToInt().toString()
                        }
                    })
                }
                AnimatedVisibility(vm.override) {
                    Column {
                        OutlinedTextField(vm.kcalO, { vm.kcalO = it }, Modifier.fillMaxWidth(), singleLine = true,
                            label = { Text(stringResource(R.string.calories)) }, keyboardOptions = num, shape = MaterialTheme.shapes.medium)
                        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(vm.proteinO, { vm.proteinO = it }, Modifier.weight(1f), singleLine = true,
                                label = { Text(stringResource(R.string.protein_g), maxLines = 1) }, keyboardOptions = num, shape = MaterialTheme.shapes.medium)
                            OutlinedTextField(vm.carbsO, { vm.carbsO = it }, Modifier.weight(1f), singleLine = true,
                                label = { Text(stringResource(R.string.carbs_g), maxLines = 1) }, keyboardOptions = num, shape = MaterialTheme.shapes.medium)
                            OutlinedTextField(vm.fatO, { vm.fatO = it }, Modifier.weight(1f), singleLine = true,
                                label = { Text(stringResource(R.string.fat_g), maxLines = 1) }, keyboardOptions = num, shape = MaterialTheme.shapes.medium)
                        }
                    }
                }
            }

            SoftCard {
                Text(stringResource(R.string.steps), style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(
                    vm.steps, { vm.steps = it }, Modifier.fillMaxWidth().heightIn(min = 120.dp).padding(top = 6.dp),
                    placeholder = { Text(stringResource(R.string.steps_hint)) },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    shape = MaterialTheme.shapes.medium
                )
            }
            AnimatedVisibility(showError) { Text(stringResource(R.string.need_recipe_name), color = MaterialTheme.colorScheme.error) }
        }

        Button(
            onClick = { showError = !vm.save(onSaved); haptics.performHapticFeedback(HapticFeedbackType.LongPress) },
            shape = CircleShape, contentPadding = PaddingValues(vertical = 16.dp),
            modifier = Modifier.fillMaxWidth().padding(16.dp)
        ) { Text(stringResource(R.string.save), style = MaterialTheme.typography.titleMedium) }
    }

    if (adding) IngredientSheet(vm, onDismiss = { adding = false })
}

// Search for an ingredient (your foods + built-in list), choose how much (grams or
// servings), and add it. Its calories are added to the recipe automatically.
/** Search a food, choose how much, add it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IngredientSheet(vm: RecipeEditorViewModel, onDismiss: () -> Unit) {
    var picked by remember { mutableStateOf<FoodItem?>(null) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp).imePadding()) {
            val item = picked
            if (item == null) {
                OutlinedTextField(
                    vm.query, vm::setQuery, Modifier.fillMaxWidth(), singleLine = true, shape = CircleShape,
                    placeholder = { Text(stringResource(R.string.search_foods)) }, leadingIcon = { Icon(Icons.Filled.Search, null) }
                )
                LazyColumn(Modifier.heightIn(max = 420.dp).padding(top = 8.dp)) {
                    items(vm.results, key = { it.key }) { f ->
                        Row(
                            Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).clickable { picked = f }.padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            EmojiBadge(f.emoji, size = 34.dp)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(f.name, style = MaterialTheme.typography.bodyMedium)
                                Text(f.servingText, style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(stringResource(R.string.kcal_value, f.kcal.roundToInt()), style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            } else {
                var grams by remember(item.key) { mutableStateOf(item.hasGrams) }
                var amount by remember(item.key) { mutableStateOf(if (item.hasGrams) fmtNum(item.servingG) else "1") }
                val a = amount.toNumberOrNull() ?: 0.0
                Text("${item.emoji}  ${item.name}", style = MaterialTheme.typography.titleLarge)
                Text(stringResource(R.string.kcal_value, item.kcalFor(a, grams)), style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(vertical = 8.dp))
                OutlinedTextField(amount, { amount = it }, Modifier.fillMaxWidth(), singleLine = true,
                    label = { Text(stringResource(R.string.amount)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), shape = MaterialTheme.shapes.medium)
                Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (item.hasGrams) FilterChip(grams, {
                        if (!grams) { amount = fmtNum(a * item.servingG); grams = true }
                    }, shape = CircleShape, label = { Text(stringResource(R.string.unit_gram)) })
                    FilterChip(!grams, {
                        if (grams && item.hasGrams) { amount = fmtNum(a / item.servingG); grams = false }
                    }, shape = CircleShape, label = { Text(item.servingLabel.removePrefix("1 ")) })
                }
                Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { picked = null }, shape = CircleShape) { Text(stringResource(R.string.back)) }
                    Button(onClick = {
                        if (a > 0) { vm.addIngredient(item, a, grams); onDismiss() }
                    }, enabled = a > 0, shape = CircleShape, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.add_ingredient))
                    }
                }
            }
        }
    }
}
