# Third-party notices — Gym & Ñam

## Data
- **Open Food Facts** (online search & barcode lookup, only when you turn online search on):
  © Open Food Facts contributors. Database under the Open Database License (ODbL) 1.0; contents under the
  Database Contents License. Attribution is shown in the app (search results + Settings → About & credits).
  Product images are not used. Terms: https://world.openfoodfacts.org/terms-of-use
- **TheMealDB** (online recipes, only when online search is on): uses the free test key `1`, which TheMealDB
  describes as for development, learning and personal projects. **Before publishing the app publicly, get a
  supporter/production key** (https://themealdb.com/documentation) and replace `/json/v1/1/` in `data/MealDb.kt`.
- **Built-in food list** (`assets/foods.csv`): nutrition reference figures in the style of USDA FoodData Central
  (U.S. government data, public domain). Nutrition values are facts; names and structure are original.
- **Exercise estimates:** MET values from the Compendium of Physical Activities (facts/values only).
- **Built-in recipes, all UI text, and the launcher icon** are original to this project.
- **Brand names** (e.g. "Takis type") appear only to describe a kind of product; no affiliation or endorsement.
  The app bundles **no images or fonts**; food icons are the phone's system emoji.

## Open-source libraries (Apache License 2.0 — https://www.apache.org/licenses/LICENSE-2.0)
Kotlin, kotlinx.coroutines, AndroidX / Jetpack (Core, Activity, Fragment, Lifecycle, Navigation, Compose UI,
Material 3, Material Icons, Room, DataStore, CameraX, WorkManager, Glance, Biometric, Health Connect client),
and ZXing core.
