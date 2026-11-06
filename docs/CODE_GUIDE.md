# Gym & Ñam: how the code works (for non-coders)

Every code file now starts with a **PLAIN-ENGLISH GUIDE** box that explains what the file does. Inside each file, `//` lines explain each part. This guide is the map that ties the files together.

---

## 1. The big picture

The app has three layers. Each layer only talks to the one next to it.

```
 ┌──────────────────────────┐
 │  SCREEN  (…Screen.kt)    │  What you SEE: cards, buttons, text.
 │  draws, reacts to taps   │  Never does math or saves anything itself.
 └────────────┬─────────────┘
              │ "the user tapped Save"          ▲ "here are the new numbers"
              ▼                                 │
 ┌──────────────────────────┐
 │ VIEWMODEL (…ViewModel.kt)│  The BRAIN of one screen: does the math,
 │                          │  decides what to save, survives rotation.
 └────────────┬─────────────┘
              ▼                                 ▲ live updates (Flow)
 ┌──────────────────────────┐
 │ DATA  (data/ folder)     │  The database (your diary), settings,
 │ Dao, SettingsStore, …    │  the food list, and the internet services.
 └──────────────────────────┘
```

Data flows back up the stack by itself. When something changes in the database, every screen that shows it redraws automatically. That's why the calorie ring moves as soon as you log a food.

### Follow one tap: logging 2 eggs for breakfast
1. **MainScaffold.kt**: you tap **+ → Food**, and the picker opens for today, with the meal guessed from the clock (`mealForNow`).
2. **PickerScreen.kt**: you type "egg". **PickerViewModel.kt** asks **Catalog.kt**, which searches `assets/foods.csv`.
3. You tap "Egg, large". **PortionSheet.kt** opens, you set 2 pieces, and the calories update live. The list says 143 kcal per 100 g and one egg weighs 50 g, so 2 × 71.5 = 143 kcal.
4. You tap **Add to Breakfast**. `PickerViewModel.log()` builds a `FoodEntry` (**FoodEntry.kt**) and saves it with `FoodDao.insert()` (**FoodDao.kt**).
5. The database tells everyone something changed:
   - **TodayViewModel** recalculates the totals, and **TodayScreen / CalorieRing** redraw.
   - **GymNamApp.kt** refreshes the home-screen widget.
   - If Health Connect is on, **HealthSync.kt** syncs the new food.

---

## 2. Folder map

`app/src/main/java/com/gymnam/app/`

| File | What it is |
|---|---|
| `GymNamApp.kt` | Start-up: creates the shared tools; runs backup, reminders, Health Connect and widget refresh |
| `MainActivity.kt` | The single window: lock screen → setup → main app |
| **data/** | **Everything stored or calculated** |
| `FoodEntry.kt`, `FoodDao.kt` | Your food diary: one row, plus its database actions |
| `FoodItem.kt` | A food you *can* pick, with serving math |
| `Catalog.kt` | The built-in list of 1,000+ foods, and search |
| `SavedFood.kt` | My foods and My meals |
| `Tracking.kt` | Weight, water and exercise |
| `Fridge.kt` | Fridge items |
| `Recipe.kt`, `BuiltInRecipes.kt` | Recipes (yours plus built-in) |
| `FridgeMatch.kt` | "You have 4 of 6", and what cooking subtracts |
| `Profile.kt` | **Your body profile and ALL the calorie and macro formulas** |
| `Units.kt` | Imperial ↔ metric (lb/kg, ft-in/cm, fl oz/ml/L). Data is always saved in imperial; metric is only for display and typing |
| `Allergens.kt` | Allergy and diet keyword lists (EN/ES) behind the ⚠️ warnings |
| `Mood.kt` | Mood log ("How do you feel?") and the "possible triggers" counter |
| `ProfilePhoto.kt` | Saves your profile photo privately (512 px square) |
| `SettingsStore.kt` | Every setting and its default |
| `AppDatabase.kt` | The database file, its tables and upgrades (migrations) |
| `OpenFoodFacts.kt`, `MealDb.kt` | The two optional internet services (blocked when internet is OFF) |
| `Backup.kt` | Export/restore as a zip of CSV files; weekly auto-backup |
| `Reminders.kt` | Log reminder and weekly summary notifications |
| `HealthSync.kt` | Optional Health Connect link |
| **ui/** | **Everything you see**, one folder per area |
| `nav/MainScaffold.kt` | Bottom bar, + sheet, and the map of all screens |
| `nav/AppTopBar.kt` | The colored top bar (photo or logo, name, streak) and the header for inner pages |
| `me/` | The Me page: photo, name, body basics, allergies and diet, possible triggers |
| `calendar/` | The Calendar: month grid and what you ate on any day |
| `today/MoodUi.kt` | The "How do you feel?" card and check-in sheet |
| `common/Avatar.kt`, `common/Warnings.kt` | Round photo/logo, ⚠️ warning chips and banner |
| `today/` | Today tab: screen, brain, calorie ring, water and exercise cards |
| `picker/` | Food search, plus the amount sheet |
| `add/` | Type-a-food form |
| `scan/` | Barcode camera |
| `fridge/` | Fridge tab, fridge item form |
| `recipes/` | Recipe page, recipe editor |
| `progress/` | Progress tab and charts |
| `settings/`, `setup/` | Settings tab; first-time setup and "Edit my goals" |
| `quick/` | Weight, water and exercise sheets (plus the MET exercise formula) |
| `theme/Theme.kt` | Color palettes, corner roundness, text styles |
| `common/Common.kt` | Shared pieces: rounded card, emoji circle, emoji guesser |
| `AppViewModels.kt` | Builds each screen's brain |
| `widget/GymNamWidget.kt` | Home-screen widget |

Other places:
- `app/src/main/res/values/strings.xml` and `values-es/strings.xml` hold every text the app shows, in English and in Spanish.
- `app/src/main/assets/` holds `foods.csv` (the food list) and `recipes.json` (the built-in recipes).
- `tools/` holds the Python scripts that **generate** those two files and check them.
- `AndroidManifest.xml` lists the app's permissions and entry points.
- `app/build.gradle.kts` sets the version numbers and libraries.

---

## 3. Kotlin cheat sheet

| You see | It means |
|---|---|
| `val x = 5` | A name for a value that **can't** change |
| `var x = 5` | A value that **can** change |
| `fun add(a: Int): Int` | A function (an action) named `add`. It takes a whole number and gives one back |
| `Int` / `Long` / `Double` / `String` / `Boolean` | Whole number / big whole number / decimal number / text / true-false |
| `String?` | The `?` means "may be empty". Empty is called `null` |
| `a ?: b` | "Use `a`, or `b` if `a` is empty" |
| `x!!` | "I promise `x` isn't empty" (crashes if wrong; used sparingly) |
| `x?.let { … }` | "If `x` isn't empty, do this with it" |
| `class` | A blueprint for a thing with data and actions |
| `data class` | A simple record, like a spreadsheet row |
| `enum class Meal { BREAKFAST, … }` | A fixed list of choices |
| `object` | A one-of-a-kind thing; you call it directly: `GoalCalculator.calculate(p)` |
| `{ x -> … }` and `it` | A small piece of code passed around ("lambda"). `it` = its single input |
| `list.map { … }` / `filter` / `sumOf` | Transform each item / keep matching items / add them up |
| `when (x) { A -> …; else -> … }` | Choose by case (like a list of if/else) |
| `suspend fun` | Can take a moment; runs without freezing the screen |
| `viewModelScope.launch { }` | Do this in the background for this screen |
| `Flow` / `StateFlow` | A live value. Whoever watches it gets every update |
| `@Composable fun` | A function that **draws** part of the screen |
| `Modifier.padding(16.dp)` | Adjusts size and spacing. `dp` = a size unit that looks the same on every phone |
| `remember { }` | Keep this value between redraws |
| `LaunchedEffect` | Run something once when this part of the screen appears |
| `stringResource(R.string.save)` | The word "Save" / "Guardar" from strings.xml |
| `@Entity`, `@Dao`, `@Query("SELECT …")` | Database table / database actions / a question in SQL |
| `0xFF7CC6AE` | A color: ignore `0xFF` (fully opaque), the rest is web color `#7CC6AE` |
| `60_000` | 60,000. The `_` is only there to make the number easier to read |

---

## 4. "How do I…?"

| I want to… | Go to |
|---|---|
| Change a text the app shows | `res/values/strings.xml` **and** `res/values-es/strings.xml`, same `name` in both |
| Change the calorie formulas or the safety floor | `data/Profile.kt` → `GoalCalculator` (formulas in the file's guide box) |
| Change activity multipliers (1.2 … 1.9) | `data/Profile.kt` → `ActivityLevel` |
| Change the exercise calorie estimates (MET values) | `ui/quick/QuickAdd.kt` → `Activity` |
| Add or fix a built-in food | `tools/foods/data_*.py`, then run `generate_foods.py` and `check_foods.py` |
| Add a built-in recipe | `tools/recipes/recipes_data.py`, then run `generate_recipes.py` |
| Add or change a color palette | `ui/theme/Theme.kt` → `Palettes` (and add its name to both strings.xml) |
| Change default settings (water goal, reminder hour…) | `data/SettingsStore.kt` → `AppSettings` |
| Change the meal guessed by the clock | `ui/common/Common.kt` → `mealForNow` |
| Add an allergy keyword (e.g. a new cheese) | `data/Allergens.kt` → the matching list (lowercase, no accents) |
| Change the water quick buttons (fl oz or ml) | `data/Units.kt` → `waterPresets` |
| Add a mood symptom | `data/Mood.kt` → `Symptom`, plus its name in both strings.xml and `ui/today/MoodUi.kt` → `symptomLabel` |
| Change "expiring soon" (3 days) | `data/FridgeMatch.kt` → `SOON_DAYS`, and `ui/fridge/FridgeTab.kt` → `expiryGroup` / `ExpiryChip` |
| Change the app icon | `res/drawable/ic_launcher_fg.xml` (drawing), `res/values/colors.xml` (colors) |
| Release a new version | `app/build.gradle.kts`: raise `versionCode` by 1 and update `versionName` |
| Add a database table or column | `data/AppDatabase.kt`: raise `version` **and** add a migration, or existing installs will crash |

---

## 5. Where the formulas are

| Formula | File | In the code |
|---|---|---|
| BMR (Mifflin-St Jeor, Katch-McArdle), TDEE, goal ±500/lb, safety floor, macros | `data/Profile.kt` | `GoalCalculator.calculate` |
| Calories for an amount: kcal per serving × (grams ÷ serving grams) | `data/FoodItem.kt` | `factor`, `kcalFor` |
| Calories from macros: 4P + 4C + 9F | `ui/add/FoodEditorViewModel.kt` | `kcalFromMacros` |
| Exercise: MET × kg × hours | `ui/quick/QuickAdd.kt` | `Activity.estimate` |
| Daily budget: goal + exercise burned | `ui/today/TodayViewModel.kt` | `TodayState.goal` |
| 7-day weight trend (moving average) | `ui/progress/ProgressViewModel.kt` | `movingAverage` |
| Goal-update suggestion (trend moved 5+ lb) | `ui/today/TodayViewModel.kt` | `suggestion` |
| Streaks | `ui/today/TodayViewModel.kt` | `streaks` |
| Kitchen measures → grams (cups, tbsp, lb, oz…) | `data/MealDb.kt` | `measureToGrams` |
| Fridge units → grams (1 lb = 453.6 g, 1 oz = 28.35 g) | `data/Fridge.kt` | `FridgeUnit` |
| Units: kg = lb ÷ 2.20462, cm = in × 2.54, ml = fl oz × 29.5735 | `data/Units.kt` | `Units` |
| Possible triggers: foods named in 2+ "felt bad" check-ins | `data/Mood.kt` | `suspectCounts` |

---

## 6. Running it

1. Install **Android Studio** (free).
2. Choose **File → Open** and pick the `GymNam` folder.
3. Wait for the first "Gradle sync". It downloads the libraries, which takes a few minutes.
4. Plug in your phone with USB debugging on, or create an emulator. Then press **▶ Run**.
   For the smooth, fast version, first choose **Build → Select Build Variant → release**.
   The default "debug" version is always slower; it's made for developing, not daily use.
5. If something fails to build, copy the red error text from the **Build** panel. It names the file and line.
