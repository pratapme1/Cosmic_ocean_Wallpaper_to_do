package com.cosmicocean.e2e

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.cosmicocean.data.CosmicDatabase
import com.cosmicocean.data.StarEntity
import com.cosmicocean.service.RealTimeWallpaperService
import com.cosmicocean.utils.WallpaperPreferencesManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import java.util.zip.CRC32

@RunWith(AndroidJUnit4::class)
class LockScreenWallpaperE2ETest {

    @Test
    fun lockScreenWallpaperUpdatesOnCrud() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val prefs = WallpaperPreferencesManager(context)
        prefs.detectDeviceResolution()
        prefs.setWallpaperEnabled(true)
        prefs.setWallpaperMode(WallpaperPreferencesManager.WALLPAPER_MODE_GENERATED)
        grantMediaPermissions(context)

        val db = CosmicDatabase.getDatabase(context)
        withContext(Dispatchers.IO) {
            db.starDao().deleteAllStars()
        }

        val starId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val baseStar = StarEntity(
            localId = starId,
            serverId = null,
            title = "Lock Add ${UUID.randomUUID()}",
            urgency = 2,
            dueDate = now + 2 * 60 * 60 * 1000,
            x = 240f,
            y = 420f,
            createdAt = now,
            isSubtask = false,
            isRecurring = false,
            echoInterval = null,
            isCompleted = false,
            completedAt = null,
            isArchived = false,
            archivedAt = null,
            contextTag = null
        )

        withContext(Dispatchers.IO) {
            db.starDao().insertStar(baseStar)
        }
        forceWallpaperUpdate(context)
        SystemClock.sleep(2000)
        val addHash = captureLockWallpaper(context, "lock_add")

        val editedStar = baseStar.copy(
            title = "Lock Edit ${UUID.randomUUID()}",
            urgency = 3,
            updatedAt = System.currentTimeMillis()
        )
        withContext(Dispatchers.IO) {
            db.starDao().insertStar(editedStar)
        }
        forceWallpaperUpdate(context)
        val editHash = waitForLockWallpaperChange(context, addHash, "lock_edit")
        check(addHash != editHash) { "Expected lock screen wallpaper to change after edit" }

        val completedStar = editedStar.copy(
            isCompleted = true,
            completedAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        withContext(Dispatchers.IO) {
            db.starDao().insertStar(completedStar)
        }
        forceWallpaperUpdate(context)
        val completeHash = waitForLockWallpaperChange(context, editHash, "lock_complete")
        check(editHash != completeHash) { "Expected lock screen wallpaper to change after complete" }
    }

    private fun forceWallpaperUpdate(context: Context) {
        RealTimeWallpaperService.updateNow(context)
    }

    private fun waitForLockWallpaperChange(context: Context, previousHash: Long, tag: String): Long {
        val timeoutMs = 8_000L
        val start = SystemClock.elapsedRealtime()
        var hash = previousHash
        while (SystemClock.elapsedRealtime() - start < timeoutMs) {
            hash = lockWallpaperHash(context)
            if (hash != previousHash) {
                captureLockWallpaper(context, tag)
                return hash
            }
            SystemClock.sleep(500)
        }
        captureLockWallpaper(context, tag)
        return hash
    }

    private fun captureLockWallpaper(context: Context, tag: String): Long {
        val bitmap = lockWallpaperBitmap(context)
        val crc = CRC32()
        val buffer = ByteArray(bitmap.byteCount)
        bitmap.copyPixelsToBuffer(java.nio.ByteBuffer.wrap(buffer))
        crc.update(buffer)

        val uiAutomation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val targetContext = InstrumentationRegistry.getInstrumentation().targetContext
        val dir = File("/data/local/tmp/AndroidTestScreenshots/lockscreen")
        if (!dir.exists()) {
            uiAutomation.executeShellCommand("mkdir -p ${dir.absolutePath}").close()
        }

        val fileName = "${tag}_${System.currentTimeMillis()}.png"
        val externalDir = targetContext.getExternalFilesDir("screenshots")
            ?: throw AssertionError("External files dir unavailable for screenshots")
        if (!externalDir.exists() && !externalDir.mkdirs()) {
            throw AssertionError("Failed to create external screenshot directory: ${externalDir.absolutePath}")
        }
        val externalFile = File(externalDir, fileName)

        FileOutputStream(externalFile).use { out ->
            if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) {
                throw AssertionError("Failed to compress lockscreen wallpaper for $tag")
            }
        }
        bitmap.recycle()
        uiAutomation.executeShellCommand("cp ${externalFile.absolutePath} ${dir.absolutePath}/${fileName}").close()
        return crc.value
    }

    private fun lockWallpaperHash(context: Context): Long {
        val bitmap = lockWallpaperBitmap(context)
        val crc = CRC32()
        val buffer = ByteArray(bitmap.byteCount)
        bitmap.copyPixelsToBuffer(java.nio.ByteBuffer.wrap(buffer))
        crc.update(buffer)
        bitmap.recycle()
        return crc.value
    }

    private fun lockWallpaperBitmap(context: Context): Bitmap {
        val manager = android.app.WallpaperManager.getInstance(context)
        val lockDrawable = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            manager.getDrawable(android.app.WallpaperManager.FLAG_LOCK)
        } else {
            null
        }
        val drawable = lockDrawable ?: manager.drawable
            ?: throw AssertionError("Wallpaper drawable unavailable for lockscreen verification")
        return drawableToBitmap(drawable)
    }

    private fun drawableToBitmap(drawable: Drawable): Bitmap {
        val width = if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else 1080
        val height = if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else 2400
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap
    }

    private fun grantMediaPermissions(context: Context) {
        val pkg = context.packageName
        val uiAutomation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val permissions = listOf(
            "android.permission.READ_MEDIA_IMAGES",
            "android.permission.READ_EXTERNAL_STORAGE"
        )
        permissions.forEach { permission ->
            uiAutomation.executeShellCommand("pm grant $pkg $permission").close()
        }
    }
}
