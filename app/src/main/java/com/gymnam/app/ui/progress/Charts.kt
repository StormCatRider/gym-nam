// ==============================================================================
// PLAIN-ENGLISH GUIDE
//
// WHAT THIS FILE IS: the two hand-drawn charts on the Progress tab (no chart
// library needed):
//  - WeightChart:   faint dots = each weigh-in; solid line = 7-day trend.
//  - CaloriesChart: one bar per day + a dashed line at your goal; bars over the
//                   goal use the "over" color.
// Both "draw in" with a short animation when the data changes.
// ==============================================================================
package com.gymnam.app.ui.progress

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.unit.dp
import com.gymnam.app.data.fmtNum
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.math.ceil
import kotlin.math.floor

private val shortDate = DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT)
// Short date for the axis in your phone's format, e.g. "11/5/26".
private fun label(day: Long) = LocalDate.ofEpochDay(day).format(shortDate)

/** Grows from 0 to 1 when the data changes, for a gentle draw-in animation. */
@Composable
private fun rememberReveal(key: Any): Float {
    val a = remember(key) { Animatable(0f) }
    LaunchedEffect(key) { a.animateTo(1f, tween(800)) }
    return a.value
}

// Y axis: from (lowest - 1 lb) to (highest + 1 lb), rounded to whole pounds,
// so small changes are visible without looking dramatic.
// x(d) turns a day into a horizontal position; y(v) turns a weight into a height
// (on screens, y = 0 is the TOP, which is why it's height minus the value).
/**
 * Weight: faint dots = daily weigh-ins, solid line = 7-day trend.
 * Y axis is padded and rounded to whole pounds so small changes are visible but not exaggerated.
 */
@Composable
fun WeightChart(weights: List<Point>, trend: List<Point>, from: Long, to: Long, modifier: Modifier = Modifier) {
    val accent = MaterialTheme.colorScheme.primary
    val grid = MaterialTheme.colorScheme.outlineVariant
    val all = weights.map { it.value } + trend.map { it.value }
    val lo = floor((all.minOrNull() ?: 0.0) - 1)
    val hi = ceil((all.maxOrNull() ?: 1.0) + 1)
    val reveal = rememberReveal(weights)
    Column(modifier) {
        Row {
            Column(Modifier.width(40.dp).height(160.dp)) {
                Text(fmtNum(hi), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Box(Modifier.weight(1f))
                Text(fmtNum(lo), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Canvas(Modifier.weight(1f).height(160.dp)) {
                val span = (to - from).coerceAtLeast(1).toFloat()
                fun x(d: Long) = (d - from) / span * size.width
                fun y(v: Double) = size.height - ((v - lo) / (hi - lo)).toFloat() * size.height
                for (i in 0..3) {
                    val gy = size.height * i / 3f
                    drawLine(grid, Offset(0f, gy), Offset(size.width, gy), strokeWidth = 1f)
                }
                weights.forEach { drawCircle(accent.copy(alpha = 0.35f * reveal), radius = 4.dp.toPx(), center = Offset(x(it.day), y(it.value))) }
                if (trend.size >= 2) {
                    val path = Path()
                    trend.forEachIndexed { i, p ->
                        val px = x(p.day); val py = y(p.value)
                        if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
                    }
                    clipRect(right = size.width * reveal) {
                        drawPath(path, accent, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                    }
                }
                trend.lastOrNull()?.let { drawCircle(accent, radius = 5.dp.toPx(), center = Offset(x(it.day), y(it.value))) }
            }
        }
        DateAxis(from, to)
    }
}

// Over 60 days (3 months / year), days are grouped by week and each bar is the
// week's average of the days you logged. The top of the chart is the highest bar
// or 120% of your goal, whichever is bigger.
/** Calories per day as bars, with a dashed goal line. Long ranges are grouped into weekly averages. */
@Composable
fun CaloriesChart(byDay: List<Point>, goal: Int, overColor: Color, modifier: Modifier = Modifier) {
    val accent = MaterialTheme.colorScheme.primary
    val goalColor = MaterialTheme.colorScheme.onSurfaceVariant
    // > 60 days: average each week (only days you logged), so bars stay readable.
    val bars = if (byDay.size > 60) byDay.chunked(7).map { w ->
        val logged = w.filter { it.value > 0 }
        Point(w.first().day, if (logged.isEmpty()) 0.0 else logged.sumOf { it.value } / logged.size)
    } else byDay
    val max = maxOf(bars.maxOfOrNull { it.value } ?: 0.0, goal * 1.2)
    val reveal = rememberReveal(byDay)
    Column(modifier) {
        Row {
            Column(Modifier.width(40.dp).height(160.dp)) {
                Text(fmtNum(Math.round(max).toDouble()), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Box(Modifier.weight(1f))
                Text("0", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Canvas(Modifier.weight(1f).height(160.dp)) {
                if (bars.isEmpty()) return@Canvas
                val slot = size.width / bars.size
                val w = (slot * 0.65f).coerceAtLeast(2f)
                bars.forEachIndexed { i, p ->
                    if (p.value <= 0) return@forEachIndexed
                    val h = (p.value / max).toFloat() * size.height * reveal
                    drawRoundRect(
                        if (p.value > goal * 1.05) overColor else accent,
                        topLeft = Offset(i * slot + (slot - w) / 2, size.height - h),
                        size = Size(w, h), cornerRadius = CornerRadius(w / 2, w / 2)
                    )
                }
                val gy = size.height - (goal / max).toFloat() * size.height
                drawLine(goalColor, Offset(0f, gy), Offset(size.width, gy), strokeWidth = 2f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 10f)))
            }
        }
        DateAxis(byDay.firstOrNull()?.day ?: 0, byDay.lastOrNull()?.day ?: 0)
    }
}

// First and last date under the chart.
@Composable
private fun DateAxis(from: Long, to: Long) {
    Row(Modifier.fillMaxWidth().padding(start = 40.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label(from), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Box(Modifier.weight(1f))
        Text(label(to), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
