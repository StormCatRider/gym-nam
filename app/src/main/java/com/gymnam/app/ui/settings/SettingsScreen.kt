// ==============================================================================
// PLAIN-ENGLISH GUIDE
//
// WHAT THIS FILE IS: the Settings tab. Each "// Section" comment below marks one
// card, in the order you see them:
//  Theme (palettes) · Goals (re-run setup) · Targets (fine-tune numbers) ·
//  Language · Water goal · Widget (show weight?) · Backup (export / import /
//  weekly auto-backup / back up now) · App lock · Reminders · Health Connect ·
//  Online search switch · Privacy summary · About & credits.
// Every switch saves immediately through SettingsViewModel.kt.
//
// PERMISSIONS ARE ASKED ONLY WHEN NEEDED: notifications only when you turn on a
// reminder; Health Connect only when you turn it on; camera only when you scan.
// ==============================================================================
package com.gymnam.app.ui.settings

import android.content.Intent
import androidx.compose.material3.FilterChip
import androidx.compose.foundation.horizontalScroll
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Switch
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.gymnam.app.ui.theme.isAppDark
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gymnam.app.R
import com.gymnam.app.data.Units
import com.gymnam.app.data.fmtNum
import com.gymnam.app.ui.common.toNumberOrNull
import com.gymnam.app.data.Targets
import com.gymnam.app.ui.common.SoftCard
import com.gymnam.app.ui.theme.Palettes
import kotlinx.coroutines.launch

// rememberLauncherForActivityResult = opens an Android system window (file
// picker, permission prompt...) and gets the answer back here.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: SettingsViewModel, snackbar: SnackbarHostState, contentPadding: PaddingValues, onOpenSetup: () -> Unit) {
    val s by vm.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val dark = isAppDark()
    val savedMsg = stringResource(R.string.saved)
    val msg by vm.msg.collectAsStateWithLifecycle()
    val working by vm.working.collectAsStateWithLifecycle()
    var water by remember { mutableStateOf("") }
    var pendingRestore by remember { mutableStateOf<android.net.Uri?>(null) }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        uri?.let(vm::export)
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        pendingRestore = uri
    }
    val msgText = when (val m = msg) {
        BackupMsg.Exported -> stringResource(R.string.backup_exported)
        is BackupMsg.Restored -> stringResource(R.string.backup_restored, m.foods)
        BackupMsg.BackedUp -> stringResource(R.string.backup_saved_downloads)
        BackupMsg.Failed -> stringResource(R.string.backup_failed)
        null -> null
    }
    LaunchedEffect(msgText) { msgText?.let { snackbar.showSnackbar(it); vm.clearMsg() } }
    val activity = context as? androidx.fragment.app.FragmentActivity
    val noLockMsg = stringResource(R.string.lock_unavailable)
    val noHcMsg = stringResource(R.string.hc_unavailable)
    val hcDeniedMsg = stringResource(R.string.hc_denied)
    // Notifications permission (Android 13+), asked only when you switch a reminder on.
    var pendingReminder by remember { mutableStateOf<String?>(null) }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) when (pendingReminder) { "log" -> vm.setLogReminder(true); "weekly" -> vm.setWeeklySummary(true) }
        pendingReminder = null
    }
    // Android 13+ needs notification permission. If it's missing, ask first, and turn
    // the reminder on only if you allow it.
    fun enableReminder(kind: String) {
        if (Build.VERSION.SDK_INT >= 33 && androidx.core.content.ContextCompat.checkSelfPermission(
                context, android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            pendingReminder = kind; notifLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        } else if (kind == "log") vm.setLogReminder(true) else vm.setWeeklySummary(true)
    }
    // Health Connect permissions, asked only when you switch it on.
    val hcLauncher = rememberLauncherForActivityResult(
        androidx.health.connect.client.PermissionController.createRequestPermissionResultContract()
    ) { granted ->
        if (granted.containsAll(com.gymnam.app.data.HealthSync.PERMISSIONS)) vm.setHealthConnect(true)
        else scope.launch { snackbar.showSnackbar(hcDeniedMsg) }
    }

    // The water goal box shows fl oz (imperial) or liters (metric); refilled when units change.
    LaunchedEffect(s?.waterGoalFlOz, s?.metric) { s?.let { water = fmtNum(Units.waterGoalValue(it.waterGoalFlOz, it.metric)) } }
    val metric = s?.metric == true

    var cal by remember { mutableStateOf("") }
    var p by remember { mutableStateOf("") }
    var c by remember { mutableStateOf("") }
    var f by remember { mutableStateOf("") }
    var filled by remember { mutableStateOf(false) }
    LaunchedEffect(s) {
        val t = s?.targets ?: return@LaunchedEffect
        if (!filled) {
            cal = t.calories.toString(); p = t.proteinG.toString()
            c = t.carbsG.toString(); f = t.fatG.toString(); filled = true
        }
    }

    // LazyColumn (instead of a long scrolling Column) only builds the cards that are on
    // screen, so Settings opens and scrolls more smoothly.
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp,
            top = contentPadding.calculateTopPadding() + 8.dp,
            bottom = contentPadding.calculateBottomPadding() + 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // (The page title now lives in the colored top bar.)
        // Theme: Light / Dark / follow the phone, then the color palette.
        item {
            SoftCard {
                Text(stringResource(R.string.settings_theme), style = MaterialTheme.typography.titleMedium)
                // Saved right away; the whole app switches instantly.
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(top = 10.dp)) {
                    val modes = listOf("system" to R.string.theme_system, "light" to R.string.theme_light, "dark" to R.string.theme_dark)
                    modes.forEachIndexed { i, (id, label) ->
                        SegmentedButton(
                            selected = (s?.themeMode ?: "system") == id,
                            onClick = { haptics.performHapticFeedback(HapticFeedbackType.LongPress); vm.setThemeMode(id) },
                            shape = SegmentedButtonDefaults.itemShape(i, modes.size)
                        ) { Text(stringResource(label), maxLines = 1) }
                    }
                }
                Spacer(Modifier.height(14.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Palettes.forEach { pal ->
                        val selected = s?.paletteId == pal.id
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Surface(
                                modifier = Modifier.size(48.dp).clip(CircleShape).clickable {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    vm.setPalette(pal.id)
                                },
                                shape = CircleShape,
                                color = if (dark) pal.accentDark else pal.accentLight,
                                border = if (selected) BorderStroke(3.dp, MaterialTheme.colorScheme.onSurface) else null
                            ) {
                                if (selected) Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Filled.Check, null, tint = androidx.compose.ui.graphics.Color(0xFF1F1F24))
                                }
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(stringResource(pal.nameRes), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }

        // Units: Imperial (lb, ft/in, fl oz) or Metric (kg, cm, ml/L). Only changes how numbers are
        // shown and typed; everything is stored the same way, so switching is always safe.
        item {
            SoftCard {
                Text("📏  " + stringResource(R.string.units_title), style = MaterialTheme.typography.titleMedium)
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(top = 10.dp)) {
                    SegmentedButton(!metric, { vm.setMetric(false) }, SegmentedButtonDefaults.itemShape(0, 2)) {
                        Text(stringResource(R.string.units_imperial), maxLines = 1)
                    }
                    SegmentedButton(metric, { vm.setMetric(true) }, SegmentedButtonDefaults.itemShape(1, 2)) {
                        Text(stringResource(R.string.units_metric), maxLines = 1)
                    }
                }
                Text(stringResource(if (metric) R.string.units_metric_d else R.string.units_imperial_d) + " · " + stringResource(R.string.units_note),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp))
            }
        }

        // Goals from the setup wizard
        item {
            SoftCard {
                Text("🎯  " + stringResource(R.string.goals_title), style = MaterialTheme.typography.titleMedium)
                s?.profile?.let { pr ->
                    Text(stringResource(R.string.goals_summary, Units.weight(pr.weightLb, metric),
                        stringResource(when (pr.goal) {
                            com.gymnam.app.data.GoalType.LOSE -> R.string.goal_lose
                            com.gymnam.app.data.GoalType.MAINTAIN -> R.string.goal_maintain
                            com.gymnam.app.data.GoalType.GAIN -> R.string.goal_gain
                        }), if (pr.formula == com.gymnam.app.data.Formula.KATCH) "Katch-McArdle" else "Mifflin-St Jeor"),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                FilledTonalButton(onClick = onOpenSetup, shape = CircleShape, modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
                    Text(stringResource(R.string.recalculate_goals))
                }
            }
        }

        // Targets (manual fine-tuning)
        item {
            SoftCard {
                Text(stringResource(R.string.settings_targets), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.settings_targets_hint), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                val num = KeyboardOptions(keyboardType = KeyboardType.Number)
                OutlinedTextField(cal, { cal = it.filter(Char::isDigit) }, Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.calories)) }, singleLine = true,
                    keyboardOptions = num, shape = MaterialTheme.shapes.medium)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(p, { p = it.filter(Char::isDigit) }, Modifier.weight(1f),
                        label = { Text(stringResource(R.string.protein_g), maxLines = 1) }, singleLine = true,
                        keyboardOptions = num, shape = MaterialTheme.shapes.medium)
                    OutlinedTextField(c, { c = it.filter(Char::isDigit) }, Modifier.weight(1f),
                        label = { Text(stringResource(R.string.carbs_g), maxLines = 1) }, singleLine = true,
                        keyboardOptions = num, shape = MaterialTheme.shapes.medium)
                    OutlinedTextField(f, { f = it.filter(Char::isDigit) }, Modifier.weight(1f),
                        label = { Text(stringResource(R.string.fat_g), maxLines = 1) }, singleLine = true,
                        keyboardOptions = num, shape = MaterialTheme.shapes.medium)
                }
                Spacer(Modifier.height(12.dp))
                FilledTonalButton(
                    onClick = {
                        val d = Targets()
                        vm.setTargets(Targets(
                            calories = cal.toIntOrNull()?.takeIf { it > 0 } ?: d.calories,
                            proteinG = p.toIntOrNull() ?: d.proteinG,
                            carbsG = c.toIntOrNull() ?: d.carbsG,
                            fatG = f.toIntOrNull() ?: d.fatG
                        ))
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        scope.launch { snackbar.showSnackbar(savedMsg) }
                    },
                    shape = CircleShape, modifier = Modifier.fillMaxWidth()
                ) { Text(stringResource(R.string.save)) }
            }
        }

        // The app follows the phone's language. On Android 13+ the button opens Android's
        // own per-app language screen, so you can pick English or Spanish just for this app.
        // Language
        item {
            SoftCard {
                Text(stringResource(R.string.settings_language), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.settings_language_hint), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = {
                            context.startActivity(
                                Intent(Settings.ACTION_APP_LOCALE_SETTINGS, Uri.parse("package:${context.packageName}"))
                            )
                        },
                        shape = CircleShape, modifier = Modifier.fillMaxWidth()
                    ) { Text(stringResource(R.string.open_language_settings)) }
                }
            }
        }

        // Water goal
        item {
            SoftCard {
                Text("💧  " + stringResource(R.string.water_goal), style = MaterialTheme.typography.titleMedium)
                Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(water, { water = it }, Modifier.weight(1f), singleLine = true,
                        suffix = { Text(if (metric) "L" else "fl oz") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        shape = MaterialTheme.shapes.medium)
                    FilledTonalButton(onClick = {
                        // Saved as whole fl oz; allowed range 8-256 fl oz (about 0.25-7.5 L).
                        water.toNumberOrNull()?.let { Units.waterGoalFlOz(it, metric) }?.takeIf { it in 8..256 }?.let {
                            vm.setWaterGoal(it); scope.launch { snackbar.showSnackbar(savedMsg) }
                        }
                    }, shape = CircleShape, modifier = Modifier.padding(start = 8.dp)) { Text(stringResource(R.string.save)) }
                }
                Text(stringResource(if (metric) R.string.water_goal_hint_metric else R.string.water_goal_hint), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
            }
        }

        // Widget
        item {
            SoftCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("📱  " + stringResource(R.string.widget_title), style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(R.string.widget_show_weight), style = MaterialTheme.typography.bodyMedium)
                    }
                    Switch(checked = s?.widgetShowWeight == true, onCheckedChange = vm::setWidgetShowWeight)
                }
                Text(stringResource(R.string.widget_hint), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
            }
        }

        // Backup
        item {
            SoftCard {
                Text("🗂️  " + stringResource(R.string.backup_title), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.backup_body), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = { exportLauncher.launch("GymNam-backup-${java.time.LocalDate.now()}.zip") },
                        enabled = !working, shape = CircleShape, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.export)) }
                    OutlinedButton(onClick = { importLauncher.launch(arrayOf("application/zip", "application/octet-stream")) },
                        enabled = !working, shape = CircleShape, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.restore)) }
                }
                Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.auto_backup), style = MaterialTheme.typography.bodyLarge)
                        Text(
                            s?.lastBackupMillis?.takeIf { it > 0 }?.let {
                                stringResource(R.string.last_backup, java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneId.systemDefault())
                                    .toLocalDate().format(java.time.format.DateTimeFormatter.ofLocalizedDate(java.time.format.FormatStyle.MEDIUM)))
                            } ?: stringResource(R.string.auto_backup_hint),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = s?.autoBackup == true, onCheckedChange = vm::setAutoBackup)
                }
                TextButton(onClick = vm::backupNow, enabled = !working) { Text(stringResource(R.string.backup_now)) }
            }
        }

        // Can only be turned on if your phone has a screen lock (PIN, pattern, password
        // or fingerprint); otherwise there would be nothing to unlock with.
        // App lock
        item {
            SoftCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("🔒  " + stringResource(R.string.lock_title), style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(R.string.lock_body), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = s?.lockEnabled == true, onCheckedChange = { on ->
                        if (on && (activity == null || !com.gymnam.app.MainActivity.canLock(activity))) {
                            scope.launch { snackbar.showSnackbar(noLockMsg) }
                        } else vm.setLock(on)
                    })
                }
            }
        }

        // Reminders
        item {
            SoftCard {
                Text("🔔  " + stringResource(R.string.reminders_title), style = MaterialTheme.typography.titleMedium)
                Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.log_reminder), style = MaterialTheme.typography.bodyLarge)
                        Text(stringResource(R.string.log_reminder_hint), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = s?.logReminder == true, onCheckedChange = { if (it) enableReminder("log") else vm.setLogReminder(false) })
                }
                if (s?.logReminder == true) Row(
                    Modifier.padding(top = 6.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    (18..22).forEach { h ->
                        FilterChip(s?.logReminderHour == h, { vm.setLogReminderHour(h) }, shape = CircleShape,
                            label = { Text(java.time.LocalTime.of(h, 0).format(java.time.format.DateTimeFormatter.ofLocalizedTime(java.time.format.FormatStyle.SHORT))) })
                    }
                }
                Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.weekly_summary), style = MaterialTheme.typography.bodyLarge)
                        Text(stringResource(R.string.weekly_summary_hint), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = s?.weeklySummary == true, onCheckedChange = { if (it) enableReminder("weekly") else vm.setWeeklySummary(false) })
                }
            }
        }

        // If Health Connect isn't available on the phone, a message says so and nothing
        // is turned on. "Manage Health Connect permissions" opens its own settings.
        // Health Connect (optional, off by default)
        item {
            SoftCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("⌚  Health Connect", style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(R.string.hc_body), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = s?.healthConnect == true, onCheckedChange = { on ->
                        when {
                            !on -> vm.setHealthConnect(false)
                            !com.gymnam.app.data.HealthSync.available(context) -> scope.launch { snackbar.showSnackbar(noHcMsg) }
                            else -> hcLauncher.launch(com.gymnam.app.data.HealthSync.PERMISSIONS)
                        }
                    })
                }
                if (s?.healthConnect == true) TextButton(onClick = {
                    runCatching { context.startActivity(Intent(androidx.health.connect.client.HealthConnectClient.ACTION_HEALTH_CONNECT_SETTINGS)) }
                }) { Text(stringResource(R.string.hc_manage)) }
                Text(stringResource(R.string.hc_double_note), style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        // Online search switch (off by default)
        item {
            SoftCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.settings_online_title), style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f))
                    Switch(
                        checked = s?.internetEnabled == true,
                        onCheckedChange = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            vm.setInternet(it)
                        }
                    )
                }
                Text(stringResource(R.string.settings_online_body), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        // Privacy
        item {
            SoftCard {
                Text(stringResource(R.string.privacy_title), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.privacy_stage1), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        // Credits for the data sources and open-source libraries. The Open Food Facts
        // credit is REQUIRED by its license (ODbL); see THIRD_PARTY_NOTICES.md.
        // About & credits (attribution required by Open Food Facts' ODbL; Apache-2.0 notices)
        item {
            SoftCard {
                Text("ℹ️  " + stringResource(R.string.credits_title), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.credits_body), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
            }
        }

        item {
            Text(
                "Gym & Ñam · v" + com.gymnam.app.BuildConfig.VERSION_NAME,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }

    pendingRestore?.let { uri ->
        AlertDialog(
            onDismissRequest = { pendingRestore = null },
            title = { Text(stringResource(R.string.restore_confirm_title)) },
            text = { Text(stringResource(R.string.restore_confirm_body)) },
            confirmButton = { TextButton(onClick = { vm.restore(uri); pendingRestore = null }) { Text(stringResource(R.string.restore)) } },
            dismissButton = { TextButton(onClick = { pendingRestore = null }) { Text(stringResource(R.string.cancel)) } }
        )
    }
}
