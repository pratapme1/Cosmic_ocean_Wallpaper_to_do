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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
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
import com.cosmicocean.data.EnvironmentPreferencesRepository
import com.cosmicocean.ui.state.EnvironmentPreferences
import com.cosmicocean.service.RealTimeWallpaperService
import com.cosmicocean.sync.SyncManager
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import androidx.annotation.VisibleForTesting
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File

class MainActivity : ComponentActivity() {

    private lateinit var viewModel: MainViewModel
    private lateinit var tokenManager: TokenManager
    private lateinit var wallpaperPreferences: WallpaperPreferencesManager
    private lateinit var database: CosmicDatabase
    private lateinit var zoneManager: ZoneManager
    private val editingStarState = mutableStateOf<Star?>(null)
    private val envRepo by lazy { EnvironmentPreferencesRepository(applicationContext) }

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
        zoneManager = ZoneManager(1080f, 1920f)
        val audioEngine = com.cosmicocean.audio.AudioEngine(this)

        val factory = MainViewModelFactory(repository, engine, constellationSystem, orbitalSystem, commandHistory, syncManager)

        // Set up command history callbacks for toast notifications
        commandHistory.onUndoRedo = { action, description ->
            Toast.makeText(this, "$action: $description", Toast.LENGTH_SHORT).show()
        }

        // Schedule periodic wallpaper updates to keep urgency states current
        schedulePeriodicWallpaperUpdates()
        scheduleDueHaptics()

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
                var showSnoozeOverdueConfirm by remember { mutableStateOf(false) }
                var editingStar by editingStarState
                var lastTapOffset by remember { mutableStateOf<androidx.compose.ui.geometry.Offset?>(null) }
                var isInteracting by remember { mutableStateOf(false) }
                var currentTheme by remember { mutableStateOf(wallpaperPreferences.getTheme()) }
                var showWallpaperConsent by remember { mutableStateOf(!wallpaperPreferences.hasWallpaperConsent()) }
                var isUploadingWallpaper by remember { mutableStateOf(false) }
                var dismissNextTask by remember { mutableStateOf(false) }
                var dismissShortSuggestion by remember { mutableStateOf(false) }
                var showCelebration by remember { mutableStateOf(false) }
                var activeFocus by remember { mutableStateOf<FocusSession?>(null) }
                val context = LocalContext.current
                val contentResolver = context.contentResolver
                val coroutineScope = rememberCoroutineScope()
                val envPrefs by envRepo.preferencesFlow.collectAsState(initial = EnvironmentPreferences())
                var hudVisible by remember { mutableStateOf(true) }
                var lastInteractionAt by remember { mutableLongStateOf(System.currentTimeMillis()) }
                val hudAutoHideMs = 2500L
                val hudZones = remember { mutableStateMapOf<String, ZoneManager.ZoneRect>() }
                val updateHudZone: (String, androidx.compose.ui.layout.LayoutCoordinates) -> Unit = { key, coords ->
                    val bounds = coords.boundsInRoot()
                    hudZones[key] = ZoneManager.ZoneRect(bounds.left, bounds.top, bounds.right, bounds.bottom)
                }

                val overlayBlockingHud = showQuickAdd ||
                    showSearch ||
                    showSettings ||
                    showPrivacySettings ||
                    showEnvironmentSettings ||
                    showTrophyGallery ||
                    showSnoozeOverdueConfirm ||
                    editingStar != null

                val shouldShowHud = hudVisible && !overlayBlockingHud && activeFocus == null
                val reservedZones = if (shouldShowHud) hudZones.values.toList() else emptyList()

                LaunchedEffect(reservedZones) {
                    zoneManager.updateReservedZones(reservedZones)
                }

                val registerInteraction = {
                    lastInteractionAt = System.currentTimeMillis()
                    hudVisible = true
                }

                LaunchedEffect(Unit) {
                    while (true) {
                        if (overlayBlockingHud || activeFocus != null) {
                            hudVisible = false
                        } else {
                            val idleMs = System.currentTimeMillis() - lastInteractionAt
                            if (idleMs >= hudAutoHideMs) {
                                hudVisible = false
                            }
                        }
                        delay(200)
                    }
                }

                val tutorialSteps = remember {
                    listOf(
                        TutorialStep(
                            title = "Create a task",
                            body = "Double-tap empty space or tap + to release your first star."
                        ),
                        TutorialStep(
                            title = "Edit or complete",
                            body = "Tap a star to edit it, or drag it into the Sun to complete."
                        ),
                        TutorialStep(
                            title = "Refresh wallpaper",
                            body = "Tap the refresh icon to update your lock screen instantly."
                        ),
                        TutorialStep(
                            title = "Customize",
                            body = "Upload a custom wallpaper or open settings. Everything stays local unless you enable sync."
                        )
                    )
                }
                val tutorialTotalSteps = tutorialSteps.size
                val tutorialStepIndex = envPrefs.tutorialStep.coerceIn(0, tutorialTotalSteps)
                val showTutorial = !envPrefs.tutorialSeen && tutorialStepIndex < tutorialTotalSteps

                LaunchedEffect(Unit) {
                    com.cosmicocean.ui.components.CelebrationBus.events.collect {
                        showCelebration = true
                        kotlinx.coroutines.delay(1200)
                        showCelebration = false
                    }
                }

                var focusRemaining by remember { mutableStateOf("") }
                LaunchedEffect(activeFocus) {
                    while (activeFocus != null) {
                        val remaining = formatFocusRemaining(activeFocus!!)
                        focusRemaining = remaining
                        if (remaining == "Done") {
                            envRepo.setFocusModeEnabled(false)
                            RealTimeWallpaperService.updateNow(this@MainActivity)
                            activeFocus = null
                            break
                        }
                        kotlinx.coroutines.delay(1000)
                    }
                }

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
                                triggerImmediateUpdate(force = true)
                                advanceTutorialStep(4)
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
                                    wallpaperPreferences.setWallpaperConsent(true)
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
                                    wallpaperPreferences.setWallpaperConsent(true)
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
                                registerInteraction()
                                lastTapOffset = offset
                                showQuickAdd = true
                            },
                            onInteraction = {
                                isInteracting = true
                                registerInteraction()
                            },
                            onStarFinalized = { star, action -> handleStarAction(star, action) },
                            onStarSnooze = { star, duration -> snoozeStar(star, duration) },
                            onStarDragEnd = { star -> saveStarPosition(star) },  // EPIC 9: Save position after drag
                            audioEngine = audioEngine
                        )

                        AmbientStatusHUD(stars = stars, isInteracting = isInteracting)

                        val nextTask = remember(stars) {
                            stars.filter { !it.isCompleted && !it.isArchived }
                                .sortedWith(compareBy<Star> { it.dueIn >= 0 }
                                    .thenBy { kotlin.math.abs(it.dueIn) }
                                    .thenBy { it.urgency })
                                .firstOrNull()
                        }

                        val shortSuggestion = remember(stars, envPrefs.contextMode, envPrefs.manualContext) {
                            findShortSuggestion(stars, envPrefs.contextMode, envPrefs.manualContext)
                        }

                        val overdueCount = remember(stars) {
                            stars.count { it.dueIn < 0 && !it.isCompleted && !it.isArchived }
                        }
                        val dueSoonCount = remember(stars) {
                            stars.count { it.dueIn in 0f..120f && !it.isCompleted && !it.isArchived }
                        }
                        val ambientMessage = remember(overdueCount, dueSoonCount) {
                            when {
                                overdueCount > 0 && dueSoonCount > 0 -> "${overdueCount} overdue • ${dueSoonCount} due soon"
                                overdueCount > 0 -> "${overdueCount} overdue"
                                dueSoonCount > 0 -> "${dueSoonCount} due soon"
                                else -> ""
                            }
                        }

                        if (!dismissNextTask && nextTask != null) {
                            Box(modifier = Modifier.align(Alignment.BottomStart)) {
                                AnimatedVisibility(visible = shouldShowHud, enter = fadeIn(), exit = fadeOut()) {
                                    Box(
                                        modifier = Modifier
                                            .padding(24.dp)
                                            .padding(bottom = 140.dp)
                                            .onGloballyPositioned { coords -> updateHudZone("nextTaskChip", coords) }
                                    ) {
                                        NextTaskChip(
                                            star = nextTask,
                                            onClick = {
                                                registerInteraction()
                                                editingStar = nextTask
                                            },
                                            onDismiss = {
                                                registerInteraction()
                                                dismissNextTask = true
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        if (!dismissShortSuggestion && shortSuggestion != null && !isInteracting) {
                            Box(modifier = Modifier.align(Alignment.BottomStart)) {
                                AnimatedVisibility(visible = shouldShowHud, enter = fadeIn(), exit = fadeOut()) {
                                    Box(
                                        modifier = Modifier
                                            .padding(24.dp)
                                            .padding(bottom = 200.dp)
                                            .onGloballyPositioned { coords -> updateHudZone("shortSuggestionChip", coords) }
                                    ) {
                                        ShortTaskSuggestionChip(
                                            title = shortSuggestion.title,
                                            estimateMinutes = shortSuggestion.estimateMinutes,
                                            onClick = {
                                                registerInteraction()
                                                editingStar = shortSuggestion.star
                                            },
                                            onDismiss = {
                                                registerInteraction()
                                                dismissShortSuggestion = true
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        if (envPrefs.ambientRemindersEnabled && !isInteracting && ambientMessage.isNotBlank()) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .padding(top = 120.dp)
                            ) {
                                AmbientReminder(message = ambientMessage, visible = true)
                            }
                        }

                        if (activeFocus != null) {
                            Box(modifier = Modifier.align(Alignment.TopCenter)) {
                                FocusSessionOverlay(
                                    title = activeFocus!!.title,
                                    remainingLabel = focusRemaining,
                                    onStop = {
                                        coroutineScope.launch {
                                            envRepo.setFocusModeEnabled(false)
                                            RealTimeWallpaperService.updateNow(this@MainActivity)
                                        }
                                        activeFocus = null
                                    }
                                )
                            }
                        }

                        // Performance Monitor (Debug)
                        Box(modifier = Modifier.align(Alignment.TopStart)) {
                            com.cosmicocean.debug.PerformanceMonitor(
                                enabled = BuildConfig.DEBUG
                            )
                        }

                        // Achievement Wall
                        Box(modifier = Modifier.align(Alignment.BottomCenter)) {
                            AnimatedVisibility(visible = shouldShowHud, enter = fadeIn(), exit = fadeOut()) {
                                Box(
                                    modifier = Modifier
                                        .onGloballyPositioned { coords -> updateHudZone("achievementWall", coords) }
                                ) {
                                    AchievementWall(
                                        completedStars = completedStars,
                                        onClick = {
                                            registerInteraction()
                                            showTrophyGallery = true
                                        }
                                    )
                                }
                            }
                        }

                        // HUD Controls
                        Box(modifier = Modifier.align(Alignment.TopEnd)) {
                            AnimatedVisibility(visible = shouldShowHud, enter = fadeIn(), exit = fadeOut()) {
                                Row(
                                    modifier = Modifier
                                        .padding(16.dp)
                                        .onGloballyPositioned { coords -> updateHudZone("hudControls", coords) }
                                ) {
                                    // Refresh Wallpaper Button
                                    IconButton(
                                        onClick = {
                                            registerInteraction()
                                            android.widget.Toast.makeText(this@MainActivity, "Refreshing wallpaper...", android.widget.Toast.LENGTH_SHORT).show()
                                            triggerImmediateUpdate()
                                            advanceTutorialStep(3)
                                        },
                                        modifier = Modifier.background(Color.Black.copy(0.4f), shape = MaterialTheme.shapes.small)
                                    ) {
                                        Icon(Icons.Default.Refresh, contentDescription = "Refresh Wallpaper", tint = Color.White)
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    IconButton(
                                        onClick = {
                                            registerInteraction()
                                            showSearch = true
                                        },
                                        modifier = Modifier.background(Color.Black.copy(0.4f), shape = MaterialTheme.shapes.small)
                                    ) {
                                        Icon(Icons.Default.Search, contentDescription = "Search", tint = Color.White)
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    IconButton(
                                        onClick = {
                                            registerInteraction()
                                            advanceTutorialStep(4)
                                            showSettings = true
                                        },
                                        modifier = Modifier.background(Color.Black.copy(0.4f), shape = MaterialTheme.shapes.small)
                                    ) {
                                        Icon(Icons.Default.Settings, contentDescription = "Settings", tint = Color.White)
                                    }
                                }
                            }
                        }

                        // NEW: Glassmorphic Wallpaper Upload Button (Bottom Start)
                        Box(modifier = Modifier.align(Alignment.BottomStart)) {
                            AnimatedVisibility(visible = shouldShowHud, enter = fadeIn(), exit = fadeOut()) {
                                Box(
                                    modifier = Modifier
                                        .padding(24.dp)
                                        .padding(bottom = 80.dp) // Align with FAB height
                                        .onGloballyPositioned { coords -> updateHudZone("customizeButton", coords) }
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
                                                registerInteraction()
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
                            }
                        }

                        Box(modifier = Modifier.align(Alignment.BottomEnd)) {
                            AnimatedVisibility(visible = shouldShowHud, enter = fadeIn(), exit = fadeOut()) {
                                FloatingActionButton(
                                    onClick = {
                                        registerInteraction()
                                        showQuickAdd = true
                                    },
                                    modifier = Modifier
                                        .padding(24.dp)
                                        .padding(bottom = 80.dp)
                                        .onGloballyPositioned { coords -> updateHudZone("fab", coords) },
                                    containerColor = Color(0xFF00E5FF)
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = "Add Task")
                                }
                            }
                        }

                        Box(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = 16.dp)
                        ) {
                            CompletionCelebration(visible = showCelebration)
                        }

                        if (showQuickAdd) {
                            val availableParents = stars.filter { !it.isCompleted && !it.isArchived && !it.isSubtask }
                            QuickAddOverlay(
                                onDismiss = { showQuickAdd = false },
                                availableParents = availableParents,
                                onSave = { title, recurringOverride, echoIntervalOverride, isSubtask, parentId ->
                                    createNewStar(title, lastTapOffset, recurringOverride, echoIntervalOverride, isSubtask, parentId)
                                }
                            )
                        }

                        if (editingStar != null) {
                            val availableParents = stars.filter {
                                !it.isCompleted && !it.isArchived && !it.isSubtask && it.id != editingStar!!.id
                            }
                            EditStarOverlay(
                                star = editingStar!!,
                                onDismiss = { editingStar = null },
                                availableParents = availableParents,
                                onSave = { title, urgency, dueInMinutes, isRecurring, echoInterval, isSubtask, parentId ->
                                    updateStar(editingStar!!, title, urgency, dueInMinutes, isRecurring, echoInterval, isSubtask, parentId)
                                    editingStar = null
                                },
                                onStartFocus = { minutes ->
                                    coroutineScope.launch {
                                        envRepo.setFocusModeEnabled(true)
                                        RealTimeWallpaperService.updateNow(this@MainActivity)
                                    }
                                    activeFocus = FocusSession(
                                        title = editingStar!!.title,
                                        startedAt = System.currentTimeMillis(),
                                        durationMinutes = minutes
                                    )
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
                                onSnoozeOverdue = { showSnoozeOverdueConfirm = true },
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
                                            return EnvironmentSettingsViewModel(
                                                NetworkModule.getApi(applicationContext),
                                                wallpaperPreferences,
                                                EnvironmentPreferencesRepository(applicationContext)
                                            ) as T
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

                        if (showSnoozeOverdueConfirm) {
                            AlertDialog(
                                onDismissRequest = { showSnoozeOverdueConfirm = false },
                                title = { Text("Snooze Overdue Tasks") },
                                text = { Text("Snooze all overdue tasks for 60 minutes?") },
                                confirmButton = {
                                    Button(onClick = {
                                        showSnoozeOverdueConfirm = false
                                        val overdueTasks = stars.filter { it.dueIn < 0 && !it.isCompleted && !it.isArchived }
                                        overdueTasks.forEach { snoozeStar(it, 60) }
                                        Toast.makeText(context, "Snoozed ${overdueTasks.size} tasks", Toast.LENGTH_SHORT).show()
                                    }) { Text("Snooze") }
                                },
                                dismissButton = {
                                    TextButton(onClick = { showSnoozeOverdueConfirm = false }) { Text("Cancel") }
                                }
                            )
                        }

                        val canShowTutorial = showTutorial &&
                            !showWallpaperConsent &&
                            !showQuickAdd &&
                            !showSearch &&
                            !showSettings &&
                            !showPrivacySettings &&
                            !showEnvironmentSettings &&
                            !showTrophyGallery &&
                            !showSnoozeOverdueConfirm &&
                            editingStar == null

                        if (canShowTutorial) {
                            TutorialOverlay(
                                title = tutorialSteps[tutorialStepIndex].title,
                                body = tutorialSteps[tutorialStepIndex].body,
                                stepIndex = tutorialStepIndex,
                                totalSteps = tutorialTotalSteps,
                                onSkip = {
                                    completeTutorial()
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    private data class FocusSession(
        val title: String,
        val startedAt: Long,
        val durationMinutes: Int
    )

    private data class TutorialStep(
        val title: String,
        val body: String
    )

    private data class ShortSuggestion(
        val star: Star,
        val title: String,
        val estimateMinutes: Int
    )

    private fun formatFocusRemaining(session: FocusSession): String {
        val totalMs = session.durationMinutes * 60 * 1000L
        val elapsed = System.currentTimeMillis() - session.startedAt
        val remaining = totalMs - elapsed
        if (remaining <= 0) return "Done"
        val minutes = (remaining / 60000).toInt()
        val seconds = ((remaining % 60000) / 1000).toInt()
        return "${minutes}m ${seconds}s"
    }

    private fun advanceTutorialStep(targetStep: Int) {
        lifecycleScope.launch {
            val prefs = envRepo.preferencesFlow.first()
            if (prefs.tutorialSeen) return@launch
            val expectedNext = prefs.tutorialStep + 1
            if (targetStep != expectedNext) return@launch
            envRepo.setTutorialStep(targetStep)
            if (targetStep >= 4) {
                envRepo.setTutorialSeen(true)
            }
        }
    }

    private fun completeTutorial() {
        lifecycleScope.launch {
            envRepo.setTutorialStep(4)
            envRepo.setTutorialSeen(true)
        }
    }

    private fun findShortSuggestion(
        stars: List<Star>,
        contextMode: com.cosmicocean.ui.state.ContextMode,
        manualContext: String
    ): ShortSuggestion? {
        val candidates = stars.filter { !it.isCompleted && !it.isArchived }
            .mapNotNull { star ->
                val estimate = extractEstimateMinutesFromTitle(star.title)
                if (estimate != null && estimate <= 15) {
                    if (contextMode == com.cosmicocean.ui.state.ContextMode.MANUAL) {
                        if (!matchesContext(star.title, manualContext)) return@mapNotNull null
                    }
                    ShortSuggestion(star, star.title, estimate)
                } else null
            }

        return candidates.sortedWith(
            compareBy<ShortSuggestion> { it.star.dueIn >= 0 }
                .thenBy { kotlin.math.abs(it.star.dueIn) }
                .thenBy { it.estimateMinutes }
        ).firstOrNull()
    }

    private fun extractEstimateMinutesFromTitle(title: String): Int? {
        val lower = title.lowercase()
        val minRegex = Regex("(\\d{1,3})\\s*(m|min|mins|minutes)")
        val hourRegex = Regex("(\\d{1,3})\\s*(h|hr|hrs|hours)")
        val minMatch = minRegex.find(lower)
        if (minMatch != null) {
            return minMatch.groupValues[1].toIntOrNull()
        }
        val hourMatch = hourRegex.find(lower)
        if (hourMatch != null) {
            val hours = hourMatch.groupValues[1].toIntOrNull() ?: return null
            return hours * 60
        }
        return null
    }

    private fun matchesContext(title: String, contextValue: String): Boolean {
        if (contextValue == "custom") return true
        val lower = title.lowercase()
        val token = "@${contextValue.lowercase()}"
        return lower.contains(token) || lower.contains(contextValue.lowercase())
    }

    private fun parseEchoInterval(pattern: String?): com.cosmicocean.model.EchoInterval? {
        return when (pattern?.lowercase()) {
            "daily" -> com.cosmicocean.model.EchoInterval.DAILY
            "weekly" -> com.cosmicocean.model.EchoInterval.WEEKLY
            "monthly" -> com.cosmicocean.model.EchoInterval.MONTHLY
            else -> null
        }
    }

    private fun handleStarAction(star: Star, type: String) {
        lifecycleScope.launch {
            when (type) {
                "complete" -> {
                    viewModel.completeStar(star)
                    com.cosmicocean.ui.components.CelebrationBus.trigger()
                }
                "archive" -> viewModel.archiveStar(star)
                "delete" -> {
                    // CRITICAL FIX: Use viewModel.deleteStar() to remove from Room DB + backend
                    viewModel.deleteStar(star)
                    Toast.makeText(this@MainActivity, "Star Exploded!", Toast.LENGTH_SHORT).show()
                }
            }
            if (type == "complete" || type == "archive") {
                advanceTutorialStep(2)
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
        val adjusted = zoneManager.clampToSafeArea(
            x = star.particle.x,
            y = star.particle.y,
            radius = star.particle.radius
        )
        star.particle.x = adjusted.first
        star.particle.y = adjusted.second
        star.particle.oldX = adjusted.first
        star.particle.oldY = adjusted.second
        lifecycleScope.launch {
            viewModel.updateStar(star)
        }
    }

    private fun updateStar(
        star: Star,
        title: String,
        urgency: Int,
        dueInMinutes: Float,
        isRecurring: Boolean,
        echoInterval: com.cosmicocean.model.EchoInterval?,
        isSubtask: Boolean,
        parentId: String?
    ) {
        star.title = title
        star.urgency = urgency
        star.dueIn = dueInMinutes
        star.isRecurring = isRecurring
        star.echoInterval = if (isRecurring) echoInterval else null
        val resolvedParent = parentId?.takeIf { it.isNotBlank() }
        val validParent = resolvedParent?.let { id ->
            viewModel.stars.firstOrNull { it.id == id && it.id != star.id && !it.isSubtask }
        }
        star.isSubtask = isSubtask && validParent != null
        star.parentId = if (star.isSubtask) validParent?.id else null

        // Update due date timestamp
        star.dueDate = System.currentTimeMillis() + (dueInMinutes * 60 * 1000).toLong()

        lifecycleScope.launch {
            try {
                viewModel.updateStar(star)
                advanceTutorialStep(2)
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

    private fun createNewStar(
        title: String,
        offset: androidx.compose.ui.geometry.Offset?,
        recurringOverride: Boolean? = null,
        echoIntervalOverride: com.cosmicocean.model.EchoInterval? = null,
        isSubtask: Boolean = false,
        parentId: String? = null
    ) {
        val random = java.util.Random()

        // Get screen dimensions for better placement
        val displayMetrics = resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels.toFloat()
        val screenHeight = displayMetrics.heightPixels.toFloat()
        zoneManager.updateScreenSize(screenWidth, screenHeight)

        // EPIC 9: Random placement across full screen (15% padding to avoid edges)
        // Stars stay where created - no zone forces, color shows urgency instead
        val horizontalPadding = screenWidth * 0.15f
        val verticalPadding = screenHeight * 0.1f

        // If user tapped, use that position; otherwise, random placement
        val x = offset?.x ?: (horizontalPadding + random.nextFloat() * (screenWidth - 2 * horizontalPadding))
        val y = offset?.y ?: (verticalPadding + random.nextFloat() * (screenHeight - 2 * verticalPadding))

        lifecycleScope.launch {
            val parsed = viewModel.parseTaskInput(title)
            val dueDateMs = com.cosmicocean.utils.TaskDateUtils.parseToMillis(parsed.dueDate, parsed.dueTime)
            val cleanedTitle = parsed.title.ifBlank { title }
            val urgency = parsed.priority
            val contextTag = parsed.contextTags?.firstOrNull()
                ?.removePrefix("@")
                ?.trim()
                ?.ifBlank { null }
            val parsedEchoInterval = parseEchoInterval(parsed.recurringPattern)
            val resolvedRecurring = recurringOverride ?: parsed.isRecurring
            val resolvedEchoInterval = if (resolvedRecurring) {
                echoIntervalOverride ?: parsedEchoInterval
            } else {
                null
            }

            // TaskRepository.addStar() handles both local DB insert AND backend sync
            val resolvedParent = parentId?.takeIf { it.isNotBlank() }
            val parentStar = if (isSubtask && resolvedParent != null) {
                viewModel.stars.firstOrNull { it.id == resolvedParent && !it.isSubtask }
            } else {
                null
            }
            val star = Star(
                x = x,
                y = y,
                title = cleanedTitle,
                urgency = urgency,
                dueDate = dueDateMs,
                contextTag = contextTag,
                isSubtask = isSubtask && parentStar != null,
                parentId = parentStar?.id,
                isRecurring = resolvedRecurring,
                echoInterval = resolvedEchoInterval
            )
            val adjusted = zoneManager.clampToSafeArea(
                x = star.particle.x,
                y = star.particle.y,
                radius = star.particle.radius
            )
            star.particle.x = adjusted.first
            star.particle.y = adjusted.second
            star.particle.oldX = adjusted.first
            star.particle.oldY = adjusted.second
            viewModel.addStar(star, parentStar)
            // No need for local delay here anymore, as RealTimeWallpaperService.ACTION_FORCE_UPDATE 
            // now includes a 500ms delay internally for consistency.
            advanceTutorialStep(1)
            triggerImmediateUpdate()
        }
    }

    @VisibleForTesting
    fun openEditForStarId(starId: String) {
        if (!BuildConfig.DEBUG) return
        val star = viewModel.stars.firstOrNull { it.id == starId }
        if (star != null) {
            runOnUiThread {
                editingStarState.value = star
            }
            return
        }

        val entity = runBlocking(Dispatchers.IO) {
            database.starDao().getByLocalId(starId)
        } ?: return

        val fallbackStar = entity.toStar()
        runOnUiThread {
            editingStarState.value = fallbackStar
        }
    }

    @VisibleForTesting
    fun getStarPosition(starId: String): Pair<Float, Float>? {
        if (!BuildConfig.DEBUG) return null
        val star = viewModel.stars.firstOrNull { it.id == starId }
        if (star != null) {
            return Pair(star.particle.x, star.particle.y)
        }
        return runBlocking(Dispatchers.IO) {
            database.starDao().getByLocalId(starId)?.let { Pair(it.x, it.y) }
        }
    }

    @VisibleForTesting
    fun getZoneTargets(): Pair<Float, Float> {
        return Pair(zoneManager.archivedZoneX, zoneManager.completedZoneX)
    }

    @VisibleForTesting
    fun hasStarInViewModel(starId: String): Boolean {
        if (!BuildConfig.DEBUG) return false
        return viewModel.stars.any { it.id == starId }
    }

    @VisibleForTesting
    fun updateStarForTest(starId: String, title: String, urgency: Int, dueInMinutes: Float) {
        if (!BuildConfig.DEBUG) return
        val star = viewModel.stars.firstOrNull { it.id == starId }
            ?: runBlocking(Dispatchers.IO) { database.starDao().getByLocalId(starId)?.toStar() }
            ?: return
        runOnUiThread {
            updateStar(
                star,
                title,
                urgency,
                dueInMinutes,
                star.isRecurring,
                star.echoInterval,
                star.isSubtask,
                star.parentId
            )
        }
    }

    @VisibleForTesting
    fun getStarSnapshot(starId: String): Pair<String, Int>? {
        if (!BuildConfig.DEBUG) return null
        val star = viewModel.stars.firstOrNull { it.id == starId } ?: return null
        return Pair(star.title, star.urgency)
    }

    private fun com.cosmicocean.data.StarEntity.toStar(): Star {
        val star = Star(
            x = x,
            y = y,
            title = title,
            urgency = urgency,
            dueDate = dueDate,
            contextTag = contextTag,
            isSubtask = isSubtask,
            parentId = parentId,
            isRecurring = isRecurring,
            echoInterval = echoInterval?.let { com.cosmicocean.model.EchoInterval.valueOf(it) },
            createdAt = createdAt,
            id = localId
        )
        star.isCompleted = isCompleted
        star.completedAt = completedAt
        star.isArchived = isArchived
        star.archivedAt = archivedAt
        star.isSnoozed = isSnoozed
        star.snoozeUntil = snoozeUntil
        return star
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
        // LOCAL-FIRST: Auto-enable wallpaper updates if preference not set
        if (com.cosmicocean.BuildConfig.LOCAL_ONLY && !wallpaperPreferences.hasSetWallpaperPreference()) {
            wallpaperPreferences.setWallpaperEnabled(true)
        }
        // Only proceed if wallpaper integration is explicitly enabled by the user
        if (!wallpaperPreferences.isWallpaperEnabled()) {
            android.util.Log.d("MainActivity", "Wallpaper updates skipped: Consent not granted.")
            return
        }

        // Start real-time wallpaper service (1-minute updates)
        RealTimeWallpaperService.start(this)

        // Keep WorkManager as fallback (every 15 minutes) in case service is killed
        val periodicWorkRequest = androidx.work.PeriodicWorkRequestBuilder<com.cosmicocean.worker.WallpaperUpdateWorker>(
            15, java.util.concurrent.TimeUnit.MINUTES
        )
            // LOCAL-FIRST: No network required for local wallpaper generation
            .setConstraints(androidx.work.Constraints.Builder().build())
            .build()

        // FIX: Changed from KEEP to UPDATE - ensures fresh scheduling even if worker was in failed state
        // UPDATE preserves enqueue time and doesn't cancel running workers (better than REPLACE)
        androidx.work.WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "wallpaper_periodic_update",
            androidx.work.ExistingPeriodicWorkPolicy.UPDATE,
            periodicWorkRequest
        )
    }

    private fun scheduleDueHaptics() {
        val periodicWorkRequest = androidx.work.PeriodicWorkRequestBuilder<com.cosmicocean.worker.DueHapticsWorker>(
            15, java.util.concurrent.TimeUnit.MINUTES
        ).setConstraints(androidx.work.Constraints.Builder().build()).build()

        androidx.work.WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "due_haptics_worker",
            androidx.work.ExistingPeriodicWorkPolicy.UPDATE,
            periodicWorkRequest
        )
    }

    private fun triggerImmediateUpdate(force: Boolean = false) {
        if (force) {
            RealTimeWallpaperService.updateNowImmediate(this)
            return
        }
        // 1. Fast path: Foreground Service
        RealTimeWallpaperService.updateNow(this)

        // 2. Backup path: WorkManager
        // FIX: Added setExpedited for truly immediate execution
        val workRequest = androidx.work.OneTimeWorkRequestBuilder<com.cosmicocean.worker.WallpaperUpdateWorker>()
            .setExpedited(androidx.work.OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            // LOCAL-FIRST: No network required for local wallpaper generation
            .setConstraints(androidx.work.Constraints.Builder().build())
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
