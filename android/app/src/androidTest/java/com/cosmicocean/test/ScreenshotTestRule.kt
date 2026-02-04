package com.cosmicocean.test

import android.graphics.Bitmap
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

class ScreenshotTestRule(
    private val baseDirPath: String = "/data/local/tmp/AndroidTestScreenshots"
) : TestWatcher() {

    override fun finished(description: Description) {
        captureScreenshot(description)
    }

    private fun captureScreenshot(description: Description) {
        val dir = File(baseDirPath, sanitize(description.className))
        val uiAutomation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val targetContext = InstrumentationRegistry.getInstrumentation().targetContext

        if (!dir.exists()) {
            uiAutomation.executeShellCommand("mkdir -p ${dir.absolutePath}").close()
        }

        val fileName = buildFileName(description)
        val file = File(dir, fileName)

        val externalDir = targetContext.getExternalFilesDir("screenshots")
            ?: throw AssertionError("External files dir unavailable for screenshots")
        if (!externalDir.exists() && !externalDir.mkdirs()) {
            throw AssertionError("Failed to create external screenshot directory: ${externalDir.absolutePath}")
        }
        val externalFile = File(externalDir, fileName)

        val bitmap = uiAutomation.takeScreenshot()
            ?: throw AssertionError("UIAutomation returned null screenshot")
        FileOutputStream(externalFile).use { out ->
            if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) {
                throw AssertionError("Failed to compress screenshot for ${description.className}.${description.methodName}")
            }
        }
        bitmap.recycle()

        val cpOutput = readShellOutput(
            uiAutomation.executeShellCommand("cp ${externalFile.absolutePath} ${file.absolutePath}")
        )
        if (cpOutput.isNotBlank()) {
            Log.w("ScreenshotTestRule", "Shell copy output: $cpOutput")
        }

        val lsOutput = readShellOutput(uiAutomation.executeShellCommand("ls -l ${file.absolutePath}"))
        if (lsOutput.isBlank() || lsOutput.contains("No such file")) {
            throw AssertionError("Screenshot file missing or inaccessible: ${file.absolutePath}")
        }

        Log.d("ScreenshotTestRule", "Captured screenshot: ${file.absolutePath}")
    }

    private fun buildFileName(description: Description): String {
        val method = sanitize(description.methodName ?: "unknown_test")
        val timestamp = System.currentTimeMillis()
        return "${method}_$timestamp.png"
    }

    private fun sanitize(value: String?): String {
        return (value ?: "unknown")
            .replace(Regex("[^a-zA-Z0-9._-]"), "_")
            .take(120)
    }

    private fun readShellOutput(pfd: android.os.ParcelFileDescriptor): String {
        pfd.use { descriptor ->
            val input: InputStream = android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor)
            return input.bufferedReader().use { it.readText() }
        }
    }
}
