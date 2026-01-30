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

import androidx.compose.runtime.collectAsState
import com.cosmicocean.viewmodel.QuickAddViewModel

class QuickAddActivity : ComponentActivity() {
    private lateinit var viewModel: QuickAddViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Manual DI for simplicity in this project
        val database = CosmicDatabase.getDatabase(this)
        val repository = TaskRepository(database.starDao(), NetworkModule.getApi(this), applicationContext)
        viewModel = QuickAddViewModel(repository)
        
        setContent {
            var text by remember { mutableStateOf("") }
            val uiState by viewModel.uiState.collectAsState()
            
            // Handle success/error side-effects
            LaunchedEffect(uiState.isSuccess) {
                if (uiState.isSuccess) {
                    Toast.makeText(this@QuickAddActivity, "Task added to Cosmic Ocean", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
            
            LaunchedEffect(uiState.errorMessage) {
                uiState.errorMessage?.let {
                    Toast.makeText(this@QuickAddActivity, "Failed: $it", Toast.LENGTH_SHORT).show()
                }
            }
            
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
                            enabled = !uiState.isSaving,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color(0xFF1E1E2A),
                                unfocusedContainerColor = Color(0xFF1E1E2A),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            )
                        )
                        if (uiState.isSaving) {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            TextButton(onClick = { finish() }, enabled = !uiState.isSaving) {
                                Text("Cancel", color = Color.Gray)
                            }
                            Button(
                                onClick = {
                                    if (text.isNotBlank()) {
                                        val displayMetrics = resources.displayMetrics
                                        viewModel.addTask(
                                            text, 
                                            displayMetrics.widthPixels.toFloat(), 
                                            displayMetrics.heightPixels.toFloat()
                                        )
                                    }
                                },
                                enabled = !uiState.isSaving
                            ) {
                                Text(if (uiState.isSaving) "Adding..." else "Add")
                            }
                        }
                    }
                }
            }
        }
    }
}
