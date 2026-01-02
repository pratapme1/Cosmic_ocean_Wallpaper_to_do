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
import com.cosmicocean.network.NetworkModule
import kotlinx.coroutines.launch

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
        lifecycleScope.launch {
            try {
                // In real app, call API
                // val response = NetworkModule.api.createTask(title)
                Toast.makeText(this@QuickAddActivity, "Task added!", Toast.LENGTH_SHORT).show()
                finish()
            } catch (e: Exception) {
                Toast.makeText(this@QuickAddActivity, "Failed to add task", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }
}
