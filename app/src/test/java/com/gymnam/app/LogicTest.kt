// ==============================================================================
// PLAIN-ENGLISH GUIDE
// WHAT THIS FILE IS: automatic checks ("unit tests") for the app's math and matching.
// They run on GitHub every time code is pushed (no phone needed). Each @Test feeds
// known inputs and checks the exact answer; if any answer changes, the build fails
// and tells us which check broke.
//   - Units: lb/kg, ft-in/cm, fl oz/ml/L conversions.
//   - Goals: the worked example from data/Profile.kt (2,240 kcal).
//   - Allergens: warnings show up when they should, and NOT for look-alikes
//     (peanut butter isn't dairy, eggplant isn't egg, "Tuna (fruta)" isn't fish).
//   - Mood: "possible triggers" counting.
// ==============================================================================
package com.gymnam.app

import com.gymnam.app.data.ActivityLevel
import com.gymnam.app.data.Allergens
import com.gymnam.app.data.DietPrefs
import com.gymnam.app.data.GoalCalculator
import com.gymnam.app.data.GoalType
import com.gymnam.app.data.MoodEntry
import com.gymnam.app.data.Profile
import com.gymnam.app.data.Sex
import com.gymnam.app.data.Units
import com.gymnam.app.data.suspectCounts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LogicTest {

    // ---------- Units ----------
    @Test fun weightConversions() {
        assertEquals("81.6 kg", Units.weight(180.0, metric = true))
        assertEquals("180 lb", Units.weight(180.0, metric = false))
        assertEquals(180.0, Units.lbFrom(81.6466, metric = true), 0.01)
        assertEquals("−3.4 lb", Units.weightChange(-3.4, metric = false))
        assertEquals("+0.7 kg", Units.weightChange(1.5, metric = true))
    }

    @Test fun heightConversions() {
        assertEquals("175 cm", Units.height(69.0, metric = true))
        assertEquals("5 ft 9 in", Units.height(69.0, metric = false))
    }

    @Test fun waterConversions() {
        assertEquals("64 fl oz", Units.water(64.0, metric = false))
        assertEquals("1.9 L", Units.water(64.0, metric = true))          // 1,893 ml
        assertEquals("250 ml", Units.water(250 / Units.ML_PER_FLOZ, metric = true))
        assertEquals("1.25 L", Units.water(1250 / Units.ML_PER_FLOZ, metric = true))
        assertEquals("2 L", Units.water(2000 / Units.ML_PER_FLOZ, metric = true))
        assertEquals(68, Units.waterGoalFlOz(2.0, metric = true))        // 2 L = 67.6 fl oz
        assertEquals(1.9, Units.waterGoalValue(64, metric = true), 0.0001)
        assertEquals(500 / Units.ML_PER_FLOZ, Units.flOzFrom(0.5, metric = true, liters = true), 0.0001)
    }

    // ---------- Goals (same example as the comment in data/Profile.kt) ----------
    @Test fun goalExample() {
        val p = Profile(sex = Sex.MALE, age = 30, heightIn = 69.0, weightLb = 180.0, activity = ActivityLevel.MODERATE,
            goal = GoalType.LOSE, rateLbWeek = 1.0, proteinPerLb = 0.8)
        val r = GoalCalculator.calculate(p)
        assertEquals(2240, r.calories)
        assertEquals(144, r.targets.proteinG)
    }

    // ---------- Allergens ----------
    private val all = DietPrefs(
        allergies = setOf("peanut", "tree_nut", "milk", "egg", "wheat", "shellfish", "fish", "soy", "sesame"),
        diets = setOf("vegetarian", "no_pork")
    )
    private fun ids(name: String, p: DietPrefs = all) = Allergens.check(name, p).map { it.id }.toSet()

    @Test fun allergensMatch() {
        assertEquals(setOf("peanut", "wheat"), ids("Peanut butter toast"))
        assertTrue("milk" in ids("Quesadilla de pollo"))
        assertTrue("egg" in ids("Huevos rancheros"))
        assertTrue("shellfish" in ids("Camarones a la diabla"))
        assertTrue("no_pork" in ids("Tacos al pastor"))
        assertTrue("fish" in ids("Chicken Caesar wrap"))
    }

    @Test fun allergensNoFalseAlarms() {
        assertEquals(emptySet<String>(), ids("Eggplant, grilled"))
        assertEquals(emptySet<String>(), ids("Tuna (fruta)"))
        assertEquals(emptySet<String>(), ids("Butternut squash"))
        assertTrue("milk" !in ids("Almond milk, unsweetened"))
        assertTrue("no_pork" !in ids("Turkey bacon"))
        assertEquals(emptySet<String>(), ids("Coconut water"))
    }

    @Test fun otherAllergyWords() {
        val p = DietPrefs(other = "kiwi, mustard")
        assertEquals(1, Allergens.check("Kiwi smoothie", p).size)
        assertEquals(0, Allergens.check("Banana", p).size)
    }

    @Test fun offTagsCount() {
        val p = DietPrefs(allergies = setOf("milk"), diets = setOf("vegan"))
        val flags = Allergens.checkItem("Chocolate bar", listOf("en:milk"), p).map { it.id }.toSet()
        assertEquals(setOf("milk", "vegan"), flags)
    }

    // ---------- Mood ----------
    @Test fun triggers() {
        val bad = listOf(
            MoodEntry(epochDay = 1, mood = 1, foods = "Milk\nBread"),
            MoodEntry(epochDay = 2, mood = 2, foods = "milk"),
            MoodEntry(epochDay = 3, mood = 1, foods = "Leche")
        )
        val s = suspectCounts(bad)
        assertEquals(1, s.size)              // only Milk shows up 2+ times (Leche is a different word)
        assertEquals(2, s[0].times)
    }
}
