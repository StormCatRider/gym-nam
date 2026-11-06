// ==============================================================================
// PLAIN-ENGLISH GUIDE
//
// WHAT THIS FILE IS: the brain of the Settings tab. Each "set..." function saves
// one switch or value (see data/SettingsStore.kt). It also runs backup export,
// restore and "back up now", and reports the result as a message at the bottom
// of the screen.
// ==============================================================================
package com.gymnam.app.ui.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gymnam.app.GymNamApp
import com.gymnam.app.data.AppSettings
import com.gymnam.app.data.Backup
import com.gymnam.app.data.Targets
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

// The possible messages after a backup action. "sealed" = this exact list and no
// others: exported, restored (with how many foods), backed up, or failed.
/** Result of the last backup/restore action, shown as a snackbar. */
sealed interface BackupMsg {
    data object Exported : BackupMsg
    data class Restored(val foods: Int) : BackupMsg
    data object BackedUp : BackupMsg
    data object Failed : BackupMsg
}

class SettingsViewModel(private val app: GymNamApp) : ViewModel() {
    private val store = app.settings

    // Live settings; null for a split second while they load.
    val settings: StateFlow<AppSettings?> =
        store.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _msg = MutableStateFlow<BackupMsg?>(null)
    val msg: StateFlow<BackupMsg?> = _msg.asStateFlow()
    // True while a backup/restore runs (the screen shows a spinner).
    private val _working = MutableStateFlow(false)
    val working: StateFlow<Boolean> = _working.asStateFlow()

    fun setPalette(id: String) = viewModelScope.launch { store.setPalette(id) }
    fun setTargets(t: Targets) = viewModelScope.launch { store.setTargets(t) }
    fun setInternet(on: Boolean) = viewModelScope.launch { store.setInternet(on) }
    fun setWaterGoal(flOz: Int) = viewModelScope.launch { store.setWaterGoal(flOz) }
    fun setWidgetShowWeight(on: Boolean) = viewModelScope.launch { store.setWidgetShowWeight(on) }
    fun setAutoBackup(on: Boolean) = viewModelScope.launch { store.setAutoBackup(on) }
    fun clearMsg() { _msg.value = null }
    fun setLock(on: Boolean) = viewModelScope.launch { store.setLock(on) }
    fun setLogReminder(on: Boolean) = viewModelScope.launch { store.setLogReminder(on) }
    fun setLogReminderHour(h: Int) = viewModelScope.launch { store.setLogReminderHour(h) }
    fun setWeeklySummary(on: Boolean) = viewModelScope.launch { store.setWeeklySummary(on) }
    // Turning Health Connect on also syncs the last 7 days right away.
    fun setHealthConnect(on: Boolean) = viewModelScope.launch {
        store.setHealthConnect(on)
        if (on) runCatching { com.gymnam.app.data.HealthSync.sync(app, days = 7) }   // catch up on the last week
    }

    // Shared wrapper: show the spinner, run the job, show "Failed" if anything goes
    // wrong, then hide the spinner.
    private fun run(block: suspend () -> BackupMsg) = viewModelScope.launch {
        _working.value = true
        _msg.value = runCatching { block() }.getOrElse { BackupMsg.Failed }
        _working.value = false
    }

    // uri = the file you picked in Android's "Save as" window. The app can only write
    // to that one file you chose.
    fun export(uri: Uri) = run {
        app.contentResolver.openOutputStream(uri)?.use { Backup.export(app, it) } ?: error("no stream")
        BackupMsg.Exported
    }

    fun restore(uri: Uri) = run {
        val n = app.contentResolver.openInputStream(uri)?.use { Backup.restore(app, it) } ?: error("no stream")
        BackupMsg.Restored(n)
    }

    fun backupNow() = run { Backup.autoBackupToDownloads(app); BackupMsg.BackedUp }
}
