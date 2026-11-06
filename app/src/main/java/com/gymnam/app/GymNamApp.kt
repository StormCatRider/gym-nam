// ==============================================================================
// PLAIN-ENGLISH GUIDE
//
// WHAT THIS FILE IS: the "power switch" of the app. Android runs this once, before
// any screen appears, every time the app starts.
//
// WHAT IT DOES:
//  1. Creates the shared tools every screen uses: the database (your logged food,
//     weights, fridge...), the settings, the built-in food list, and the two
//     optional internet services (Open Food Facts and TheMealDB).
//  2. Starts four "listeners" that keep working in the background:
//     weekly backup, reminders, Health Connect sync, and the home-screen widget.
//
// HOW TO READ KOTLIN HERE (more in docs/CODE_GUIDE.md):
//  - "import ..." lines at the top just say which outside pieces this file uses.
//  - "val" = a named thing that is set once. "by lazy" = only build it the first
//    time someone asks for it (saves start-up time).
// ==============================================================================
package com.gymnam.app

import android.app.Application
import androidx.glance.appwidget.updateAll
import com.gymnam.app.data.AppDatabase
import com.gymnam.app.data.Backup
import com.gymnam.app.data.BuiltInRecipes
import com.gymnam.app.data.Catalog
import com.gymnam.app.data.HealthSync
import com.gymnam.app.data.Reminders
import com.gymnam.app.data.MealDb
import com.gymnam.app.data.OpenFoodFacts
import com.gymnam.app.data.SettingsStore
import com.gymnam.app.widget.GymNamWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.time.LocalDate

// The app's "toolbox". Any screen can reach these tools through GymNamApp.
// db          = the on-phone database (Room). Everything you log lives here.
// settings    = your choices from the Settings tab (palette, goals, internet...).
// catalog     = the built-in list of 1,000+ foods (assets/foods.csv).
// off         = Open Food Facts lookups (barcodes, food search). Needs internet ON.
// mealDb      = TheMealDB recipe search. Needs internet ON.
// builtInRecipes = the recipes that ship with the app (assets/recipes.json).
/** Tiny manual dependency container: no DI library needed for an app this size. */
class GymNamApp : Application() {
    val db: AppDatabase by lazy { AppDatabase.create(this) }
    val settings: SettingsStore by lazy { SettingsStore(this) }
    val catalog: Catalog by lazy { Catalog(this) }
    val off: OpenFoodFacts by lazy { OpenFoodFacts(settings) }
    val mealDb: MealDb by lazy { MealDb(settings, catalog) }
    val builtInRecipes: BuiltInRecipes by lazy { BuiltInRecipes(this) }

    // A "scope" is a place where background jobs run. Jobs started here live as long
    // as the app is running, no matter which screen is open.
    /** Background work tied to the app process (not to any screen). */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // A "flow" is a stream of values that updates over time (like a live feed).
    // This one checks the date every 60 seconds (60_000 milliseconds) and only
    // announces a new value when the day actually changes, at midnight.
    /** Today's date (epoch day), re-emitted when the date changes so nothing is stuck on yesterday after midnight. */
    private val todayFlow = flow {
        while (true) { emit(LocalDate.now().toEpochDay()); delay(60_000) }
    }.distinctUntilChanged()

    // onCreate = "when the app starts". Android calls this automatically.
    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    override fun onCreate() {
        super.onCreate()

        // "collect" = keep listening; run the code each time the value changes.
        // So if you flip the auto-backup switch, the weekly job is added or removed.
        // Weekly auto-backup follows the Settings switch.
        appScope.launch {
            settings.settings.map { it.autoBackup }.distinctUntilChanged().collect { Backup.schedule(this@GymNamApp, it) }
        }

        // Reminders follow their Settings switches.
        appScope.launch {
            settings.settings.map { Triple(it.logReminder, it.logReminderHour, it.weeklySummary) }.distinctUntilChanged()
                .collect { Reminders.sync(this@GymNamApp, settings.settings.first()) }
        }

        // "combine" waits on several streams at once (today's food, latest weight, the
        // Health Connect switch). "debounce(3_000)" means: wait until things have been
        // quiet for 3 seconds, so typing several foods quickly triggers only one sync.
        // "runCatching" = try it, and if it fails, don't crash the app.
        // Optional Health Connect: sync on start and shortly after you log food or weight.
        appScope.launch {
            combine(todayFlow.flatMapLatest { db.foodDao().entriesForDay(it) }, db.trackingDao().latestWeight(),
                settings.settings.map { it.healthConnect }.distinctUntilChanged()) { a, b, on -> Triple(a, b, on) }
                .debounce(3_000)
                .collect { (_, _, on) -> if (on) runCatching { HealthSync.sync(this@GymNamApp) } }
        }

        // Same idea: when food, water, exercise, weight or settings change,
        // redraw the home-screen widget (waits 0.8 s of quiet first).
        // Keep the home-screen widget in sync with whatever you log while the app is open.
        appScope.launch {
            val t = db.trackingDao()
            todayFlow.flatMapLatest { today ->
                combine(
                    db.foodDao().entriesForDay(today), t.waterForDay(today), t.exerciseForDay(today),
                    t.latestWeight(), settings.settings
                ) { a, b, c, d, e -> listOf(a, b, c, d, e) }
            }
                .debounce(800)
                .collect { runCatching { GymNamWidget().updateAll(this@GymNamApp) } }
        }
    }
}
