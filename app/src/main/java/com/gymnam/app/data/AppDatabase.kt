// ==============================================================================
// PLAIN-ENGLISH GUIDE
//
// WHAT THIS FILE IS: the database itself (Room = Android's database library,
// built on SQLite). It is ONE file on your phone named "gymnam.db".
// Nothing here goes to the internet.
//
// TABLES (think: sheets in a spreadsheet):
//  food_entries   your food diary            saved_foods   "My foods"
//  saved_meals    "My meals"                 fridge_items  the Fridge tab
//  recipes        your own recipes           weight_entries, water_entries,
//  exercise_entries  the tracking cards on Today
//
// VERSIONS AND MIGRATIONS: each time the app gained new tables (stages 2, 3 and 4)
// the database "version" went up. A "migration" is the step that adds the new
// tables WITHOUT erasing what you already logged. If you ever add a table, you
// must raise "version" and write a new migration, or the app will crash on
// phones that have the old version.
// ==============================================================================
package com.gymnam.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

// The database can only store numbers and text. These "converters" turn our
// choice lists (Meal, AmountUnit, FridgeUnit) into text ("LUNCH") and back.
class Converters {
    @TypeConverter fun mealToString(m: Meal): String = m.name
    @TypeConverter fun stringToMeal(s: String): Meal = Meal.valueOf(s)
    @TypeConverter fun unitToString(u: AmountUnit): String = u.name
    @TypeConverter fun stringToUnit(s: String): AmountUnit = AmountUnit.valueOf(s)
    @TypeConverter fun fridgeUnitToString(u: FridgeUnit): String = u.name
    @TypeConverter fun stringToFridgeUnit(s: String): FridgeUnit = FridgeUnit.valueOf(s)
}

// The list of tables and the current version number (4).
@Database(
    entities = [
        FoodEntry::class, SavedFood::class, SavedMeal::class, FridgeItem::class, Recipe::class,
        WeightEntry::class, WaterEntry::class, ExerciseEntry::class
    ],
    version = 4,
    exportSchema = true
)
// Each "fun xxxDao()" hands out one menu of database actions (see the Dao files).
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun foodDao(): FoodDao
    abstract fun savedDao(): SavedDao
    abstract fun fridgeDao(): FridgeDao
    abstract fun recipeDao(): RecipeDao
    abstract fun trackingDao(): TrackingDao

    companion object {
        // CREATE TABLE IF NOT EXISTS = make the table only if it isn't there yet.
        // Each `name` TYPE pair is one column. INTEGER = whole number, REAL = decimal,
        // TEXT = words, NOT NULL = must have a value.
        /** Stage 1 → 2: adds My foods + My meals. Your existing food log is kept. */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `saved_foods` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, " +
                        "`emoji` TEXT NOT NULL, `servingName` TEXT NOT NULL, `servingG` REAL NOT NULL, " +
                        "`kcal` REAL NOT NULL, `proteinG` REAL NOT NULL, `carbsG` REAL NOT NULL, " +
                        "`fatG` REAL NOT NULL, `barcode` TEXT, `createdAt` INTEGER NOT NULL)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_saved_foods_barcode` ON `saved_foods` (`barcode`)")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `saved_meals` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, " +
                        "`emoji` TEXT NOT NULL, `itemsJson` TEXT NOT NULL, `createdAt` INTEGER NOT NULL)"
                )
            }
        }

        /** Stage 2 → 3: adds the Fridge and My recipes. */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `fridge_items` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, " +
                        "`emoji` TEXT NOT NULL, `quantity` REAL NOT NULL, `unit` TEXT NOT NULL, " +
                        "`expiryDay` INTEGER, `barcode` TEXT, `createdAt` INTEGER NOT NULL)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `recipes` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, " +
                        "`emoji` TEXT NOT NULL, `servings` INTEGER NOT NULL, `minutes` INTEGER NOT NULL, " +
                        "`ingredientsJson` TEXT NOT NULL, `steps` TEXT NOT NULL, `kcalOverride` REAL, " +
                        "`proteinOverride` REAL, `carbsOverride` REAL, `fatOverride` REAL, `createdAt` INTEGER NOT NULL)"
                )
            }
        }

        /** Stage 3 → 4: adds weight, water and exercise. */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `weight_entries` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`epochDay` INTEGER NOT NULL, `lb` REAL NOT NULL, `createdAt` INTEGER NOT NULL)"
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_weight_entries_epochDay` ON `weight_entries` (`epochDay`)")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `water_entries` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`epochDay` INTEGER NOT NULL, `flOz` REAL NOT NULL, `createdAt` INTEGER NOT NULL)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_water_entries_epochDay` ON `water_entries` (`epochDay`)")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `exercise_entries` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`epochDay` INTEGER NOT NULL, `name` TEXT NOT NULL, `emoji` TEXT NOT NULL, `minutes` INTEGER NOT NULL, " +
                        "`kcal` INTEGER NOT NULL, `source` TEXT NOT NULL, `createdAt` INTEGER NOT NULL)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_exercise_entries_epochDay` ON `exercise_entries` (`epochDay`)")
            }
        }

        // Opens (or creates) gymnam.db and registers every migration so older
        // installs upgrade step by step: 1 -> 2 -> 3 -> 4.
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "gymnam.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                .build()
    }
}
