// ==============================================================================
// PLAIN-ENGLISH GUIDE
//
// WHAT THIS FILE IS: the OPTIONAL link with Health Connect (Android's on-phone
// health data hub; it's not a cloud service). Off unless you turn it on.
//  READS  : active calories and steps -> one "Daily activity" exercise entry per
//           day, which adds to your calorie budget.
//  WRITES : each food you log (calories + macros) and your weigh-ins, so other
//           apps (like your future gym app) can use them.
// Health Connect only lets an app change or delete records it wrote itself.
// ==============================================================================
package com.gymnam.app.data

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.MealType
import androidx.health.connect.client.records.NutritionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.health.connect.client.units.Energy
import androidx.health.connect.client.units.Mass
import com.gymnam.app.GymNamApp
import kotlinx.coroutines.flow.first
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Optional Health Connect sync (on-device; Health Connect is Android's local health store, not a cloud).
 *   Reads:  active calories + steps → one "Daily activity" exercise entry per day (adds to your budget).
 *   Writes: each food you log (calories + macros) and your weigh-ins, so other apps (like the future
 *           gym app) can read them.
 * Gym & Ñam only ever replaces/deletes records it wrote itself; Health Connect enforces that.
 */
object HealthSync {
    // Exactly the 4 permissions the app asks for, nothing more
    // (read active calories, read steps, write nutrition, write weight).
    val PERMISSIONS = setOf(
        HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class),
        HealthPermission.getReadPermission(StepsRecord::class),
        HealthPermission.getWritePermission(NutritionRecord::class),
        HealthPermission.getWritePermission(WeightRecord::class),
    )

    // Is Health Connect installed and working on this phone?
    fun available(context: Context) =
        HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE

    private fun client(context: Context) = HealthConnectClient.getOrCreate(context)

    // Did you grant all 4 permissions? (false if anything goes wrong)
    suspend fun hasPermissions(context: Context): Boolean = runCatching {
        client(context).permissionController.getGrantedPermissions().containsAll(PERMISSIONS)
    }.getOrDefault(false)

    // Syncs yesterday and today. Stops quietly if the switch is OFF, Health Connect
    // is missing, or permissions were not granted.
    /** Syncs the last [days] days (default: yesterday + today). Quietly does nothing if not allowed. */
    suspend fun sync(app: GymNamApp, days: Int = 2) {
        if (!app.settings.settings.first().healthConnect || !available(app) || !hasPermissions(app)) return
        val today = LocalDate.now().toEpochDay()
        for (d in (today - (days - 1))..today) runCatching { syncDay(app, d) }
    }

    // One day, from midnight to midnight in your time zone (TimeRangeFilter).
    private suspend fun syncDay(app: GymNamApp, day: Long) {
        val zone = ZoneId.systemDefault()
        val date = LocalDate.ofEpochDay(day)
        val start = date.atStartOfDay(zone).toInstant()
        val end = date.plusDays(1).atStartOfDay(zone).toInstant()
        val range = TimeRangeFilter.between(start, end)
        val hc = client(app)
        val tracking = app.db.trackingDao()

        // 1) Read: active calories + steps → one exercise entry marked "hc" (replaced on every sync).
        val agg = hc.aggregate(AggregateRequest(
            metrics = setOf(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL, StepsRecord.COUNT_TOTAL),
            timeRangeFilter = range
        ))
        val kcal = agg[ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL]?.inKilocalories?.toInt() ?: 0
        val steps = agg[StepsRecord.COUNT_TOTAL] ?: 0L
        // Remove the previous imported entry first, so re-syncing never double-counts.
        // NumberFormat adds thousands separators to the steps (8,421).
        tracking.deleteHcExercise(day)
        if (kcal > 0 || steps > 0) {
            tracking.insertExercise(ExerciseEntry(
                epochDay = day, name = app.getString(com.gymnam.app.R.string.hc_activity, NumberFormat.getIntegerInstance().format(steps)),
                emoji = "⌚", minutes = 0, kcal = kcal, source = "hc"
            ))
        }

        // 2) Write: today's foods and weigh-in (delete what we wrote before for this day, then re-add).
        // Delete what WE wrote for this day, then write the day's foods again. This keeps
        // Health Connect matching your diary even after you edit or delete a food.
        hc.deleteRecords(NutritionRecord::class, range)
        val foods = app.db.foodDao().entriesForDayNow(day)
        if (foods.isNotEmpty()) hc.insertRecords(foods.map { e ->
            val t = timeFor(e, date, zone)
            NutritionRecord(
                startTime = t, startZoneOffset = zone.rules.getOffset(t),
                endTime = t.plusSeconds(60), endZoneOffset = zone.rules.getOffset(t),
                metadata = Metadata.manualEntry(),
                energy = Energy.kilocalories(e.calories.toDouble()),
                protein = Mass.grams(e.proteinG), totalCarbohydrate = Mass.grams(e.carbsG), totalFat = Mass.grams(e.fatG),
                name = e.name,
                mealType = when (e.meal) {
                    Meal.BREAKFAST -> MealType.MEAL_TYPE_BREAKFAST; Meal.LUNCH -> MealType.MEAL_TYPE_LUNCH
                    Meal.DINNER -> MealType.MEAL_TYPE_DINNER; Meal.SNACK -> MealType.MEAL_TYPE_SNACK
                }
            )
        })
        // Same for the day's weight (saved at 7:00 AM).
        hc.deleteRecords(WeightRecord::class, range)
        tracking.weightForDay(day)?.let { w ->
            val t = date.atTime(7, 0).atZone(zone).toInstant()
            hc.insertRecords(listOf(WeightRecord(time = t, zoneOffset = zone.rules.getOffset(t),
                weight = Mass.pounds(w.lb), metadata = Metadata.manualEntry())))
        }
    }

    // The time stamp for a food: when you logged it if that was the same day,
    // otherwise a typical meal time (breakfast 8, lunch 13, snack 16, dinner 19).
    /** When you logged it, if that was on the same day; otherwise a typical time for that meal. */
    private fun timeFor(e: FoodEntry, date: LocalDate, zone: ZoneId): Instant {
        val logged = Instant.ofEpochMilli(e.createdAt)
        if (logged.atZone(zone).toLocalDate() == date) return logged
        val hour = when (e.meal) { Meal.BREAKFAST -> 8; Meal.LUNCH -> 13; Meal.DINNER -> 19; Meal.SNACK -> 16 }
        return date.atTime(hour, 0).atZone(zone).toInstant()
    }
}

