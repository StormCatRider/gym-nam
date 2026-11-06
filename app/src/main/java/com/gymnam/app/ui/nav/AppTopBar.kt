// ==============================================================================
// PLAIN-ENGLISH GUIDE
// WHAT THIS FILE IS: the colored bar at the top of the 4 main tabs.
//  - Its color is your palette's accent color (mint, peach, ...), in light and dark mode.
//  - Left: your photo (or the app logo). Tapping it opens the Me page.
//  - Next to it: "Gym & Ñam" + "Hi, <your name>" on Today, or the tab's name elsewhere.
//  - Right (Today only): your current logging streak 🔥.
// The bar also paints behind the phone's status bar (clock, battery) so the color
// reaches the very top; statusBarsPadding() keeps our content below the clock.
// ==============================================================================
package com.gymnam.app.ui.nav

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gymnam.app.R
import com.gymnam.app.ui.common.Avatar

@Composable
fun AppTopBar(title: String, subtitle: String?, streak: Int, photoVersion: Long, onAvatar: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary) {
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(start = 12.dp, end = 14.dp, top = 8.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Avatar(photoVersion, 42.dp, onClick = onAvatar)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                subtitle?.let {
                    Text(it, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.75f))
                }
            }
            if (streak > 0) Surface(shape = CircleShape, color = Color.White.copy(alpha = 0.55f), contentColor = MaterialTheme.colorScheme.onPrimary) {
                Text("🔥 $streak", style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
            }
        }
    }
}

/**
 * The same colored bar for pages opened FROM the top bar (Me, Calendar):
 * a back arrow and a title. It also paints behind the status bar.
 */
@Composable
fun InnerTopBar(title: String, onBack: () -> Unit, actions: @Composable () -> Unit = {}) {
    Surface(color = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary) {
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            actions()
        }
    }
}
