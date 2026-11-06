// ==============================================================================
// PLAIN-ENGLISH GUIDE
//
// WHAT THIS FILE IS: the "type a food yourself" form (also used to edit a food
// from your diary). Cards from top to bottom:
//  1. Big emoji + name, and a row of emojis to choose from.
//  2. Meal (Breakfast / Lunch / Dinner / Snack).
//  3. Amount (grams or servings), calories, protein, carbs, fat, with a
//     "calculated from macros" hint.
//  4. "Also save to My foods" switch (only when adding new).
//  5. Save button (shows an error if the name or calories are missing).
// The brain is FoodEditorViewModel.kt.
// ==============================================================================
package com.gymnam.app.ui.add

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gymnam.app.R
import com.gymnam.app.data.AmountUnit
import com.gymnam.app.data.Meal
import com.gymnam.app.ui.common.FoodEmojis
import com.gymnam.app.ui.common.SoftCard
import com.gymnam.app.ui.common.labelRes

// KeyboardOptions(Decimal) = shows the number keyboard for number boxes;
// ImeAction.Next = the keyboard's Enter jumps to the next box.
@Composable
fun FoodEditorScreen(vm: FoodEditorViewModel, contentPadding: PaddingValues, onBack: () -> Unit, onSaved: () -> Unit = onBack) {
    val haptics = LocalHapticFeedback.current
    var showError by remember { mutableStateOf(false) }
    val decimal = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next)

    Column(
        Modifier.fillMaxSize()
            .padding(top = contentPadding.calculateTopPadding(), bottom = contentPadding.calculateBottomPadding())
            .imePadding()
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
            Text(
                stringResource(if (vm.isEdit) R.string.edit_food else R.string.new_food),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f)
            )
            if (vm.isEdit) TextButton(onClick = {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                vm.delete(onSaved)
            }) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) }
        }

        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Big preview of the chosen icon + name field
            SoftCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(Modifier.size(64.dp), shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                        Box(contentAlignment = Alignment.Center) { Text(vm.emoji, fontSize = 32.sp) }
                    }
                    Spacer(Modifier.width(14.dp))
                    OutlinedTextField(
                        value = vm.name, onValueChange = vm::updateName,
                        label = { Text(stringResource(R.string.food_name)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.pick_icon), style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(6.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(FoodEmojis) { e ->
                        val selected = e == vm.emoji
                        Surface(
                            modifier = Modifier.size(44.dp).clip(CircleShape).clickable { vm.pickEmoji(e) },
                            shape = CircleShape,
                            color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                            border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
                        ) { Box(contentAlignment = Alignment.Center) { Text(e, fontSize = 20.sp) } }
                    }
                }
            }

            // Meal section
            SoftCard {
                Text(stringResource(R.string.meal), style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Meal.entries.forEach { m ->
                        FilterChip(
                            selected = vm.meal == m, onClick = { vm.meal = m },
                            label = { Text(stringResource(m.labelRes()), maxLines = 1) },
                            shape = CircleShape
                        )
                    }
                }
            }

            // Amount + nutrition
            SoftCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = vm.amount, onValueChange = { vm.amount = it },
                        label = { Text(stringResource(R.string.amount)) },
                        singleLine = true, keyboardOptions = decimal,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(10.dp))
                    FilterChip(vm.unit == AmountUnit.GRAM, { vm.unit = AmountUnit.GRAM },
                        label = { Text(stringResource(R.string.unit_gram)) }, shape = CircleShape)
                    Spacer(Modifier.width(6.dp))
                    FilterChip(vm.unit == AmountUnit.SERVING, { vm.unit = AmountUnit.SERVING },
                        label = { Text(stringResource(R.string.unit_serving)) }, shape = CircleShape)
                }
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = vm.calories, onValueChange = { vm.calories = it },
                    label = { Text(stringResource(R.string.calories)) },
                    singleLine = true, keyboardOptions = decimal,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MacroField(vm.protein, { vm.protein = it }, stringResource(R.string.protein_g), decimal, Modifier.weight(1f))
                    MacroField(vm.carbs, { vm.carbs = it }, stringResource(R.string.carbs_g), decimal, Modifier.weight(1f))
                    MacroField(vm.fat, { vm.fat = it }, stringResource(R.string.fat_g),
                        decimal.copy(imeAction = ImeAction.Done), Modifier.weight(1f))
                }
                val est = vm.kcalFromMacros
                AnimatedVisibility(est != null && est.toString() != vm.calories) {
                    Text(
                        stringResource(R.string.from_macros, est ?: 0),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 10.dp).clickable { vm.useMacroKcal() }
                    )
                }
            }

            if (!vm.isEdit) SoftCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(if (vm.barcode != null) R.string.save_to_mine_barcode else R.string.save_to_mine),
                        style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f)
                    )
                    Switch(checked = vm.saveToMine, onCheckedChange = { vm.saveToMine = it })
                }
                vm.barcode?.let {
                    Text("▮▯▮▮▯▮ $it", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            AnimatedVisibility(showError) {
                Text(stringResource(R.string.need_name_and_kcal), color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(Modifier.height(8.dp))
        }

        Button(
            onClick = {
                val ok = vm.save(onSaved)
                showError = !ok
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            },
            shape = CircleShape,
            contentPadding = PaddingValues(vertical = 16.dp),
            modifier = Modifier.fillMaxWidth().padding(16.dp)
        ) { Text(stringResource(R.string.save), style = MaterialTheme.typography.titleMedium) }
    }
}

// One small number box for protein, carbs or fat (in grams).
@Composable
private fun MacroField(
    value: String, onChange: (String) -> Unit, label: String,
    keyboard: KeyboardOptions, modifier: Modifier
) {
    OutlinedTextField(
        value = value, onValueChange = onChange,
        label = { Text(label, maxLines = 1, style = MaterialTheme.typography.bodySmall) },
        singleLine = true, keyboardOptions = keyboard,
        shape = MaterialTheme.shapes.medium,
        modifier = modifier
    )
}
