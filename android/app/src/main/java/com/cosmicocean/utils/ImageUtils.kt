package com.cosmicocean.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import kotlin.math.min

object ImageUtils {
    private const val TAG = "ImageUtils"
    private const val MAX_WIDTH = 1920
    private const val MAX_HEIGHT = 1080
    private const val COMPRESSION_QUALITY = 80

    /**
     * Resizes and compresses an image from a Uri into a temporary JPEG file.
     * Useful for reducing upload size and preventing OOMs.
     */
    fun compressAndResizeImage(context: Context, uri: Uri): File? {
        var inputStream: InputStream? = null
        try {
            val contentResolver = context.contentResolver
            
            // 1. Get original dimensions without loading bitmap into memory
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            inputStream = contentResolver.openInputStream(uri)
            BitmapFactory.decodeStream(inputStream, null, options)
            inputStream?.close()

            val originalWidth = options.outWidth
            val originalHeight = options.outHeight
            
            if (originalWidth <= 0 || originalHeight <= 0) {
                Log.e(TAG, "Invalid image dimensions: ${originalWidth}x${originalHeight}")
                return null
            }

            Log.d(TAG, "Original dimensions: ${originalWidth}x${originalHeight}")

            // 2. Calculate scale factor to target 1080p roughly
            val scale = min(originalWidth.toFloat() / MAX_WIDTH, originalHeight.toFloat() / MAX_HEIGHT)
            val inSampleSize = if (scale > 1) {
                // Find nearest power of 2 for inSampleSize (required by Android)
                var sample = 1
                while (sample * 2 <= scale) {
                    sample *= 2
                }
                sample
            } else {
                1
            }

            // 3. Decode with inSampleSize to save memory
            options.inJustDecodeBounds = false
            options.inSampleSize = inSampleSize
            inputStream = contentResolver.openInputStream(uri)
            val sampledBitmap = BitmapFactory.decodeStream(inputStream, null, options)
            inputStream?.close()

            if (sampledBitmap == null) {
                Log.e(TAG, "Failed to decode sampled bitmap")
                return null
            }

            // 4. Final precise resize to fit within MAX_WIDTH x MAX_HEIGHT if still too large
            val finalBitmap = if (sampledBitmap.width > MAX_WIDTH || sampledBitmap.height > MAX_HEIGHT) {
                val ratio = min(MAX_WIDTH.toFloat() / sampledBitmap.width, MAX_HEIGHT.toFloat() / sampledBitmap.height)
                val targetWidth = (sampledBitmap.width * ratio).toInt()
                val targetHeight = (sampledBitmap.height * ratio).toInt()
                
                Log.d(TAG, "Precise resizing from ${sampledBitmap.width}x${sampledBitmap.height} to ${targetWidth}x${targetHeight}")
                val resized = Bitmap.createScaledBitmap(sampledBitmap, targetWidth, targetHeight, true)
                if (resized != sampledBitmap) {
                    sampledBitmap.recycle()
                }
                resized
            } else {
                sampledBitmap
            }

            // 5. Compress and save to temporary file
            val tempFile = File.createTempFile("wallpaper_upload", ".jpg", context.cacheDir)
            FileOutputStream(tempFile).use { out ->
                finalBitmap.compress(Bitmap.CompressFormat.JPEG, COMPRESSION_QUALITY, out)
            }
            
            Log.d(TAG, "Compressed file size: ${tempFile.length() / 1024} KB")
            
            finalBitmap.recycle()
            return tempFile

        } catch (e: Exception) {
            Log.e(TAG, "Error compressing image", e)
            return null
        } finally {
            inputStream?.close()
        }
    }
}
