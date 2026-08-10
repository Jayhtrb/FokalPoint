package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.ui.components.GlassCard
import com.example.ui.viewmodel.AuthViewModel
import com.example.ui.viewmodel.AuthState

@Composable
fun AuthScreen(
    navController: NavController,
    viewModel: AuthViewModel = viewModel()
) {
    val authState by viewModel.authState.collectAsState()
    var isLogin by remember { mutableStateOf(true) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var userRole by remember { mutableStateOf("customer") }
    var isLoading by remember { mutableStateOf(false) }
    var showError by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    var showOTPInput by remember { mutableStateOf(false) }
    var otpCode by remember { mutableStateOf("") }
    
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    // Handle deep link authentication
    LaunchedEffect(Unit) {
        viewModel.handleDeepLink(context)
    }
    
    // Handle auth state changes
    LaunchedEffect(authState) {
        when (authState) {
            is AuthState.Authenticated -> {
                isLoading = false
                // Navigate to appropriate dashboard
                navController.navigate("dashboard") {
                    popUpTo("auth") { inclusive = true }
                }
            }
            is AuthState.Error -> {
                isLoading = false
                showError = true
                errorMessage = (authState as AuthState.Error).message
            }
            is AuthState.Loading -> {
                isLoading = true
            }
            else -> { /* Idle or Unauthenticated */ }
        }
    }
    
    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary,
                            MaterialTheme.colorScheme.background
                        )
                    )
                )
        ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Logo & Brand
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .background(
                            color = Color.White.copy(alpha = 0.1f),
                            shape = RoundedCornerShape(20.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "📸",
                        style = MaterialTheme.typography.displayLarge
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    "FokalPoint",
                    style = MaterialTheme.typography.headlineLarge,
                    color = Color.White
                )
                Text(
                    "Every Moment in Focus",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.7f)
                )
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // Auth Card
            GlassCard(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp)
                ) {
                    // Toggle Login/Signup
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        AuthToggleButton(
                            text = "Sign In",
                            selected = isLogin,
                            onClick = { 
                                isLogin = true
                                showError = false
                            }
                        )
                        AuthToggleButton(
                            text = "Sign Up",
                            selected = !isLogin,
                            onClick = { 
                                isLogin = false
                                showError = false
                            }
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    // OAuth Providers
                    OAuthProvidersSection(
                        isLoading = isLoading,
                        onGoogleSignIn = {
                            isLoading = true
                            viewModel.signInWithGoogle(context)
                        },
                        onGitHubSignIn = {
                            isLoading = true
                            viewModel.signInWithGitHub(context)
                        }
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Divider
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        HorizontalDivider(
                            modifier = Modifier.weight(1f),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                        )
                        Text(
                            text = " or continue with email ",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                        HorizontalDivider(
                            modifier = Modifier.weight(1f),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Email Field
                    OutlinedTextField(
                        value = email,
                        onValueChange = { 
                            email = it
                            showError = false
                        },
                        label = { Text("Email") },
                        placeholder = { Text("you@example.com") },
                        leadingIcon = { Icon(Icons.Outlined.Email, contentDescription = null) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        isError = showError && errorMessage.contains("email", ignoreCase = true),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color.White,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
                            cursorColor = Color.White,
                            focusedLabelColor = Color.White,
                            unfocusedLabelColor = Color.White.copy(alpha = 0.5f)
                        )
                    )
                    
                    // Name Field (Sign Up only)
                    if (!isLogin) {
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = name,
                            onValueChange = { 
                                name = it
                                showError = false
                            },
                            label = { Text("Full Name") },
                            placeholder = { Text("John Doe") },
                            leadingIcon = { Icon(Icons.Outlined.Person, contentDescription = null) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color.White,
                                unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
                                cursorColor = Color.White,
                                focusedLabelColor = Color.White,
                                unfocusedLabelColor = Color.White.copy(alpha = 0.5f)
                            )
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    // Password Field
                    var passwordVisible by remember { mutableStateOf(false) }
                    
                    OutlinedTextField(
                        value = password,
                        onValueChange = { 
                            password = it
                            showError = false
                        },
                        label = { Text("Password") },
                        placeholder = { Text("••••••••") },
                        leadingIcon = { Icon(Icons.Outlined.Lock, contentDescription = null) },
                        trailingIcon = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(
                                    if (passwordVisible) 
                                        Icons.Outlined.VisibilityOff 
                                    else 
                                        Icons.Outlined.Visibility,
                                    contentDescription = null
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        visualTransformation = if (passwordVisible) 
                            VisualTransformation.None 
                        else 
                            PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        isError = showError && errorMessage.contains("password", ignoreCase = true),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color.White,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
                            cursorColor = Color.White,
                            focusedLabelColor = Color.White,
                            unfocusedLabelColor = Color.White.copy(alpha = 0.5f)
                        )
                    )
                    
                    // User Role (Sign Up only)
                    if (!isLogin) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            RoleChip(
                                label = "👤 Customer",
                                selected = userRole == "customer",
                                onClick = { userRole = "customer" }
                            )
                            RoleChip(
                                label = "📸 Creator",
                                selected = userRole == "creator",
                                onClick = { userRole = "creator" }
                            )
                        }
                    }
                    
                    // OTP Input (if enabled)
                    if (showOTPInput) {
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = otpCode,
                            onValueChange = { 
                                if (it.length <= 6 && it.all { char -> char.isDigit() }) {
                                    otpCode = it
                                }
                            },
                            label = { Text("Enter OTP") },
                            placeholder = { Text("123456") },
                            leadingIcon = { Icon(Icons.Outlined.Verified, contentDescription = null) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color.White,
                                unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
                                cursorColor = Color.White,
                                focusedLabelColor = Color.White,
                                unfocusedLabelColor = Color.White.copy(alpha = 0.5f)
                            )
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Error Message
                    if (showError) {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Outlined.Error,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    errorMessage,
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    
                    // Submit Button
                    Button(
                        onClick = {
                            isLoading = true
                            if (isLogin) {
                                viewModel.signInWithEmail(email, password)
                            } else {
                                if (showOTPInput) {
                                    viewModel.verifyOTP(otpCode)
                                } else {
                                    viewModel.signUpWithEmail(
                                        email = email,
                                        password = password,
                                        name = name,
                                        role = userRole
                                    )
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        enabled = !isLoading && 
                            email.isNotBlank() && 
                            password.isNotBlank() && 
                            (isLogin || name.isNotBlank())
                    ) {
                        when {
                            isLoading -> {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    color = Color.White
                                )
                            }
                            showOTPInput -> {
                                Text("Verify OTP")
                            }
                            isLogin -> {
                                Text("Sign In")
                            }
                            else -> {
                                Text("Create Account")
                            }
                        }
                    }
                    
                    // Forgot Password / OTP Link
                    if (isLogin) {
                        Spacer(modifier = Modifier.height(8.dp))
                        TextButton(
                            onClick = {
                                // Show forgot password dialog
                            },
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Text(
                                "Forgot Password?",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            }
        }
    }
    
    if (authState is AuthState.Loading) {
        AuthLoadingScreen()
    }
}
}

@Composable
fun RowScope.AuthToggleButton(
    text: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (selected) 
                Color.White.copy(alpha = 0.2f) 
            else 
                Color.Transparent,
            contentColor = if (selected) 
                Color.White 
            else 
                Color.White.copy(alpha = 0.6f)
        ),
        modifier = Modifier.weight(1f),
        shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)
    ) {
        Text(text)
    }
}

@Composable
fun OAuthProvidersSection(
    isLoading: Boolean,
    onGoogleSignIn: () -> Unit,
    onGitHubSignIn: () -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Google Sign In Button
        OAuthButton(
            icon = Icons.Outlined.Email,
            label = "Continue with Google",
            backgroundColor = Color(0xFFEA4335),
            onClick = onGoogleSignIn,
            isLoading = isLoading
        )
        
        // GitHub Sign In Button
        OAuthButton(
            icon = Icons.Outlined.Code,
            label = "Continue with GitHub",
            backgroundColor = Color(0xFF24292E),
            onClick = onGitHubSignIn,
            isLoading = isLoading
        )
    }
}

@Composable
fun OAuthButton(
    icon: ImageVector,
    label: String,
    backgroundColor: Color,
    onClick: () -> Unit,
    isLoading: Boolean
) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = backgroundColor
        ),
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp),
        enabled = !isLoading
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                color = Color.White
            )
        } else {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(label, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
fun RowScope.RoleChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { 
            Text(
                label,
                style = MaterialTheme.typography.bodySmall,
                color = if (selected) Color.White else Color.White.copy(alpha = 0.7f)
            )
        },
        modifier = Modifier.weight(1f),
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = Color.White.copy(alpha = 0.2f),
            selectedLabelColor = Color.White,
            disabledSelectedContainerColor = Color.White.copy(alpha = 0.1f)
        )
    )
}
