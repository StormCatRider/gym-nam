# Gym & Ñam v1.1.0

Calorie counter for Android 10+ (Kotlin, Jetpack Compose, Room, Material 3).

**New to code?** Start with [docs/CODE_GUIDE.md](docs/CODE_GUIDE.md). Every source file also opens with a
PLAIN-ENGLISH GUIDE box and has `//` notes explaining each part.

## Run it
1. Unzip, then in Android Studio go to **File → Open** and select the `GymNam` folder.
2. Let Gradle sync. The first sync downloads Gradle 8.11.1 and the libraries.
   - If Studio says the wrapper is missing, go to *Settings → Build Tools → Gradle* and set
     "Distribution" to **Wrapper** (it reads `gradle/wrapper/gradle-wrapper.properties`).
3. Turn on USB debugging on your Pixel, plug it in and press ▶ Run.

## New in v1.1
- **Units:** Imperial (lb, ft/in, fl oz) or Metric (kg, cm, ml / L), chosen in setup and switchable in Settings. Data is always stored in imperial, so switching never changes your data.
- **Look:** Light / Dark / Follow phone in Settings. A colored top bar in your palette color with the app logo or your photo. Readable setup text in night mode.
- **Me page** (tap the logo or photo): profile photo (Android Photo Picker, saved privately), name, body basics, allergies (the 9 major allergens + your own words) and diets (vegetarian, vegan, no pork, lactose-free).
- **⚠️ Warnings** on foods, search results, the amount sheet and recipes, always with "check the label". Scanned products also use Open Food Facts' label allergens.
- **"How do you feel?"** mood log with symptoms and the foods you suspect, plus "It made me feel sick" from any food. The Me page shows foods that keep coming up ("possible triggers").
- **Calendar** (📅 on Today): month view with a dot per day (within goal / over goal) and each day's foods, totals, water, exercise and moods.
- **Smoother:** release build with R8 + baseline profiles. In Android Studio choose Build → Select Build Variant → **release**. GitHub also builds `app-release.apk` on every push (Actions → latest run → Artifacts).
- The backup zip now includes `mood.csv`. Database v5 (it migrates automatically and keeps your data).

## New in stage 5 (v1.0): setup, goals, lock, Health Connect, reminders
- **First-launch setup:** a 3-screen tour, then theme, then about you (sex, age, height in **ft/in**, weight in **lb**),
  activity level, goal (lose / maintain / gain + lb/week), formula and protein. It ends with the **math shown step by step**.
  Rerun it any time: Settings → Goals → Recalculate goals.
- **Goal formulas** (`data/Profile.kt`, `GoalCalculator`):
  - Mifflin-St Jeor: BMR = 10·W + 6.25·H − 5·A + 5 (men) / − 161 (women), with W in kg (= lb ÷ 2.2046), H in cm (= in × 2.54), A = age
  - Katch-McArdle: BMR = 370 + 21.6·LBM, where LBM = W·(1 − body-fat %)
  - TDEE = BMR × activity (1.2 / 1.375 / 1.55 / 1.725 / 1.9)
  - Target = TDEE ± rate × 3,500 kcal/lb ÷ 7 days. Never below 1,500 (men) / 1,200 (women) kcal, nor below BMR when losing.
  - Protein = g/lb × body weight. Fat = 25% of calories ÷ 9 (at least 0.3 g/lb). Carbs = the rest ÷ 4.
- **Goal-update suggestion:** when your 7-day trend weight is **5 lb** away from the weight your goals used, Today shows
  "Update from X to Y kcal?". "Not now" stays quiet until the trend moves another 5 lb.
- **App lock (optional):** fingerprint/face or your phone's PIN, via Android's own prompt. The app stores nothing.
  It re-locks after 1 minute in the background.
- **Reminders (optional):** an evening log reminder (only if nothing is logged that day; pick 6–10 pm) and a **weekly summary** on
  Sunday at 6 pm. Notification permission is asked only when you turn one on.
- **Health Connect (optional, off by default):** permissions are requested only when you turn it on.
  - **Reads** steps and active calories into a "Daily activity" exercise entry.
  - **Writes** each food (kcal and macros) and your weigh-ins, so your future gym app can read them.
  - It only replaces records it wrote itself.

## Audit (v1.0.1)
**Copyright / licensing**
- The project bundles **no images, fonts or copied text**. The launcher icon is an original vector. Food icons are the phone's emoji.
- Brand-name foods were renamed to generic descriptions with a "(… type)" hint, so searching "takis" still works.
- Open Food Facts (ODbL) attribution is shown under online results and in Settings → About & credits.
  TheMealDB's free key is for personal use; get their supporter key before publishing. See `THIRD_PARTY_NOTICES.md`.

**Database: checked twice**
1. *Automated pass over all 1,038 foods and 34 recipes* (`python3 tools/foods/check_foods.py`). It checks:
   calories vs 4·P + 4·C + 9·F, macro mass ≤ 100 g, protein density, serving weights per label (1 oz = 28 g, etc.),
   per-serving extremes, missing translations, duplicates, and that every recipe's numbers equal its ingredients.
2. *Reference check against USDA FoodData Central* on 26 foods: every value I was unsure of plus a cross-section.
   15 matched; 11 were corrected to USDA (cooked ground beef ×4, hamburger patty, ground turkey 93%, sirloin, ribeye,
   pork chop, drumstick, wings) plus flour tortillas. The automated pass was re-run afterward: 0 flags.
- Composite dishes (tacos, burritos, restaurant meals) are typical estimates, and the app says so.

**Code review**
- Fixed fridge matching false positives ("Queso panela" was counted as bread, "Papaya" as potato, "Caldo de pollo" as
  chicken, "Salsa verde" as soy sauce). Single-word keys now match whole words only, and ingredients have exclude lists.
- Fixed the midnight rollover: Today, the widget and Health Connect now follow the new day if the app stays open.
- Verified Health Connect's `Metadata.manualEntry()` API (connect-client ≥ 1.1.0-alpha12; the project uses 1.1.0).
- Executable mirrors of the goal math, CSV backup round trip, streaks and 7-day trend all give the expected results.

## If the build complains
- *Health Connect `Metadata.manualEntry` not found:* your connect-client version differs. Use `1.1.0` (as set),
  or on an older alpha remove the `metadata = …` arguments in `data/HealthSync.kt`.
- *A library version can't be found:* in Android Studio, use the suggested newer version (File → Project Structure → Suggestions).

## Stage 4 (Progress, water, weight, exercise, widget, backup)
- **Progress tab** with range chips (7 days / 30 days / 90 days / 1 year):
  - **Streak** 🔥: current and best, plus days logged in the range.
  - **Weight (lb):** latest, **7-day trend** and change over the range. The chart shows daily weigh-ins as dots and the trend as a line.
    The trend for each day is the average of the weigh-ins in the 7 days ending that day, which smooths out water-weight swings.
  - **Calories:** daily bars against a dashed goal line (bars over goal show in soft red). Over 60 days they become weekly averages.
    Average kcal and protein per logged day.
  - **Water:** average fl oz per day against your goal.
  - **History:** every logged day. Tap one to open it on Today.
- **+ button:** Water, Weight and Exercise are all live now.
  - **Water (fl oz):** presets 8–32 or a custom amount. Today has a water card with glasses, +8/+12/+16 and undo.
  - **Weight (lb):** one per day (logging again replaces it). ±0.2 lb steppers.
  - **Exercise:** walk, run, bike, weights, HIIT, swim, sports, yoga, other. Calories are **estimated with METs**:
    kcal = MET × body weight (kg) × hours, using your latest weight (kg = lb ÷ 2.2046). The number is editable.
    Burned calories **add to today's budget**: the ring uses goal + exercise.
- **Home-screen widget:** calories left, progress bar, macros and water. Weight appears **only if** you turn it on in Settings.
  It updates as you log, and every 30 min.
- **Backup:** Export makes a .zip of CSVs (opens in Excel/Sheets). Restore replaces data from a backup after asking you first.
  A **weekly automatic backup** goes to Downloads/GymNam and keeps the last 4. "Back up now" is in Settings.
- Your data from stages 1–3 is kept (database migration 3 → 4).

## Stage 3 (Fridge & recipes)
- **Fridge tab** has two sections: 🧊 Fridge and 🍳 Recipes.
  - Items are grouped as **Expired / Use soon (≤ 3 days) / Fresh / No date**, with colored chips ("in 2 days").
  - Add items by typing or **scanning**. A barcode fills the name from My foods (offline) or Open Food Facts (if online is on).
  - Quantity in **pcs, pack, g, oz, lb or ml**. Expiry quick picks: +3 days, +1 week, +2 weeks, +1 month, or a calendar.
  - Tap an item to edit it. Long-press to delete it, with Undo. The + button also has **Fridge item** now.
- **34 built-in recipes** (EN/ES, Mexican-leaning, high protein) whose nutrition is computed from the same food list.
- **Sort chips:** I have the most · Uses expiring · Fits X kcal left (today's remaining calories) · High protein · Quickest · Lightest.
  Each card shows 🧊 4/6 (what you have), ⏰ if it uses something expiring, and ✓ if it fits your calories left.
- **Recipe page:** nutrition per serving, ingredients marked ✅ when found in your fridge (and which item), and steps.
- **Cook & log:** choose servings eaten and the meal. Optionally **take the whole recipe's ingredients out of the fridge**
  (lb/oz/g/ml convert automatically, eggs count as pieces, items that hit zero are removed). You see a "2 lb → 1.01 lb" preview first.
- **My recipes:** build from ingredients (search the food list or My foods, grams or servings) and calories calculate live.
  Turn on **"Set totals myself"** to override them. Long-press an ingredient to mark it a pantry staple 🧂.
  "Save as mine" copies a built-in or online recipe so you can edit it.
- **TheMealDB online recipes** (English) appear under the same internet switch, off by default. They don't publish nutrition,
  so the app **estimates** it by matching each ingredient to the food list and converting measures (cups, tbsp, lb, oz, cloves…).
- Your data from stages 1–2 is kept (database migration 2 → 3).
- Recipe source: `tools/recipes/recipes_data.py`. Run `python3 tools/recipes/generate_recipes.py` to rebuild `assets/recipes.json`.

## Stage 2 (food search, barcodes, My foods/meals)
- **+ → Food** now opens a search screen with **All · My foods · My meals · Recent** tabs.
- **1,038 built-in foods** that work offline, in English and Spanish, with accent-insensitive search ("platano" finds Plátano).
  About 220 are Mexican foods: tacos by type, tamales, pozole, pan dulce, aguas frescas and more.
  The source list is `tools/foods/data_*.py`. Edit a row and run `python3 tools/foods/generate_foods.py` to rebuild `assets/foods.csv`.
- **Portion sheet:** −/+ steppers, servings ↔ grams (it converts the number for you), live kcal and macros, and meal chips.
- **My foods:** turn on "Save to My foods" when adding something. Long-press a saved food to delete it.
- **My meals:** on Today, tap 🔖 on a meal to save all its foods. Log them all later in one tap.
- **Recent:** re-log anything you've eaten before.
- **Barcode scanner:** CameraX + ZXing, decoded on the phone with no Google services. A barcode you saved
  works offline forever. Unknown barcodes go to Open Food Facts (if online is on) or to manual entry.
- **Online search switch** (Settings) is **OFF by default**. When it's off, no network call is ever made.
  When it's on, only your search words or barcodes are sent to Open Food Facts. Online search runs only when you tap
  the button, which respects their rate limits.
- Your stage 1 food log is kept (database migration 1 → 2).

## Stage 1 features (still there)
Today ring, macro bars, meal cards, edit/delete with Undo, 5 pastel palettes + dark mode, EN/ES.

## Roadmap
| Stage | Adds |
|---|---|
| 2 ✅ | 1,000+ offline foods, saved foods/meals, Open Food Facts + barcode (internet toggle, off by default) |
| 3 ✅ | Fridge (expiry, scan, auto-subtract), recipes (built-in + TheMealDB + yours) |
| 4 ✅ | Progress: weight (lb) with 7-day trend, history, charts, water (fl oz), streaks, widget, CSV export/import + weekly auto-backup |
| 5 ✅ | Onboarding (ft/in, lb, Mifflin-St Jeor / Katch-McArdle, protein g/lb), goal-update suggestions, PIN/fingerprint, Health Connect (optional), reminders |

## Code map
- `data/`: Room `FoodEntry` (one flat row per logged food, easy to export or share with the future gym app), `SettingsStore` (DataStore)
- `ui/today/`: `TodayScreen`, `CalorieRing`, `TodayViewModel`
- `ui/add/`: the food editor
- `ui/nav/MainScaffold.kt`: bottom bar, center + button, add sheet, navigation
- `ui/theme/Theme.kt`: pastel palettes. Add your own to the `Palettes` list.
- `data/Catalog.kt`: the built-in food list and search · `data/OpenFoodFacts.kt`: online search + barcode, gated by the switch
- `ui/picker/`: search screen, portion sheet, meal sheet · `ui/scan/`: barcode scanner
- `tools/foods/`: source data and generator for the built-in list
