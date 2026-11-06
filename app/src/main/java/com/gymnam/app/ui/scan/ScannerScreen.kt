// ==============================================================================
// PLAIN-ENGLISH GUIDE
//
// WHAT THIS FILE IS: the barcode camera.
//  1. Asks for camera permission (only the first time).
//  2. Shows the camera with an aiming frame and a flashlight button.
//  3. Reads each camera frame ON THE PHONE with ZXing (free open-source barcode
//     reader; no Google services, no internet). Nothing is saved or uploaded.
//  4. The moment a code is read: short vibration, and the digits go back to the
//     screen that opened the scanner.
// Supported barcodes: EAN-13, UPC-A, EAN-8, UPC-E (the ones on grocery products).
// ==============================================================================
package com.gymnam.app.ui.scan

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.NotFoundException
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.gymnam.app.R
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

// If permission is missing, ask once (launcher). If you said no, show a message
// with a button to try again.
/**
 * Camera barcode scanner. Frames are decoded on-device with ZXing (open source, no Google
 * services, no network). Nothing is saved; only the decoded digits leave this screen.
 */
@Composable
fun ScannerScreen(onResult: (String) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    LaunchedEffect(Unit) { if (!granted) launcher.launch(Manifest.permission.CAMERA) }

    Box(Modifier.fillMaxSize()) {
        if (granted) CameraWithScanner(onResult)
        else Column(
            Modifier.fillMaxSize().padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("📷", style = MaterialTheme.typography.displayMedium)
            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.camera_needed), textAlign = TextAlign.Center)
            Spacer(Modifier.height(16.dp))
            Button(onClick = { launcher.launch(Manifest.permission.CAMERA) }, shape = CircleShape) {
                Text(stringResource(R.string.allow))
            }
        }
        FilledTonalIconButton(onClick = onBack, modifier = Modifier.statusBarsPadding().padding(12.dp)) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
        }
    }
}

// CameraX = Android's camera library. executor = a background worker so reading
// frames doesn't slow the screen. done = makes sure only the FIRST code found is
// sent (the camera keeps producing frames very fast).
@Composable
private fun CameraWithScanner(onResult: (String) -> Unit) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val haptics = LocalHapticFeedback.current
    val executor = remember { Executors.newSingleThreadExecutor() }
    val done = remember { AtomicBoolean(false) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var torch by remember { mutableStateOf(false) }
    var provider by remember { mutableStateOf<ProcessCameraProvider?>(null) }

    val analyzer = remember {
        BarcodeAnalyzer { code ->
            if (done.compareAndSet(false, true)) {
                ContextCompat.getMainExecutor(context).execute {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onResult(code)
                }
            }
        }
    }

    // Start the camera when the screen opens; release it when the screen closes.
    DisposableEffect(Unit) {
        onDispose {
            provider?.unbindAll()
            executor.shutdown()
        }
    }

    Box(Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                PreviewView(ctx).also { view ->
                    val future = ProcessCameraProvider.getInstance(ctx)
                    future.addListener({
                        val p = future.get()
                        provider = p
                        val preview = Preview.Builder().build().also { it.setSurfaceProvider(view.surfaceProvider) }
                        val analysis = ImageAnalysis.Builder()
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build().also { it.setAnalyzer(executor, analyzer) }
                        p.unbindAll()
                        camera = runCatching {
                            p.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
                        }.getOrNull()
                    }, ContextCompat.getMainExecutor(ctx))
                }
            }
        )
        // Aiming frame
        Surface(
            modifier = Modifier.align(Alignment.Center).fillMaxWidth(0.8f).height(150.dp),
            shape = RoundedCornerShape(24.dp),
            color = Color.Transparent,
            border = BorderStroke(3.dp, Color.White.copy(alpha = 0.9f))
        ) {}
        Row(
            Modifier.align(Alignment.BottomCenter).padding(bottom = 64.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(shape = CircleShape, color = Color.Black.copy(alpha = 0.55f)) {
                Text(stringResource(R.string.point_camera), color = Color.White,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp))
            }
            Spacer(Modifier.padding(6.dp))
            FilledTonalIconButton(onClick = {
                torch = !torch
                camera?.cameraControl?.enableTorch(torch)
            }) {
                Icon(if (torch) Icons.Filled.FlashOn else Icons.Filled.FlashOff, stringResource(R.string.torch))
            }
        }
    }
}

// Receives every camera frame. It uses only the black-and-white part of the image
// (enough for barcodes, and faster). TRY_HARDER = spend a bit more effort per frame.
/** Reads EAN-13 / UPC-A / EAN-8 / UPC-E from the camera's grayscale (Y) plane. */
private class BarcodeAnalyzer(private val onCode: (String) -> Unit) : ImageAnalysis.Analyzer {
    private val reader = MultiFormatReader().apply {
        setHints(mapOf(
            DecodeHintType.POSSIBLE_FORMATS to listOf(
                BarcodeFormat.EAN_13, BarcodeFormat.UPC_A, BarcodeFormat.EAN_8, BarcodeFormat.UPC_E
            ),
            DecodeHintType.TRY_HARDER to true
        ))
    }

    override fun analyze(image: ImageProxy) {
        try {
            val plane = image.planes[0]
            val w = image.width
            val h = image.height
            val rowStride = plane.rowStride
            val buf = plane.buffer
            val data = ByteArray(w * h)
            // Copy row by row: the buffer may have padding at the end of each row.
            for (row in 0 until h) {
                buf.position(row * rowStride)
                buf.get(data, row * w, w)
            }
            // Try as-is (barcode across the sensor), then rotated 90° (barcode across the screen in portrait).
            val code = decode(data, w, h) ?: decode(rotate90(data, w, h), h, w)
            if (code != null) onCode(code)
        } catch (_: Exception) {
        } finally {
            image.close()
        }
    }

    // Asks ZXing to find a barcode in one frame; null if none.
    private fun decode(data: ByteArray, w: Int, h: Int): String? = try {
        val src = PlanarYUVLuminanceSource(data, w, h, 0, 0, w, h, false)
        reader.decodeWithState(BinaryBitmap(HybridBinarizer(src))).text
    } catch (_: NotFoundException) {
        null
    } catch (_: Exception) {
        null
    } finally {
        reader.reset()
    }

    // Turns the frame 90°, so barcodes held sideways are also read.
    private fun rotate90(src: ByteArray, w: Int, h: Int): ByteArray {
        val out = ByteArray(src.size)
        for (y in 0 until h) for (x in 0 until w) out[x * h + (h - 1 - y)] = src[y * w + x]
        return out
    }
}
