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
    // FIX: Updated for PORTRAIT screens (most phones are 1080x2400+)
    // Previous values (1920x1080) caused portrait images to be heavily downscaled
    private const val MAX_WIDTH = 1440   // Supports up to 1440p width
    private const val MAX_HEIGHT = 3200  // Supports tall screens like 1080x3200
    private const val COMPRESSION_QUALITY = 85 // Increased quality for better wallpapers

    /**
     * Resizes and compresses an image from a Uri into a temporary JPEG file.
     * Useful for reducing upload size and preventing OOMs.
     *
     * IMPORTANT: Dimensions are optimized for portrait phone screens.
     * A 1080x1920 image will be kept at full size (not downscaled).
     * Only images larger than 1440x3200 will be downscaled.
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
                val success = finalBitmap.compress(Bitmap.CompressFormat.JPEG, COMPRESSION_QUALITY, out)
                if (!success) {
                    Log.e(TAG, "CRITICAL: Bitmap.compress() returned false!")
                    return null
                }
            }

            val fileSizeKB = tempFile.length() / 1024
            Log.d(TAG, "Compressed file: ${tempFile.absolutePath}")
            Log.d(TAG, "Compressed file size: ${fileSizeKB} KB")
            Log.d(TAG, "Final bitmap dimensions: ${finalBitmap.width}x${finalBitmap.height}")

            // Validate file was actually written
            if (tempFile.length() < 1000) { // Less than 1KB is suspicious
                Log.e(TAG, "WARNING: Compressed file is suspiciously small: ${tempFile.length()} bytes")
            }

            finalBitmap.recycle()
            return tempFile

        } catch (e: Exception) {
            Log.e(TAG, "Error compressing image", e)
            return null
        } finally {
            inputStream?.close()
        }
    }

    /**
     * Validates that a bitmap has actual content (not all black or transparent).
     * Samples a few pixels to check for valid image data.
     *
     * @return true if the bitmap appears to have valid content, false if it seems empty/black
     */
    fun validateBitmapContent(bitmap: Bitmap): Boolean {
        if (bitmap.width <= 0 || bitmap.height <= 0) {
            Log.e(TAG, "Invalid bitmap dimensions: ${bitmap.width}x${bitmap.height}")
            return false
        }

        // Sample pixels from different regions
        val samplePoints = listOf(
            Pair(0, 0),                                      // Top-left
            Pair(bitmap.width - 1, 0),                       // Top-right
            Pair(0, bitmap.height - 1),                      // Bottom-left
            Pair(bitmap.width - 1, bitmap.height - 1),       // Bottom-right
            Pair(bitmap.width / 2, bitmap.height / 2),       // Center
            Pair(bitmap.width / 4, bitmap.height / 4),       // Upper-left quadrant
            Pair(bitmap.width * 3 / 4, bitmap.height * 3 / 4) // Lower-right quadrant
        )

        var nonBlackPixels = 0
        var nonTransparentPixels = 0

        for ((x, y) in samplePoints) {
            val pixel = bitmap.getPixel(x, y)
            val alpha = android.graphics.Color.alpha(pixel)
            val red = android.graphics.Color.red(pixel)
            val green = android.graphics.Color.green(pixel)
            val blue = android.graphics.Color.blue(pixel)

            // Check if pixel is not transparent
            if (alpha > 0) {
                nonTransparentPixels++
            }

            // Check if pixel is not black (allowing some dark colors)
            if (red > 10 || green > 10 || blue > 10) {
                nonBlackPixels++
            }
        }

        val hasContent = nonTransparentPixels >= 3 && nonBlackPixels >= 2

        Log.d(TAG, "Bitmap validation: ${bitmap.width}x${bitmap.height}, " +
                "nonTransparent=$nonTransparentPixels/7, nonBlack=$nonBlackPixels/7, valid=$hasContent")

        if (!hasContent) {
            Log.w(TAG, "WARNING: Bitmap appears to be mostly black or transparent!")
        }

        return hasContent
    }
}
