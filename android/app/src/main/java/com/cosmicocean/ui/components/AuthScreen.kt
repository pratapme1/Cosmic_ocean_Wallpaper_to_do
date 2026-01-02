package com.cosmicocean.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

// Color palette - cosmic ocean theme
private val DeepSpace = Color(0xFF000814)
private val CosmicBlue = Color(0xFF00E5FF)
private val StarLight = Color(0xFF88D4F5)
private val DimStar = Color(0x4488D4F5)
private val ErrorRed = Color(0xFFFF3B30)

@Composable
fun AuthScreen(
    onLogin: (String, String) -> Unit,
    onRegister: (String, String) -> Unit,
    onSkipToGuest: () -> Unit,
    onForgotPassword: (String) -> Unit = {},
    errorMessage: String? = null
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isLoginMode by remember { mutableStateOf(true) }
    var showPassword by remember { mutableStateOf(false) }
    var showForgotPassword by remember { mutableStateOf(false) }

    // Subtle animation for the background
    val infiniteTransition = rememberInfiniteTransition(label = "bg_pulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha_pulse"
    )

    val focusManager = LocalFocusManager.current

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = DeepSpace
    ) {
        // Animated gradient background
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            CosmicBlue.copy(alpha = alpha * 0.1f),
                            DeepSpace
                        ),
                        radius = 1500f
                    )
                )
        )

        // Forgot Password Dialog
        if (showForgotPassword) {
            ForgotPasswordDialog(
                onDismiss = { showForgotPassword = false },
                onSubmit = { resetEmail ->
                    onForgotPassword(resetEmail)
                    showForgotPassword = false
                }
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Spacer(modifier = Modifier.weight(0.2f))

            // App Icon & Title
            AnimatedAppLogo()

            Spacer(modifier = Modifier.height(48.dp))

            // Mode Toggle (Login/Register)
            ModeToggle(
                isLoginMode = isLoginMode,
                onModeChange = {
                    isLoginMode = it
                    // Clear error when switching modes
                }
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Email Field
            ModernTextField(
                value = email,
                onValueChange = { email = it },
                label = "Email",
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Next
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Password Field
            ModernTextField(
                value = password,
                onValueChange = { password = it },
                label = "Password",
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done,
                visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    TextButton(onClick = { showPassword = !showPassword }) {
                        Text(
                            text = if (showPassword) "HIDE" else "SHOW",
                            color = StarLight,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                onDone = {
                    focusManager.clearFocus()
                    if (isLoginMode) onLogin(email, password) else onRegister(email, password)
                }
            )

            // Error Message
            AnimatedVisibility(
                visible = errorMessage != null,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Text(
                    text = errorMessage ?: "",
                    color = ErrorRed,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 16.dp)
                )
            }

            // Forgot Password Link (Login mode only)
            if (isLoginMode) {
                TextButton(
                    onClick = { showForgotPassword = true },
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    Text(
                        text = "Forgot password?",
                        color = DimStar,
                        fontSize = 14.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Primary Action Button
            PrimaryButton(
                text = if (isLoginMode) "Login" else "Create Account",
                enabled = email.isNotBlank() && password.isNotBlank(),
                onClick = {
                    focusManager.clearFocus()
                    if (isLoginMode) {
                        onLogin(email, password)
                    } else {
                        onRegister(email, password)
                    }
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Guest Mode Button
            TextButton(
                onClick = onSkipToGuest,
                modifier = Modifier.alpha(0.7f)
            ) {
                Text(
                    text = "Continue as Guest →",
                    color = Color.White,
                    fontSize = 14.sp
                )
            }

            Spacer(modifier = Modifier.weight(0.3f))

            // Password hint for register mode
            if (!isLoginMode) {
                Text(
                    text = "Password must be at least 8 characters",
                    fontSize = 12.sp,
                    color = DimStar,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 24.dp)
                )
            }
        }
    }
}

@Composable
private fun AnimatedAppLogo() {
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(100)
        visible = true
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(800)) + scaleIn(
            initialScale = 0.8f,
            animationSpec = tween(800, easing = FastOutSlowInEasing)
        )
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // App Icon (emoji placeholder - will be replaced with actual icon)
            Text(
                text = "✨",
                fontSize = 64.sp
            )

            Spacer(modifier = Modifier.height(16.dp))

            // App Name
            Text(
                text = "Cosmic Ocean",
                fontSize = 32.sp,
                fontWeight = FontWeight.Light,
                color = Color.White,
                letterSpacing = 1.sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Tagline
            Text(
                text = "The task app that disappears",
                fontSize = 14.sp,
                color = DimStar,
                letterSpacing = 0.5.sp
            )
        }
    }
}

@Composable
private fun ModeToggle(
    isLoginMode: Boolean,
    onModeChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .background(
                color = Color.White.copy(alpha = 0.05f),
                shape = RoundedCornerShape(24.dp)
            ),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        ModeButton(
            text = "Login",
            isSelected = isLoginMode,
            onClick = { onModeChange(true) },
            modifier = Modifier.weight(1f)
        )
        ModeButton(
            text = "Register",
            isSelected = !isLoginMode,
            onClick = { onModeChange(false) },
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun ModeButton(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        modifier = modifier
            .fillMaxHeight()
            .padding(4.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isSelected) CosmicBlue.copy(alpha = 0.2f) else Color.Transparent,
            contentColor = if (isSelected) CosmicBlue else DimStar
        ),
        shape = RoundedCornerShape(20.dp),
        elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp, 0.dp)
    ) {
        Text(
            text = text,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            fontSize = 16.sp
        )
    }
}

@Composable
private fun ModernTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    keyboardType: KeyboardType,
    imeAction: ImeAction,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailingIcon: @Composable (() -> Unit)? = null,
    onDone: () -> Unit = {}
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(
            keyboardType = keyboardType,
            imeAction = imeAction
        ),
        keyboardActions = KeyboardActions(
            onDone = { onDone() },
            onNext = { /* Focus moves automatically */ }
        ),
        visualTransformation = visualTransformation,
        trailingIcon = trailingIcon,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = CosmicBlue,
            unfocusedBorderColor = DimStar,
            focusedLabelColor = CosmicBlue,
            unfocusedLabelColor = DimStar,
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White,
            cursorColor = CosmicBlue
        ),
        singleLine = true
    )
}

@Composable
private fun PrimaryButton(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = CosmicBlue,
            disabledContainerColor = DimStar
        ),
        shape = RoundedCornerShape(28.dp),
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = 4.dp,
            pressedElevation = 8.dp,
            disabledElevation = 0.dp
        )
    ) {
        Text(
            text = text,
            color = DeepSpace,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp
        )
    }
}

@Composable
private fun ForgotPasswordDialog(
    onDismiss: () -> Unit,
    onSubmit: (String) -> Unit
) {
    var email by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Reset Password",
                color = Color.White
            )
        },
        text = {
            Column {
                Text(
                    text = "Enter your email address and we'll send you a link to reset your password.",
                    color = DimStar,
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CosmicBlue,
                        unfocusedBorderColor = DimStar,
                        focusedLabelColor = CosmicBlue,
                        unfocusedLabelColor = DimStar,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    )
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (email.isNotBlank()) onSubmit(email) },
                enabled = email.isNotBlank()
            ) {
                Text("Send Reset Link", color = CosmicBlue)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = DimStar)
            }
        },
        containerColor = Color(0xFF1A1A2E),
        shape = RoundedCornerShape(16.dp)
    )
}
