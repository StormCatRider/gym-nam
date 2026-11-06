// ==============================================================================
// PLAIN-ENGLISH GUIDE
//
// WHAT THIS FILE IS: backup and restore.
//  - export(): writes ONE .zip file containing a CSV file per table
//    (food_log.csv, weight.csv, water.csv, ...). CSV opens in Excel / Google
//    Sheets, so your data is never locked inside the app.
//  - restore(): reads such a zip and REPLACES all your data with it.
//  - autoBackupToDownloads(): the weekly automatic copy in Downloads/GymNam
//    (keeps the newest 4).
//  - schedule(): turns the weekly job on or off.
//  - Csv: tiny helper that writes/reads CSV text correctly.
// Settings and goals are NOT included in the backup.
// PRIVACY NOTE: the Downloads folder can be read by other apps you allow
// to see your files; turn auto-backup off in Settings if you don't want that.
// ==============================================================================
package com.gymnam.app.data

import android.content.ContentValues
import android.content.Context
import android.os.Environment
import android.provider.MediaStore
import androidx.room.withTransaction
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.gymnam.app.GymNamApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream
import java.time.LocalDate
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Backup = one .zip with a plain CSV per table (opens in Excel / Google Sheets):
 *   food_log.csv, weight.csv, water.csv, exercise.csv, mood.csv, my_foods.csv, my_meals.csv, fridge.csv, recipes.csv
 * Restore replaces everything with the backup's contents, in one transaction (all or nothing).
 * Settings and goals are not included.
 */
object Backup {

    // put(...) writes one CSV file into the zip: a header row with column names,
    // then one row per record. Lines end with \r\n (what Excel expects).
    // The invisible "BOM" mark at the start makes Excel show ñ and á correctly.
    // Dates are written as 2026-11-05 instead of the internal day numbers.
    suspend fun export(app: GymNamApp, out: OutputStream) = withContext(Dispatchers.IO) {
        val db = app.db
        val f = db.foodDao(); val t = db.trackingDao(); val s = db.savedDao(); val fr = db.fridgeDao(); val r = db.recipeDao()
        ZipOutputStream(out.buffered()).use { zip ->
            fun put(name: String, header: List<String>, rows: List<List<Any?>>) {
                zip.putNextEntry(ZipEntry(name))
                val sb = StringBuilder("﻿") // BOM so Excel reads ñ/á correctly
                sb.append(header.joinToString(",") { Csv.quote(it) }).append("\r\n")
                rows.forEach { row -> sb.append(row.joinToString(",") { Csv.quote(it?.toString().orEmpty()) }).append("\r\n") }
                zip.write(sb.toString().toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
            put("food_log.csv",
                listOf("id", "date", "meal", "name", "emoji", "amount", "unit", "kcal", "protein_g", "carbs_g", "fat_g", "created"),
                f.all().map { listOf(it.id, LocalDate.ofEpochDay(it.epochDay), it.meal, it.name, it.emoji, it.amount, it.unit,
                    it.calories, it.proteinG, it.carbsG, it.fatG, it.createdAt) })
            put("weight.csv", listOf("id", "date", "lb", "created"),
                t.allWeights().map { listOf(it.id, LocalDate.ofEpochDay(it.epochDay), it.lb, it.createdAt) })
            put("water.csv", listOf("id", "date", "fl_oz", "created"),
                t.allWater().map { listOf(it.id, LocalDate.ofEpochDay(it.epochDay), it.flOz, it.createdAt) })
            put("exercise.csv", listOf("id", "date", "name", "emoji", "minutes", "kcal", "source", "created"),
                t.allExercise().map { listOf(it.id, LocalDate.ofEpochDay(it.epochDay), it.name, it.emoji, it.minutes, it.kcal, it.source, it.createdAt) })
            // mood.csv (app 1.1): symptoms are ids like "nausea,bloating"; foods are separated by " | ".
            put("mood.csv", listOf("id", "date", "mood", "symptoms", "foods", "note", "created"),
                app.db.moodDao().all().map { listOf(it.id, LocalDate.ofEpochDay(it.epochDay), it.mood, it.symptoms,
                    it.foodList.joinToString(" | "), it.note, it.createdAt) })
            put("my_foods.csv", listOf("id", "name", "emoji", "serving", "serving_g", "kcal", "protein_g", "carbs_g", "fat_g", "barcode", "created"),
                s.foodsNow().map { listOf(it.id, it.name, it.emoji, it.servingName, it.servingG, it.kcal, it.proteinG, it.carbsG, it.fatG, it.barcode, it.createdAt) })
            put("my_meals.csv", listOf("id", "name", "emoji", "items_json", "created"),
                s.mealsNow().map { listOf(it.id, it.name, it.emoji, it.itemsJson, it.createdAt) })
            put("fridge.csv", listOf("id", "name", "emoji", "quantity", "unit", "expires", "barcode", "created"),
                fr.allNow().map { listOf(it.id, it.name, it.emoji, it.quantity, it.unit, it.expiryDay?.let(LocalDate::ofEpochDay), it.barcode, it.createdAt) })
            put("recipes.csv", listOf("id", "name", "emoji", "servings", "minutes", "ingredients_json", "steps",
                "kcal_override", "protein_override", "carbs_override", "fat_override", "created"),
                r.allNow().map { listOf(it.id, it.name, it.emoji, it.servings, it.minutes, it.ingredientsJson, it.steps,
                    it.kcalOverride, it.proteinOverride, it.carbsOverride, it.fatOverride, it.createdAt) })
        }
    }

    // 1. Unzip every CSV into memory.
    // 2. Refuse the file if it has no food_log.csv (not one of our backups).
    // 3. Small helpers (d, l, i, s, day) read a column as decimal / long number /
    //    whole number / text / date, using 0 or empty if the cell is bad.
    // 4. Rebuild every record. Rows with an unreadable date are skipped.
    // 5. Replace all tables at once (see withTransaction below).
    // Returns how many food rows were restored (shown in the message).
    /** Returns how many food-log rows were restored. Throws if the file isn't a Gym & Ñam backup. */
    suspend fun restore(app: GymNamApp, input: InputStream): Int = withContext(Dispatchers.IO) {
        val tables = mutableMapOf<String, List<Map<String, String>>>()
        ZipInputStream(input.buffered()).use { zip ->
            while (true) {
                val e = zip.nextEntry ?: break
                tables[e.name] = Csv.parse(zip.readBytes().toString(Charsets.UTF_8).removePrefix("﻿"))
            }
        }
        require("food_log.csv" in tables) { "Not a Gym & Ñam backup" }
        fun rows(n: String) = tables[n].orEmpty()
        fun Map<String, String>.d(k: String) = this[k]?.toDoubleOrNull() ?: 0.0
        fun Map<String, String>.dn(k: String) = this[k]?.takeIf { it.isNotBlank() }?.toDoubleOrNull()
        fun Map<String, String>.l(k: String) = this[k]?.toLongOrNull() ?: 0L
        fun Map<String, String>.i(k: String) = this[k]?.toDoubleOrNull()?.toInt() ?: 0
        fun Map<String, String>.s(k: String) = this[k].orEmpty()
        fun Map<String, String>.day(k: String) = runCatching { LocalDate.parse(s(k)).toEpochDay() }.getOrNull()

        val food = rows("food_log.csv").mapNotNull { m ->
            FoodEntry(m.l("id"), m.day("date") ?: return@mapNotNull null,
                runCatching { Meal.valueOf(m.s("meal")) }.getOrDefault(Meal.SNACK), m.s("name"), m.s("emoji").ifBlank { "🍽️" },
                m.d("amount"), runCatching { AmountUnit.valueOf(m.s("unit")) }.getOrDefault(AmountUnit.SERVING),
                m.i("kcal"), m.d("protein_g"), m.d("carbs_g"), m.d("fat_g"), m.l("created"))
        }
        val weights = rows("weight.csv").mapNotNull { m -> WeightEntry(m.l("id"), m.day("date") ?: return@mapNotNull null, m.d("lb"), m.l("created")) }
            .distinctBy { it.epochDay }
        val water = rows("water.csv").mapNotNull { m -> WaterEntry(m.l("id"), m.day("date") ?: return@mapNotNull null, m.d("fl_oz"), m.l("created")) }
        val exercise = rows("exercise.csv").mapNotNull { m ->
            ExerciseEntry(m.l("id"), m.day("date") ?: return@mapNotNull null, m.s("name"), m.s("emoji"), m.i("minutes"), m.i("kcal"),
                m.s("source").ifBlank { "manual" }, m.l("created"))
        }
        // Older backups have no mood.csv: then your current mood log is left as it is.
        val hasMood = "mood.csv" in tables
        val moods = rows("mood.csv").mapNotNull { m ->
            MoodEntry(m.l("id"), m.day("date") ?: return@mapNotNull null, m.l("created"), m.i("mood").coerceIn(1, 5),
                m.s("symptoms"), m.s("foods").split(" | ").map { it.trim() }.filter { it.isNotEmpty() }.joinToString("\n"), m.s("note"))
        }
        val foods = rows("my_foods.csv").map { m ->
            SavedFood(m.l("id"), m.s("name"), m.s("emoji"), m.s("serving"), m.d("serving_g"), m.d("kcal"), m.d("protein_g"),
                m.d("carbs_g"), m.d("fat_g"), m.s("barcode").ifBlank { null }, m.l("created"))
        }
        val meals = rows("my_meals.csv").map { m -> SavedMeal(m.l("id"), m.s("name"), m.s("emoji"), m.s("items_json"), m.l("created")) }
        val fridge = rows("fridge.csv").map { m ->
            FridgeItem(m.l("id"), m.s("name"), m.s("emoji"), m.d("quantity"),
                runCatching { FridgeUnit.valueOf(m.s("unit")) }.getOrDefault(FridgeUnit.PIECE),
                m.day("expires"), m.s("barcode").ifBlank { null }, m.l("created"))
        }
        val recipes = rows("recipes.csv").map { m ->
            Recipe(m.l("id"), m.s("name"), m.s("emoji"), m.i("servings").coerceAtLeast(1), m.i("minutes"), m.s("ingredients_json"),
                m.s("steps"), m.dn("kcal_override"), m.dn("protein_override"), m.dn("carbs_override"), m.dn("fat_override"), m.l("created"))
        }

        val db = app.db
        // A "transaction" = all-or-nothing. If anything fails halfway, the database goes
        // back to exactly how it was, so a bad file can never leave you half-erased.
        db.withTransaction {
            val f = db.foodDao(); val t = db.trackingDao(); val s = db.savedDao(); val fr = db.fridgeDao(); val r = db.recipeDao()
            f.clear(); t.clearWeights(); t.clearWater(); t.clearExercise(); s.clearFoods(); s.clearMeals(); fr.clear(); r.clear()
            f.insertAll(food); t.insertWeights(weights); t.insertWaters(water); t.insertExercises(exercise)
            s.insertFoods(foods); s.insertMeals(meals); fr.insertAll(fridge); r.insertAll(recipes)
            if (hasMood) { db.moodDao().clear(); db.moodDao().insertAll(moods) }
        }
        food.size
    }

    // MediaStore = Android's shared-files system. It lets the app create a file in
    // Downloads without asking for storage permission (Android 10+).
    // IS_PENDING = 1 hides the file while it's being written; 0 shows it when done.
    // Then it lists our auto-backups newest first and deletes all but the newest 4.
    /**
     * Writes Downloads/GymNam/GymNam-auto-YYYY-MM-DD.zip (no storage permission needed on Android 10+)
     * and keeps the 4 newest automatic backups.
     */
    suspend fun autoBackupToDownloads(app: GymNamApp) = withContext(Dispatchers.IO) {
        val resolver = app.contentResolver
        val folder = Environment.DIRECTORY_DOWNLOADS + "/GymNam/"
        val name = "GymNam-auto-${LocalDate.now()}.zip"
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, name)
            put(MediaStore.Downloads.MIME_TYPE, "application/zip")
            put(MediaStore.Downloads.RELATIVE_PATH, folder)
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: error("Couldn't create backup file")
        resolver.openOutputStream(uri)?.use { export(app, it) }
        resolver.update(uri, ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }, null, null)

        // Prune: keep the newest 4 automatic backups this app created.
        resolver.query(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.Downloads._ID, MediaStore.Downloads.DISPLAY_NAME),
            "${MediaStore.Downloads.DISPLAY_NAME} LIKE ?", arrayOf("GymNam-auto-%"),
            "${MediaStore.Downloads.DATE_ADDED} DESC"
        )?.use { c ->
            var i = 0
            while (c.moveToNext()) {
                if (i++ >= 4) {
                    val id = c.getLong(0)
                    runCatching { resolver.delete(android.content.ContentUris.withAppendedId(MediaStore.Downloads.EXTERNAL_CONTENT_URI, id), null, null) }
                }
            }
        }
        app.settings.setLastBackup(System.currentTimeMillis())
    }

    private const val WORK = "weekly-backup"

    // WorkManager = Android's scheduler for background jobs; it runs them even if the
    // app is closed or the phone restarted. "Every 7 days" here; KEEP = if it's
    // already scheduled, leave it as is (don't restart the 7-day clock).
    /** Turns the weekly backup on/off. Safe to call on every app start. */
    fun schedule(context: Context, enabled: Boolean) {
        val wm = WorkManager.getInstance(context)
        if (enabled) wm.enqueueUniquePeriodicWork(
            WORK, ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<BackupWorker>(7, TimeUnit.DAYS).build()
        ) else wm.cancelUniqueWork(WORK)
    }
}

// The weekly job itself. If it fails (e.g. storage busy) it asks WorkManager to
// retry later.
class BackupWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result = runCatching {
        Backup.autoBackupToDownloads(applicationContext as GymNamApp)
        Result.success()
    }.getOrElse { Result.retry() }
}

// CSV rules: values are separated by commas. If a value itself contains a comma,
// quote or line break, it is wrapped in "quotes" and inner quotes are doubled ("").
// quote() applies that when writing; parse() undoes it when reading, one
// character at a time, and returns each row as {column name -> value}.
/** Minimal RFC-4180 CSV: quotes, commas and line breaks inside fields. */
object Csv {
    fun quote(s: String): String =
        if (s.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + s.replace("\"", "\"\"") + "\"" else s

    fun parse(text: String): List<Map<String, String>> {
        val records = mutableListOf<List<String>>()
        var field = StringBuilder(); var row = mutableListOf<String>(); var inQuotes = false; var i = 0
        while (i < text.length) {
            val ch = text[i]
            when {
                inQuotes && ch == '"' && i + 1 < text.length && text[i + 1] == '"' -> { field.append('"'); i++ }
                ch == '"' -> inQuotes = !inQuotes
                !inQuotes && ch == ',' -> { row.add(field.toString()); field = StringBuilder() }
                !inQuotes && (ch == '\n' || ch == '\r') -> {
                    if (ch == '\r' && i + 1 < text.length && text[i + 1] == '\n') i++
                    row.add(field.toString()); field = StringBuilder()
                    if (row.any { it.isNotEmpty() }) records.add(row)
                    row = mutableListOf()
                }
                else -> field.append(ch)
            }
            i++
        }
        if (field.isNotEmpty() || row.isNotEmpty()) { row.add(field.toString()); if (row.any { it.isNotEmpty() }) records.add(row) }
        if (records.isEmpty()) return emptyList()
        val header = records.first()
        return records.drop(1).map { r -> header.indices.associate { header[it] to r.getOrElse(it) { "" } } }
    }
}
