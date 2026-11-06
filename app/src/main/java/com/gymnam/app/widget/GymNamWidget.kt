// ==============================================================================
// PLAIN-ENGLISH GUIDE
//
// WHAT THIS FILE IS: the home-screen widget: calories left, a progress bar,
// protein/carbs/fat, water, and (only if you allowed it in Settings) your weight.
// Tapping it opens the app. Widgets use "Glance", a limited version of Compose
// made for home screens. It's refreshed by GymNamApp.kt whenever you log something.
// ==============================================================================
package com.gymnam.app.widget

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.gymnam.app.GymNamApp
import com.gymnam.app.MainActivity
import com.gymnam.app.R
import com.gymnam.app.data.fmtNum
import com.gymnam.app.ui.theme.paletteById
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.roundToInt

// Today's numbers the widget shows.
/** Snapshot of today for the widget. */
private data class WidgetData(
    val left: Int, val goal: Int, val eaten: Int,
    val protein: Int, val carbs: Int, val fat: Int,
    val waterFlOz: Int, val waterGoal: Int,
    val weightLb: Double?, val accent: Color
)

// Calories left = (daily goal + exercise burned) - calories eaten.
// Example: goal 2,240 + 300 exercise - 1,800 eaten = 740 left.
private suspend fun load(context: Context): WidgetData {
    val app = context.applicationContext as GymNamApp
    val today = LocalDate.now().toEpochDay()
    val s = app.settings.settings.first()
    val food = app.db.foodDao().entriesForDayNow(today)
    val t = app.db.trackingDao()
    val burned = t.burnedNow(today)
    val goal = s.targets.calories + burned
    val eaten = food.sumOf { it.calories }
    return WidgetData(
        left = goal - eaten, goal = goal, eaten = eaten,
        protein = food.sumOf { it.proteinG }.roundToInt(), carbs = food.sumOf { it.carbsG }.roundToInt(),
        fat = food.sumOf { it.fatG }.roundToInt(),
        waterFlOz = t.waterNow(today).roundToInt(), waterGoal = s.waterGoalFlOz,
        // Weight only if you turned it on in Settings (off by default, for privacy).
        weightLb = if (s.widgetShowWeight) t.latestWeightNow()?.lb else null,
        accent = paletteById(s.paletteId).accentLight
    )
}

// Draws the widget. If you're over your goal the number turns red and the label
// says "over" instead of "left". The bar = eaten / goal, capped at full.
class GymNamWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val d = load(context)
        val ink = ColorProvider(Color(0xFF2B2A33))
        val soft = ColorProvider(Color(0xFF77757F))
        provideContent {
            Column(
                GlanceModifier.fillMaxSize().background(Color(0xFFFFFBF8)).cornerRadius(24.dp).padding(14.dp)
                    .clickable(actionStartActivity<MainActivity>()),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(context.getString(R.string.app_name), style = TextStyle(color = ColorProvider(d.accent), fontSize = 12.sp, fontWeight = FontWeight.Medium))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        abs(d.left).toString(),
                        style = TextStyle(color = if (d.left < 0) ColorProvider(Color(0xFFE07070)) else ink, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                    )
                    Spacer(GlanceModifier.width(6.dp))
                    Text(
                        context.getString(if (d.left < 0) R.string.kcal_over else R.string.kcal_left),
                        style = TextStyle(color = soft, fontSize = 13.sp), modifier = GlanceModifier.padding(bottom = 5.dp)
                    )
                }
                LinearProgressIndicator(
                    progress = if (d.goal > 0) (d.eaten.toFloat() / d.goal).coerceIn(0f, 1f) else 0f,
                    modifier = GlanceModifier.fillMaxWidth().height(8.dp),
                    color = ColorProvider(if (d.left < 0) Color(0xFFE07070) else d.accent),
                    backgroundColor = ColorProvider(d.accent.copy(alpha = 0.25f))
                )
                Spacer(GlanceModifier.height(8.dp))
                Text(
                    "P ${d.protein} · C ${d.carbs} · F ${d.fat} g   💧 ${d.waterFlOz}/${d.waterGoal} fl oz",
                    style = TextStyle(color = soft, fontSize = 12.sp)
                )
                d.weightLb?.let {
                    Text("⚖️ ${fmtNum(it)} lb", style = TextStyle(color = soft, fontSize = 12.sp))
                }
            }
        }
    }
}

// Android's entry point for the widget (registered in AndroidManifest.xml).
class GymNamWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = GymNamWidget()
}
