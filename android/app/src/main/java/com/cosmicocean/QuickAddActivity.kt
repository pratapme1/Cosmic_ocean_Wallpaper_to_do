package com.cosmicocean

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.cosmicocean.data.CosmicDatabase
import com.cosmicocean.data.TaskRepository
import com.cosmicocean.model.Star
import com.cosmicocean.network.NetworkModule
import kotlinx.coroutines.launch
import java.util.Random

class QuickAddActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setContent {
            var text by remember { mutableStateOf("") }
            
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xFF14141E),
                    modifier = Modifier.padding(24.dp).fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "New Task",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        TextField(
                            value = text,
                            onValueChange = { text = it },
                            placeholder = { Text("e.g. Call mom 10m", color = Color.Gray) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color(0xFF1E1E2A),
                                unfocusedContainerColor = Color(0xFF1E1E2A),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            )
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            TextButton(onClick = { finish() }) {
                                Text("Cancel", color = Color.Gray)
                            }
                            Button(onClick = {
                                if (text.isNotBlank()) {
                                    saveTask(text)
                                }
                            }) {
                                Text("Add")
                            }
                        }
                    }
                }
            }
        }
    }

    private fun saveTask(title: String) {
        val database = CosmicDatabase.getDatabase(this)
        val repository = TaskRepository(database.starDao(), NetworkModule.getApi(this), applicationContext)
        
        lifecycleScope.launch {
            try {
                // Calculate random position (similar to MainActivity)
                val random = Random()
                val displayMetrics = resources.displayMetrics
                val screenWidth = displayMetrics.widthPixels.toFloat()
                val screenHeight = displayMetrics.heightPixels.toFloat()
                
                val horizontalPadding = screenWidth * 0.15f
                val verticalPadding = screenHeight * 0.1f
                
                val x = horizontalPadding + random.nextFloat() * (screenWidth - 2 * horizontalPadding)
                val y = verticalPadding + random.nextFloat() * (screenHeight - 2 * verticalPadding)
                
                // Create star
                val star = Star(x, y, title, 2, null)
                
                // Save to local DB and sync to backend
                repository.addStar(star)
                
                Toast.makeText(this@QuickAddActivity, "Task added to Cosmic Ocean", Toast.LENGTH_SHORT).show()
                finish()
            } catch (e: Exception) {
                android.util.Log.e("QuickAddActivity", "Error adding task", e)
                Toast.makeText(this@QuickAddActivity, "Failed to add task: ${e.message}", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }
}
