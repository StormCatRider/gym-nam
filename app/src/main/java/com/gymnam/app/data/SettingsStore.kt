// ==============================================================================
// PLAIN-ENGLISH GUIDE
//
// WHAT THIS FILE IS: where your Settings are saved on the phone (Android
// "DataStore", a small private key -> value file, separate from the database).
//
//  - Targets     = your daily calories, protein, carbs, fat.
//  - AppSettings = every setting the app has, with its DEFAULT value after "=".
//    Defaults matter for privacy: internet OFF, weight hidden on widget, lock OFF,
//    Health Connect OFF, reminders OFF.
//  - SettingsStore = reads them all as a live stream and has one "set..." function
//    per setting.
// ==============================================================================
package com.gymnam.app.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

// Creates the private settings file named "settings".
private val Context.dataStore by preferencesDataStore(name = "settings")

// Defaults (2,000 kcal etc.) are only used until you finish setup.
/**
 * Daily targets. Setup fills them from Mifflin-St Jeor / Katch-McArdle and
 * protein = g per lb of body weight; you can fine-tune them in Settings.
 */
data class Targets(
    val calories: Int = 2000,
    val proteinG: Int = 150,
    val carbsG: Int = 200,
    val fatG: Int = 67
)

// paletteId = color theme ("mint", ...). waterGoalFlOz = daily water goal (64 fl oz).
// logReminderHour = 20 means 8 PM (24-hour clock).
data class AppSettings(
    val targets: Targets = Targets(),
    val paletteId: String = "mint",
    /** Master switch for every network call. OFF by default; nothing leaves the phone. */
    val internetEnabled: Boolean = false,
    val waterGoalFlOz: Int = 64,
    /** Privacy: the home-screen widget hides your weight unless you turn this on. */
    val widgetShowWeight: Boolean = false,
    val autoBackup: Boolean = true,
    val lastBackupMillis: Long = 0,
    /** First-time setup finished. */
    val onboarded: Boolean = false,
    val profile: Profile? = null,
    /** Trend weight (lb) at which you said "not now" to a goal update, so we don't nag. */
    val dismissedSuggestionLb: Double? = null,
    /** Optional: fingerprint / face / phone PIN to open the app. */
    val lockEnabled: Boolean = false,
    /** Optional Health Connect sync; permissions are only requested when you turn it on. */
    val healthConnect: Boolean = false,
    val logReminder: Boolean = false,
    val logReminderHour: Int = 20,
    val weeklySummary: Boolean = false
)

class SettingsStore(private val context: Context) {
    // K = the "keys": the label each setting is saved under in the file.
    // Never rename a key: phones that already saved a value under the old name would
    // lose it.
    private object K {
        val cal = intPreferencesKey("goal_calories")
        val p = intPreferencesKey("goal_protein")
        val c = intPreferencesKey("goal_carbs")
        val f = intPreferencesKey("goal_fat")
        val palette = stringPreferencesKey("palette")
        val internet = booleanPreferencesKey("internet_enabled")
        val water = intPreferencesKey("water_goal_floz")
        val widgetWeight = booleanPreferencesKey("widget_show_weight")
        val autoBackup = booleanPreferencesKey("auto_backup")
        val lastBackup = longPreferencesKey("last_backup_millis")
        val onboarded = booleanPreferencesKey("onboarded")
        val profile = stringPreferencesKey("profile_json")
        val dismissed = doublePreferencesKey("dismissed_suggestion_lb")
        val lock = booleanPreferencesKey("lock_enabled")
        val hc = booleanPreferencesKey("health_connect")
        val logReminder = booleanPreferencesKey("log_reminder")
        val logHour = intPreferencesKey("log_reminder_hour")
        val weekly = booleanPreferencesKey("weekly_summary")
    }

    // Live stream of all settings. "p[K.cal] ?: d.calories" = use the saved value,
    // or the default if nothing was saved yet.
    val settings: Flow<AppSettings> = context.dataStore.data.map { p ->
        val d = Targets()
        AppSettings(
            targets = Targets(
                calories = p[K.cal] ?: d.calories,
                proteinG = p[K.p] ?: d.proteinG,
                carbsG = p[K.c] ?: d.carbsG,
                fatG = p[K.f] ?: d.fatG
            ),
            paletteId = p[K.palette] ?: "mint",
            internetEnabled = p[K.internet] ?: false,
            waterGoalFlOz = p[K.water] ?: 64,
            widgetShowWeight = p[K.widgetWeight] ?: false,
            autoBackup = p[K.autoBackup] ?: true,
            lastBackupMillis = p[K.lastBackup] ?: 0,
            onboarded = p[K.onboarded] ?: false,
            profile = Profile.fromJson(p[K.profile]),
            dismissedSuggestionLb = p[K.dismissed],
            lockEnabled = p[K.lock] ?: false,
            healthConnect = p[K.hc] ?: false,
            logReminder = p[K.logReminder] ?: false,
            logReminderHour = p[K.logHour] ?: 20,
            weeklySummary = p[K.weekly] ?: false
        )
    }

    // Each "set..." function saves one setting. dataStore.edit { } writes safely
    // even if two things save at the same time.
    suspend fun setTargets(t: Targets) = context.dataStore.edit {
        it[K.cal] = t.calories; it[K.p] = t.proteinG; it[K.c] = t.carbsG; it[K.f] = t.fatG
    }

    suspend fun setPalette(id: String) = context.dataStore.edit { it[K.palette] = id }

    suspend fun setInternet(on: Boolean) = context.dataStore.edit { it[K.internet] = on }

    // first() = take the current value once (instead of listening forever).
    // This is the check every internet call goes through.
    suspend fun internetEnabled(): Boolean = settings.first().internetEnabled

    suspend fun setWaterGoal(flOz: Int) = context.dataStore.edit { it[K.water] = flOz }
    suspend fun setWidgetShowWeight(on: Boolean) = context.dataStore.edit { it[K.widgetWeight] = on }
    suspend fun setAutoBackup(on: Boolean) = context.dataStore.edit { it[K.autoBackup] = on }
    suspend fun setLastBackup(millis: Long) = context.dataStore.edit { it[K.lastBackup] = millis }

    // Called when you finish setup: saves the profile and the calculated goal,
    // marks setup as done (onboarded) and clears any "not now" on goal suggestions.
    /** Saves the profile and the targets calculated from it, and marks setup as done. */
    suspend fun saveProfile(p: Profile, t: Targets) = context.dataStore.edit {
        it[K.profile] = p.toJson(); it[K.onboarded] = true; it.remove(K.dismissed)
        it[K.cal] = t.calories; it[K.p] = t.proteinG; it[K.c] = t.carbsG; it[K.f] = t.fatG
    }
    suspend fun dismissSuggestion(lb: Double) = context.dataStore.edit { it[K.dismissed] = lb }
    suspend fun setLock(on: Boolean) = context.dataStore.edit { it[K.lock] = on }
    suspend fun setHealthConnect(on: Boolean) = context.dataStore.edit { it[K.hc] = on }
    suspend fun setLogReminder(on: Boolean) = context.dataStore.edit { it[K.logReminder] = on }
    suspend fun setLogReminderHour(h: Int) = context.dataStore.edit { it[K.logHour] = h }
    suspend fun setWeeklySummary(on: Boolean) = context.dataStore.edit { it[K.weekly] = on }
}
