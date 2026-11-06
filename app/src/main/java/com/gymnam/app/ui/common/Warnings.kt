// ==============================================================================
// PLAIN-ENGLISH GUIDE
// WHAT THIS FILE IS: how allergy/diet warnings LOOK (the logic is in data/Allergens.kt).
//  - FlagChips:     small red tags next to a food name: "⚠️ Peanuts", "⚠️ Not vegan".
//  - WarningBanner: a bigger red box (on the amount sheet and recipe pages) that lists
//                   the matches AND reminds you to check the label.
// Nothing shows if you haven't set any allergies or diets on the Me page.
// ==============================================================================
package com.gymnam.app.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.gymnam.app.R
import com.gymnam.app.data.Flag
import com.gymnam.app.ui.theme.isAppDark

/** Soft red that stays readable in light and dark mode. */
@Composable
private fun warnColors(): Pair<Color, Color> =
    if (isAppDark()) Color(0xFFF08C8C).copy(alpha = 0.22f) to Color(0xFFF5B0B0)
    else Color(0xFFE07070).copy(alpha = 0.16f) to Color(0xFFB54848)

/** Text for one warning: the allergen/diet name, or the word you typed under "Other". */
@Composable
fun flagText(f: Flag): String = f.custom?.replaceFirstChar { it.uppercase() } ?: stringResource(f.chipRes)

@Composable
fun FlagChips(flags: List<Flag>, modifier: Modifier = Modifier) {
    if (flags.isEmpty()) return
    val (bg, fg) = warnColors()
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        flags.take(2).forEach { f ->
            Surface(shape = CircleShape, color = bg) {
                Text("⚠️ " + flagText(f), style = MaterialTheme.typography.labelSmall, color = fg,
                    fontWeight = FontWeight.SemiBold, maxLines = 1,
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp))
            }
        }
        if (flags.size > 2) Text("+${flags.size - 2}", style = MaterialTheme.typography.labelSmall, color = fg)
    }
}

@Composable
fun WarningBanner(flags: List<Flag>, modifier: Modifier = Modifier) {
    if (flags.isEmpty()) return
    val (bg, fg) = warnColors()
    Surface(modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium, color = bg) {
        Column(Modifier.padding(12.dp)) {
            Text("⚠️ " + stringResource(R.string.warn_may_contain, flags.map { flagText(it) }.joinToString(", ")),
                style = MaterialTheme.typography.bodyMedium, color = fg, fontWeight = FontWeight.SemiBold)
            Text(stringResource(R.string.warn_check_label), style = MaterialTheme.typography.bodySmall, color = fg)
        }
    }
}
