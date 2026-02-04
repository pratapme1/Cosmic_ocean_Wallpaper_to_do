package com.cosmicocean.widget

import android.content.Context
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.Action
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.cosmicocean.data.CosmicDatabase
import com.cosmicocean.network.LocalOnlyUserStore
import com.cosmicocean.network.NetworkModule
import java.util.concurrent.TimeUnit

class TaskWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val db = CosmicDatabase.getDatabase(context)
        val tasks = db.starDao().getTop3Tasks().map { star ->
            TaskItem(
                id = star.localId,
                title = star.title,
                estimate = formatDue(star.dueDate),
                priority = star.urgency,
                due_date = star.dueDate?.toString()
            )
        }

        val userId = LocalOnlyUserStore(context).getOrCreateUser().id
        val streak = db.achievementStatsDao().getStatsSync(userId)?.currentStreak ?: 0

        provideContent {
            TaskWidgetContent(tasks, streak)
        }
    }

    @Composable
    private fun TaskWidgetContent(tasks: List<TaskItem>, streak: Int) {
        val context = androidx.glance.LocalContext.current
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(ColorProvider(Color(0xFF14141E))) // Cosmic Dark
                .padding(12.dp)
        ) {
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "🌌 DAILY FOCUS",
                    style = TextStyle(
                        color = ColorProvider(Color(0xFF00E5FF)),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
                Spacer(modifier = GlanceModifier.defaultWeight())
                Text(
                    text = "🔥 $streak",
                    style = TextStyle(
                        color = ColorProvider(Color(0xFFFFB74D)),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
                Spacer(modifier = GlanceModifier.width(8.dp))
                Text(
                    text = "⟳",
                    modifier = GlanceModifier.clickable(actionRunCallback<RefreshAction>()),
                    style = TextStyle(color = ColorProvider(Color.Gray), fontSize = 16.sp)
                )
                
                Spacer(modifier = GlanceModifier.width(12.dp))
                
                Text(
                    text = "+",
                    modifier = GlanceModifier.clickable(androidx.glance.appwidget.action.actionStartActivity(android.content.Intent(context, com.cosmicocean.QuickAddActivity::class.java))),
                    style = TextStyle(color = ColorProvider(Color(0xFF00E5FF)), fontSize = 24.sp)
                )
            }

            Spacer(modifier = GlanceModifier.height(8.dp))

            if (tasks.isEmpty()) {
                Text(
                    text = "All clear. Add a task to get started.",
                    style = TextStyle(color = ColorProvider(Color.White), fontSize = 12.sp)
                )
            } else {
                tasks.forEach { task ->
                    TaskRow(task)
                }
            }
        }
    }

    @Composable
    private fun TaskRow(task: TaskItem) {
        val taskIdParam = ActionParameters.Key<String>("taskId")
        
        Row(
            modifier = GlanceModifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // "Checkbox" - Just a text circle for now
            Text(
                text = "○",
                modifier = GlanceModifier.clickable(
                    actionRunCallback<ToggleTaskAction>(
                        actionParametersOf(taskIdParam to task.id)
                    )
                ),
                style = TextStyle(color = ColorProvider(Color.White), fontSize = 18.sp)
            )
            
            Spacer(modifier = GlanceModifier.width(8.dp))
            
            Column(modifier = GlanceModifier.defaultWeight()) {
                Text(
                    text = task.title,
                    style = TextStyle(color = ColorProvider(Color.White), fontSize = 14.sp)
                )
                Text(
                    text = "~${task.estimate}",
                    style = TextStyle(color = ColorProvider(Color.Gray), fontSize = 10.sp)
                )
            }

            Spacer(modifier = GlanceModifier.width(8.dp))

            // "Not Today" Button
            Text(
                text = "Zzz",
                modifier = GlanceModifier.clickable(
                    actionRunCallback<SnoozeTaskAction>(
                        actionParametersOf(taskIdParam to task.id)
                    )
                ),
                style = TextStyle(color = ColorProvider(Color(0xFFB0BEC5)), fontSize = 12.sp)
            )
        }
    }

    data class TaskItem(
        val id: String, 
        val title: String, 
        val estimate: String?, 
        val priority: Int = 1, 
        val due_date: String? = null
    )

    private fun formatDue(dueDateMs: Long?): String? {
        if (dueDateMs == null) return "No due"
        val now = System.currentTimeMillis()
        val diffMinutes = TimeUnit.MILLISECONDS.toMinutes(dueDateMs - now)
        return when {
            diffMinutes < 0 -> "Overdue"
            diffMinutes < 60 -> "${diffMinutes}m"
            diffMinutes < 24 * 60 -> "${diffMinutes / 60}h"
            else -> "${diffMinutes / (24 * 60)}d"
        }
    }
}

class ToggleTaskAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val taskId = parameters[ActionParameters.Key<String>("taskId")]
        Log.d("TaskWidget", "Completing task: $taskId")

        try {
            if (taskId != null) {
                // Use updateTask with completed=true (completeTask endpoint doesn't exist)
                val response = NetworkModule.getApi(context).updateTask(
                    taskId,
                    mapOf(
                        "completed" to true,
                        "source" to "widget"
                    )
                )

                if (response.isSuccessful) {
                    Log.d("TaskWidget", "Task $taskId completed successfully")
                } else {
                    Log.e("TaskWidget", "Task completion failed: ${response.code()}")
                }
            }
            // Update widget UI
            TaskWidget().update(context, glanceId)
        } catch (e: Exception) {
            Log.e("TaskWidget", "Action failed", e)
        }
    }
}

class SnoozeTaskAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val taskId = parameters[ActionParameters.Key<String>("taskId")]
        Log.d("TaskWidget", "Snoozing task: $taskId")
        
        try {
            if (taskId != null) {
                NetworkModule.getApi(context).snoozeTask(taskId)
            }
            TaskWidget().update(context, glanceId)
        } catch (e: Exception) {
            Log.e("TaskWidget", "Snooze failed", e)
        }
    }
}

class RefreshAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        TaskWidget().update(context, glanceId)
    }
}

class TaskWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TaskWidget()
}
