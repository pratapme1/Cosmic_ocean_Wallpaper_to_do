package com.cosmicocean.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cosmicocean.sync.ConflictResolution
import com.cosmicocean.sync.SyncState

/**
 * CRITICAL FIX: Sync Status Indicator (Issue #10)
 * Shows sync state, pending count, and errors to user
 */
@Composable
fun SyncStatusIndicator(
    syncState: SyncState,
    pendingCount: Int,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = shouldShowIndicator(syncState, pendingCount),
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        Row(
            modifier = modifier
                .background(
                    color = getBackgroundColor(syncState),
                    shape = RoundedCornerShape(16.dp)
                )
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            when (syncState) {
                is SyncState.Syncing -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        "Syncing...",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                is SyncState.Error -> {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Sync error",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                    Text(
                        "Sync failed",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                is SyncState.Offline -> {
                    // Use Warning icon for offline state
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Offline",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "Offline",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                is SyncState.Synced -> {
                    if (pendingCount > 0) {
                        Text(
                            pendingCount.toString(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            "pending",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                else -> {}
            }
        }
    }
}

@Composable
private fun shouldShowIndicator(state: SyncState, pendingCount: Int): Boolean {
    return when (state) {
        is SyncState.Syncing -> true
        is SyncState.Error -> true
        is SyncState.Offline -> true
        is SyncState.Synced -> pendingCount > 0
        else -> false
    }
}

@Composable
private fun getBackgroundColor(state: SyncState): Color {
    return when (state) {
        is SyncState.Syncing -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
        is SyncState.Error -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f)
        is SyncState.Offline -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    }
}

/**
 * CRITICAL FIX: Conflict Resolution Dialog (Issue #6)
 * Shows when user needs to choose between local and server version
 */
@Composable
fun ConflictResolutionDialog(
    conflicts: List<ConflictResolution>,
    onResolve: (localId: String, useLocal: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val pendingConflicts = conflicts.filterIsInstance<ConflictResolution.RequiresUserChoice>()
    
    if (pendingConflicts.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = {
                Text("Sync Conflict")
            },
            text = {
                Column {
                    Text(
                        "Server has newer version of \"${pendingConflicts.first().localData.title}\". " +
                        "Your local changes may be overwritten.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Show local version
                    Text(
                        "Your version:",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        pendingConflicts.first().localData.title,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // Show server version
                    Text(
                        "Server version:",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Text(
                        pendingConflicts.first().serverData.title ?: "",
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onResolve(pendingConflicts.first().localId, true)
                    }
                ) {
                    Text("Keep Mine")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        onResolve(pendingConflicts.first().localId, false)
                    }
                ) {
                    Text("Use Server")
                }
            }
        )
    }
}

/**
 * Compact sync indicator for toolbar/status bar
 */
@Composable
fun CompactSyncIndicator(
    syncState: SyncState,
    pendingCount: Int,
    onClick: () -> Unit
) {
    IconButton(onClick = onClick) {
        when (syncState) {
            is SyncState.Syncing -> {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp
                )
            }
            is SyncState.Error -> {
                // Show warning icon with error color
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = "Sync error",
                    tint = MaterialTheme.colorScheme.error
                )
            }
            is SyncState.Offline -> {
                // Show warning icon for offline
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = "Offline",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            is SyncState.Synced -> {
                if (pendingCount > 0) {
                    // Show refresh icon for pending
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Sync pending"
                    )
                } else {
                    // Show checkmark for synced
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Synced"
                    )
                }
            }
            else -> {
                // Show checkmark for idle
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Sync status"
                )
            }
        }
    }
}
