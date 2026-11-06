// ==============================================================================
// PLAIN-ENGLISH GUIDE
//
// WHAT THIS FILE IS: the big animated calorie ring on the Today screen.
//  - It fills clockwise as you eat (eaten / goal).
//  - Past 100%, a second arc in the "over" color starts on top.
//  - The number in the middle counts smoothly to the new value.
// ==============================================================================
package com.gymnam.app.ui.today

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.gymnam.app.R
import kotlin.math.abs

/**
 * Big animated ring. The ring fills as you eat; if you go past your goal a second
 * arc in the "over" colour wraps on top so it's obvious but not alarming.
 */
@Composable
fun CalorieRing(
    eaten: Int,
    goal: Int,
    overColor: Color,
    size: Dp = 230.dp,
    stroke: Dp = 22.dp
) {
    val safeGoal = goal.coerceAtLeast(1)
    // fraction = how full: 1,120 eaten / 2,240 goal = 0.5 = half the ring.
    // safeGoal is at least 1 so we never divide by zero.
    val fraction = eaten.toFloat() / safeGoal
    // animate... = slide smoothly to the new value over 900 ms instead of jumping.
    // coerceIn(0f, 1f) keeps it between empty and full.
    val main by animateFloatAsState(
        targetValue = fraction.coerceIn(0f, 1f),
        animationSpec = tween(900, easing = FastOutSlowInEasing), label = "ring"
    )
    // The "over" arc: e.g. 2,464 / 2,240 = 1.1 -> 0.1 = 10% of a lap, starts 0.3 s later.
    val over by animateFloatAsState(
        targetValue = (fraction - 1f).coerceIn(0f, 1f),
        animationSpec = tween(900, delayMillis = 300, easing = FastOutSlowInEasing), label = "over"
    )
    val left = safeGoal - eaten
    val shown by animateIntAsState(left, tween(700), label = "kcal")

    val track = MaterialTheme.colorScheme.primaryContainer
    val accent = MaterialTheme.colorScheme.primary

    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        // Canvas = free drawing. drawArc(color, start angle, sweep angle, ...):
        // -90 degrees = start at 12 o'clock; 360 x fraction = how far around to draw.
        // Three arcs: the faint track, the accent fill, and the "over" arc.
        Canvas(Modifier.fillMaxSize()) {
            val w = stroke.toPx()
            val inset = w / 2
            val arcSize = androidx.compose.ui.geometry.Size(this.size.width - w, this.size.height - w)
            val topLeft = androidx.compose.ui.geometry.Offset(inset, inset)
            drawArc(track, -90f, 360f, false, topLeft, arcSize, style = Stroke(w, cap = StrokeCap.Round))
            if (main > 0f) drawArc(accent, -90f, 360f * main, false, topLeft, arcSize, style = Stroke(w, cap = StrokeCap.Round))
            if (over > 0f) drawArc(overColor, -90f, 360f * over, false, topLeft, arcSize, style = Stroke(w, cap = StrokeCap.Round))
        }
        // The number and "kcal left" / "kcal over" in the middle.
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                abs(shown).toString(),
                style = MaterialTheme.typography.displayMedium,
                color = if (left < 0) overColor else MaterialTheme.colorScheme.onSurface
            )
            Text(
                stringResource(if (left < 0) R.string.kcal_over else R.string.kcal_left),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
