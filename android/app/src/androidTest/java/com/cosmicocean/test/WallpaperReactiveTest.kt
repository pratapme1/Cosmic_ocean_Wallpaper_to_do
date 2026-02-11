package com.cosmicocean.test

import android.graphics.Bitmap
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.UiDevice
import com.cosmicocean.data.CosmicDatabase
import com.cosmicocean.data.StarEntity
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class WallpaperReactiveTest {

    @Test
    fun testWallpaperReactivity() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val device = UiDevice.getInstance(instrumentation)
        val context = instrumentation.targetContext
        val db = CosmicDatabase.getDatabase(context)
        val dao = db.starDao()

        // Go to Home Screen to see Wallpaper
        device.pressHome()
        Thread.sleep(2000)

        // 1. Create Task
        val id = UUID.randomUUID().toString()
        val task = StarEntity(
            localId = id,
            title = "VISUAL_PROOF_TASK",
            urgency = 3, // Critical Red
            dueDate = System.currentTimeMillis() + 3600000,
            x = 500f, y = 500f,
            createdAt = System.currentTimeMillis(),
            isSubtask = false,
            parentId = null,
            isRecurring = false,
            echoInterval = null,
            isCompleted = false,
            completedAt = null,
            isArchived = false,
            archivedAt = null
        )
        dao.insertStar(task)
        
        // Wait for wallpaper update
        Thread.sleep(3000)
        takeScreenshot(device, "1_task_added.png")
        
        // 2. Complete Task (Should disappear)
        val completedTask = task.copy(isCompleted = true, completedAt = System.currentTimeMillis())
        dao.insertStar(completedTask)
        
        // Wait for wallpaper update
        Thread.sleep(3000)
        takeScreenshot(device, "2_task_completed.png")
        
        // 3. Delete Task (Cleanup)
        dao.deleteStar(completedTask)
        
        // Wait for wallpaper update
        Thread.sleep(3000)
        takeScreenshot(device, "3_task_deleted.png")
    }

    private fun takeScreenshot(device: UiDevice, name: String) {
        val path = File("/sdcard/Pictures/$name")
        device.takeScreenshot(path)
    }
}
