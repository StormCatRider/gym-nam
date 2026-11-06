// ==============================================================================
// PLAIN-ENGLISH GUIDE
//
// WHAT THIS FILE IS: the app's single window ("Activity"). When you tap the icon,
// Android opens this. Every screen you see is drawn inside it.
//
// WHAT IT DOES, IN ORDER:
//  1. Waits for your settings to load.
//  2. If the PIN/fingerprint lock is ON and you haven't unlocked -> lock screen.
//  3. If you've never finished the first setup -> the setup (onboarding) screens.
//  4. Otherwise -> the normal app with the bottom bar (MainScaffold).
//  It also re-locks the app if it was in the background for more than 1 minute.
// ==============================================================================
package com.gymnam.app

import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.gymnam.app.data.AppSettings
import com.gymnam.app.data.LocalMetric
import com.gymnam.app.ui.nav.MainScaffold
import com.gymnam.app.ui.setup.SetupScreen
import com.gymnam.app.ui.setup.SetupViewModel
import com.gymnam.app.ui.theme.GymNamTheme

// "class X : FragmentActivity()" means: MainActivity is a kind of FragmentActivity
// (it inherits all of Android's window behavior and adds ours on top).
/** FragmentActivity (not ComponentActivity) because the fingerprint/PIN prompt needs it. */
class MainActivity : FragmentActivity() {

    // "var" = a value that can change (unlike "val"). "mutableStateOf" makes it
    // special: when it changes, the screen redraws automatically.
    // backgroundAt = the moment the app went to the background (0 = never).
    /** Unlocked for this visit. Re-locks after 1 minute in the background. */
    private var unlocked by mutableStateOf(false)
    private var backgroundAt = 0L

    // Runs when the window is created (app opened, or phone rotated).
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Lets the app draw behind the status bar and navigation bar for a modern look.
        enableEdgeToEdge()
        unlocked = savedInstanceState?.getBoolean("unlocked") ?: false   // rotating doesn't re-lock
        val app = application as GymNamApp
        // Everything inside setContent { ... } describes WHAT to show. This style is
        // called Jetpack Compose: you describe the screen, Compose draws it and redraws
        // it whenever the data it uses changes.
        setContent {
            // Wait for settings so nothing flashes before the lock or the setup screen.
            val settings: AppSettings? by app.settings.settings.collectAsStateWithLifecycle(initialValue = null)
            // themeMode = Light / Dark / follow phone (Settings). LocalMetric tells every screen
            // whether to show kg/cm/ml or lb/ft/fl oz (see data/Units.kt).
            settings?.let { s -> GymNamTheme(paletteId = s.paletteId, themeMode = s.themeMode) {
              CompositionLocalProvider(LocalMetric provides s.metric) {
                // "when" is like a list of if/else checks; the first one that is true wins.
                when {
                    s.lockEnabled && !unlocked -> LockScreen(::authenticate)
                    !s.onboarded -> {
                        val vm: SetupViewModel = viewModel(key = "first-setup", factory = viewModelFactory {
                            initializer { SetupViewModel(app.settings, app.db.trackingDao(), withTour = true) }
                        })
                        SetupScreen(vm, onDone = {}, onCancel = null)   // saving flips "onboarded" → app opens
                    }
                    else -> MainScaffold()
                }
              }
            } }
        }
    }

    // Android may destroy and rebuild the window (for example when you rotate the
    // phone). This saves "unlocked" so a rotation doesn't ask for your fingerprint again.
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean("unlocked", unlocked)
    }

    // onStop = the app just went to the background. Remember the time.
    override fun onStop() {
        super.onStop()
        backgroundAt = SystemClock.elapsedRealtime()
    }

    // onStart = the app came back. If it was away more than 60 seconds, lock again.
    override fun onStart() {
        super.onStart()
        if (backgroundAt > 0 && SystemClock.elapsedRealtime() - backgroundAt > 60_000) unlocked = false
    }

    // Shows Android's own fingerprint / face / PIN prompt. The app never sees or
    // stores your fingerprint or PIN; Android only tells us "success" or not.
    /** Fingerprint/face, or the phone's PIN/pattern/password as a fallback. Nothing is stored by the app. */
    fun authenticate() {
        val prompt = BiometricPrompt(this, ContextCompat.getMainExecutor(this), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) { unlocked = true }
        })
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(getString(R.string.unlock_title))
            .setSubtitle(getString(R.string.app_name))
            .apply {
                // Android 11+ (code name "R") and Android 10 ask for this in different ways.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL)
                } else {
                    @Suppress("DEPRECATION") setDeviceCredentialAllowed(true)   // Android 10
                }
            }
            .build()
        prompt.authenticate(info)
    }

    // "companion object" holds helpers that belong to the class itself, so other
    // code can call MainActivity.canLock(...) without having the window open.
    companion object {
        /** True when the phone has a screen lock or biometrics, i.e. the app lock can work. */
        fun canLock(activity: FragmentActivity): Boolean {
            val bm = BiometricManager.from(activity)
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
                bm.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL) ==
                    BiometricManager.BIOMETRIC_SUCCESS
            else (activity.getSystemService(android.content.Context.KEYGUARD_SERVICE) as android.app.KeyguardManager).isDeviceSecure
        }
    }
}

// @Composable marks a function that DRAWS something. This one draws the lock
// screen: a padlock, the app name and an Unlock button.
// LaunchedEffect(Unit) runs once when the screen appears: it opens the
// fingerprint prompt right away so you don't even have to tap.
@Composable
private fun LockScreen(onUnlock: () -> Unit) {
    LaunchedEffect(Unit) { onUnlock() }
    // Box = a container that stacks things. Column = puts things one under another.
    // Spacer = empty space. "dp" = a size unit that looks the same on every phone.
    // Surface sets both the background and a readable text color (also in night mode).
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text("🔒", style = MaterialTheme.typography.displayMedium)
            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(20.dp))
            Button(onClick = onUnlock, shape = CircleShape) { Text(stringResource(R.string.unlock)) }
        }
    }
    }
}
