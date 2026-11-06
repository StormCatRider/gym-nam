// ==============================================================================
// PLAIN-ENGLISH GUIDE
// WHAT THIS FILE IS: saves, loads and deletes your profile photo.
//
// PRIVACY:
//  - You pick the photo with Android's own Photo Picker, which shares ONLY the one
//    photo you choose. The app never asks for "access to all your photos".
//  - A small copy (max 512 x 512 pixels, cropped to a square) is saved in the app's
//    PRIVATE folder (filesDir). Other apps can't see it, it's not in your gallery,
//    it is never uploaded, and it's not included in backups. Uninstalling deletes it.
//  - The original photo in your gallery is never changed.
// ==============================================================================
package com.gymnam.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max
import kotlin.math.min

object ProfilePhoto {
    private const val MAX_PX = 512

    private fun file(context: Context) = File(context.filesDir, "profile_photo.jpg")

    /**
     * Copies the chosen photo into the app's private folder, shrunk and cropped to a square.
     * ImageDecoder (Android 9+) also fixes sideways phone photos (it reads the rotation tag).
     * Returns true if it worked.
     */
    suspend fun save(context: Context, uri: Uri): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            val bmp = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                // Shrink while decoding: a 4000 px photo becomes ~500-1000 px, saving memory.
                val w = info.size.width; val h = info.size.height
                val sample = max(1, min(w, h) / MAX_PX)
                decoder.setTargetSize(max(1, w / sample), max(1, h / sample))
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE   // needed to crop/compress it
            }
            // Center square crop, then scale to at most 512 x 512.
            val side = min(bmp.width, bmp.height)
            val square = Bitmap.createBitmap(bmp, (bmp.width - side) / 2, (bmp.height - side) / 2, side, side)
            val outSide = min(side, MAX_PX)
            val scaled = if (side > outSide) Bitmap.createScaledBitmap(square, outSide, outSide, true) else square
            file(context).outputStream().use { scaled.compress(Bitmap.CompressFormat.JPEG, 88, it) }
            true
        }.getOrDefault(false)
    }

    /** The saved photo, or null if there is none (or it can't be read). Runs off the main thread. */
    suspend fun load(context: Context): Bitmap? = withContext(Dispatchers.IO) {
        val f = file(context)
        if (!f.exists()) null else runCatching { BitmapFactory.decodeFile(f.absolutePath) }.getOrNull()
    }

    fun delete(context: Context) { file(context).delete() }
}
