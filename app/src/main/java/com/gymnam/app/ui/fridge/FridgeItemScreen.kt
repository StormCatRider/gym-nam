// ==============================================================================
// PLAIN-ENGLISH GUIDE
//
// WHAT THIS FILE IS: the "add / edit a fridge item" form: name (+ emoji choice),
// quantity and unit (pcs, pack, g, oz, lb, ml), expiry date (calendar picker,
// with quick +3 days / +1 week / +2 weeks / +1 month buttons), and an optional barcode scan
// that fills the name. The brain is FridgeItemViewModel.kt.
// ==============================================================================
package com.gymnam.app.ui.fridge

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gymnam.app.R
import com.gymnam.app.data.FridgeUnit
import com.gymnam.app.ui.common.FoodEmojis
import com.gymnam.app.ui.common.SoftCard
import kotlinx.coroutines.flow.StateFlow
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

// startWithScan = opened from the "Scan" button, so the camera opens right away.
// scannedBarcode = the code coming back from the camera.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FridgeItemScreen(
    vm: FridgeItemViewModel,
    contentPadding: PaddingValues,
    scannedBarcode: StateFlow<String?>,
    consumeBarcode: () -> Unit,
    startWithScan: Boolean,
    onScan: () -> Unit,
    onBack: () -> Unit
) {
    val haptics = LocalHapticFeedback.current
    val code by scannedBarcode.collectAsStateWithLifecycle()
    var showDate by remember { mutableStateOf(false) }
    var showError by remember { mutableStateOf(false) }
    var scanLaunched by rememberSaveable { mutableStateOf(false) }  // survives the trip to the camera
    val today = LocalDate.now()

    LaunchedEffect(code) { code?.let { vm.onBarcode(it); consumeBarcode() } }
    // Opened from "Scan": go straight to the camera once.
    LaunchedEffect(Unit) { if (startWithScan && !scanLaunched && code == null) { scanLaunched = true; onScan() } }

    Column(
        Modifier.fillMaxSize()
            .padding(top = contentPadding.calculateTopPadding(), bottom = contentPadding.calculateBottomPadding())
            .imePadding()
    ) {
        Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
            Text(stringResource(if (vm.isEdit) R.string.edit_fridge_item else R.string.new_fridge_item),
                style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            if (vm.isEdit) TextButton(onClick = { vm.delete(onBack) }) {
                Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
            }
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
                        value = vm.name, onValueChange = vm::updateName, singleLine = true,
                        label = { Text(stringResource(R.string.food_name)) },
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        shape = MaterialTheme.shapes.medium, modifier = Modifier.weight(1f)
                    )
                }
                Spacer(Modifier.height(10.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(FoodEmojis) { e ->
                        val sel = e == vm.emoji
                        Surface(
                            modifier = Modifier.size(42.dp).clip(CircleShape).clickable { vm.pickEmoji(e) },
                            shape = CircleShape,
                            color = if (sel) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                            border = if (sel) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
                        ) { Box(contentAlignment = Alignment.Center) { Text(e, fontSize = 19.sp) } }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AssistChip(onClick = onScan, shape = CircleShape, label = { Text(stringResource(R.string.scan_barcode)) },
                        leadingIcon = { Icon(Icons.Filled.QrCodeScanner, null, Modifier.size(18.dp)) })
                    Spacer(Modifier.width(10.dp))
                    when (vm.lookup) {
                        LookupState.LOOKING -> CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        LookupState.NOT_FOUND -> Text(stringResource(R.string.fridge_barcode_unknown), style = MaterialTheme.typography.bodySmall)
                        LookupState.OFFLINE -> Text(stringResource(R.string.fridge_barcode_offline), style = MaterialTheme.typography.bodySmall)
                        else -> vm.barcode?.let { Text("▮▯▮ $it", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
            }

            SoftCard {
                Text(stringResource(R.string.quantity), style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(
                    value = vm.quantity, onValueChange = { vm.quantity = it }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
                )
                Row(Modifier.padding(top = 8.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FridgeUnit.entries.forEach { u ->
                        FilterChip(vm.unit == u, { vm.unit = u }, shape = CircleShape, label = { Text(unitLabel(u)) })
                    }
                }
            }

            SoftCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.expires), style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                    vm.expiryDay?.let { ExpiryChip(it, today.toEpochDay(), isSystemInDarkTheme()) }
                }
                Text(
                    vm.expiryDay?.let { LocalDate.ofEpochDay(it).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)) }
                        ?: stringResource(R.string.no_date),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(vertical = 6.dp).clip(MaterialTheme.shapes.small).clickable { showDate = true }
                )
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(3L to R.string.plus_3_days, 7L to R.string.plus_1_week, 14L to R.string.plus_2_weeks, 30L to R.string.plus_1_month)
                        .forEach { (days, label) ->
                            val target = today.plusDays(days).toEpochDay()
                            FilterChip(vm.expiryDay == target, { vm.expiryDay = target }, shape = CircleShape,
                                label = { Text(stringResource(label)) })
                        }
                    FilterChip(false, { showDate = true }, shape = CircleShape, label = { Text(stringResource(R.string.pick_date)) })
                    if (vm.expiryDay != null) FilterChip(false, { vm.expiryDay = null }, shape = CircleShape,
                        label = { Text(stringResource(R.string.no_date)) })
                }
            }

            AnimatedVisibility(showError) {
                Text(stringResource(R.string.need_name_qty), color = MaterialTheme.colorScheme.error)
            }
        }

        Button(
            onClick = {
                showError = !vm.save(onBack)
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            },
            shape = CircleShape, contentPadding = PaddingValues(vertical = 16.dp),
            modifier = Modifier.fillMaxWidth().padding(16.dp)
        ) { Text(stringResource(R.string.save), style = MaterialTheme.typography.titleMedium) }
    }

    if (showDate) {
        // Android's calendar picker counts time in milliseconds at midnight UTC, while the
        // app counts whole days. This converts between the two so the date you tap is
        // exactly the date saved (no "off by one day" bugs in Seattle or Arizona time).
        // The date picker works in UTC midnight millis.
        val state = rememberDatePickerState(
            initialSelectedDateMillis = (vm.expiryDay?.let { LocalDate.ofEpochDay(it) } ?: today)
                .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showDate = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let {
                        vm.expiryDay = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toEpochDay()
                    }
                    showDate = false
                }) { Text(stringResource(R.string.save)) }
            },
            dismissButton = { TextButton(onClick = { showDate = false }) { Text(stringResource(R.string.cancel)) } }
        ) { DatePicker(state = state) }
    }
}
