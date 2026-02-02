package com.cosmicocean

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import com.cosmicocean.auth.TokenManager
import com.cosmicocean.data.CosmicDatabase
import com.cosmicocean.data.TaskRepository
import com.cosmicocean.effects.ConstellationSystem
import com.cosmicocean.effects.OrbitalSystem
import com.cosmicocean.model.Star
import com.cosmicocean.network.NetworkModule
import com.cosmicocean.physics.VerletEngine
import com.cosmicocean.physics.ZoneManager
import com.cosmicocean.systems.CommandHistory
import com.cosmicocean.ui.components.*
import com.cosmicocean.utils.WallpaperPreferencesManager
import com.cosmicocean.viewmodel.MainViewModel
import com.cosmicocean.viewmodel.MainViewModelFactory
import com.cosmicocean.viewmodel.EnvironmentSettingsViewModel
import com.cosmicocean.service.RealTimeWallpaperService
import com.cosmicocean.sync.SyncManager
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File

class MainActivity : ComponentActivity() {

    private lateinit var viewModel: MainViewModel
    private lateinit var tokenManager: TokenManager
    private lateinit var wallpaperPreferences: WallpaperPreferencesManager
    private lateinit var database: CosmicDatabase

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        tokenManager = TokenManager(this)
        wallpaperPreferences = WallpaperPreferencesManager(this)

        // Detect device resolution on first run or if not set
        wallpaperPreferences.detectDeviceResolution()

        database = CosmicDatabase.getDatabase(this)
        
        // CRITICAL FIX: Initialize SyncManager for local-first architecture
        val apiService = NetworkModule.getApi(this)
        val syncManager = SyncManager.getInstance(
            database.syncQueueDao(),
            database.starDao(),
            apiService,
            applicationContext
        )
        
        // CRITICAL FIX: Repository now uses SyncManager for all sync operations
        val repository = TaskRepository(
            starDao = database.starDao(),
            apiService = apiService,
            context = applicationContext,
            syncManager = syncManager
        )
        
        val engine = VerletEngine()
        val constellationSystem = ConstellationSystem(engine)
        val orbitalSystem = OrbitalSystem(engine)
        val commandHistory = CommandHistory()
        val zoneManager = ZoneManager(1080f, 1920f)
        val audioEngine = com.cosmicocean.audio.AudioEngine(this)

        val factory = MainViewModelFactory(repository, engine, constellationSystem, orbitalSystem, commandHistory, syncManager)

        // Set up command history callbacks for toast notifications
        commandHistory.onUndoRedo = { action, description ->
            Toast.makeText(this, "$action: $description", Toast.LENGTH_SHORT).show()
        }

        // Schedule periodic wallpaper updates to keep urgency states current
        schedulePeriodicWallpaperUpdates()

        setContent {
            var isAuthenticated by remember { mutableStateOf(tokenManager.isLoggedIn()) }
            var authError by remember { mutableStateOf<String?>(null) }

            if (!isAuthenticated) {
                // Show authentication screen
                AuthScreen(
                    onLogin = { email, password -> handleLogin(email, password,
                        onSuccess = { isAuthenticated = true; authError = null },
                        onError = { authError = it }
                    ) },
                    onRegister = { email, password -> handleRegister(email, password,
                        onSuccess = { isAuthenticated = true; authError = null },
                        onError = { authError = it }
                    ) },
                    onSkipToGuest = {
                        isAuthenticated = true
                        authError = null
                    },
                    onForgotPassword = { email ->
                        handleForgotPassword(email)
                    },
                    errorMessage = authError
                )
                return@setContent
            }

            // Report app open after authentication
            reportAppOpen()
            viewModel = viewModel(factory = factory)
            val stars = viewModel.stars
            val completedStars = viewModel.completedStars
            
            MaterialTheme {
                var showQuickAdd by remember { mutableStateOf(false) }
                var showSearch by remember { mutableStateOf(false) }
                var showSettings by remember { mutableStateOf(false) }
                var showTrophyGallery by remember { mutableStateOf(false) }
                var showPrivacySettings by remember { mutableStateOf(false) }
                var showEnvironmentSettings by remember { mutableStateOf(false) }
                var editingStar by remember { mutableStateOf<Star?>(null) }
                var lastTapOffset by remember { mutableStateOf<androidx.compose.ui.geometry.Offset?>(null) }
                var isInteracting by remember { mutableStateOf(false) }
                var currentTheme by remember { mutableStateOf(wallpaperPreferences.getTheme()) }
                var showWallpaperConsent by remember { mutableStateOf(!wallpaperPreferences.hasSetWallpaperPreference()) }
                var isUploadingWallpaper by remember { mutableStateOf(false) }
                val context = LocalContext.current
                val contentResolver = context.contentResolver
                val coroutineScope = rememberCoroutineScope()

                // File picker for wallpaper upload
                val wallpaperPickerLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.GetContent()
                ) { uri ->
                    uri?.let { selectedUri ->
                        coroutineScope.launch {
                            isUploadingWallpaper = true
                            Toast.makeText(context, "Setting custom wallpaper...", Toast.LENGTH_SHORT).show()
                            try {
                                // Compress and Resize image before saving
                                val tempFile = com.cosmicocean.utils.ImageUtils.compressAndResizeImage(context, selectedUri)

                                if (tempFile == null || !tempFile.exists()) {
                                    Toast.makeText(context, "Failed to prepare image", Toast.LENGTH_SHORT).show()
                                    return@launch
                                }

                                // LOCAL-FIRST FIX: Save to permanent local storage
                                val permanentFile = File(context.filesDir, "custom_wallpaper.jpg")
                                android.util.Log.d("MainActivity", "DEBUG: Saving from temp ${tempFile.absolutePath} (size=${tempFile.length()}) to ${permanentFile.absolutePath}")

                                tempFile.copyTo(permanentFile, overwrite = true)
                                tempFile.delete() // Clean up temp file

                                android.util.Log.d("MainActivity", "DEBUG: Permanent file saved: exists=${permanentFile.exists()}, size=${permanentFile.length()}, canRead=${permanentFile.canRead()}")
                                android.util.Log.d("MainActivity", "Custom wallpaper saved to: ${permanentFile.absolutePath}")

                                // LOCAL-FIRST FIX: Set wallpaper mode and path in preferences
                                wallpaperPreferences.setWallpaperMode(WallpaperPreferencesManager.WALLPAPER_MODE_CUSTOM)
                                wallpaperPreferences.setCustomWallpaperPath(permanentFile.absolutePath)

                                android.util.Log.d("MainActivity", "Wallpaper mode set to CUSTOM, path: ${permanentFile.absolutePath}")

                                // Trigger immediate local wallpaper update
                                triggerImmediateUpdate()
                                Toast.makeText(context, "Custom wallpaper applied!", Toast.LENGTH_SHORT).show()

                                // OPTIONAL: Background upload to backend for sync/backup
                                try {
                                    val requestFile = permanentFile.asRequestBody("image/jpeg".toMediaTypeOrNull())
                                    val body = MultipartBody.Part.createFormData("image", permanentFile.name, requestFile)
                                    NetworkModule.getApi(context).uploadWallpaper(body)
                                    android.util.Log.d("MainActivity", "Custom wallpaper synced to backend")
                                } catch (e: Exception) {
                                    // Backend sync failure is non-critical for local-first
                                    android.util.Log.w("MainActivity", "Backend sync failed (offline mode): ${e.message}")
                                }
                            } catch (e: Exception) {
                                android.util.Log.e("MainActivity", "Wallpaper setup error", e)
                                Toast.makeText(context, "Setup error: ${e.message}", Toast.LENGTH_SHORT).show()
                            } finally {
                                isUploadingWallpaper = false
                            }
                        }
                    }
                }

                if (showWallpaperConsent) {
                    AlertDialog(
                        onDismissRequest = { /* Don't dismiss without choice */ },
                        title = { Text("Cosmic Wallpaper Setup", color = Color.White) },
                        text = {
                            Text(
                                "To fully experience the Cosmic Ocean, can we update your system wallpaper every minute? This allows your lock screen to reflect your current cosmic tasks and urgency levels.",
                                color = Color.LightGray
                            )
                        },
                        confirmButton = {
                            Button(
                                onClick = {
                                    wallpaperPreferences.setWallpaperEnabled(true)
                                    showWallpaperConsent = false
                                    schedulePeriodicWallpaperUpdates()
                                    Toast.makeText(context, "Cosmic sync enabled!", Toast.LENGTH_SHORT).show()
                                }
                            ) {
                                Text("Enable Sync")
                            }
                        },
                        dismissButton = {
                            TextButton(
                                onClick = {
                                    wallpaperPreferences.setWallpaperEnabled(false)
                                    showWallpaperConsent = false
                                    Toast.makeText(context, "Manual mode only.", Toast.LENGTH_SHORT).show()
                                }
                            ) {
                                Text("Not Now", color = Color.Gray)
                            }
                        },
                        containerColor = Color(0xFF14141E)
                    )
                }

                LaunchedEffect(isInteracting) {
                    if (isInteracting) {
                        kotlinx.coroutines.delay(2000)
                        isInteracting = false
                    }
                }

                Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF000814)) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        
                        CosmicCanvas(
                            engine = viewModel.engine,
                            zoneManager = zoneManager,
                            constellationSystem = viewModel.constellationSystem,
                            orbitalSystem = viewModel.orbitalSystem,
                            stars = stars,
                            onStarTap = { star -> editingStar = star },
                            onEmptyDoubleTap = { offset ->
                                lastTapOffset = offset
                                showQuickAdd = true
                            },
                            onInteraction = { isInteracting = true },
                            onStarFinalized = { star, action -> handleStarAction(star, action) },
                            onStarSnooze = { star, duration -> snoozeStar(star, duration) },
                            onStarDragEnd = { star -> saveStarPosition(star) },  // EPIC 9: Save position after drag
                            audioEngine = audioEngine
                        )

                        AmbientStatusHUD(stars = stars, isInteracting = isInteracting)

                        // Performance Monitor (Debug)
                        Box(modifier = Modifier.align(Alignment.TopStart)) {
                            com.cosmicocean.debug.PerformanceMonitor(
                                enabled = BuildConfig.DEBUG
                            )
                        }

                        // Achievement Wall
                        Box(modifier = Modifier.align(Alignment.BottomCenter)) {
                            AchievementWall(
                                completedStars = completedStars,
                                onClick = { showTrophyGallery = true }
                            )
                        }

                        // HUD Controls
                        Row(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(16.dp)
                        ) {
                            // Refresh Wallpaper Button
                            IconButton(
                                onClick = {
                                    android.widget.Toast.makeText(this@MainActivity, "Refreshing wallpaper...", android.widget.Toast.LENGTH_SHORT).show()
                                    triggerImmediateUpdate()
                                },
                                modifier = Modifier.background(Color.Black.copy(0.4f), shape = MaterialTheme.shapes.small)
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = "Refresh Wallpaper", tint = Color.White)
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            IconButton(
                                onClick = { showSearch = true },
                                modifier = Modifier.background(Color.Black.copy(0.4f), shape = MaterialTheme.shapes.small)
                            ) {
                                Icon(Icons.Default.Search, contentDescription = "Search", tint = Color.White)
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            IconButton(
                                onClick = { showSettings = true },
                                modifier = Modifier.background(Color.Black.copy(0.4f), shape = MaterialTheme.shapes.small)
                            ) {
                                Icon(Icons.Default.Settings, contentDescription = "Settings", tint = Color.White)
                            }
                        }

                        // NEW: Glassmorphic Wallpaper Upload Button (Bottom Start)
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(24.dp)
                                .padding(bottom = 80.dp) // Align with FAB height
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(24.dp))
                                    .background(
                                        brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                                            colors = listOf(
                                                Color(0xFF2A2A3E).copy(alpha = 0.8f),
                                                Color(0xFF1A1A2E).copy(alpha = 0.9f)
                                            )
                                        )
                                    )
                                    .border(
                                        width = 1.dp,
                                        color = if (isUploadingWallpaper) Color(0xFFE040FB) else Color.White.copy(alpha = 0.3f),
                                        shape = RoundedCornerShape(24.dp)
                                    )
                                    .clickable(enabled = !isUploadingWallpaper) {
                                        wallpaperPickerLauncher.launch("image/*")
                                    }
                                    .padding(horizontal = 16.dp, vertical = 12.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    if (isUploadingWallpaper) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(20.dp),
                                            color = Color(0xFFE040FB),
                                            strokeWidth = 2.dp
                                        )
                                        Text(
                                            "Uploading...",
                                            color = Color.White.copy(alpha = 0.9f),
                                            style = MaterialTheme.typography.labelLarge
                                        )
                                    } else {
                                        Icon(
                                            painter = painterResource(id = R.drawable.ic_upload),
                                            contentDescription = "Custom Wallpaper",
                                            tint = Color(0xFFE040FB), // Cosmic Purple Accent
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Text(
                                            "Customize",
                                            color = Color.White.copy(alpha = 0.9f),
                                            style = MaterialTheme.typography.labelLarge
                                        )
                                    }
                                }
                            }
                        }

                        FloatingActionButton(
                            onClick = { showQuickAdd = true },
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(24.dp).padding(bottom = 80.dp),
                            containerColor = Color(0xFF00E5FF)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Add Task")
                        }

                        if (showQuickAdd) {
                            QuickAddOverlay(
                                onDismiss = { showQuickAdd = false },
                                onSave = { title -> createNewStar(title, lastTapOffset) }
                            )
                        }

                        if (editingStar != null) {
                            EditStarOverlay(
                                star = editingStar!!,
                                onDismiss = { editingStar = null },
                                onSave = { title, urgency, dueInMinutes ->
                                    updateStar(editingStar!!, title, urgency, dueInMinutes)
                                    editingStar = null
                                }
                            )
                        }

                        if (showSearch) {
                            SearchOverlay(
                                stars = stars,
                                onDismiss = { showSearch = false },
                                onStarSelected = { star -> showSearch = false }
                            )
                        }

                        if (showSettings) {
                            SettingsOverlay(
                                onDismiss = { showSettings = false },
                                onDoneForToday = { showSettings = false; markDoneForToday() },
                                onClearAll = { showSettings = false; clearAllTasks() },
                                onLogout = {
                                    showSettings = false
                                    handleLogout()
                                    isAuthenticated = false
                                },
                                userEmail = tokenManager.getEmail(),
                                currentTheme = currentTheme,
                                onThemeChange = { newTheme ->
                                    currentTheme = newTheme
                                    changeWallpaperTheme(newTheme)
                                },
                                onOpenPrivacySettings = {
                                    showSettings = false
                                    showPrivacySettings = true
                                },
                                onOpenEnvironmentSettings = {
                                    showSettings = false
                                    showEnvironmentSettings = true
                                }
                            )
                        }

                        // Epic 10: Privacy Settings Screen
                        if (showPrivacySettings) {
                            PrivacySettingsWrapper(
                                apiService = NetworkModule.getApi(this@MainActivity),
                                onNavigateBack = { showPrivacySettings = false }
                            )
                        }

                        // Epic 10 Phase 3: Environment Settings Screen
                        if (showEnvironmentSettings) {
                            EnvironmentSettingsWrapper(
                                viewModel = viewModel(
                                    factory = object : androidx.lifecycle.ViewModelProvider.Factory {
                                        @Suppress("UNCHECKED_CAST")
                                        override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                                            return EnvironmentSettingsViewModel(NetworkModule.getApi(applicationContext), wallpaperPreferences) as T
                                        }
                                    }
                                ),
                                onNavigateBack = { showEnvironmentSettings = false },
                                onWallpaperEnabledChanged = { enabled: Boolean ->
                                    // Re-start or stop service when toggled
                                    if (enabled) {
                                        com.cosmicocean.service.RealTimeWallpaperService.start(this@MainActivity)
                                    } else {
                                        com.cosmicocean.service.RealTimeWallpaperService.stop(this@MainActivity)
                                    }
                                }
                            )
                        }

                        if (showTrophyGallery) {
                            TrophyGallery(
                                completedStars = completedStars,
                                onDismiss = { showTrophyGallery = false }
                            )
                        }
                    }
                }
            }
        }
    }

    private fun handleStarAction(star: Star, type: String) {
        lifecycleScope.launch {
            when (type) {
                "complete" -> viewModel.completeStar(star)
                "archive" -> viewModel.archiveStar(star)
                "delete" -> {
                    // CRITICAL FIX: Use viewModel.deleteStar() to remove from Room DB + backend
                    viewModel.deleteStar(star)
                    Toast.makeText(this@MainActivity, "Star Exploded!", Toast.LENGTH_SHORT).show()
                }
            }
            triggerImmediateUpdate()
        }
    }

    private fun snoozeStar(star: Star, durationMinutes: Int) {
        // Apply snooze locally
        star.snooze(durationMinutes)

        // Sync to backend API
        lifecycleScope.launch {
            try {
                NetworkModule.getApi(this@MainActivity).snoozeTask(
                    star.id,
                    mapOf("duration_minutes" to durationMinutes)
                )
                val durationText = when {
                    durationMinutes < 60 -> "${durationMinutes}m"
                    durationMinutes < 1440 -> "${durationMinutes / 60}h"
                    else -> "${durationMinutes / 1440}d"
                }
                Toast.makeText(this@MainActivity, "Snoozed for $durationText", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "Snooze failed (offline)", Toast.LENGTH_SHORT).show()
            }
        }

        triggerImmediateUpdate()
    }

    private fun saveStarPosition(star: Star) {
        // EPIC 9 FIX: Save star position after dragging
        lifecycleScope.launch {
            viewModel.updateStar(star)
        }
    }

    private fun updateStar(star: Star, title: String, urgency: Int, dueInMinutes: Float) {
        star.title = title
        star.urgency = urgency
        star.dueIn = dueInMinutes

        // Update due date timestamp
        star.dueDate = System.currentTimeMillis() + (dueInMinutes * 60 * 1000).toLong()

        lifecycleScope.launch {
            try {
                NetworkModule.getApi(this@MainActivity).updateTask(
                    star.id,
                    mapOf(
                        "title" to title,
                        "priority" to urgency.toString(),
                        "due_date" to star.dueDate.toString()
                    )
                )
                triggerImmediateUpdate()
            } catch (e: Exception) {}
        }
    }

    private fun clearAllTasks() {
        lifecycleScope.launch {
            try {
                // LOCAL-FIRST FIX: Use ViewModel which handles local DB + sync queue
                viewModel.clearAllTasks()

                // Also clear constellation and orbit data
                database.constellationDao().deleteAllLinks()
                database.orbitDao().deleteAllOrbits()

                triggerImmediateUpdate()
                Toast.makeText(this@MainActivity, "Ocean Cleared", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                android.util.Log.e("MainActivity", "Failed to clear data: ${e.message}")
                Toast.makeText(this@MainActivity, "Failed to clear data", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun createNewStar(title: String, offset: androidx.compose.ui.geometry.Offset?) {
        val random = java.util.Random()

        // Get screen dimensions for better placement
        val displayMetrics = resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels.toFloat()
        val screenHeight = displayMetrics.heightPixels.toFloat()

        // EPIC 9: Random placement across full screen (15% padding to avoid edges)
        // Stars stay where created - no zone forces, color shows urgency instead
        val horizontalPadding = screenWidth * 0.15f
        val verticalPadding = screenHeight * 0.1f

        // If user tapped, use that position; otherwise, random placement
        val x = offset?.x ?: (horizontalPadding + random.nextFloat() * (screenWidth - 2 * horizontalPadding))
        val y = offset?.y ?: (verticalPadding + random.nextFloat() * (screenHeight - 2 * verticalPadding))

        // Create star with default P2 (will be updated from backend after NLP parsing)
        // TaskRepository.addStar() handles both local DB insert AND backend sync
        val star = Star(x, y, title, 2, null)

        lifecycleScope.launch {
            viewModel.addStar(star)
            // No need for local delay here anymore, as RealTimeWallpaperService.ACTION_FORCE_UPDATE 
            // now includes a 500ms delay internally for consistency.
            triggerImmediateUpdate()
        }
    }

    private fun reportAppOpen() {
        lifecycleScope.launch {
            try {
                NetworkModule.getApi(this@MainActivity).reportAppOpen()
            } catch (e: Exception) {}
        }
    }

    private fun markDoneForToday() {
        lifecycleScope.launch {
            try {
                val response = NetworkModule.getApi(this@MainActivity).markDoneForToday()
                if (response.isSuccessful) {
                    triggerImmediateUpdate()
                }
            } catch (e: Exception) {}
        }
    }

    private fun schedulePeriodicWallpaperUpdates() {
        // Only proceed if wallpaper integration is explicitly enabled by the user
        if (!wallpaperPreferences.isWallpaperEnabled()) {
            android.util.Log.d("MainActivity", "Wallpaper updates skipped: Consent not granted.")
            return
        }

        // Start real-time wallpaper service (1-minute updates)
        if (tokenManager.isLoggedIn()) {
            RealTimeWallpaperService.start(this)
        }

        // Keep WorkManager as fallback (every 15 minutes) in case service is killed
        val periodicWorkRequest = androidx.work.PeriodicWorkRequestBuilder<com.cosmicocean.worker.WallpaperUpdateWorker>(
            15, java.util.concurrent.TimeUnit.MINUTES
        )
            .setConstraints(
                androidx.work.Constraints.Builder()
                    .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED)
                    // FIX: Removed setRequiresBatteryNotLow(true) - was blocking updates when battery <20%
                    .build()
            )
            .build()

        // FIX: Changed from KEEP to UPDATE - ensures fresh scheduling even if worker was in failed state
        // UPDATE preserves enqueue time and doesn't cancel running workers (better than REPLACE)
        androidx.work.WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "wallpaper_periodic_update",
            androidx.work.ExistingPeriodicWorkPolicy.UPDATE,
            periodicWorkRequest
        )
    }

    private fun triggerImmediateUpdate() {
        // 1. Fast path: Foreground Service (serialized with 500ms delay)
        RealTimeWallpaperService.updateNow(this)

        // 2. Backup path: WorkManager
        // FIX: Added setExpedited for truly immediate execution
        val workRequest = androidx.work.OneTimeWorkRequestBuilder<com.cosmicocean.worker.WallpaperUpdateWorker>()
            .setExpedited(androidx.work.OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .setConstraints(androidx.work.Constraints.Builder().setRequiredNetworkType(androidx.work.NetworkType.CONNECTED).build())
            .build()
        androidx.work.WorkManager.getInstance(this).enqueue(workRequest)
    }

    private fun handleLogin(email: String, password: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        lifecycleScope.launch {
            try {
                val response = NetworkModule.getApi(this@MainActivity).login(
                    mapOf(
                        "email" to email,
                        "password" to password
                    )
                )

                if (response.isSuccessful && response.body() != null) {
                    val authResponse = response.body()!!
                    tokenManager.saveTokens(
                        authResponse.accessToken,
                        authResponse.refreshToken,
                        authResponse.user.id,
                        authResponse.user.email
                    )
                    // Start real-time wallpaper service
                    RealTimeWallpaperService.start(this@MainActivity)
                    Toast.makeText(this@MainActivity, "Welcome back, ${authResponse.user.email}!", Toast.LENGTH_SHORT).show()
                    onSuccess()
                } else {
                    val errorMsg = when (response.code()) {
                        401 -> "Invalid email or password"
                        404 -> "User not found"
                        else -> "Login failed: ${response.code()}"
                    }
                    onError(errorMsg)
                }
            } catch (e: Exception) {
                onError("Connection error: ${e.message}")
            }
        }
    }

    private fun handleRegister(email: String, password: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        if (password.length < 8) {
            onError("Password must be at least 8 characters")
            return
        }

        lifecycleScope.launch {
            try {
                // Get device timezone
                val deviceTimezone = java.util.TimeZone.getDefault().id

                val response = NetworkModule.getApi(this@MainActivity).register(
                    mapOf(
                        "email" to email,
                        "password" to password,
                        "timezone" to deviceTimezone
                    )
                )

                if (response.isSuccessful && response.body() != null) {
                    val authResponse = response.body()!!
                    tokenManager.saveTokens(
                        authResponse.accessToken,
                        authResponse.refreshToken,
                        authResponse.user.id,
                        authResponse.user.email
                    )
                    // Start real-time wallpaper service
                    RealTimeWallpaperService.start(this@MainActivity)
                    Toast.makeText(this@MainActivity, "Account created! Welcome ${authResponse.user.email}!", Toast.LENGTH_SHORT).show()
                    onSuccess()
                } else {
                    val errorMsg = when (response.code()) {
                        409 -> "Email already registered"
                        400 -> "Invalid email or password format"
                        else -> "Registration failed: ${response.code()}"
                    }
                    onError(errorMsg)
                }
            } catch (e: Exception) {
                onError("Connection error: ${e.message}")
            }
        }
    }

    private fun handleLogout() {
        // Stop real-time wallpaper service
        RealTimeWallpaperService.stop(this)

        // CRITICAL FIX: Clear local database to prevent user data leakage
        // This ensures new user doesn't see previous user's cached tasks
        lifecycleScope.launch {
            try {
                database.starDao().deleteAllStars()
                database.constellationDao().deleteAllLinks()
                database.orbitDao().deleteAllOrbits()
                android.util.Log.d("MainActivity", "Local database cleared on logout")
            } catch (e: Exception) {
                android.util.Log.e("MainActivity", "Failed to clear database: ${e.message}")
            }
        }

        tokenManager.clearTokens()
        Toast.makeText(this@MainActivity, "Logged out successfully", Toast.LENGTH_SHORT).show()
    }

    private fun handleForgotPassword(email: String) {
        Toast.makeText(
            this,
            "📧 Password reset link sent to $email\n\nCheck your inbox and spam folder.",
            Toast.LENGTH_LONG
        ).show()
    }

    private fun changeWallpaperTheme(theme: String) {
        android.util.Log.e("MainActivity", "🎨 Theme change requested: $theme")

        // 1. Update local preferences
        wallpaperPreferences.setTheme(theme)
        android.util.Log.e("MainActivity", "✅ Local preferences updated to: $theme")

        // 2. Update backend if authenticated
        if (tokenManager.isLoggedIn()) {
            lifecycleScope.launch {
                try {
                    // Re-detect resolution to ensure we always use correct screen size
                    val resolution = wallpaperPreferences.detectDeviceResolution()
                    android.util.Log.e("MainActivity", "Sending PATCH /api/user with theme=$theme, resolution=$resolution")
                    NetworkModule.getApi(this@MainActivity).updateUser(
                        mapOf(
                            "theme" to theme,
                            "resolution" to resolution
                        )
                    )
                    android.util.Log.e("MainActivity", "✅ Backend updated successfully")
                    Toast.makeText(this@MainActivity, "Theme changed to $theme", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    android.util.Log.e("MainActivity", "⚠️ Backend update failed: ${e.message}", e)
                    Toast.makeText(this@MainActivity, "Theme saved locally", Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            Toast.makeText(this@MainActivity, "Theme changed to $theme", Toast.LENGTH_SHORT).show()
        }

        // 3. Trigger immediate wallpaper update
        android.util.Log.e("MainActivity", "🚀 Triggering immediate wallpaper update...")
        triggerImmediateUpdate()
    }
}
