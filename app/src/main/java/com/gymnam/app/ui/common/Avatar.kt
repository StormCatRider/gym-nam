// ==============================================================================
// PLAIN-ENGLISH GUIDE
// WHAT THIS FILE IS: the round picture in the top bar and on the Me page.
//  - If you added a profile photo, it shows your photo.
//  - Otherwise it shows the app's logo (the leaf in a ring) on its mint circle.
// photoVersion changes whenever you set or remove a photo (see SettingsStore),
// which tells this circle to load the picture again.
// ==============================================================================
package com.gymnam.app.ui.common

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.gymnam.app.R
import com.gymnam.app.data.ProfilePhoto

/** The mint behind the logo: the same color as the app icon's background (colors.xml icon_bg). */
private val LogoMint = Color(0xFFB8E0D2)

@Composable
fun Avatar(photoVersion: Long, size: Dp, onClick: (() -> Unit)? = null, ring: Boolean = true) {
    val context = LocalContext.current
    // produceState loads the photo in the background and redraws once it's ready.
    val photo: ImageBitmap? by produceState<ImageBitmap?>(null, photoVersion) {
        value = if (photoVersion == 0L) null else ProfilePhoto.load(context)?.asImageBitmap()
    }
    val label = stringResource(R.string.me_title)
    Box(
        Modifier.size(size).clip(CircleShape).background(LogoMint)
            .then(if (ring) Modifier.border(2.dp, Color.White.copy(alpha = 0.75f), CircleShape) else Modifier)
            .then(if (onClick != null) Modifier.clickable(onClickLabel = label, onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center
    ) {
        val p = photo
        if (p != null) {
            Image(p, label, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            // The launcher drawing has empty margins (it's made for icon masks), so it's
            // zoomed in 1.6x to fill the circle nicely.
            Image(
                painterResource(R.drawable.ic_launcher_fg), label,
                Modifier.fillMaxSize().graphicsLayer { scaleX = 1.6f; scaleY = 1.6f }
            )
        }
    }
}
