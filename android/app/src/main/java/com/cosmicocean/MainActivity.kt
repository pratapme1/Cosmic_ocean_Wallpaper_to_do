package com.cosmicocean

import android.content.Intent
import android.net.Uri
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
import androidx.compose.ui.platform.testTag
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
import com.cosmicocean.service.CosmicLiveWallpaperService
import com.cosmicocean.sync.SyncManager
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
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

        scheduleDueHaptics()
        scheduleWallpaperRefresh()

        // Pull the latest Vi reminders (and flush queued completions) on app open
        lifecycleScope.launch {
            com.cosmicocean.reminders.RemoteRemindersRepository
                .getInstance(applicationContext)
                .refresh()
        }

        setContent {
            var isAuthenticated by remember { 
                mutableStateOf(tokenManager.isLoggedIn() || BuildConfig.LOCAL_ONLY) 
            }
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
                var currentTaskPlacement by remember { mutableStateOf(wallpaperPreferences.getTaskPlacement()) }
                var currentHudOverlayUri by remember { mutableStateOf(wallpaperPreferences.getHudOverlayUri()) }
                var currentHudOverlayVertical by remember { mutableIntStateOf(wallpaperPreferences.getHudOverlayVerticalPercent()) }
                var currentHudOverlayOpacity by remember { mutableIntStateOf(wallpaperPreferences.getHudOverlayOpacityPercent()) }
                var hudOverlayMissing by remember { mutableStateOf(false) }
                var showWallpaperConsent by remember { mutableStateOf(false) }
                var showDiscovery by remember { mutableStateOf(false) }
                var isUploadingWallpaper by remember { mutableStateOf(false) }
                var dismissNextTask by remember { mutableStateOf(false) }
                var dismissShortSuggestion by remember { mutableStateOf(false) }
                var showCelebration by remember { mutableStateOf(false) }
                var activeFocus by remember { mutableStateOf<FocusSession?>(null) }
                val context = LocalContext.current
                val coroutineScope = rememberCoroutineScope()
                val envPrefs by envRepo.preferencesFlow.collectAsState(initial = EnvironmentPreferences())
                var hudVisible by remember { mutableStateOf(true) }

                LaunchedEffect(isAuthenticated) {
                    if (isAuthenticated && !envPrefs.tutorialSeen && envPrefs.tutorialStep == 0) {
                        showDiscovery = true
                    }
                }

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
                            body = "Upload a custom wallpaper or open settings."
                        )
                    )
                }
                val tutorialTotalSteps = tutorialSteps.size
                val showTutorial = !envPrefs.tutorialSeen && envPrefs.tutorialStep < tutorialTotalSteps

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

                                // LOCAL-FIRST FIX: Save to permanent local storage with unique name to bypass caching
                                val oldPath = wallpaperPreferences.getCustomWallpaperPath()
                                if (oldPath != null) {
                                    try {
                                        val oldFile = File(oldPath)
                                        if (oldFile.exists()) oldFile.delete()
                                    } catch (e: Exception) {
                                        android.util.Log.w("MainActivity", "Failed to delete old wallpaper", e)
                                    }
                                }

                                val newFileName = "custom_wallpaper_${System.currentTimeMillis()}.jpg"
                                val permanentFile = File(context.filesDir, newFileName)
                                tempFile.copyTo(permanentFile, overwrite = true)
                                tempFile.delete() // Clean up temp file

                                // LOCAL-FIRST FIX: Set wallpaper mode and path in preferences
                                wallpaperPreferences.setWallpaperMode(WallpaperPreferencesManager.WALLPAPER_MODE_CUSTOM)
                                wallpaperPreferences.setCustomWallpaperPath(permanentFile.absolutePath)
                                envRepo.setWallpaperMode(WallpaperPreferencesManager.WALLPAPER_MODE_CUSTOM)

                                advanceTutorialStep(4)
                                Toast.makeText(context, "Custom wallpaper applied!", Toast.LENGTH_SHORT).show()

                                // OPTIONAL: Background upload to backend for sync/backup
                                try {
                                    val requestFile = permanentFile.asRequestBody("image/jpeg".toMediaTypeOrNull())
                                    val body = MultipartBody.Part.createFormData("image", permanentFile.name, requestFile)
                                    NetworkModule.getApi(context).uploadWallpaper(body)
                                } catch (e: Exception) {
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

                val hudOverlayPickerLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.OpenDocument()
                ) { uri ->
                    uri?.let { selectedUri ->
                        try {
                            contentResolver.takePersistableUriPermission(
                                selectedUri,
                                Intent.FLAG_GRANT_READ_URI_PERMISSION
                            )
                        } catch (e: Exception) {
                            android.util.Log.w("MainActivity", "HUD overlay permission not persisted: ${e.message}")
                        }
                        val uriString = selectedUri.toString()
                        wallpaperPreferences.setHudOverlayUri(uriString)
                        currentHudOverlayUri = uriString
                        hudOverlayMissing = false
                        Toast.makeText(context, "HUD overlay selected", Toast.LENGTH_SHORT).show()
                    }
                }

                LaunchedEffect(showSettings, currentHudOverlayUri) {
                    val uri = currentHudOverlayUri
                    hudOverlayMissing = if (showSettings && uri != null) {
                        withContext(Dispatchers.IO) { !canReadUriString(uri) }
                    } else {
                        false
                    }
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
                            onStarDragEnd = { star -> saveStarPosition(star) },
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
                                    IconButton(
                                        onClick = {
                                            registerInteraction()
                                            showSearch = true
                                        },
                                        modifier = Modifier
                                            .testTag("search_button")
                                            .background(Color.Black.copy(0.4f), shape = MaterialTheme.shapes.small)
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
                                        modifier = Modifier
                                            .testTag("settings_button")
                                            .background(Color.Black.copy(0.4f), shape = MaterialTheme.shapes.small)
                                    ) {
                                        Icon(Icons.Default.Settings, contentDescription = "Settings", tint = Color.White)
                                    }
                                }
                            }
                        }

                        // Customize Button
                        Box(modifier = Modifier.align(Alignment.BottomStart)) {
                            AnimatedVisibility(visible = shouldShowHud, enter = fadeIn(), exit = fadeOut()) {
                                Box(
                                    modifier = Modifier
                                        .padding(24.dp)
                                        .padding(bottom = 80.dp)
                                        .testTag("custom_wallpaper_button")
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
                                                    tint = Color(0xFFE040FB),
                                                    modifier = Modifier.size(20.dp)
                                                )
                                                Text(
                                                    "Custom Wallpaper",
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
                                        .testTag("add_task_button")
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
                            val viKeyManager = remember { com.cosmicocean.reminders.ViSupabaseKeyManager(applicationContext) }
                            var hasViKey by remember { mutableStateOf(viKeyManager.hasKey()) }
                            SettingsOverlay(
                                onDismiss = { showSettings = false },
                                onDoneForToday = { showSettings = false; markDoneForToday() },
                                onSnoozeOverdue = { showSnoozeOverdueConfirm = true },
                                onClearAll = { showSettings = false; clearAllTasks() },
                                userEmail = tokenManager.getEmail(),
                                currentTheme = currentTheme,
                                isCustomWallpaper = wallpaperPreferences.getWallpaperMode() == WallpaperPreferencesManager.WALLPAPER_MODE_CUSTOM,
                                onThemeChange = { newTheme ->
                                    currentTheme = newTheme
                                    changeWallpaperTheme(newTheme)
                                },
                                currentTaskPlacement = currentTaskPlacement,
                                onTaskPlacementChange = { newPlacement ->
                                    currentTaskPlacement = newPlacement
                                    wallpaperPreferences.setTaskPlacement(newPlacement)
                                },
                                hudOverlayUri = currentHudOverlayUri,
                                hudOverlayVerticalPercent = currentHudOverlayVertical,
                                hudOverlayOpacityPercent = currentHudOverlayOpacity,
                                hudOverlayMissing = hudOverlayMissing,
                                onPickHudOverlay = {
                                    hudOverlayPickerLauncher.launch(arrayOf("image/png"))
                                },
                                onHudOverlayVerticalChange = { newPosition ->
                                    currentHudOverlayVertical = newPosition
                                    wallpaperPreferences.setHudOverlayVerticalPercent(newPosition)
                                },
                                onHudOverlayOpacityChange = { newOpacity ->
                                    currentHudOverlayOpacity = newOpacity
                                    wallpaperPreferences.setHudOverlayOpacityPercent(newOpacity)
                                },
                                onClearHudOverlay = {
                                    wallpaperPreferences.clearHudOverlay()
                                    currentHudOverlayUri = null
                                    hudOverlayMissing = false
                                },
                                onOpenPrivacySettings = {
                                    showSettings = false
                                    showPrivacySettings = true
                                },
                                onOpenEnvironmentSettings = {
                                    showSettings = false
                                    showEnvironmentSettings = true
                                },
                                hasViKey = hasViKey,
                                onSaveViKey = { key ->
                                    viKeyManager.saveKey(key)
                                    hasViKey = true
                                    coroutineScope.launch {
                                        com.cosmicocean.reminders.RemoteRemindersRepository
                                            .getInstance(applicationContext)
                                            .refresh()
                                    }
                                },
                                onClearViKey = {
                                    viKeyManager.clearKey()
                                    hasViKey = false
                                    coroutineScope.launch {
                                        com.cosmicocean.reminders.RemoteRemindersRepository
                                            .getInstance(applicationContext)
                                            .clearLocal()
                                    }
                                }
                            )
                        }

                        if (showPrivacySettings) {
                            PrivacySettingsWrapper(
                                onNavigateBack = { showPrivacySettings = false }
                            )
                        }

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
                                onWallpaperEnabledChanged = { enabled ->
                                    // Keep sync with local state if needed
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

                        val canShowTutorial = !envPrefs.tutorialSeen && envPrefs.tutorialStep < tutorialTotalSteps &&
                            !showWallpaperConsent &&
                            !showDiscovery &&
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
                                title = tutorialSteps[envPrefs.tutorialStep].title,
                                body = tutorialSteps[envPrefs.tutorialStep].body,
                                stepIndex = envPrefs.tutorialStep,
                                totalSteps = tutorialTotalSteps,
                                onNext = {
                                    if (envPrefs.tutorialStep < tutorialTotalSteps - 1) {
                                        lifecycleScope.launch { envRepo.setTutorialStep(envPrefs.tutorialStep + 1) }
                                    } else {
                                        completeTutorial()
                                    }
                                },
                                onSkip = {
                                    completeTutorial()
                                }
                            )
                        }

                        if (showDiscovery) {
                            DiscoveryOverlay(
                                onDismiss = { 
                                    showDiscovery = false
                                    // Start Wallpaper setup next
                                    if (!wallpaperPreferences.hasWallpaperConsent()) {
                                        showWallpaperConsent = true
                                    } else {
                                        // If already has consent (edge case), start tutorial
                                        lifecycleScope.launch { envRepo.setTutorialStep(0) }
                                    }
                                }
                            )
                        }

                        if (showWallpaperConsent) {
                            WallpaperSetupOverlay(
                                onSetupClick = {
                                    wallpaperPreferences.setWallpaperEnabled(true)
                                    wallpaperPreferences.setWallpaperConsent(true)
                                    showWallpaperConsent = false
                                    // Start tutorial after wallpaper setup
                                    lifecycleScope.launch { envRepo.setTutorialStep(0) }
                                    Toast.makeText(context, "Cosmic wallpaper ready!", Toast.LENGTH_SHORT).show()
                                },
                                onDismiss = {
                                    wallpaperPreferences.setWallpaperEnabled(false)
                                    wallpaperPreferences.setWallpaperConsent(true)
                                    showWallpaperConsent = false
                                    // Start tutorial even if wallpaper is declined
                                    lifecycleScope.launch { envRepo.setTutorialStep(0) }
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    private data class FocusSession(val title: String, val startedAt: Long, val durationMinutes: Int)
    private data class TutorialStep(val title: String, val body: String)
    private data class ShortSuggestion(val star: Star, val title: String, val estimateMinutes: Int)

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

    private fun findShortSuggestion(stars: List<Star>, contextMode: com.cosmicocean.ui.state.ContextMode, manualContext: String): ShortSuggestion? {
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
        if (minMatch != null) return minMatch.groupValues[1].toIntOrNull()
        val hourMatch = hourRegex.find(lower)
        if (hourMatch != null) return (hourMatch.groupValues[1].toIntOrNull() ?: 0) * 60
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
                    viewModel.deleteStar(star)
                    Toast.makeText(this@MainActivity, "Star Exploded!", Toast.LENGTH_SHORT).show()
                }
            }
            if (type == "complete" || type == "archive") {
                advanceTutorialStep(2)
            }
        }
    }

    private fun snoozeStar(star: Star, durationMinutes: Int) {
        star.snooze(durationMinutes)
        lifecycleScope.launch {
            try {
                NetworkModule.getApi(this@MainActivity).snoozeTask(star.id, mapOf("duration_minutes" to durationMinutes))
            } catch (e: Exception) {}
        }
    }

    private fun saveStarPosition(star: Star) {
        val adjusted = zoneManager.clampToSafeArea(star.particle.x, star.particle.y, star.particle.radius)
        star.particle.x = adjusted.first
        star.particle.y = adjusted.second
        star.particle.oldX = adjusted.first
        star.particle.oldY = adjusted.second
        lifecycleScope.launch { viewModel.updateStar(star) }
    }

    private fun updateStar(star: Star, title: String, urgency: Int, dueInMinutes: Float, isRecurring: Boolean, echoInterval: com.cosmicocean.model.EchoInterval?, isSubtask: Boolean, parentId: String?) {
        star.title = title
        star.urgency = urgency
        star.dueIn = dueInMinutes
        star.isRecurring = isRecurring
        star.echoInterval = if (isRecurring) echoInterval else null
        val validParent = parentId?.let { id -> viewModel.stars.firstOrNull { it.id == id && it.id != star.id && !it.isSubtask } }
        star.isSubtask = isSubtask && validParent != null
        star.parentId = if (star.isSubtask) validParent?.id else null
        star.dueDate = System.currentTimeMillis() + (dueInMinutes * 60 * 1000).toLong()
        lifecycleScope.launch {
            try {
                viewModel.updateStar(star)
                advanceTutorialStep(2)
            } catch (e: Exception) {}
        }
    }

    private fun clearAllTasks() {
        lifecycleScope.launch {
            try {
                viewModel.clearAllTasks()
                database.constellationDao().deleteAllLinks()
                database.orbitDao().deleteAllOrbits()
                Toast.makeText(this@MainActivity, "Ocean Cleared", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {}
        }
    }

    private fun createNewStar(title: String, offset: androidx.compose.ui.geometry.Offset?, recurringOverride: Boolean? = null, echoIntervalOverride: com.cosmicocean.model.EchoInterval? = null, isSubtask: Boolean = false, parentId: String? = null) {
        val random = java.util.Random()
        val displayMetrics = resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels.toFloat()
        val screenHeight = displayMetrics.heightPixels.toFloat()
        zoneManager.updateScreenSize(screenWidth, screenHeight)
        val x = offset?.x ?: (screenWidth * 0.15f + random.nextFloat() * screenWidth * 0.7f)
        val y = offset?.y ?: (screenHeight * 0.1f + random.nextFloat() * screenHeight * 0.8f)

        lifecycleScope.launch {
            val parsed = viewModel.parseTaskInput(title)
            val dueDateMs = com.cosmicocean.utils.TaskDateUtils.parseToMillis(parsed.dueDate, parsed.dueTime)
            val parentStar = if (isSubtask && parentId != null) viewModel.stars.firstOrNull { it.id == parentId && !it.isSubtask } else null
            val star = Star(x = x, y = y, title = parsed.title.ifBlank { title }, urgency = parsed.priority, dueDate = dueDateMs, contextTag = parsed.contextTags?.firstOrNull()?.removePrefix("@")?.trim()?.ifBlank { null }, isSubtask = isSubtask && parentStar != null, parentId = parentStar?.id, isRecurring = recurringOverride ?: parsed.isRecurring, echoInterval = if (recurringOverride ?: parsed.isRecurring) echoIntervalOverride ?: parseEchoInterval(parsed.recurringPattern) else null)
            val adjusted = zoneManager.clampToSafeArea(star.particle.x, star.particle.y, star.particle.radius)
            star.particle.x = adjusted.first
            star.particle.y = adjusted.second
            star.particle.oldX = adjusted.first
            star.particle.oldY = adjusted.second
            viewModel.addStar(star, parentStar)
            advanceTutorialStep(1)
        }
    }

    private fun reportAppOpen() {
        lifecycleScope.launch { try { NetworkModule.getApi(this@MainActivity).reportAppOpen() } catch (e: Exception) {} }
    }

    private fun canReadUriString(uriString: String): Boolean {
        return try {
            contentResolver.openInputStream(Uri.parse(uriString))?.use { true } ?: false
        } catch (e: Exception) {
            false
        }
    }

    private fun markDoneForToday() {
        lifecycleScope.launch { try { NetworkModule.getApi(this@MainActivity).markDoneForToday() } catch (e: Exception) {} }
    }

    private fun scheduleDueHaptics() {
        val periodicWorkRequest = androidx.work.PeriodicWorkRequestBuilder<com.cosmicocean.worker.DueHapticsWorker>(15, java.util.concurrent.TimeUnit.MINUTES).build()
        androidx.work.WorkManager.getInstance(this).enqueueUniquePeriodicWork("due_haptics_worker", androidx.work.ExistingPeriodicWorkPolicy.UPDATE, periodicWorkRequest)
    }

    private fun scheduleWallpaperRefresh() {
        val periodicWorkRequest = androidx.work.PeriodicWorkRequestBuilder<WallpaperWorker>(
            WallpaperWorker.WORK_MANAGER_REFRESH_INTERVAL_MINUTES,
            java.util.concurrent.TimeUnit.MINUTES
        ).build()
        val workManager = androidx.work.WorkManager.getInstance(this)
        workManager.enqueueUniquePeriodicWork(
            WallpaperWorker.UNIQUE_WORK_NAME,
            androidx.work.ExistingPeriodicWorkPolicy.UPDATE,
            periodicWorkRequest
        )
        workManager.enqueueUniqueWork(
            "${WallpaperWorker.UNIQUE_WORK_NAME}_startup",
            androidx.work.ExistingWorkPolicy.REPLACE,
            androidx.work.OneTimeWorkRequestBuilder<WallpaperWorker>().build()
        )
    }

    private fun handleLogin(email: String, password: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        lifecycleScope.launch {
            try {
                val response = NetworkModule.getApi(this@MainActivity).login(mapOf("email" to email, "password" to password))
                if (response.isSuccessful && response.body() != null) {
                    val authResponse = response.body()!!
                    tokenManager.saveTokens(authResponse.accessToken, authResponse.refreshToken, authResponse.user.id, authResponse.user.email)
                    onSuccess()
                } else onError("Login failed")
            } catch (e: Exception) { onError("Connection error") }
        }
    }

    private fun handleRegister(email: String, password: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        if (password.length < 8) { onError("Password too short"); return }
        lifecycleScope.launch {
            try {
                val response = NetworkModule.getApi(this@MainActivity).register(mapOf("email" to email, "password" to password, "timezone" to java.util.TimeZone.getDefault().id))
                if (response.isSuccessful && response.body() != null) {
                    val authResponse = response.body()!!
                    tokenManager.saveTokens(authResponse.accessToken, authResponse.refreshToken, authResponse.user.id, authResponse.user.email)
                    onSuccess()
                } else onError("Registration failed")
            } catch (e: Exception) { onError("Connection error") }
        }
    }

    private fun handleLogout() {
        lifecycleScope.launch {
            try {
                database.starDao().deleteAllStars()
                database.constellationDao().deleteAllLinks()
                database.orbitDao().deleteAllOrbits()
            } catch (e: Exception) {}
        }
        tokenManager.clearTokens()
    }

    private fun handleForgotPassword(email: String) {
        Toast.makeText(this, "Password reset link sent to $email", Toast.LENGTH_LONG).show()
    }

    private fun changeWallpaperTheme(theme: String) {
        wallpaperPreferences.setTheme(theme)
        if (tokenManager.isLoggedIn()) {
            lifecycleScope.launch {
                try {
                    NetworkModule.getApi(this@MainActivity).updateUser(mapOf("theme" to theme, "resolution" to wallpaperPreferences.detectDeviceResolution()))
                } catch (e: Exception) {}
            }
        }
    }
}
