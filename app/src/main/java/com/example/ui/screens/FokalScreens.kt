@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.animation.ExperimentalSharedTransitionApi::class
)
package com.example.ui.screens

import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.R
import com.example.data.model.*
import com.example.ui.theme.*
import com.example.ui.viewmodel.FokalViewModel
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

val LocalSharedTransitionScope = compositionLocalOf<SharedTransitionScope?> { null }
val LocalAnimatedVisibilityScope = compositionLocalOf<AnimatedVisibilityScope?> { null }


// ----------------------------------------------------
// MAIN APP COMPOSABLE COORDINATOR
// ----------------------------------------------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FokalAppContent(viewModel: FokalViewModel) {
    val context = LocalContext.current
    val currentRole by viewModel.currentUserRole.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    
    // Simple state-based navigation
    var currentScreen by remember { mutableStateOf("customer_main") }
    var hasRoutedInitially by remember { mutableStateOf(false) }

    LaunchedEffect(currentRole) {
        if (!hasRoutedInitially && currentRole.isNotEmpty()) {
            if (currentRole.equals("Creator", ignoreCase = true)) {
                currentScreen = "creator_main"
                hasRoutedInitially = true
            } else if (currentRole.equals("Customer", ignoreCase = true)) {
                currentScreen = "customer_main"
                hasRoutedInitially = true
            }
        }
    }
    
    // Email dispatch telemetry listeners
    var activeEmailNotification by remember { mutableStateOf<com.example.data.network.EmailEvent.Success?>(null) }
    
    LaunchedEffect(Unit) {
        com.example.data.network.EmailNotificationService.emailEvents.collect { event ->
            when (event) {
                is com.example.data.network.EmailEvent.Success -> {
                    activeEmailNotification = event
                }
                is com.example.data.network.EmailEvent.Failure -> {
                    Toast.makeText(context, "❌ Email dispatch failed: ${event.error}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
    // Backstack for detail and checkout screens
    val backStack = remember { mutableStateListOf<String>() }
    
    val selectedCreatorId by viewModel.selectedCreatorId.collectAsStateWithLifecycle()
    val selectedChatCreatorId by viewModel.selectedChatCreatorId.collectAsStateWithLifecycle()
    var activeReviewBookingId by remember { mutableStateOf<Long?>(null) }
    var activeReviewCreatorId by remember { mutableStateOf<String?>(null) }
    
    fun navigateTo(screen: String) {
        if (currentScreen != screen) {
            backStack.add(currentScreen)
            currentScreen = screen
        }
    }
    
    fun goBack() {
        if (backStack.isNotEmpty()) {
            currentScreen = backStack.removeLast()
        } else {
            currentScreen = "auth"
        }
    }

    BackHandler(enabled = currentScreen != "auth" && currentScreen != "onboarding" && currentScreen != "customer_main" && currentScreen != "creator_main") {
        goBack()
    }

    Scaffold(
        modifier = Modifier.fillMaxSize().testTag("app_scaffold"),
        contentWindowInsets = WindowInsets.safeDrawing
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            SharedTransitionLayout {
                AnimatedContent(
                    targetState = currentScreen,
                    transitionSpec = {
                        fadeIn(animationSpec = tween(220)) togetherWith fadeOut(animationSpec = tween(220))
                    },
                    label = "ScreenTransition"
                ) { screenState ->
                    CompositionLocalProvider(
                        LocalSharedTransitionScope provides this@SharedTransitionLayout,
                        LocalAnimatedVisibilityScope provides this@AnimatedContent
                    ) {
                        when (screenState) {
                            "auth" -> {
                                val localNavController = androidx.navigation.compose.rememberNavController()
                                val authViewModel: com.example.ui.viewmodel.AuthViewModel = viewModel()
                                AuthScreen(
                                    navController = localNavController,
                                    viewModel = authViewModel
                                )
                                LaunchedEffect(localNavController) {
                                    localNavController.addOnDestinationChangedListener { _, destination, _ ->
                                        if (destination.route == "dashboard") {
                                            val role = authViewModel.user.value?.role ?: "Customer"
                                            viewModel.currentUserRole.value = role
                                            if (role.equals("Customer", ignoreCase = true)) {
                                                currentScreen = "customer_main"
                                            } else {
                                                currentScreen = "creator_main"
                                            }
                                            backStack.clear()
                                        }
                                    }
                                }
                            }
                            "onboarding" -> OnboardingScreen(
                                viewModel = viewModel,
                                onComplete = { role ->
                                    if (role == "Customer") {
                                        currentScreen = "customer_main"
                                    } else {
                                        currentScreen = "creator_main"
                                    }
                                    backStack.clear()
                                }
                            )
                            "customer_main" -> CustomerMainScreen(
                                viewModel = viewModel,
                                onNavigateToCreatorDetail = { creatorId ->
                                    viewModel.selectedCreatorId.value = creatorId
                                    navigateTo("creator_detail")
                                },
                                onNavigateToChat = { creatorId ->
                                    viewModel.selectedChatCreatorId.value = creatorId
                                    navigateTo("chat_detail")
                                },
                                onNavigateToReview = { bookingId, creatorId ->
                                    activeReviewBookingId = bookingId
                                    activeReviewCreatorId = creatorId
                                    navigateTo("review_system")
                                },
                                onNavigateToPostShootAlert = {
                                    navigateTo("post_shoot_alert")
                                }
                            )
                            "creator_main" -> CreatorMainScreen(
                                viewModel = viewModel,
                                onLogout = {
                                    viewModel.switchRole("Customer")
                                    currentScreen = "onboarding"
                                    backStack.clear()
                                },
                                onNavigateToChat = { partnerId ->
                                    viewModel.selectedChatCreatorId.value = partnerId
                                    navigateTo("chat_detail")
                                },
                                onNavigateToPayoutMethods = {
                                    navigateTo("manage_payout_methods")
                                }
                            )
                            "creator_detail" -> {
                                val creatorId = selectedCreatorId ?: "amit_sharma_creator"
                                CreatorDetailScreen(
                                    creatorId = creatorId,
                                    viewModel = viewModel,
                                    onBack = { goBack() },
                                    onBookNow = { navigateTo("booking_scheduler") },
                                    onNavigateToChat = {
                                        viewModel.selectedChatCreatorId.value = creatorId
                                        navigateTo("chat_detail")
                                    }
                                )
                            }
                            "booking_scheduler" -> {
                                BookingSchedulerScreen(
                                    viewModel = viewModel,
                                    onBack = { goBack() },
                                    onProceedToCheckout = { navigateTo("checkout") }
                                )
                            }
                            "checkout" -> {
                                CheckoutScreen(
                                    viewModel = viewModel,
                                    onBack = { goBack() },
                                    onSuccess = {
                                        currentScreen = "customer_main"
                                        backStack.clear()
                                        Toast.makeText(context, "Booking successfully confirmed!", Toast.LENGTH_LONG).show()
                                    }
                                )
                            }
                            "chat_detail" -> {
                                val partnerId = selectedChatCreatorId ?: "riya_sen_creator"
                                ChatDetailScreen(
                                    partnerId = partnerId,
                                    viewModel = viewModel,
                                    onBack = { goBack() }
                                )
                            }
                            "manage_payout_methods" -> {
                                ManagePayoutMethods(
                                    viewModel = viewModel,
                                    onBack = { goBack() }
                                )
                            }
                            "post_shoot_alert" -> {
                                PostShootAlertScreen(
                                    viewModel = viewModel,
                                    onBack = { goBack() }
                                )
                            }
                            "review_system" -> {
                                ReviewSystem(
                                    bookingId = activeReviewBookingId ?: 0L,
                                    creatorId = activeReviewCreatorId ?: "",
                                    onReviewSubmitted = { goBack() },
                                    viewModel = viewModel
                                )
                            }
                        }
                    }
                }
            }

            // FLOATING EMAIL DELIVERY TELEMETRY BANNER (M3 POLISHED)
            activeEmailNotification?.let { email ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    contentAlignment = androidx.compose.ui.Alignment.BottomCenter
                ) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 24.dp)
                            .testTag("floating_email_telemetry"),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 12.dp),
                        border = BorderStroke(1.dp, AmberGold),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Email,
                                        contentDescription = "Email",
                                        tint = AmberGold,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Automated Email Sent",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = AmberGold
                                    )
                                }
                                
                                // Channel Badge
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFF4CAF50).copy(alpha = 0.15f)),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = email.provider.uppercase(),
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        fontSize = 9.sp,
                                        color = Color(0xFF4CAF50),
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(10.dp))
                            
                            Text(
                                text = "Recipient: ${email.recipient}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Subject: ${email.subject}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            // HTML Raw preview without html tags for visibility
                            val textPreview = remember(email.body) {
                                email.body.replace(Regex("<[^>]*>"), " ")
                                    .replace(Regex("\\s+"), " ")
                                    .trim()
                                    .take(180) + "..."
                            }
                            Text(
                                text = textPreview,
                                fontSize = 10.sp,
                                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                Button(
                                    onClick = { activeEmailNotification = null },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                    modifier = Modifier.height(32.dp),
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp)
                                ) {
                                    Text("Dismiss", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ----------------------------------------------------
// ONBOARDING & REGISTRATION SCREEN
// ----------------------------------------------------
@Composable
fun OnboardingScreen(
    viewModel: FokalViewModel,
    onComplete: (String) -> Unit
) {
    var registerStep by remember { mutableIntStateOf(1) } // 1: Who are you, 2: Creator specification
    var isLoginMode by remember { mutableStateOf(false) } // False = Signup, True = Login
    var chosenRole by remember { mutableStateOf("Customer") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var creatorType by remember { mutableStateOf("Photography") } // Photographer, Videographer, Both
    var experienceLevel by remember { mutableStateOf("Professional") } // Beginner, Professional, Studio
    var fullName by remember { mutableStateOf("") }
    var userCity by remember { mutableStateOf("Mumbai") }
    
    var yearsOfExperience by remember { mutableIntStateOf(4) }
    var instagramInput by remember { mutableStateOf("") }
    var websiteInput by remember { mutableStateOf("") }
    var youtubeInput by remember { mutableStateOf("") }
    val selectedSkills = remember { mutableStateListOf("Photographer") }
    
    val cities = listOf("Mumbai", "Bengaluru", "Delhi", "Goa", "Jaipur")
    val scope = rememberCoroutineScope()

    val context = LocalContext.current
    var showAccountChooser by remember { mutableStateOf(false) }
    var selectedSocialProvider by remember { mutableStateOf("") } // "Google" or "GitHub"

    // Retrieve active auth message from ViewModel to notify authentication completes
    val authMsgState = viewModel.authMessage.collectAsStateWithLifecycle()
    val authLoadingState = viewModel.authLoading.collectAsStateWithLifecycle()
    val currentUserIdState = viewModel.currentUserId.collectAsStateWithLifecycle()
    val currentUserRoleState = viewModel.currentUserRole.collectAsStateWithLifecycle()

    LaunchedEffect(authMsgState.value) {
        authMsgState.value?.let { msg ->
            android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
            viewModel.authMessage.value = null // reset
        }
    }

    LaunchedEffect(currentUserIdState.value) {
        val currentId = currentUserIdState.value
        // Automatically proceed if successfully authenticated with a real custom user ID
        if (currentId.isNotEmpty() && currentId != "current_customer_test" && currentId != "amit_sharma_creator" && !currentId.contains("test")) {
            onComplete(currentUserRoleState.value)
        }
    }

    val handleSocialClick = { provider: String ->
        selectedSocialProvider = provider
        showAccountChooser = true
    }

    if (showAccountChooser) {
        val accounts = if (selectedSocialProvider == "Google") {
            listOf(
                Triple("google_vikram_sen", "Vikram Sen", "vikram.sen@gmail.com"),
                Triple("google_neha_patil", "Neha Patil", "neha.patil@gmail.com")
            )
        } else {
            listOf(
                Triple("github_git_sharma", "git_sharma", "sharma.git@github.com"),
                Triple("github_creative_lens", "creative_lens", "lens.coder@github.com")
            )
        }
        
        AlertDialog(
            onDismissRequest = { showAccountChooser = false },
            title = {
                Text(
                    text = "Sign in with $selectedSocialProvider",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(vertical = 8.dp)
                ) {
                    Text(
                        text = "Choose how you want to continue to FokalPoint:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    Text(
                        text = "Option A: Instant Sandbox Profile (Recommended)",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = AmberGold
                    )
                    
                    accounts.forEach { (id, name, email) ->
                        val avatarUrl = when (id) {
                            "google_vikram_sen" -> "https://images.unsplash.com/photo-1534528741775-53994a69daeb?auto=format&fit=crop&w=150&q=80"
                            "google_neha_patil" -> "https://images.unsplash.com/photo-1544005313-94ddf0286df2?auto=format&fit=crop&w=150&q=80"
                            "github_git_sharma" -> "https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?auto=format&fit=crop&w=150&q=80"
                            else -> "https://images.unsplash.com/photo-1500648767791-00dcc994a43e?auto=format&fit=crop&w=150&q=80"
                        }
                        
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showAccountChooser = false
                                    viewModel.loginOrSignUpSocialUser(
                                        id = id,
                                        name = name,
                                        email = email,
                                        profileImage = avatarUrl,
                                        initialRole = chosenRole
                                    )
                                    onComplete(chosenRole)
                                }
                                .testTag("account_item_$id"),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                AsyncImage(
                                    model = avatarUrl,
                                    contentDescription = "$name Avatar",
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape),
                                    contentScale = ContentScale.Crop
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = name,
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Text(
                                        text = email,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                    
                    Divider(
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f),
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                    
                    Text(
                        text = "Option B: Live Browser OAuth (Supabase)",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    
                    OutlinedButton(
                        onClick = {
                            showAccountChooser = false
                            val url = com.example.BuildConfig.SUPABASE_URL
                            if (url.isNotEmpty() && !url.contains("placeholder") && url.startsWith("http")) {
                                val redirectUrl = "fokalpoint://login-callback"
                                val authUrl = "$url/auth/v1/authorize?provider=${selectedSocialProvider.lowercase()}&redirect_to=$redirectUrl"
                                try {
                                    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(authUrl))
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    android.widget.Toast.makeText(context, "No browser detected. Please use the Instant Sandbox options.", android.widget.Toast.LENGTH_LONG).show()
                                }
                            } else {
                                android.widget.Toast.makeText(context, "Supabase URL is not configured or is placeholder. Please use Instant Sandbox.", android.widget.Toast.LENGTH_LONG).show()
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Language,
                            contentDescription = null,
                            tint = AmberGold,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Launch Live OAuth Browser",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showAccountChooser = false }) {
                    Text("Cancel", color = AmberGold)
                }
            }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth().widthIn(max = 480.dp)
        ) {
            Spacer(modifier = Modifier.height(20.dp))
            
            // App Branding Icon Section
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Brush.radialGradient(listOf(AmberGold, Color.Transparent)))
                    .border(2.dp, AmberGold, RoundedCornerShape(24.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Camera,
                    contentDescription = "App Logo Logo",
                    tint = TextPrimaryDark,
                    modifier = Modifier.size(44.dp)
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "FokalPoint",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.primary,
                letterSpacing = 1.sp
            )
            Text(
                text = "Every Moment in Focus",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
            )
            
            Spacer(modifier = Modifier.height(30.dp))

            if (registerStep == 1) {
                Text(
                    text = "Welcome to FokalPoint!",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "Let's personalize your creative setup.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, bottom = 20.dp)
                )

                // Modern Custom Segmented Control for Sign Up vs Log In Toggle
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (!isLoginMode) AmberGold else Color.Transparent)
                            .clickable { isLoginMode = false }
                            .testTag("tab_signup"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "New Account",
                            fontWeight = FontWeight.Bold,
                            color = if (!isLoginMode) Color.Black else MaterialTheme.colorScheme.onSurface,
                            fontSize = 13.sp
                        )
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isLoginMode) AmberGold else Color.Transparent)
                            .clickable { isLoginMode = true }
                            .testTag("tab_login"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Existing Sign In",
                            fontWeight = FontWeight.Bold,
                            color = if (isLoginMode) Color.Black else MaterialTheme.colorScheme.onSurface,
                            fontSize = 13.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Social Logins Section
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 20.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "Instant Sign In",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = "Access your FokalPoint account instantly using secure social authentication.",
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                        
                        if (authLoadingState.value) {
                            CircularProgressIndicator(color = AmberGold, modifier = Modifier.size(32.dp))
                        } else {
                            // Google Authenticator Button
                            Button(
                                onClick = { handleSocialClick("Google") },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(46.dp)
                                    .testTag("btn_social_login_google"),
                                colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, Color(0xFFE0E0E0))
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    AsyncImage(
                                        model = "https://img.icons8.com/color/48/google-logo.png",
                                        contentDescription = "Google Icon",
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = "Continue with Google",
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 13.sp,
                                        color = Color(0xFF2C2C2C)
                                    )
                                }
                            }

                            // GitHub Authenticator Button
                            Button(
                                onClick = { handleSocialClick("GitHub") },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(46.dp)
                                    .testTag("btn_social_login_github"),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF181717)),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    AsyncImage(
                                        model = "https://img.icons8.com/color/48/ffffff/github.png",
                                        contentDescription = "GitHub Icon",
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = "Continue with GitHub",
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 13.sp,
                                        color = Color.White
                                    )
                                }
                            }
                        }
                    }
                }
                
                // OR Divider
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Divider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                    Text(
                        text = " OR USE EMAIL SECURELY ",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                    Divider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                }

                // Full Name Input (Only on Sign Up Mode)
                if (!isLoginMode) {
                    OutlinedTextField(
                        value = fullName,
                        onValueChange = { fullName = it },
                        label = { Text("Your Complete Name") },
                        placeholder = { Text("e.g. Ananya Rao") },
                        leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, tint = AmberGold) },
                        modifier = Modifier.fillMaxWidth().testTag("input_full_name"),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AmberGold,
                            unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
                        )
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }

                // Email Input field
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email Address") },
                    placeholder = { Text("e.g. creative@fokalpoint.com") },
                    leadingIcon = { Icon(Icons.Default.Email, contentDescription = null, tint = AmberGold) },
                    modifier = Modifier.fillMaxWidth().testTag("input_email"),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AmberGold,
                        unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
                    )
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Password input field
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    placeholder = { Text("Min 6 characters") },
                    leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = AmberGold) },
                    trailingIcon = {
                        val image = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff
                        val description = if (passwordVisible) "Hide password" else "Show password"
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(imageVector = image, contentDescription = description, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    },
                    modifier = Modifier.fillMaxWidth().testTag("input_password"),
                    singleLine = true,
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AmberGold,
                        unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
                    )
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Customer vs Creator Role Question Card Setup (Only on Sign Up Mode)
                if (!isLoginMode) {
                    Text(
                        text = "Choose Your Role",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Card(
                            modifier = Modifier
                                .weight(1f)
                                .height(110.dp)
                                .clickable { chosenRole = "Customer" }
                                .border(
                                    width = if (chosenRole == "Customer") 2.dp else 1.dp,
                                    color = if (chosenRole == "Customer") AmberGold else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                                    shape = RoundedCornerShape(16.dp)
                                ),
                            colors = CardDefaults.cardColors(
                                containerColor = if (chosenRole == "Customer") MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface
                            ),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Column(
                                modifier = Modifier.fillMaxSize().padding(12.dp),
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Groups,
                                    contentDescription = null,
                                    tint = if (chosenRole == "Customer") AmberGold else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(28.dp)
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text("Customer", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text("Hire Creatives", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }

                        Card(
                            modifier = Modifier
                                .weight(1f)
                                .height(110.dp)
                                .clickable { chosenRole = "Creator" }
                                .border(
                                    width = if (chosenRole == "Creator") 2.dp else 1.dp,
                                    color = if (chosenRole == "Creator") AmberGold else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                                    shape = RoundedCornerShape(16.dp)
                                ),
                            colors = CardDefaults.cardColors(
                                containerColor = if (chosenRole == "Creator") MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface
                            ),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Column(
                                modifier = Modifier.fillMaxSize().padding(12.dp),
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PhotoCamera,
                                    contentDescription = null,
                                    tint = if (chosenRole == "Creator") AmberGold else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(28.dp)
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text("Creator", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text("Book Shoots", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Location city dropdown choices list
                    Text(
                        text = "Primary Location",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                    )

                    var customLocationMode by remember { mutableStateOf(false) }
                    var customLocationInput by remember { mutableStateOf("") }

                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        cities.forEach { city ->
                            val isSelected = !customLocationMode && userCity == city
                            FilterChip(
                                selected = isSelected,
                                onClick = { 
                                    customLocationMode = false
                                    userCity = city 
                                },
                                label = { Text(city) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = AmberGold,
                                    selectedLabelColor = Color.Black
                                )
                            )
                        }
                        
                        FilterChip(
                            selected = customLocationMode,
                            onClick = { 
                                customLocationMode = true
                                if (customLocationInput.isNotEmpty()) {
                                    userCity = customLocationInput
                                }
                            },
                            label = { Text("Other (Custom) 🌍") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = AmberGold,
                                selectedLabelColor = Color.Black
                            )
                        )
                    }

                    if (customLocationMode) {
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = customLocationInput,
                            onValueChange = { 
                                customLocationInput = it
                                userCity = it
                            },
                            placeholder = { Text("e.g. Paris, France; London, UK; Mumbai, MH") },
                            label = { Text("Worldwide Location") },
                            leadingIcon = { Icon(Icons.Default.Place, contentDescription = null, tint = AmberGold) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("onboarding_custom_location_input"),
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AmberGold)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                // Main CTA trigger
                Button(
                    onClick = {
                        if (email.trim().isEmpty() || password.trim().isEmpty()) {
                            android.widget.Toast.makeText(context, "Email and password are required", android.widget.Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        if (password.length < 6) {
                            android.widget.Toast.makeText(context, "Password should be at least 6 characters", android.widget.Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        
                        if (isLoginMode) {
                            viewModel.signInWithEmailAndPassword(email.trim(), password.trim(), chosenRole)
                        } else {
                            val displayName = fullName.trim().ifEmpty { email.substringBefore("@") }
                            if (chosenRole == "Creator") {
                                // Creator onboarding Step 2 gathers specialization metadata BEFORE submitting signup to Supabase
                                registerStep = 2
                            } else {
                                viewModel.signUpWithEmailAndPassword(email.trim(), password.trim(), displayName, "Customer", userCity)
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp).testTag("button_onboarding_next"),
                    colors = ButtonDefaults.buttonColors(containerColor = AmberGold)
                ) {
                    if (authLoadingState.value) {
                        CircularProgressIndicator(color = Color.Black, modifier = Modifier.size(24.dp))
                    } else {
                        Text(
                            text = if (isLoginMode) "Log In to FokalPoint" else if (chosenRole == "Creator") "Continue Setup" else "Explore FokalPoint",
                            fontWeight = FontWeight.Bold,
                            color = Color.Black,
                            fontSize = 16.sp
                        )
                    }
                }

                // Guest Entrance Flow Indicator
                Spacer(modifier = Modifier.height(16.dp))
                
                TextButton(
                    onClick = {
                        viewModel.switchRole(chosenRole)
                        onComplete(chosenRole)
                    },
                    modifier = Modifier.testTag("button_onboarding_guest")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Explore as Guest (${chosenRole})",
                            color = AmberGold,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Default.ArrowForward,
                            contentDescription = "Guest explore",
                            tint = AmberGold,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

            } else {
                // Creator specifications Details (Step 2 of Creator Sign Up)
                Text(
                    text = "Creator Style Details",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Showcase your specialities to local builders",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
                )

                // Select Creator Type Specialty selection
                Text(
                    text = "Primary Specialization",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                )

                val specialities = listOf("Photographer", "Videographer", "Both")
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    specialities.forEach { style ->
                        val isSelected = creatorType == style
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isSelected) AmberGold else MaterialTheme.colorScheme.surfaceVariant)
                                .clickable { creatorType = style }
                                .border(1.dp, if (isSelected) AmberGold else Color.Transparent, RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = style,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) Color.Black else MaterialTheme.colorScheme.onSurface,
                                fontSize = 12.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Multi-select skillset checkboxes
                Text(
                    text = "Choose Your Skillsets (Select multiple/all)",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                )

                val skillOptions = listOf("Photographer", "Videographer", "Reel Creator")
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    skillOptions.forEach { skill ->
                        val isChecked = selectedSkills.contains(skill)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isChecked) AmberGold.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surface)
                                .clickable {
                                    if (isChecked) {
                                        if (selectedSkills.size > 1) selectedSkills.remove(skill)
                                    } else {
                                        selectedSkills.add(skill)
                                    }
                                }
                                .border(
                                    width = if (isChecked) 2.dp else 1.dp,
                                    color = if (isChecked) AmberGold else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                                    shape = RoundedCornerShape(12.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                                modifier = Modifier.padding(horizontal = 4.dp)
                            ) {
                                Icon(
                                    imageVector = if (isChecked) Icons.Default.CheckCircle else Icons.Default.AddCircleOutline,
                                    contentDescription = null,
                                    tint = if (isChecked) AmberGold else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = skill,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Experience Selection Level Choose
                Text(
                    text = "Experience Profile",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                )

                val levels = listOf("Beginner", "Professional", "Studio")
                Column(
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    levels.forEach { level ->
                        val isSelected = experienceLevel == level
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isSelected) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface)
                                .clickable { experienceLevel = level }
                                .border(
                                    width = if (isSelected) 1.5.dp else 1.dp,
                                    color = if (isSelected) AmberGold else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f),
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = { experienceLevel = level },
                                colors = RadioButtonDefaults.colors(selectedColor = AmberGold)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Column {
                                Text(
                                    text = level,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                                Text(
                                    text = when (level) {
                                        "Beginner" -> "Fresh styling, startup friendly, basic gears"
                                        "Professional" -> "Industry standard, high fidelity cinema rigs"
                                        else -> "Full camera production layout, soundstage"
                                    },
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Interactive slider for years of experience
                Text(
                    text = "Years of Experience: $yearsOfExperience Years",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth()
                )
                Slider(
                    value = yearsOfExperience.toFloat(),
                    onValueChange = { yearsOfExperience = it.toInt() },
                    valueRange = 1f..25f,
                    steps = 24,
                    colors = SliderDefaults.colors(
                        activeTrackColor = AmberGold,
                        thumbColor = AmberGold
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Link Social Links input fields
                Text(
                    text = "Link Your Social Portfolios",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                )

                // Instagram field
                OutlinedTextField(
                    value = instagramInput,
                    onValueChange = { instagramInput = it },
                    label = { Text("Instagram Username/URL") },
                    placeholder = { Text("e.g. amit_sharmaproductions") },
                    leadingIcon = { Icon(Icons.Default.PhotoCamera, contentDescription = null, tint = AmberGold) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AmberGold)
                )

                Spacer(modifier = Modifier.height(10.dp))

                // YouTube field
                OutlinedTextField(
                    value = youtubeInput,
                    onValueChange = { youtubeInput = it },
                    label = { Text("YouTube Channel Link") },
                    placeholder = { Text("e.g. https://youtube.com/c/amitsharmaproductions") },
                    leadingIcon = { Icon(Icons.Default.VideoCameraBack, contentDescription = null, tint = AmberGold) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AmberGold)
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Website Field
                OutlinedTextField(
                    value = websiteInput,
                    onValueChange = { websiteInput = it },
                    label = { Text("Website / Portfolio Address") },
                    placeholder = { Text("e.g. www.amitsharmamedia.com") },
                    leadingIcon = { Icon(Icons.Default.Language, contentDescription = null, tint = AmberGold) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AmberGold)
                )

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = { registerStep = 1 },
                        modifier = Modifier.weight(1f).height(50.dp)
                    ) {
                        Text("Back", color = MaterialTheme.colorScheme.onBackground)
                    }

                    Button(
                        onClick = {
                            viewModel.regCreatorType.value = creatorType
                            viewModel.regExperienceLevel.value = experienceLevel
                            viewModel.regYearsOfExperience.value = yearsOfExperience
                            viewModel.regInstagram.value = instagramInput.trim()
                            viewModel.regWebsite.value = websiteInput.trim()
                            viewModel.regYoutube.value = youtubeInput.trim()
                            viewModel.regSkillset.value = selectedSkills.joinToString(", ")

                            val displayName = fullName.trim().ifEmpty { email.substringBefore("@") }
                            viewModel.signUpWithEmailAndPassword(email.trim(), password.trim(), displayName, "Creator", userCity)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AmberGold),
                        modifier = Modifier.weight(1.5f).height(50.dp).testTag("button_onboarding_register")
                    ) {
                        if (authLoadingState.value) {
                            CircularProgressIndicator(color = Color.Black, modifier = Modifier.size(24.dp))
                        } else {
                            Text("Launch Creator Office", fontWeight = FontWeight.Bold, color = Color.Black)
                        }
                    }
                }
            }
        }
    }
}


// ----------------------------------------------------
// CUSTOMER JOURNEY SCREEN WITH TABBED SECTIONS
// ----------------------------------------------------
@Composable
fun CustomerMainScreen(
    viewModel: FokalViewModel,
    onNavigateToCreatorDetail: (String) -> Unit,
    onNavigateToChat: (String) -> Unit,
    onNavigateToReview: ((Long, String) -> Unit)? = null,
    onNavigateToPostShootAlert: (() -> Unit)? = null
) {
    var selectedTab by remember { mutableIntStateOf(0) } // 0: Home, 1: Search, 2: Bookings, 3: Favorites, 4: Messages, 5: Fokal AI

    val tabs = listOf(
        TabItem("Home", Icons.Default.Explore),
        TabItem("Explore", Icons.Default.Search),
        TabItem("Bookings", Icons.Default.CalendarToday),
        TabItem("Favorites", Icons.Default.Favorite),
        TabItem("Messages", Icons.AutoMirrored.Filled.Message),
        TabItem("Fokal AI", Icons.Default.SmartToy)
    )

    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp
            ) {
                tabs.forEachIndexed { index, tab ->
                    NavigationBarItem(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        icon = { Icon(tab.icon, contentDescription = tab.title) },
                        label = { Text(tab.title, fontSize = 9.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color.Black,
                            selectedTextColor = AmberGold,
                            indicatorColor = AmberGold,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            when (selectedTab) {
                0 -> CustomerDashboardScreen(viewModel, onNavigateToCreatorDetail, onNavigateToPostShootAlert)
                1 -> CustomerSearchScreen(viewModel, onNavigateToCreatorDetail)
                2 -> CustomerBookingsScreen(viewModel, onNavigateToChat, onNavigateToReview)
                3 -> CustomerFavoritesScreen(viewModel, onNavigateToCreatorDetail)
                4 -> CustomerMessagesListScreen(viewModel, onNavigateToChat)
                5 -> FokalAIScreen(viewModel)
            }
        }
    }
}


// ----------------------------------------------------
// CUSTOMER DASHBOARD - HOMEPAGE (TAB 0)
// ----------------------------------------------------
@Composable
fun CustomerDashboardScreen(
    viewModel: FokalViewModel,
    onNavigateToCreatorDetail: (String) -> Unit,
    onNavigateToPostShootAlert: (() -> Unit)? = null
) {
    val creators by viewModel.filteredCreators.collectAsStateWithLifecycle()
    val scrollState = rememberScrollState()

    var isCityMenuExpanded by remember { mutableStateOf(false) }
    val searchQueryState by viewModel.searchQuery.collectAsStateWithLifecycle()
    val filterCityState by viewModel.filterCity.collectAsStateWithLifecycle()

    val cities = listOf("All Cities", "Mumbai", "Delhi", "Bengaluru", "Goa", "Jaipur")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
    ) {
        // Hero Cinematic Banner
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(260.dp)
        ) {
            Image(
                painter = painterResource(id = R.drawable.wedding_hero),
                contentDescription = "Cinematic Photography Banner",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, BlackCoal.copy(alpha = 0.95f))
                        )
                    )
            )

            // Warm golden overlay details
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 44.dp)
            ) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = AmberGold),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.padding(bottom = 8.dp)
                ) {
                    Text(
                        text = "NEW SEASON LIVE",
                        fontWeight = FontWeight.Black,
                        fontSize = 10.sp,
                        color = Color.Black,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
                
                Text(
                    text = "Capture Life's Golden Moments",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White
                )
                Text(
                    text = "Book certified Photographers & Videographers anywhere in India.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.8f)
                )
            }

            // Quick Switcher Button on Header representing platform control
            Card(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    .clickable { viewModel.switchRole("Creator") },
                colors = CardDefaults.cardColors(containerColor = BlackCoal.copy(alpha = 0.8f)),
                shape = CircleShape,
                border = BorderStroke(1.dp, AmberGold)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.SwitchAccount,
                        contentDescription = null,
                        tint = AmberGold,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Office Mode", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Search & Location Bar overlaying Hero Section
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .offset(y = (-24).dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Find & Book Creators",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (searchQueryState.isNotEmpty() || filterCityState.isNotEmpty()) {
                        TextButton(
                            onClick = { 
                                viewModel.searchQuery.value = ""
                                viewModel.filterCity.value = ""
                            },
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text("Clear", color = AmberGold, fontSize = 12.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Service search input bar
                OutlinedTextField(
                    value = searchQueryState,
                    onValueChange = { viewModel.searchQuery.value = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("home_search_service_input"),
                    placeholder = { Text("What are you planning? (e.g., Wedding)", fontSize = 13.sp) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search icon",
                            tint = AmberGold
                        )
                    },
                    trailingIcon = {
                        if (searchQueryState.isNotEmpty()) {
                            IconButton(onClick = { viewModel.searchQuery.value = "" }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear search query")
                            }
                        }
                    },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AmberGold,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                    ),
                    shape = RoundedCornerShape(10.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Location selector dropdown field
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = if (filterCityState.isEmpty()) "All Locations (Global)" else filterCityState,
                        onValueChange = {},
                        readOnly = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("home_search_location_input"),
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Place,
                                contentDescription = "Location icon",
                                tint = AmberGold
                            )
                        },
                        trailingIcon = {
                            IconButton(onClick = { isCityMenuExpanded = !isCityMenuExpanded }) {
                                Icon(
                                    imageVector = if (isCityMenuExpanded) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown,
                                    contentDescription = "Toggle location dropdown"
                                )
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                            focusedBorderColor = AmberGold,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent
                        ),
                        shape = RoundedCornerShape(10.dp)
                    )
                    // Transparent overlay to verify clicks work properly
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .clickable { isCityMenuExpanded = true }
                    )

                    var showCustomLocationFilterDialog by remember { mutableStateOf(false) }

                    DropdownMenu(
                        expanded = isCityMenuExpanded,
                        onDismissRequest = { isCityMenuExpanded = false },
                        modifier = Modifier.fillMaxWidth(0.8f)
                    ) {
                        cities.forEach { city ->
                            DropdownMenuItem(
                                text = { Text(if (city == "All Cities") "All Locations (Global)" else city) },
                                onClick = {
                                    viewModel.filterCity.value = if (city == "All Cities") "" else city
                                    isCityMenuExpanded = false
                                }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("🌍 Type Custom Location...") },
                            onClick = {
                                isCityMenuExpanded = false
                                showCustomLocationFilterDialog = true
                            }
                        )
                    }

                    if (showCustomLocationFilterDialog) {
                        var inputLoc by remember { mutableStateOf("") }
                        AlertDialog(
                            onDismissRequest = { showCustomLocationFilterDialog = false },
                            title = { Text("Enter Custom Location") },
                            text = {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("Type any city or country (e.g., Paris, France; London, UK; New York, USA) to view matching creators there.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    OutlinedTextField(
                                        value = inputLoc,
                                        onValueChange = { inputLoc = it },
                                        placeholder = { Text("e.g. Paris, France") },
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true,
                                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AmberGold)
                                    )
                                }
                            },
                            confirmButton = {
                                Button(
                                    colors = ButtonDefaults.buttonColors(containerColor = AmberGold),
                                    onClick = {
                                        viewModel.filterCity.value = inputLoc.trim()
                                        showCustomLocationFilterDialog = false
                                    }
                                ) {
                                    Text("Set Location", color = Color.Black, fontWeight = FontWeight.Bold)
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { showCustomLocationFilterDialog = false }) {
                                    Text("Cancel")
                                }
                            }
                        )
                    }
                }
            }
        }

        // PREMIUM CTA CARD: Post a Gig Alert
        var showPostLeadDialog by remember { mutableStateOf(false) }
        
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.5.dp, AmberGold)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Default.Campaign, contentDescription = null, tint = AmberGold, modifier = Modifier.size(20.dp))
                        Text(
                            text = "Need a Photographer Globally?",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Broadcast an instant Shoot alert! Available photo & video crews worldwide will get alerted to ping you directly.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
                        lineHeight = 15.sp
                    )
                }
                
                Button(
                    onClick = { onNavigateToPostShootAlert?.invoke() },
                    colors = ButtonDefaults.buttonColors(containerColor = AmberGold)
                ) {
                    Text("Post Alert", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                }
            }
        }

        if (showPostLeadDialog) {
            var shootType by remember { mutableStateOf("Portrait") }
            var locationInput by remember { mutableStateOf("") }
            var budgetInput by remember { mutableStateOf("25000") }
            var dateDetailInput by remember { mutableStateOf("Next Month") }
            var specDescription by remember { mutableStateOf("") }
            
            AlertDialog(
                onDismissRequest = { showPostLeadDialog = false },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.Campaign, contentDescription = null, tint = AmberGold)
                        Text("Post Worldwide Shoot Alert", style = MaterialTheme.typography.titleLarge)
                    }
                },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            "Let photographers and videographers globally find you! Describe your needs, shoot location, date, and budget details.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        
                        Divider()
                        
                        OutlinedTextField(
                            value = shootType,
                            onValueChange = { shootType = it },
                            label = { Text("Event / Shoot Type") },
                            placeholder = { Text("e.g. Wedding, Street Fashion, Commercial") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AmberGold)
                        )
                        
                        OutlinedTextField(
                            value = locationInput,
                            onValueChange = { locationInput = it },
                            label = { Text("Shooting Location") },
                            placeholder = { Text("e.g. London, UK; Paris, France; Montreal") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            leadingIcon = { Icon(Icons.Default.Place, contentDescription = null, tint = AmberGold) },
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AmberGold)
                        )
                        
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedTextField(
                                value = budgetInput,
                                onValueChange = { budgetInput = it },
                                label = { Text("Budget") },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AmberGold)
                            )
                            
                            OutlinedTextField(
                                value = dateDetailInput,
                                onValueChange = { dateDetailInput = it },
                                label = { Text("Timeframe") },
                                placeholder = { Text("e.g. July 15") },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AmberGold)
                            )
                        }
                        
                        OutlinedTextField(
                            value = specDescription,
                            onValueChange = { specDescription = it },
                            label = { Text("Shoot Description / Brief") },
                            placeholder = { Text("e.g. Need high-contrast urban portraits. Bring custom lenses.") },
                            modifier = Modifier.fillMaxWidth().height(95.dp),
                            maxLines = 3,
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AmberGold)
                        )
                    }
                },
                confirmButton = {
                    Button(
                        colors = ButtonDefaults.buttonColors(containerColor = AmberGold),
                        onClick = {
                            if (locationInput.trim().isEmpty() || specDescription.trim().isEmpty() || shootType.trim().isEmpty()) {
                                return@Button
                            }
                            val bVal = budgetInput.toDoubleOrNull() ?: 25000.0
                            viewModel.createClientLead(
                                eventType = shootType.trim(),
                                location = locationInput.trim(),
                                budget = bVal,
                                description = specDescription.trim(),
                                dateDetail = dateDetailInput.trim()
                            )
                            showPostLeadDialog = false
                        }
                    ) {
                        Text("Broadcast Broadly", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showPostLeadDialog = false }) {
                        Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            )
        }

        // Category grid section
        Text(
            text = "Browse by Categories",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .offset(y = (-8).dp),
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(4.dp))

        val categories = listOf(
            CategoryItem("Wedding", Icons.Default.Favorite, "https://images.unsplash.com/photo-1511285560929-80b456fea0bc?auto=format&fit=crop&w=120&q=80"),
            CategoryItem("Pre-Wedding", Icons.Default.CameraAlt, "https://images.unsplash.com/photo-1515934751635-c81c6bc9a2d8?auto=format&fit=crop&w=120&q=80"),
            CategoryItem("Maternity", Icons.Default.PregnantWoman, "https://images.unsplash.com/photo-1518156677180-95a2893f3e9f?auto=format&fit=crop&w=120&q=80"),
            CategoryItem("Corporate", Icons.Default.BusinessCenter, "https://images.unsplash.com/photo-1515187029135-18ee286d815b?auto=format&fit=crop&w=120&q=80"),
            CategoryItem("Fashion", Icons.Default.CrueltyFree, "https://images.unsplash.com/photo-1490481651871-ab68de25d43d?auto=format&fit=crop&w=120&q=80"),
            CategoryItem("Baby Shoot", Icons.Default.BabyChangingStation, "https://images.unsplash.com/photo-1519689680058-324335c77ebe?auto=format&fit=crop&w=120&q=80")
        )

        // Custom 3x2 Grid using rows & columns for nest scroll compatibility
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .offset(y = (-8).dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Row 1
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                for (i in 0..2) {
                    Box(modifier = Modifier.weight(1f)) {
                        CategoryGridCard(category = categories[i], viewModel = viewModel)
                    }
                }
            }
            // Row 2
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                for (i in 3..5) {
                    Box(modifier = Modifier.weight(1f)) {
                        CategoryGridCard(category = categories[i], viewModel = viewModel)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Featured Creators Header
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp)
        ) {
            Text(
                text = "Featured Creators",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "Top-rated professionals with outstanding portfolios",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Horizontal Carousel of Featured Creators
        val allCreatorsList by viewModel.creatorsList.collectAsStateWithLifecycle()
        val featuredPhotographers = remember(allCreatorsList) {
            allCreatorsList.filter { creator ->
                creator.rating >= 4.8 && (creator.creatorType == "Photographer" || creator.creatorType == "Both")
            }
        }

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(featuredPhotographers) { photographer ->
                FeaturedCreatorCard(
                    creator = photographer,
                    viewModel = viewModel,
                    onClick = { onNavigateToCreatorDetail(photographer.id) }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Featured Photographers Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Verified Creators",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Tested professionals with guaranteed delivery",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            TextButton(onClick = { viewModel.clearAllFilters() }) {
                Text("See All", color = AmberGold)
            }
        }

        // Active filters chips
        val activeCategory by viewModel.selectedCategory.collectAsStateWithLifecycle()
        if (activeCategory.isNotEmpty() || filterCityState.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (activeCategory.isNotEmpty()) {
                    ElevatedResultAssistChip(
                        onClick = { viewModel.selectedCategory.value = "" },
                        label = "Category: $activeCategory"
                    )
                }
                if (filterCityState.isNotEmpty()) {
                    ElevatedResultAssistChip(
                        onClick = { viewModel.filterCity.value = "" },
                        label = "City: $filterCityState"
                    )
                }
            }
        }

        // List Grid for Creators
        if (creators.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
                    .height(200.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "No creators match the active filters.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    TextButton(onClick = { viewModel.clearAllFilters() }) {
                        Text("Reset Filters", color = AmberGold)
                    }
                }
            }
        } else {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                creators.forEach { creator ->
                    CreatorRowCard(
                        creator = creator,
                        viewModel = viewModel,
                        onClick = { onNavigateToCreatorDetail(creator.id) }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(40.dp))
    }
}

@Composable
fun FeaturedCreatorCard(
    creator: Creator,
    viewModel: FokalViewModel,
    onClick: () -> Unit
) {
    val name = viewModel.getCreatorNameSync(creator.id)
    val city = viewModel.getCreatorCitySync(creator.id)
    val avatar = viewModel.getCreatorAvatarSync(creator.id)
    
    val sharedTransitionScope = LocalSharedTransitionScope.current
    val animatedVisibilityScope = LocalAnimatedVisibilityScope.current
    
    val previewPic = when (creator.id) {
        "amit_sharma_creator" -> "https://images.unsplash.com/photo-1511285560929-80b456fea0bc?auto=format&fit=crop&w=300&q=80"
        "riya_sen_creator" -> "https://images.unsplash.com/photo-1518156677180-95a2893f3e9f?auto=format&fit=crop&w=300&q=80"
        "kabir_singh_creator" -> "https://images.unsplash.com/photo-1515187029135-18ee286d815b?auto=format&fit=crop&w=300&q=80"
        "vikram_goa_creator" -> "https://images.unsplash.com/photo-1515934751635-c81c6bc9a2d8?auto=format&fit=crop&w=300&q=80"
        "manisha_mehta_creator" -> "https://images.unsplash.com/photo-1607190074257-dd4b7af0309f?auto=format&fit=crop&w=300&q=80"
        else -> "https://images.unsplash.com/photo-1544005313-94ddf0286df2?auto=format&fit=crop&w=300&q=80"
    }

    Card(
        modifier = Modifier
            .width(220.dp)
            .clickable { onClick() }
            .testTag("featured_creator_card_${creator.id}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(115.dp)
            ) {
                val bannerModifier = Modifier.fillMaxSize()
                val finalBannerModifier = if (sharedTransitionScope != null && animatedVisibilityScope != null) {
                    with(sharedTransitionScope) {
                        bannerModifier.sharedElement(
                            rememberSharedContentState(key = "banner_${creator.id}"),
                            animatedVisibilityScope = animatedVisibilityScope
                        )
                    }
                } else {
                    bannerModifier
                }

                AsyncImage(
                    model = previewPic,
                    contentDescription = "Portfolio preview photo",
                    modifier = finalBannerModifier,
                    contentScale = ContentScale.Crop
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f))
                            )
                        )
                )

                if (creator.verified) {
                    Surface(
                        color = AmberGold,
                        shape = RoundedCornerShape(bottomEnd = 12.dp, topStart = 16.dp),
                        modifier = Modifier.align(Alignment.TopStart)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Verified,
                                contentDescription = "Certified Pro Badge",
                                tint = Color.Black,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "VERIFIED",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.Black
                            )
                        }
                    }
                }

                val avatarModifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 12.dp, bottom = 8.dp)
                    .size(44.dp)
                    .clip(CircleShape)
                    .border(1.5.dp, AmberGold, CircleShape)
                val finalAvatarModifier = if (sharedTransitionScope != null && animatedVisibilityScope != null) {
                    with(sharedTransitionScope) {
                        avatarModifier.sharedElement(
                            rememberSharedContentState(key = "avatar_${creator.id}"),
                            animatedVisibilityScope = animatedVisibilityScope
                        )
                    }
                } else {
                    avatarModifier
                }

                AsyncImage(
                    model = avatar,
                    contentDescription = "$name avatar micro",
                    modifier = finalAvatarModifier,
                    contentScale = ContentScale.Crop
                )
                
                Card(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 12.dp, bottom = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.75f)),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = "Rating star",
                            tint = AmberGold,
                            modifier = Modifier.size(11.dp)
                        )
                        Text(
                            text = "${creator.rating}",
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = 11.sp
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
            ) {
                val nameModifier = Modifier
                val finalNameModifier = if (sharedTransitionScope != null && animatedVisibilityScope != null) {
                    with(sharedTransitionScope) {
                        nameModifier.sharedElement(
                            rememberSharedContentState(key = "name_${creator.id}"),
                            animatedVisibilityScope = animatedVisibilityScope
                        )
                    }
                } else {
                    nameModifier
                }

                Text(
                    text = name,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = finalNameModifier
                )
                
                Spacer(modifier = Modifier.height(2.dp))
                
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Place,
                        contentDescription = "Place location pin",
                        tint = AmberGold,
                        modifier = Modifier.size(12.dp)
                    )
                    Text(
                        text = city,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.82f)
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "From ₹${creator.startingPrice.toInt()}/shoot",
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    color = AmberGold
                )
            }
        }
    }
}

@Composable
fun CategoryGridCard(category: CategoryItem, viewModel: FokalViewModel) {
    val isSelected = viewModel.selectedCategory.collectAsStateWithLifecycle().value == category.name
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(84.dp)
            .clickable {
                if (isSelected) viewModel.selectedCategory.value = ""
                else viewModel.selectedCategory.value = category.name
            }
            .testTag("category_grid_card_${category.name.lowercase().replace(" ", "_")}"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) AmberGold else MaterialTheme.colorScheme.surfaceVariant
        ),
        border = if (isSelected) BorderStroke(2.dp, AmberGold) else null,
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AsyncImage(
                model = category.imgUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = if (isSelected) 0.3f else 0.55f))
            )
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = category.icon,
                    contentDescription = null,
                    tint = if (isSelected) AmberGold else Color.White,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = category.name,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
fun ElevatedResultAssistChip(onClick: () -> Unit, label: String) {
    ElevatedAssistChip(
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp)) }
    )
}



// ----------------------------------------------------
// PORTFOLIO / CREATOR ROW CARD ELEMENT
// ----------------------------------------------------
@Composable
fun CreatorRowCard(
    creator: Creator,
    viewModel: FokalViewModel,
    onClick: () -> Unit
) {
    val name = viewModel.getCreatorNameSync(creator.id)
    val city = viewModel.getCreatorCitySync(creator.id)
    val avatar = viewModel.getCreatorAvatarSync(creator.id)
    
    val isFav by viewModel.isCreatorFavorite(creator.id).collectAsStateWithLifecycle(initialValue = false)

    val sharedTransitionScope = LocalSharedTransitionScope.current
    val animatedVisibilityScope = LocalAnimatedVisibilityScope.current

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .testTag("creator_card_${creator.id}"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
            ) {
                // Background cinematic Unsplash banner preview for portfolio
                val previewPic = when (creator.id) {
                    "amit_sharma_creator" -> "https://images.unsplash.com/photo-1511285560929-80b456fea0bc?auto=format&fit=crop&w=500&q=80"
                    "riya_sen_creator" -> "https://images.unsplash.com/photo-1518156677180-95a2893f3e9f?auto=format&fit=crop&w=500&q=80"
                    "kabir_singh_creator" -> "https://images.unsplash.com/photo-1515187029135-18ee286d815b?auto=format&fit=crop&w=500&q=80"
                    "vikram_goa_creator" -> "https://images.unsplash.com/photo-1515934751635-c81c6bc9a2d8?auto=format&fit=crop&w=500&q=80"
                    "manisha_mehta_creator" -> "https://images.unsplash.com/photo-1607190074257-dd4b7af0309f?auto=format&fit=crop&w=500&q=80"
                    else -> "https://images.unsplash.com/photo-1544005313-94ddf0286df2?auto=format&fit=crop&w=500&q=80"
                }

                val bannerModifier = Modifier.fillMaxSize()
                val finalBannerModifier = if (sharedTransitionScope != null && animatedVisibilityScope != null) {
                    with(sharedTransitionScope) {
                        bannerModifier.sharedElement(
                            rememberSharedContentState(key = "banner_${creator.id}"),
                            animatedVisibilityScope = animatedVisibilityScope
                        )
                    }
                } else {
                    bannerModifier
                }

                AsyncImage(
                    model = previewPic,
                    contentDescription = "Portfolio background",
                    modifier = finalBannerModifier,
                    contentScale = ContentScale.Crop
                )

                // Glassmorphic overlay gradients
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f))
                            )
                        )
                )

                // Favorite option floating on image
                IconButton(
                    onClick = { viewModel.toggleFavorite(creator.id) },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp)
                        .background(Color.Black.copy(alpha = 0.4f), CircleShape)
                ) {
                    Icon(
                        imageVector = if (isFav) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "Heart Saved Favorite",
                        tint = if (isFav) Color.Red else Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Pricing Info tag floating
                Card(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(12.dp),
                    colors = CardDefaults.cardColors(containerColor = BlackCoal.copy(alpha = 0.85f)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "From ₹${creator.startingPrice.toInt()}/shoot",
                        fontWeight = FontWeight.Bold,
                        color = AmberGold,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            // Text info details lower container
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Profile avatar thumb
                val avatarModifier = Modifier
                    .size(50.dp)
                    .clip(CircleShape)
                    .border(1.5.dp, AmberGold, CircleShape)
                val finalAvatarModifier = if (sharedTransitionScope != null && animatedVisibilityScope != null) {
                    with(sharedTransitionScope) {
                        avatarModifier.sharedElement(
                            rememberSharedContentState(key = "avatar_${creator.id}"),
                            animatedVisibilityScope = animatedVisibilityScope
                        )
                    }
                } else {
                    avatarModifier
                }

                AsyncImage(
                    model = avatar,
                    contentDescription = "Creator Avatar",
                    modifier = finalAvatarModifier,
                    contentScale = ContentScale.Crop
                )

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val nameModifier = Modifier
                        val finalNameModifier = if (sharedTransitionScope != null && animatedVisibilityScope != null) {
                            with(sharedTransitionScope) {
                                nameModifier.sharedElement(
                                    rememberSharedContentState(key = "name_${creator.id}"),
                                    animatedVisibilityScope = animatedVisibilityScope
                                )
                            }
                        } else {
                            nameModifier
                        }

                        Text(
                            text = name,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = finalNameModifier
                        )
                        if (creator.verified) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Default.Verified,
                                contentDescription = "Certified Pro",
                                tint = AmberGold,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(text = city, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(text = "•", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(text = "${creator.yearsOfExperience} yrs exp", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }

                    // Rating stars info
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        modifier = Modifier.padding(top = 2.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = "Rating STAR",
                            tint = AmberGold,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "${creator.rating} Rep (Verified Reviews)",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}


// ----------------------------------------------------
// SEARCH & DISCOVERY SHEET (TAB 1)
// ----------------------------------------------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomerSearchScreen(
    viewModel: FokalViewModel,
    onNavigateToCreatorDetail: (String) -> Unit
) {
    val creators by viewModel.filteredCreators.collectAsStateWithLifecycle()
    val query by viewModel.searchQuery.collectAsStateWithLifecycle()
    val activeCity by viewModel.filterCity.collectAsStateWithLifecycle()
    val activeExp by viewModel.filterExperience.collectAsStateWithLifecycle()
    val budgetLimit by viewModel.filterBudget.collectAsStateWithLifecycle()

    var showFiltersSheet by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        // Search Header Toolbar
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { viewModel.searchQuery.value = it },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("search_text_input"),
                        placeholder = { Text("Search photographer name, biography...") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        trailingIcon = {
                            if (query.isNotEmpty()) {
                                IconButton(onClick = { viewModel.searchQuery.value = "" }) {
                                    Icon(Icons.Default.Close, contentDescription = null)
                                }
                            }
                        },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AmberGold
                        )
                    )
                    
                    Spacer(modifier = Modifier.width(10.dp))

                    IconButton(
                        onClick = { showFiltersSheet = !showFiltersSheet },
                        modifier = Modifier
                            .size(54.dp)
                            .background(
                                color = if (activeCity.isNotEmpty() || activeExp.isNotEmpty() || budgetLimit != null) {
                                    AmberGold
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant
                                },
                                shape = RoundedCornerShape(12.dp)
                            )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = "Search Filter Menu Button",
                            tint = if (activeCity.isNotEmpty() || activeExp.isNotEmpty() || budgetLimit != null) Color.Black else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                // Interactive Filters expand block
                AnimatedVisibility(visible = showFiltersSheet) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Divider()

                        // Custom Location text field + City choices
                        Text("Filter by Location (Anywhere)", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        OutlinedTextField(
                            value = activeCity,
                            onValueChange = { viewModel.filterCity.value = it },
                            modifier = Modifier.fillMaxWidth().testTag("filter_custom_location_input"),
                            placeholder = { Text("Enter city or country (e.g. Paris, London, Delhi)") },
                            leadingIcon = { Icon(Icons.Default.Place, contentDescription = null, tint = AmberGold) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AmberGold)
                        )
                        
                        val quickCities = listOf("Mumbai", "Bengaluru", "Delhi", "Goa", "Jaipur")
                        Row(
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            quickCities.forEach { c ->
                                val isSelected = activeCity.equals(c, ignoreCase = true)
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(if (isSelected) AmberGold else MaterialTheme.colorScheme.surfaceVariant)
                                        .clickable {
                                            viewModel.filterCity.value = if (isSelected) "" else c
                                        }
                                        .padding(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = c,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) Color.Black else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }

                        // Reputation Filter (Rep Score)
                        Text("Minimum Reputation (Rep Score)", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        val repOptions = listOf(
                            null to "Any Rep",
                            4.0 to "4.0+ ★",
                            4.5 to "4.5+ ★",
                            4.8 to "4.8+ ★",
                            5.0 to "5.0 ★"
                        )
                        val filterMinRepValue by viewModel.filterMinRep.collectAsStateWithLifecycle()
                        Row(
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            repOptions.forEach { (repVal, label) ->
                                val isSelected = filterMinRepValue == repVal
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(if (isSelected) AmberGold else MaterialTheme.colorScheme.surfaceVariant)
                                        .clickable {
                                            viewModel.filterMinRep.value = repVal
                                        }
                                        .padding(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = label,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) Color.Black else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }

                        // Experience Level filter
                        Text("Experience Scale", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        val levels = listOf("Beginner", "Professional", "Studio")
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            levels.forEach { level ->
                                val isSelected = activeExp == level
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(if (isSelected) AmberGold else MaterialTheme.colorScheme.surfaceVariant)
                                        .clickable {
                                            viewModel.filterExperience.value = if (isSelected) "" else level
                                        }
                                        .padding(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = level,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) Color.Black else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }

                        // Maximum budget slider
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Max Starting Budget Limit", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Text(
                                text = if (budgetLimit == null) "Unlimited" else "₹${budgetLimit!!.toInt()}",
                                fontWeight = FontWeight.Black,
                                color = AmberGold,
                                fontSize = 12.sp
                            )
                        }

                        Slider(
                            value = (budgetLimit ?: 80000.0).toFloat(),
                            onValueChange = {
                                if (it >= 75000f) {
                                    viewModel.filterBudget.value = null
                                } else {
                                    viewModel.filterBudget.value = it.toDouble()
                                }
                            },
                            valueRange = 10000f..80000f,
                            colors = SliderDefaults.colors(
                                thumbColor = AmberGold,
                                activeTrackColor = AmberGold
                            )
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(onClick = { viewModel.clearAllFilters() }) {
                                Text("Clear Settings", color = MaterialTheme.colorScheme.error)
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = { showFiltersSheet = false },
                                colors = ButtonDefaults.buttonColors(containerColor = AmberGold)
                            ) {
                                Text("Apply Discover Filters", color = Color.Black, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }

        // Search Results List Flow
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize().testTag("search_results_list")
        ) {
            if (creators.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(300.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Warning, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.height(10.dp))
                            Text("No Matches Found", fontWeight = FontWeight.Bold)
                            Text("Try adjust city or sliders parameters above", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                        }
                    }
                }
            } else {
                items(creators) { creator ->
                    CreatorRowCard(
                        creator = creator,
                        viewModel = viewModel,
                        onClick = { onNavigateToCreatorDetail(creator.id) }
                    )
                }
            }
        }
    }
}


// ----------------------------------------------------
// BOOKINGS TRAIL SCREEN (TAB 2)
// ----------------------------------------------------
@Composable
fun CustomerBookingsScreen(
    viewModel: FokalViewModel,
    onNavigateToChat: (String) -> Unit,
    onNavigateToReview: ((Long, String) -> Unit)? = null
) {
    val bookings by viewModel.bookingsList.collectAsStateWithLifecycle()
    val scrollState = rememberLazyListState()
    
    var filterState by remember { mutableStateOf("All") } // "All", "Upcoming", "Past"
    
    val filteredBookings = remember(bookings, filterState) {
        when (filterState) {
            "Upcoming" -> bookings.filter { it.status == "Pending" || it.status == "Accepted" || it.status == "Confirmed" }
            "Past" -> bookings.filter { it.status == "Completed" || it.status == "Cancelled" }
            else -> bookings
        }
    }

    Column(modifier = Modifier.fillMaxSize().testTag("bookings_list_screen")) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(16.dp)
        ) {
            Column {
                Text(
                    text = "My Photo Shoots",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Track progress on pending and accepted bookings",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Status Filter Chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("All", "Upcoming", "Past").forEach { category ->
                val isSelected = filterState == category
                FilterChip(
                    selected = isSelected,
                    onClick = { filterState = category },
                    label = { Text(category) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = AmberGold,
                        selectedLabelColor = Color.Black
                    ),
                    modifier = Modifier.testTag("bookings_filter_chip_$category")
                )
            }
        }

        if (filteredBookings.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.EventNote,
                        contentDescription = "Empty list",
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "No Shoot Bookings Found",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "Find creators under Search or Home tabs to schedule your golden hour shots.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
            Box(modifier = Modifier.padding(16.dp)) {
                com.example.service.ReminderSettings(viewModel = viewModel)
            }
        } else {
            LazyColumn(
                state = scrollState,
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f).testTag("bookings_lazy_column")
            ) {
                items(filteredBookings) { booking ->
                    BookingItemCard(
                        booking = booking,
                        viewModel = viewModel,
                        isCreator = false,
                        onNavigateToChat = onNavigateToChat,
                        onNavigateToReview = onNavigateToReview
                    )
                }
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    com.example.service.ReminderSettings(viewModel = viewModel)
                }
            }
        }
    }
}

@Composable
fun BookingItemCard(
    booking: Booking,
    viewModel: FokalViewModel,
    isCreator: Boolean,
    onNavigateToChat: ((String) -> Unit)? = null,
    onNavigateToReview: ((Long, String) -> Unit)? = null
) {
    var showCancelPolicyDialog by remember { mutableStateOf(false) }

    val targetName = if (isCreator) {
        "Client: Ananya Rao"
    } else {
        "Freelancer: ${viewModel.getCreatorNameSync(booking.creatorId)}"
    }
    val targetAvatar = if (isCreator) {
        "https://images.unsplash.com/photo-1494790108377-be9c29b29330?auto=format&fit=crop&w=150&q=80"
    } else {
        viewModel.getCreatorAvatarSync(booking.creatorId)
    }

    val statusColor = when (booking.status) {
        "Completed" -> Color(0xFF4CAF50)
        "Accepted" -> Color(0xFF2196F3)
        "Confirmed" -> Color(0xFF00BCD4)
        "Cancelled" -> Color(0xFFF44336)
        else -> AmberGold // Pending
    }

    Card(
        modifier = Modifier.fillMaxWidth().testTag("booking_item_card_${booking.id}"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Status row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(statusColor)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = booking.status,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = statusColor
                    )
                }
                
                Text(
                    text = "ID: #${booking.id}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Body info
            Row(verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(
                    model = targetAvatar,
                    contentDescription = null,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = booking.eventType,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Text(
                        text = targetName,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
            Spacer(modifier = Modifier.height(12.dp))

            // Logistics details row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("WHEN", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
                    Text("${booking.date} | ${booking.time}", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("AMOUNT", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
                    Text("₹${booking.price.toInt()}", fontSize = 12.sp, fontWeight = FontWeight.Black, color = AmberGold)
                    Spacer(modifier = Modifier.height(4.dp))
                    val isPaid = booking.paymentStatus.contains("Paid", ignoreCase = true)
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(
                                if (isPaid) Color(0xFF4CAF50).copy(alpha = 0.12f)
                                else Color(0xFFFF9800).copy(alpha = 0.12f)
                            )
                            .border(
                                width = 1.dp,
                                color = if (isPaid) Color(0xFF4CAF50).copy(alpha = 0.4f) else Color(0xFFFF9800).copy(alpha = 0.4f),
                                shape = RoundedCornerShape(4.dp)
                            )
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = booking.paymentStatus.uppercase(),
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Black,
                            color = if (isPaid) Color(0xFF4CAF50) else Color(0xFFFF9800)
                        )
                    }
                }
            }

            // Actions for creators or customers
            if (isCreator) {
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = {
                        viewModel.selectedChatCreatorId.value = booking.customerId
                        onNavigateToChat?.invoke(booking.customerId)
                    },
                    modifier = Modifier.fillMaxWidth().height(36.dp).testTag("creator_discuss_btn_${booking.id}"),
                    colors = ButtonDefaults.buttonColors(containerColor = AmberGold)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.AutoMirrored.Filled.Message, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color.Black)
                        Text("Chat with Client (Real-Time)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                    }
                }

                if (booking.status == "Pending") {
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { viewModel.updateBookingStatus(booking.id, "Accepted") },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2196F3)),
                            modifier = Modifier.weight(1f).height(40.dp).testTag("booking_accept_btn_${booking.id}")
                        ) {
                            Text("Accept", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        OutlinedButton(
                            onClick = { viewModel.updateBookingStatus(booking.id, "Cancelled") },
                            modifier = Modifier.weight(1f).height(40.dp).testTag("booking_cancel_btn_${booking.id}"),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Red)
                        ) {
                            Text("Ignore", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                } else if (booking.status == "Accepted" || booking.status == "Confirmed") {
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { viewModel.updateBookingStatus(booking.id, "Completed") },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                            modifier = Modifier.weight(1f).height(40.dp).testTag("booking_complete_btn_${booking.id}")
                        ) {
                            Text("Mark Completed", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        OutlinedButton(
                            onClick = { showCancelPolicyDialog = true },
                            modifier = Modifier.weight(1f).height(40.dp).testTag("booking_cancel_btn_${booking.id}"),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Red)
                        ) {
                            Text("Cancel Shoot", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            } else {
                // Customer side controls
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = {
                        viewModel.selectedChatCreatorId.value = booking.creatorId
                        onNavigateToChat?.invoke(booking.creatorId)
                    },
                    modifier = Modifier.fillMaxWidth().height(36.dp).testTag("customer_discuss_btn_${booking.id}"),
                    colors = ButtonDefaults.buttonColors(containerColor = AmberGold)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.AutoMirrored.Filled.Message, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color.Black)
                        Text("Discuss Details with Photographer (Real-Time)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                    }
                }

                if (booking.status == "Pending" || booking.status == "Accepted" || booking.status == "Confirmed") {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { showCancelPolicyDialog = true },
                        modifier = Modifier.fillMaxWidth().height(36.dp).testTag("customer_cancel_btn_${booking.id}"),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Red),
                        border = BorderStroke(1.dp, Color.Red.copy(alpha = 0.5f))
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(Icons.Default.Cancel, contentDescription = null, modifier = Modifier.size(14.dp))
                            Text("Cancel Shoot Appointment", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                if (booking.status == "Completed") {
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = { onNavigateToReview?.invoke(booking.id, booking.creatorId) },
                        modifier = Modifier.fillMaxWidth().height(36.dp).testTag("customer_review_btn_${booking.id}"),
                        colors = ButtonDefaults.buttonColors(containerColor = AmberGold)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Filled.Star, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color.Black)
                            Text("Write a Review", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                        }
                    }
                }
            }

            if (showCancelPolicyDialog) {
                androidx.compose.ui.window.Dialog(
                    onDismissRequest = { showCancelPolicyDialog = false }
                ) {
                    com.example.ui.components.CancellationPolicyCard(
                        booking = booking,
                        onCancel = {
                            viewModel.updateBookingStatus(booking.id, "Cancelled")
                            showCancelPolicyDialog = false
                        }
                    )
                }
            }
        }
    }
}


// ----------------------------------------------------
// FAVORITES SHORTLIST SCREEN (TAB 3)
// ----------------------------------------------------
@Composable
fun CustomerFavoritesScreen(
    viewModel: FokalViewModel,
    onNavigateToCreatorDetail: (String) -> Unit
) {
    val favorites by viewModel.favoritesList.collectAsStateWithLifecycle()
    val creators by viewModel.creatorsList.collectAsStateWithLifecycle()

    val favCreators = creators.filter { c ->
        favorites.any { f -> f.creatorId == c.id }
    }

    Column(modifier = Modifier.fillMaxSize().testTag("favorites_screen")) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(16.dp)
        ) {
            Column {
                Text(
                    text = "My Lens Shortlist",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Ready comparison details for weddings and celebrations events",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (favCreators.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.FavoriteBorder,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "No Saved Photographers",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "Tap the heart icon on creator preview cards to bundle up comparing collections.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(favCreators) { creator ->
                    CreatorRowCard(
                        creator = creator,
                        viewModel = viewModel,
                        onClick = { onNavigateToCreatorDetail(creator.id) }
                    )
                }
            }
        }
    }
}


// ----------------------------------------------------
// DISCUSSION THREAD LIST SCREEN (TAB 4)
// ----------------------------------------------------
@Composable
fun CustomerMessagesListScreen(
    viewModel: FokalViewModel,
    onNavigateToChat: (String) -> Unit
) {
    val chatPartners by viewModel.chatPartners.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize().testTag("messages_list_screen")) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(16.dp)
        ) {
            Column {
                Text(
                    text = "Shoot Chats",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Secure pre-planning, custom requests, and delivery timelines",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        val displayPartners = remember(chatPartners) {
            if (chatPartners.isEmpty()) {
                // If DB is cleared/unlinked, enforce seeing seeded discussions
                listOf("amit_sharma_creator", "riya_sen_creator")
            } else {
                chatPartners
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize()
        ) {
            items(displayPartners) { partnerId ->
                val name = viewModel.getCreatorNameSync(partnerId)
                val city = viewModel.getCreatorCitySync(partnerId)
                val avatar = viewModel.getCreatorAvatarSync(partnerId)

                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onNavigateToChat(partnerId) }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box {
                            AsyncImage(
                                model = avatar,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(54.dp)
                                    .clip(CircleShape),
                                contentScale = ContentScale.Crop
                            )
                            // verified badge dot indicator
                            Box(
                                modifier = Modifier
                                    .size(14.dp)
                                    .clip(CircleShape)
                                    .background(AmberGold)
                                    .align(Alignment.BottomEnd)
                                    .border(1.5.dp, MaterialTheme.colorScheme.surface, CircleShape)
                            )
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = name,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                                Text(
                                    text = "Ready Consult",
                                    fontSize = 10.sp,
                                    color = AmberGold,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Text(
                                text = "Shoot Location: $city Studio",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
                }
            }
        }
    }
}


// ----------------------------------------------------
// FOKAL AI BOT ADVISOR INTEGRATION (TAB 5)
// ----------------------------------------------------
@Composable
fun FokalAIScreen(viewModel: FokalViewModel) {
    val aiText by viewModel.aiResponse.collectAsStateWithLifecycle()
    val isLoading by viewModel.aiLoading.collectAsStateWithLifecycle()
    var prompt by remember { mutableStateOf("") }
    
    val scope = rememberCoroutineScope()
    val keyController = LocalSoftwareKeyboardController.current

    val preloadedPrompts = listOf(
        "Suggest Wedding photographers in Mumbai within 50k",
        "Generate stunning captions for pre-wedding couple walks",
        "Estimate average price for 4 hours maternity studio shoot",
        "Generate standard professional biography for studio"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // App header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(AmberGold.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.SmartToy, contentDescription = null, tint = AmberGold)
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text("Fokal AI Advisor", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text("AI Photographer Matchmaker & Storyboarder", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Divider()

        // Outputs display screen bubble
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(vertical = 12.dp)
        ) {
            if (aiText.isEmpty() && !isLoading) {
                // Empty instruction landing assist UI
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "💡 Ask Fokal AI Anything!",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium,
                            color = AmberGold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Get photographer recommendation matches based on city and pricing, estimate standard coverage rates, generate Instagram captions, or refine bio details cleanly.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Quick Starter Questions:",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                        
                        preloadedPrompts.forEach { item ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .clickable {
                                        prompt = item
                                        keyController?.hide()
                                        viewModel.askFokalAI(item)
                                    },
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.background),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.KeyboardArrowRight, contentDescription = null, tint = AmberGold)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(item, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }
            } else {
                // Content response
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("AI RECOMMENDATION", fontWeight = FontWeight.Black, fontSize = 10.sp, color = AmberGold)
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        if (isLoading) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                CircularProgressIndicator(color = AmberGold)
                                Spacer(modifier = Modifier.height(10.dp))
                                Text("Computing best local quotes...", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        } else {
                            Text(
                                text = aiText,
                                fontSize = 13.sp,
                                lineHeight = 20.sp,
                                modifier = Modifier.testTag("ai_result_text")
                            )
                        }
                    }
                }
            }
        }

        // Search Input controller
        Surface(
            tonalElevation = 2.dp,
            shadowElevation = 4.dp,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = prompt,
                    onValueChange = { prompt = it },
                    placeholder = { Text("Ask recommendations or estimates...") },
                    modifier = Modifier.weight(1f).testTag("ai_prompt_input_field"),
                    maxLines = 2,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AmberGold
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = {
                        if (prompt.isNotEmpty()) {
                            keyController?.hide()
                            viewModel.askFokalAI(prompt)
                        }
                    })
                )

                Spacer(modifier = Modifier.width(6.dp))

                FloatingActionButton(
                    onClick = {
                        if (prompt.isNotEmpty()) {
                            keyController?.hide()
                            viewModel.askFokalAI(prompt)
                        }
                    },
                    containerColor = AmberGold,
                    contentColor = Color.Black,
                    modifier = Modifier.size(50.dp).testTag("ai_send_fab")
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send message")
                }
            }
        }
    }
}


// ----------------------------------------------------
// CREATOR PROFILE DETAILS PREVIEW
// ----------------------------------------------------
@Composable
fun CreatorDetailScreen(
    creatorId: String,
    viewModel: FokalViewModel,
    onBack: () -> Unit,
    onBookNow: () -> Unit,
    onNavigateToChat: () -> Unit
) {
    val portfolioLists by viewModel.activePortfolio.collectAsStateWithLifecycle()
    val reviewLists by viewModel.activeReviews.collectAsStateWithLifecycle()
    
    val blockedDatesMap by viewModel.blockedDatesState.collectAsStateWithLifecycle()
    val manualBlocked = blockedDatesMap[creatorId] ?: emptyList()

    val creatorBookings by viewModel.activeCreatorBookings.collectAsStateWithLifecycle()
    val acceptedBookedDates = creatorBookings
        .filter { it.status == "Accepted" || it.status == "Confirmed" || it.status == "Completed" }
        .map { it.date }

    val selectedDateVal by viewModel.selectedShootDate.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val name = viewModel.getCreatorNameSync(creatorId)
    val city = viewModel.getCreatorCitySync(creatorId)
    val avatar = viewModel.getCreatorAvatarSync(creatorId)

    // Retrieve creator details matching Sync
    var creatorInstance by remember { mutableStateOf<Creator?>(null) }
    LaunchedEffect(creatorId) {
        creatorInstance = viewModel.getCreatorSync(creatorId)
    }

    Scaffold(
        bottomBar = {
            Surface(
                tonalElevation = 8.dp,
                shadowElevation = 12.dp,
                color = MaterialTheme.colorScheme.surface
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = { onNavigateToChat() },
                        modifier = Modifier.weight(1f).height(50.dp).testTag("button_contact_chat")
                    ) {
                        Icon(Icons.Default.Chat, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Chat Plan")
                    }

                    Button(
                        onClick = { onBookNow() },
                        colors = ButtonDefaults.buttonColors(containerColor = AmberGold),
                        modifier = Modifier.weight(1.5f).height(50.dp).testTag("button_book_now")
                    ) {
                        Text("Schedule Shoot", fontWeight = FontWeight.Bold, color = Color.Black)
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
        ) {
            // Profile Glass header cover
            val sharedTransitionScope = LocalSharedTransitionScope.current
            val animatedVisibilityScope = LocalAnimatedVisibilityScope.current

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
            ) {
                // Background cover Preview based on creators
                val coverPic = when (creatorId) {
                    "riya_sen_creator" -> "https://images.unsplash.com/photo-1490481651871-ab68de25d43d?auto=format&fit=crop&w=500&q=80"
                    "kabir_singh_creator" -> "https://images.unsplash.com/photo-1469334031218-e382a71b716b?auto=format&fit=crop&w=500&q=80"
                    else -> "https://images.unsplash.com/photo-1511285560929-80b456fea0bc?auto=format&fit=crop&w=500&q=80"
                }

                val coverModifier = Modifier.fillMaxSize()
                val finalCoverModifier = if (sharedTransitionScope != null && animatedVisibilityScope != null) {
                    with(sharedTransitionScope) {
                        coverModifier.sharedElement(
                            rememberSharedContentState(key = "banner_${creatorId}"),
                            animatedVisibilityScope = animatedVisibilityScope
                        )
                    }
                } else {
                    coverModifier
                }

                AsyncImage(
                    model = coverPic,
                    contentDescription = null,
                    modifier = finalCoverModifier,
                    contentScale = ContentScale.Crop
                )
                Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)))

                // Header toolbar action
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { onBack() },
                        modifier = Modifier.background(Color.Black.copy(alpha = 0.4f), CircleShape)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }

                    IconButton(
                        onClick = { viewModel.toggleFavorite(creatorId) },
                        modifier = Modifier.background(Color.Black.copy(alpha = 0.4f), CircleShape)
                    ) {
                        val isFav by viewModel.isCreatorFavorite(creatorId).collectAsStateWithLifecycle(initialValue = false)
                        Icon(
                            imageVector = if (isFav) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = null,
                            tint = if (isFav) Color.Red else Color.White
                        )
                    }
                }

                // Avatar and essential brief overlap
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(16.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    val avatarModifier = Modifier
                        .size(76.dp)
                        .clip(CircleShape)
                        .border(2.dp, AmberGold, CircleShape)
                    val finalAvatarModifier = if (sharedTransitionScope != null && animatedVisibilityScope != null) {
                        with(sharedTransitionScope) {
                            avatarModifier.sharedElement(
                                rememberSharedContentState(key = "avatar_${creatorId}"),
                                animatedVisibilityScope = animatedVisibilityScope
                            )
                        }
                    } else {
                        avatarModifier
                    }

                    AsyncImage(
                        model = avatar,
                        contentDescription = null,
                        modifier = finalAvatarModifier,
                        contentScale = ContentScale.Crop
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val nameModifier = Modifier
                            val finalNameModifier = if (sharedTransitionScope != null && animatedVisibilityScope != null) {
                                with(sharedTransitionScope) {
                                    nameModifier.sharedElement(
                                        rememberSharedContentState(key = "name_${creatorId}"),
                                        animatedVisibilityScope = animatedVisibilityScope
                                    )
                                }
                            } else {
                                nameModifier
                            }

                            Text(
                                text = name, 
                                fontWeight = FontWeight.Bold, 
                                fontSize = 20.sp, 
                                color = Color.White,
                                modifier = finalNameModifier
                            )
                            if (creatorInstance?.verified == true) {
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(Icons.Default.Verified, contentDescription = null, tint = AmberGold, modifier = Modifier.size(18.dp))
                            }
                        }
                        Text("$city Studio • Independent", fontSize = 12.sp, color = Color.White.copy(alpha = 0.8f))
                    }
                }
            }

            // Body text fields
            Column(modifier = Modifier.padding(16.dp)) {
                // Ratings and pricing row banner
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("REP SCORE", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Star, contentDescription = null, tint = AmberGold, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(2.dp))
                                Text("${creatorInstance?.rating ?: 4.8} / 5", fontWeight = FontWeight.Black, fontSize = 14.sp)
                            }
                        }
                        Divider(modifier = Modifier.height(24.dp).width(1.dp))
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("EXPERIENCE", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
                            Text("${creatorInstance?.yearsOfExperience ?: 5} Years", fontWeight = FontWeight.Black, fontSize = 14.sp)
                        }
                        Divider(modifier = Modifier.height(24.dp).width(1.dp))
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("STARTING FEE", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
                            Text("₹${creatorInstance?.startingPrice?.toInt() ?: 20000}", fontWeight = FontWeight.Black, fontSize = 14.sp, color = AmberGold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // About section biography
                Text("About", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = creatorInstance?.bio ?: "Professional memory creator capturing unscripted, natural human interactions beautifully.",
                    fontSize = 13.sp,
                    lineHeight = 20.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )

                // Specialist Skillsets Section
                val skillList = (creatorInstance?.skillset ?: "Photographer").split(",").map { it.trim() }.filter { it.isNotEmpty() }
                if (skillList.isNotEmpty()) {
                    Text("Verified Skillsets", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        skillList.forEach { skill ->
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(AmberGold.copy(alpha = 0.12f))
                                    .border(1.dp, AmberGold.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = when (skill) {
                                            "Photographer" -> Icons.Default.PhotoCamera
                                            "Videographer" -> Icons.Default.Videocam
                                            else -> Icons.Default.Movie
                                        },
                                        contentDescription = null,
                                        tint = AmberGold,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(skill, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                                }
                            }
                        }
                    }
                }

                // Connected Socials Section
                Text("Connected Socials", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val instagramUser = creatorInstance?.instagram ?: ""
                    val youtubeUrl = creatorInstance?.youtube ?: ""
                    val webUrl = creatorInstance?.website ?: ""

                    if (instagramUser.isNotEmpty()) {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .clickable { /* Open Instagram simulation */ }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.PhotoCamera, contentDescription = null, tint = AmberGold, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("@$instagramUser", fontSize = 11.sp, fontWeight = FontWeight.Medium)
                        }
                    }

                    if (youtubeUrl.isNotEmpty()) {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .clickable { /* Open YouTube simulation */ }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Videocam, contentDescription = null, tint = Color.Red, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("YouTube", fontSize = 11.sp, fontWeight = FontWeight.Medium)
                        }
                    }

                    if (webUrl.isNotEmpty()) {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .clickable { /* Open website simulation */ }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Language, contentDescription = null, tint = AmberGold, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(webUrl.removePrefix("www.").substringBefore("/"), fontSize = 11.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Geashing tools equipment and language specifications
                Text("Logistics", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("EQUIPMENT", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
                        Text(creatorInstance?.equipment ?: "Sony Mirrorless & Cine Setups", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text("LANGUAGES Spoken", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
                        Text(creatorInstance?.languages ?: "English, Hindi", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Real-Time Calendar Availability Widget
                Text("Photographer Slot Availability", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "Tap a green slot to select your desired booking day instantly.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                var currentMonthSelected by remember { mutableStateOf("October 2026") }
                val monthDates = if (currentMonthSelected == "October 2026") {
                    // October 1, 2026 is a Thursday (4 blank starting items)
                    List(4) { "" } + List(31) { (it + 1).toString() }
                } else {
                    // November 1, 2026 is a Sunday (0 blank starting items)
                    List(30) { (it + 1).toString() }
                }

                val yearStr = "2026"
                val monthNumericStr = if (currentMonthSelected == "October 2026") "10" else "11"

                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        // Month Toggle Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = currentMonthSelected,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 14.sp,
                                color = AmberGold
                            )

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                val monthOpts = listOf("October 2026", "November 2026")
                                monthOpts.forEach { m ->
                                    val isMSelected = currentMonthSelected == m
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(if (isMSelected) AmberGold else MaterialTheme.colorScheme.surface)
                                            .clickable { currentMonthSelected = m }
                                            .padding(horizontal = 8.dp, vertical = 6.dp)
                                    ) {
                                        Text(
                                            text = m.split(" ")[0].substring(0, 3), // Oct, Nov
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isMSelected) Color.Black else MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Days of week row header
                        val weekdays = listOf("Su", "Mo", "Tu", "We", "Th", "Fr", "Sa")
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            weekdays.forEach { dayOfWeek ->
                                Text(
                                    text = dayOfWeek,
                                    modifier = Modifier.weight(1f),
                                    textAlign = TextAlign.Center,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Days grid
                        val chunkedDays = monthDates.chunked(7)
                        chunkedDays.forEach { week ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                week.forEach { day ->
                                    if (day.isEmpty()) {
                                        Box(modifier = Modifier.weight(1f))
                                    } else {
                                        val padDay = if (day.length == 1) "0$day" else day
                                        val dateStr = "2026-$monthNumericStr-$padDay"
                                        val isManualBlocked = manualBlocked.contains(dateStr)
                                        val isBookedFilled = acceptedBookedDates.contains(dateStr)
                                        val isUnavailable = isManualBlocked || isBookedFilled
                                        val isSelected = selectedDateVal == dateStr

                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .aspectRatio(1f)
                                                .padding(2.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(
                                                    when {
                                                        isSelected -> AmberGold
                                                        isBookedFilled -> Color.Red.copy(alpha = 0.25f)
                                                        isManualBlocked -> Color.Red.copy(alpha = 0.15f)
                                                        else -> MaterialTheme.colorScheme.surface
                                                    }
                                                )
                                                .border(
                                                    width = 1.dp,
                                                    color = when {
                                                        isSelected -> AmberGold
                                                        isUnavailable -> Color.Red.copy(alpha = 0.4f)
                                                        else -> Color.Transparent
                                                    },
                                                    shape = RoundedCornerShape(8.dp)
                                                )
                                                .clickable {
                                                    if (isUnavailable) {
                                                        val reason = if (isBookedFilled) "Fully Booked (Accepted Shoot)" else "Blocked by Photographer"
                                                        Toast.makeText(context, "$currentMonthSelected $day is $reason", Toast.LENGTH_SHORT).show()
                                                    } else {
                                                        viewModel.selectedShootDate.value = dateStr
                                                        Toast.makeText(context, "Selected Slot: $dateStr", Toast.LENGTH_SHORT).show()
                                                    }
                                                }
                                                .testTag("calendar_day_${dateStr}"),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                Text(
                                                    text = day,
                                                    fontSize = 12.sp,
                                                    fontWeight = if (isSelected || isUnavailable) FontWeight.Bold else FontWeight.Medium,
                                                    color = when {
                                                        isSelected -> Color.Black
                                                        isUnavailable -> Color.Red
                                                        else -> MaterialTheme.colorScheme.onSurface
                                                    }
                                                )
                                                if (isUnavailable) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(4.dp)
                                                            .clip(CircleShape)
                                                            .background(Color.Red)
                                                    )
                                                } else if (isSelected) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(4.dp)
                                                            .clip(CircleShape)
                                                            .background(Color.Black)
                                                    )
                                                } else {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(4.dp)
                                                            .clip(CircleShape)
                                                            .background(Color(0xFF4CAF50))
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                                // If the week has fewer than 7 slots (last week), fill with space
                                if (week.size < 7) {
                                    repeat(7 - week.size) {
                                        Box(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Calendar Legend / Guidelines Indicator
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Color(0xFF4CAF50)))
                                Text("Available", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Color.Red))
                                Text("Unavailable / Booked", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(AmberGold))
                                Text("Selected", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }

                        Divider(modifier = Modifier.padding(vertical = 12.dp))

                        Text(
                            text = "Selected Slot: $selectedDateVal",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = AmberGold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Portfolio visual gallery (Instagram-like scroll grid)
                Text("Portfolio Gallery", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                
                if (portfolioLists.isEmpty()) {
                    Text("No uploads published yet.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        portfolioLists.forEach { p ->
                            Card(
                                modifier = Modifier
                                    .size(140.dp)
                                    .clip(RoundedCornerShape(12.dp))
                            ) {
                                Box(modifier = Modifier.fillMaxSize()) {
                                    AsyncImage(
                                        model = p.mediaUrl,
                                        contentDescription = p.title,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .align(Alignment.BottomStart)
                                            .background(Color.Black.copy(alpha = 0.5f))
                                            .padding(6.dp)
                                    ) {
                                        Text(
                                            text = p.category,
                                            color = Color.White,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Verification reviews
                Text("Verified Reviews (${reviewLists.size})", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                
                if (reviewLists.isEmpty()) {
                    Text("No review history published yet.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Column(
                        modifier = Modifier.padding(top = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        reviewLists.forEach { r ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(r.customerName, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.Star, contentDescription = null, tint = AmberGold, modifier = Modifier.size(12.dp))
                                            Text("${r.rating}", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                    Text(
                                        r.review,
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(top = 4.dp),
                                        lineHeight = 16.sp
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }
}


// ----------------------------------------------------
// SCHEDULING CALENDAR SELECTION
// ----------------------------------------------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookingSchedulerScreen(
    viewModel: FokalViewModel,
    onBack: () -> Unit,
    onProceedToCheckout: () -> Unit
) {
    val creatorId = viewModel.selectedCreatorId.value ?: "amit_sharma_creator"
    val creatorName = viewModel.getCreatorNameSync(creatorId)

    val initialDate by viewModel.selectedShootDate.collectAsStateWithLifecycle()
    var shootDate by remember(initialDate) { mutableStateOf(initialDate) }
    var shootTime by remember { mutableStateOf("10:00") }
    var selectedHours by remember { mutableIntStateOf(4) }
    var chosenPackage by remember { mutableStateOf("Standard") } // Basic, Standard, Premium
    var locationInput by remember { mutableStateOf("Mumbai") }

    val selectedServices = remember { mutableStateListOf("Photo") }
    var estimatedBasePrice by remember { mutableStateOf(20000.0) }

    // Dynamically calculate price based on configurations
    LaunchedEffect(chosenPackage, selectedHours, creatorId, selectedServices.size) {
        val baseFee = when (creatorId) {
            "amit_sharma_creator" -> 45000.0
            "riya_sen_creator" -> 30000.0
            "kabir_singh_creator" -> 75000.0
            "vikram_goa_creator" -> 12000.0
            else -> 25000.0
        }
        val packageMultiplier = when (chosenPackage) {
            "Basic" -> 0.8
            "Standard" -> 1.0
            else -> 1.5 // Premium
        }
        val hourMultiplier = if (selectedHours > 4) 1.2 else 1.0
        val serviceMultiplier = when (selectedServices.size) {
            1 -> 1.0
            2 -> 1.4
            3 -> 1.8
            else -> 1.0
        }
        estimatedBasePrice = baseFee * packageMultiplier * hourMultiplier * serviceMultiplier
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Configure Shoot Details") },
                navigationIcon = {
                    IconButton(onClick = { onBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Text(
                text = "Scheduling with $creatorName",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = AmberGold
            )
            Text(
                text = "Pick options to dynamically generate invoice quotes.",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Choose Services selection
            Text("Services Needed (Pick multiple/all)", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            val serviceOptions = listOf("Photo", "Video", "Reel Creator")
            Row(
                modifier = Modifier.padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                serviceOptions.forEach { service ->
                    val isChecked = selectedServices.contains(service)
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .border(
                                width = if (isChecked) 2.dp else 1.dp,
                                color = if (isChecked) AmberGold else MaterialTheme.colorScheme.outline.copy(alpha = 0.1f),
                                shape = RoundedCornerShape(12.dp)
                            )
                            .clickable {
                                if (isChecked) {
                                    if (selectedServices.size > 1) selectedServices.remove(service)
                                } else {
                                    selectedServices.add(service)
                                }
                            },
                        colors = CardDefaults.cardColors(
                            containerColor = if (isChecked) AmberGold.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp).fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = when (service) {
                                    "Photo" -> Icons.Default.PhotoCamera
                                    "Video" -> Icons.Default.Videocam
                                    else -> Icons.Default.Movie
                                },
                                contentDescription = null,
                                tint = if (isChecked) AmberGold else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(service, fontWeight = FontWeight.Bold, fontSize = 11.sp, textAlign = TextAlign.Center)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Choose booking package
            Text("Select Package Bundle", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            val packTypes = listOf("Basic", "Standard", "Premium")
            Row(
                modifier = Modifier.padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                packTypes.forEach { pkg ->
                    val isSelected = chosenPackage == pkg
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .border(
                                width = if (isSelected) 2.dp else 1.dp,
                                color = if (isSelected) AmberGold else MaterialTheme.colorScheme.outline.copy(alpha = 0.1f),
                                shape = RoundedCornerShape(12.dp)
                            )
                            .clickable { chosenPackage = pkg },
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(pkg, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text(
                                text = when (pkg) {
                                    "Basic" -> "4 hrs, 20 edits"
                                    "Standard" -> "8 hrs, Drone, RAW"
                                    else -> "Multi-day, Albums"
                                },
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Date Selection input
            Text("Pick Date (Golden Hour)", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            val dates = (listOf("2026-10-14", "2026-10-15", "2026-10-16", "2026-11-02", "2026-11-03") + shootDate).distinct().sorted()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                dates.forEach { d ->
                    val isSelected = shootDate == d
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isSelected) AmberGold else MaterialTheme.colorScheme.surfaceVariant)
                            .clickable { shootDate = d }
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        Text(
                            text = d,
                            fontWeight = FontWeight.Bold,
                            color = if (isSelected) Color.Black else MaterialTheme.colorScheme.onSurface,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Coverage Hours Selector
            Text("Coverage Duration", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val hourOptions = listOf(2, 4, 8, 12)
                hourOptions.forEach { hrs ->
                    val isSelected = selectedHours == hrs
                    OutlinedIconChip(
                        selected = isSelected,
                        onClick = { selectedHours = hrs },
                        label = "$hrs Hours"
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Venue location Address
            Text("Shoot Location Address", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            OutlinedTextField(
                value = locationInput,
                onValueChange = { locationInput = it },
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).testTag("input_venue_location"),
                placeholder = { Text("e.g. Gateway Taj, Mumbai") },
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AmberGold)
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Live pricing summary invoice card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("LIVE PRICING BREAKDOWN", fontSize = 11.sp, fontWeight = FontWeight.Black, color = AmberGold)
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Base Shoot Rate ($chosenPackage)", fontSize = 13.sp)
                        Text("₹${estimatedBasePrice.toInt()}", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                    
                    Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Travel & logistics cover", fontSize = 13.sp)
                        Text("₹2,500", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    }

                    Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Platform charge (GST 18%)", fontSize = 13.sp)
                        val gst = (estimatedBasePrice + 2500.0) * 0.18
                        Text("₹${gst.toInt()}", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    }

                    Divider(modifier = Modifier.padding(vertical = 12.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Estimated Total Amount", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        val finalCost = (estimatedBasePrice + 2500.0) * 1.18
                        Text("₹${finalCost.toInt()}", fontWeight = FontWeight.Black, fontSize = 18.sp, color = AmberGold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = {
                    val finalCost = (estimatedBasePrice + 2500.0) * 1.18
                    viewModel.createBooking(
                        eventType = selectedServices.joinToString(" + "),
                        date = shootDate,
                        time = shootTime,
                        hours = selectedHours,
                        packageType = "$chosenPackage (${selectedServices.joinToString(", ")})",
                        totalCost = finalCost
                    )
                    onProceedToCheckout()
                },
                colors = ButtonDefaults.buttonColors(containerColor = AmberGold),
                modifier = Modifier.fillMaxWidth().height(52.dp).testTag("button_confirm_checkout_nav")
            ) {
                Text("Proceed to Checkout", fontWeight = FontWeight.Bold, color = Color.Black, fontSize = 16.sp)
            }
        }
    }
}

@Composable
fun OutlinedIconChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: String
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) AmberGold else MaterialTheme.colorScheme.surfaceVariant)
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(
            text = label,
            fontWeight = FontWeight.Bold,
            color = if (selected) Color.Black else MaterialTheme.colorScheme.onSurface,
            fontSize = 12.sp
        )
    }
}


// ----------------------------------------------------
// SECURE CHECKOUT & BILLING INVOICE
// ----------------------------------------------------
@Composable
fun CheckoutScreen(
    viewModel: FokalViewModel,
    onBack: () -> Unit,
    onSuccess: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var paymentOption by remember { mutableStateOf("UPI") } // UPI, Credit/Debit Card, Net Banking
    var couponInput by remember { mutableStateOf("") }
    var promoDiscount by remember { mutableStateOf(0.0) }
    
    // Choose deposit vs full amount
    var selectedAmountType by remember { mutableStateOf("Full") } // Deposit, Full

    // Interactive details state
    var cardName by remember { mutableStateOf("") }
    var cardNumber by remember { mutableStateOf("") }
    var cardExpiry by remember { mutableStateOf("") }
    var cardCVV by remember { mutableStateOf("") }
    var upiId by remember { mutableStateOf("") }
    var selectedBank by remember { mutableStateOf("HDFC Bank") }
    var generateQrCheck by remember { mutableStateOf(false) }

    // Live processing simulator setup
    var isProcessing by remember { mutableStateOf(false) }
    var processingStep by remember { mutableStateOf("") }
    var progressVal by remember { mutableStateOf(0f) }
    var showSuccessDialog by remember { mutableStateOf(false) }
    var finalReceiptTransactionId by remember { mutableStateOf("") }
    var receiptAmountPaid by remember { mutableStateOf(0.0) }

    val bookings by viewModel.bookingsList.collectAsStateWithLifecycle()
    val latestBooking = remember(bookings) { bookings.firstOrNull() }
    val context = LocalContext.current

    val originalPrice = latestBooking?.price ?: 22500.0
    val totalWithDiscount = maxOf(0.0, originalPrice - promoDiscount)
    val depositAmount = totalWithDiscount * 0.3
    val payableAmount = if (selectedAmountType == "Deposit") depositAmount else totalWithDiscount

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Secure Checkout", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { onBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                // SSL Encryption Badge Card
                Card(
                    modifier = Modifier.fillMaxWidth().testTag("checkout_encrypted_badge"),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, AmberGold.copy(alpha = 0.25f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Lock, contentDescription = "SSL Verified", tint = AmberGold, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("🔒 256-BIT SECURE STRIPE GATEWAY", fontSize = 11.sp, fontWeight = FontWeight.Black, color = AmberGold)
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "To authorize the Fokal Action slot booking, finalize payment below. Your funds are held securely until the shoot is complete.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Booking invoice breaking panel
                Text("Booking Invoice", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Service Speciality Requested:")
                            Text(latestBooking?.eventType ?: "Creative Photography", fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Execution Date:")
                            Text(latestBooking?.date ?: "Pending Scheduling", fontWeight = FontWeight.SemiBold)
                        }
                        Divider(modifier = Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Package Base Cost")
                            Text("₹${originalPrice.toInt()}")
                        }
                        if (promoDiscount > 0) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Promotional Discount", color = Color(0xFF4CAF50))
                                Text("- ₹${promoDiscount.toInt()}", color = Color(0xFF4CAF50), fontWeight = FontWeight.Bold)
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Platform Protection & GST", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("Inclusive", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Divider(modifier = Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Total Project Valuation", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Text("₹${totalWithDiscount.toInt()}", fontWeight = FontWeight.Black, color = AmberGold, fontSize = 16.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Pay deposits or full amounts option switcher
                Text("Choose Settlement Amount", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Deposit option (30%)
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { selectedAmountType = "Deposit" }
                            .border(
                                width = if (selectedAmountType == "Deposit") 2.dp else 1.dp,
                                color = if (selectedAmountType == "Deposit") AmberGold else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f),
                                shape = RoundedCornerShape(12.dp)
                            ),
                        colors = CardDefaults.cardColors(
                            containerColor = if (selectedAmountType == "Deposit") AmberGold.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(
                                    selected = selectedAmountType == "Deposit",
                                    onClick = { selectedAmountType = "Deposit" },
                                    colors = RadioButtonDefaults.colors(selectedColor = AmberGold)
                                )
                                Text("Pay Deposit", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Secure 30% booking advance", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.height(6.dp))
                            Text("₹${depositAmount.toInt()}", fontWeight = FontWeight.Black, color = AmberGold, fontSize = 15.sp)
                        }
                    }

                    // Full Amount option (100%)
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { selectedAmountType = "Full" }
                            .border(
                                width = if (selectedAmountType == "Full") 2.dp else 1.dp,
                                color = if (selectedAmountType == "Full") AmberGold else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f),
                                shape = RoundedCornerShape(12.dp)
                            ),
                        colors = CardDefaults.cardColors(
                            containerColor = if (selectedAmountType == "Full") AmberGold.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(
                                    selected = selectedAmountType == "Full",
                                    onClick = { selectedAmountType = "Full" },
                                    colors = RadioButtonDefaults.colors(selectedColor = AmberGold)
                                )
                                Text("Full Amount", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Settle entire shoot fee", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.height(6.dp))
                            Text("₹${totalWithDiscount.toInt()}", fontWeight = FontWeight.Black, color = AmberGold, fontSize = 15.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Payment Methods Options Setup
                Text("Select Payment Gateway", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                val payMethods = listOf("UPI", "Credit/Debit Card", "Net Banking")
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    payMethods.forEach { method ->
                        val isSelected = paymentOption == method
                        Card(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { paymentOption = method }
                                .border(
                                    width = if (isSelected) 2.dp else 1.dp,
                                    color = if (isSelected) AmberGold else MaterialTheme.colorScheme.outline.copy(alpha = 0.1f),
                                    shape = RoundedCornerShape(12.dp)
                                ),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp).fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = when (method) {
                                        "UPI" -> Icons.Default.QrCode
                                        "Credit/Debit Card" -> Icons.Default.CreditCard
                                        else -> Icons.Default.AccountBalance
                                    },
                                    contentDescription = null,
                                    tint = if (isSelected) AmberGold else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(method, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // DYNAMIC INTEGRATED FIELDS BASED ON SELECTION
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        when (paymentOption) {
                            "UPI" -> {
                                Text("Popular UPI Quick Actions", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    val quickUPIs = listOf("GPay", "PhonePe", "Paytm")
                                    quickUPIs.forEach { clientApp ->
                                        Button(
                                            onClick = { upiId = "pay.fokal@okhttp" + clientApp.lowercase() },
                                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                            modifier = Modifier.weight(1f).height(36.dp)
                                        ) {
                                            Text(clientApp, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }

                                OutlinedTextField(
                                    value = upiId,
                                    onValueChange = { upiId = it },
                                    label = { Text("Virtual Payment Address (VPA)") },
                                    placeholder = { Text("e.g. mobile@okaxis") },
                                    leadingIcon = { Icon(Icons.Default.PhoneAndroid, contentDescription = null, tint = AmberGold) },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AmberGold)
                                )

                                Spacer(modifier = Modifier.height(12.dp))

                                // Or dynamic QR generation simulation
                                Button(
                                    onClick = { generateQrCheck = !generateQrCheck },
                                    modifier = Modifier.wrapContentSize(),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                                ) {
                                    Icon(Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(if (generateQrCheck) "Hide Live Payment QR" else "Generate Dynamic Payment QR", fontSize = 11.sp)
                                }

                                if (generateQrCheck) {
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Column(
                                        modifier = Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(12.dp)).padding(16.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text("FOKALPOINT UPI MERCHANT PORTAL", fontSize = 9.sp, fontWeight = FontWeight.Black, color = Color.Gray)
                                        Spacer(modifier = Modifier.height(8.dp))
                                        
                                        // Visual mock of dynamic QR
                                        Box(
                                            modifier = Modifier.size(140.dp).border(2.dp, AmberGold, RoundedCornerShape(8.dp)).padding(8.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Canvas(modifier = Modifier.size(120.dp)) {
                                                // Dynamic patterns for barcode/QR mock
                                                drawRect(color = Color.Black, size = Size(35f, 35f), topLeft = Offset(0f, 0f))
                                                drawRect(color = Color.Black, size = Size(35f, 35f), topLeft = Offset(size.width - 35f, 0f))
                                                drawRect(color = Color.Black, size = Size(35f, 35f), topLeft = Offset(0f, size.height - 35f))
                                                
                                                // Outer grids
                                                drawRect(color = Color.Black, size = Size(10f, 10f), topLeft = Offset(50f, 50f))
                                                drawRect(color = Color.Black, size = Size(14f, 14f), topLeft = Offset(80f, 60f))
                                                drawRect(color = Color.Black, size = Size(8f, 25f), topLeft = Offset(20f, 75f))
                                                drawRect(color = Color.Black, size = Size(25f, 8f), topLeft = Offset(75f, 30f))
                                                drawRect(color = Color.Black, size = Size(15f, 15f), topLeft = Offset(100f, 100f))
                                                drawRect(color = Color.Black, size = Size(30f, 10f), topLeft = Offset(45f, 110f))
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(10.dp))
                                        Text("Scan QR Code using GPay, PhonePe or any banking app to settle ₹${payableAmount.toInt()}", fontSize = 10.sp, color = Color.DarkGray, textAlign = TextAlign.Center)
                                    }
                                }
                            }
                            "Credit/Debit Card" -> {
                                Text("Stripe Credit Card Portal", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                Spacer(modifier = Modifier.height(10.dp))

                                // Beautiful digital credit card mock
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(150.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(
                                            Brush.verticalGradient(
                                                colors = listOf(Color(0xFF1E1E1E), Color(0xFF333333))
                                            )
                                        )
                                        .padding(16.dp)
                                ) {
                                    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text("fokal point.", fontWeight = FontWeight.Bold, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic, color = Color.White)
                                            Icon(Icons.Default.CreditCard, contentDescription = null, tint = AmberGold)
                                        }
                                        Text(
                                            text = cardNumber.ifEmpty { "••••  ••••  ••••  ••••" },
                                            color = Color.White,
                                            fontSize = 18.sp,
                                            letterSpacing = 2.sp,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = FontFamily.Monospace
                                        )
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Column {
                                                Text("CARDHOLDER", fontSize = 8.sp, color = Color.Gray)
                                                Text(cardName.ifEmpty { "SAMPLE USER" }.uppercase(), fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold)
                                            }
                                            Column(horizontalAlignment = Alignment.End) {
                                                Text("EXPIRES", fontSize = 8.sp, color = Color.Gray)
                                                Text(cardExpiry.ifEmpty { "MM/YY" }, fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(14.dp))

                                OutlinedTextField(
                                    value = cardNumber,
                                    onValueChange = { input ->
                                        // Simple card space injection formatting
                                        val digits = input.filter { it.isDigit() }
                                        val formatted = buildString {
                                            for (i in digits.indices) {
                                                append(digits[i])
                                                if ((i + 1) % 4 == 0 && i + 1 < digits.length && i < 15) {
                                                    append("  ")
                                                }
                                            }
                                        }
                                        if (digits.length <= 16) {
                                            cardNumber = formatted
                                        }
                                    },
                                    label = { Text("Card Number") },
                                    placeholder = { Text("4111 2222 3333 4444") },
                                    leadingIcon = { Icon(Icons.Default.Payment, contentDescription = null, tint = AmberGold) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AmberGold)
                                )

                                Spacer(modifier = Modifier.height(10.dp))

                                OutlinedTextField(
                                    value = cardName,
                                    onValueChange = { cardName = it },
                                    label = { Text("Cardholder Name") },
                                    placeholder = { Text("John Doe") },
                                    leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, tint = AmberGold) },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AmberGold)
                                )

                                Spacer(modifier = Modifier.height(10.dp))

                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    OutlinedTextField(
                                        value = cardExpiry,
                                        onValueChange = { input ->
                                            val cleaned = input.filter { it.isDigit() }
                                            if (cleaned.length <= 4) {
                                                cardExpiry = if (cleaned.length > 2) {
                                                    cleaned.substring(0, 2) + "/" + cleaned.substring(2)
                                                } else {
                                                    cleaned
                                                }
                                            }
                                        },
                                        label = { Text("Expires (MM/YY)") },
                                        placeholder = { Text("12/28") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        singleLine = true,
                                        modifier = Modifier.weight(1.2f),
                                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AmberGold)
                                    )

                                    OutlinedTextField(
                                        value = cardCVV,
                                        onValueChange = { input -> 
                                            val cleaned = input.filter { it.isDigit() }
                                            if (cleaned.length <= 3) cardCVV = cleaned
                                        },
                                        label = { Text("CVV") },
                                        placeholder = { Text("777") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        singleLine = true,
                                        modifier = Modifier.weight(0.8f),
                                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AmberGold)
                                    )
                                }
                            }
                            else -> {
                                Text("Popular Net Banking Integrations", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                Spacer(modifier = Modifier.height(8.dp))
                                val banksList = listOf("HDFC Bank", "ICICI Bank", "State Bank of India", "Axis Bank")
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    banksList.forEach { bank ->
                                        val isChecked = selectedBank == bank
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(8.dp))
                                                .border(1.dp, if (isChecked) AmberGold else Color.Transparent)
                                                .background(if (isChecked) AmberGold.copy(alpha = 0.08f) else Color.Transparent)
                                                .clickable { selectedBank = bank }
                                                .padding(horizontal = 10.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            RadioButton(
                                                selected = isChecked,
                                                onClick = { selectedBank = bank },
                                                colors = RadioButtonDefaults.colors(selectedColor = AmberGold)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Icon(Icons.Default.AccountBalance, contentDescription = null, tint = if (isChecked) AmberGold else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(bank, fontSize = 12.sp, fontWeight = if (isChecked) FontWeight.Bold else FontWeight.Normal)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Optional Promo code section
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = couponInput,
                        onValueChange = { couponInput = it },
                        placeholder = { Text("Coupon e.g. FIRST10") },
                        modifier = Modifier.weight(1f).testTag("coupon_input_field"),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AmberGold)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (couponInput.uppercase() == "FIRST10") {
                                promoDiscount = 1500.0
                                Toast.makeText(context, "Promo Applied!", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "Invalid Coupon", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AmberGold)
                    ) {
                        Text("Apply", color = Color.Black)
                    }
                }

                if (promoDiscount > 0) {
                    Text(
                        text = "✓ Promo code 'FIRST10' applied successfully! Saved ₹1,500.",
                        color = Color(0xFF4CAF50),
                        fontSize = 11.sp,
                        modifier = Modifier.padding(top = 4.dp),
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // The complete payment click CTA
                val currentAmountTypeVal = selectedAmountType
                Button(
                    onClick = {
                        // Validate inputs
                        if (paymentOption == "UPI" && upiId.isEmpty() && !generateQrCheck) {
                            Toast.makeText(context, "Please enter your UPI ID or generate a static QR code.", Toast.LENGTH_LONG).show()
                            return@Button
                        }
                        if (paymentOption == "Credit/Debit Card" && (cardNumber.length < 10 || cardExpiry.isEmpty() || cardCVV.length < 3)) {
                            Toast.makeText(context, "Please complete your Stripe card details (Number, Expiry, CVV).", Toast.LENGTH_LONG).show()
                            return@Button
                        }

                        // Start payment pipeline processing simulation!
                        isProcessing = true
                        scope.launch {
                            progressVal = 0.15f
                            processingStep = "Establishing secure TLS handshake with Stripe gateway..."
                            kotlinx.coroutines.delay(1200)

                            progressVal = 0.45f
                            processingStep = if (paymentOption == "UPI") {
                                "Pushing authorization ping request directly to UPI VPA client..."
                            } else {
                                "Tokenizing credit card parameters with Stripe secure APIs..."
                            }
                            kotlinx.coroutines.delay(1300)

                            progressVal = 0.8f
                            processingStep = "Processing settlement balance and acquiring approval signature..."
                            kotlinx.coroutines.delay(1200)

                            progressVal = 1f
                            processingStep = "Double Entry Ledgers Updated. Synced Fokal point status to DB..."

                            // Call ViewModel DB updates
                            val newPaymentStatusTag = if (currentAmountTypeVal == "Deposit") "Paid Deposit (30%)" else "Paid (Full)"
                            if (latestBooking != null) {
                                viewModel.confirmBookingPayment(
                                    bookingId = latestBooking.id,
                                    paymentStatus = newPaymentStatusTag,
                                    newStatus = "Confirmed"
                                )
                            }
                            receiptAmountPaid = payableAmount
                            finalReceiptTransactionId = "TXN" + (100000..999999).random() + "FPS" + (10..99).random()

                            kotlinx.coroutines.delay(700)
                            isProcessing = false
                            showSuccessDialog = true
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AmberGold),
                    modifier = Modifier.fillMaxWidth().height(52.dp).testTag("button_complete_payment")
                ) {
                    Text(
                        text = "Pay ₹${payableAmount.toInt()} using $paymentOption",
                        fontWeight = FontWeight.Black,
                        color = Color.Black,
                        fontSize = 16.sp
                    )
                }
            }

            // Real TLS Processing overlay screen
            if (isProcessing) {
                Surface(
                    color = Color.Black.copy(alpha = 0.85f),
                    modifier = Modifier.fillMaxSize()
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(
                            color = AmberGold,
                            modifier = Modifier.size(54.dp),
                            strokeWidth = 4.dp
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Text(
                            text = "SECURELY DEPLOYING PAYMENT",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        LinearProgressIndicator(
                            progress = { progressVal },
                            modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                            color = AmberGold,
                            trackColor = Color.DarkGray
                        )
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = processingStep,
                            color = Color.LightGray,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center,
                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                        )
                    }
                }
            }

            // Success receipt dialog
            if (showSuccessDialog) {
                AlertDialog(
                    onDismissRequest = {
                        showSuccessDialog = false
                        onSuccess()
                    },
                    modifier = Modifier.testTag("payment_success_dialog"),
                    icon = {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Success",
                            tint = Color(0xFF4CAF50),
                            modifier = Modifier.size(56.dp)
                        )
                    },
                    title = {
                        Text(
                            "Booking Secured!",
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    },
                    text = {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                "Your payment transaction has completed and settled successfully.",
                                textAlign = TextAlign.Center,
                                fontSize = 12.sp,
                                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            // Summary panel
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Transaction Reference:", fontSize = 11.sp, color = Color.Gray)
                                Text(finalReceiptTransactionId, fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Settled Amount:", fontSize = 11.sp, color = Color.Gray)
                                Text("₹${receiptAmountPaid.toInt()}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = AmberGold)
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Paid Scope:", fontSize = 11.sp, color = Color.Gray)
                                Text(if (selectedAmountType == "Deposit") "Deposit Only (30%)" else "Entire Shoot Fee Paid", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF4CAF50))
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Channel Gateway:", fontSize = 11.sp, color = Color.Gray)
                                Text(paymentOption, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Scheduler Status:", fontSize = 11.sp, color = Color.Gray)
                                Text("CONFIRMED", fontSize = 11.sp, fontWeight = FontWeight.Black, color = Color(0xFF4CAF50))
                            }
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                showSuccessDialog = false
                                onSuccess()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = AmberGold),
                            modifier = Modifier.fillMaxWidth().height(48.dp)
                        ) {
                            Text("Go to My Bookings", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    }
                )
            }
        }
    }
}


// ----------------------------------------------------
// DISCUSSION THREAD DETAILS DISCUSSION CHAT MAIN
// ----------------------------------------------------
@Composable
fun ChatDetailScreen(
    partnerId: String,
    viewModel: FokalViewModel,
    onBack: () -> Unit
) {
    val messages by viewModel.chatMessages.collectAsStateWithLifecycle()
    val partnerName = viewModel.getCreatorNameSync(partnerId)
    val partnerAvatar = viewModel.getCreatorAvatarSync(partnerId)
    var inputText by remember { mutableStateOf("") }
    
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // Scroll to end on new messages
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    val displayMessages = remember(messages) {
        if (messages.isEmpty()) {
            // Initial seeded dummy messages to make conversation rich
            listOf(
                Message(senderId = partnerId, receiverId = "me", message = "Hi! Thank you for inquiring about standard packages on FokalPoint."),
                Message(senderId = partnerId, receiverId = "me", message = "I am fully available for the requested slot. Do you prefer indoor or outdoor settings?")
            )
        } else {
            messages
        }
    }

    Scaffold(
        topBar = {
            Surface(tonalElevation = 4.dp, color = MaterialTheme.colorScheme.surface) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { onBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    AsyncImage(
                        model = partnerAvatar,
                        contentDescription = null,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )

                    Spacer(modifier = Modifier.width(10.dp))

                    Column {
                        Text(partnerName, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(Color(0xFF4CAF50)))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Active consult", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        },
        bottomBar = {
            Surface(tonalElevation = 8.dp, color = MaterialTheme.colorScheme.surface) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.ime)
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        modifier = Modifier.weight(1f).testTag("chat_input_text"),
                        placeholder = { Text("Ask customization, coordinates...") },
                        maxLines = 2,
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AmberGold)
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    IconButton(
                        onClick = {
                            if (inputText.trim().isNotEmpty()) {
                                viewModel.sendMessage(inputText)
                                inputText = ""
                            }
                        },
                        colors = IconButtonDefaults.iconButtonColors(contentColor = Color.Black, containerColor = AmberGold)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send Chat")
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 12.dp)
            ) {
                items(displayMessages) { message ->
                    val isMe = message.senderId == viewModel.currentUserId.value || message.senderId == "me"
                    ChatBubble(message = message, isMe = isMe)
                }
            }
        }
    }
}

@Composable
fun ChatBubble(message: Message, isMe: Boolean) {
    val bubbleBg = if (isMe) {
        AmberGold
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val contentColor = if (isMe) Color.Black else MaterialTheme.colorScheme.onSurface

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isMe) Arrangement.End else Arrangement.Start
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = bubbleBg),
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isMe) 16.dp else 0.dp,
                bottomEnd = if (isMe) 0.dp else 16.dp
            ),
            modifier = Modifier.widthIn(max = 280.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = message.message,
                    color = contentColor,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
                
                Spacer(modifier = Modifier.height(2.dp))
                
                Text(
                    text = "10:14 AM • Read",
                    fontSize = 9.sp,
                    color = contentColor.copy(alpha = 0.6f),
                    modifier = Modifier.align(Alignment.End)
                )
            }
        }
    }
}


// ----------------------------------------------------
// CREATOR DASHBOARD PANEL (TAB ROOT FOR CREATORS)
// ----------------------------------------------------
@Composable
fun CreatorMainScreen(
    viewModel: FokalViewModel,
    onLogout: () -> Unit,
    onNavigateToChat: (String) -> Unit,
    onNavigateToPayoutMethods: () -> Unit
) {
    var creatorSection by remember { mutableIntStateOf(0) } // 0: Overview, 1: Gig Alerts, 2: Portfolios, 3: Calendars, 4: Earnings

    val creatorTabs = listOf(
        TabItem("Analytics", Icons.Default.Dashboard),
        TabItem("Gig Alerts", Icons.Default.NotificationsActive),
        TabItem("Portfolio", Icons.Default.PhotoLibrary),
        TabItem("Scheduler", Icons.Default.CalendarMonth),
        TabItem("Payments", Icons.Default.Payments)
    )

    val bookings by viewModel.bookingsList.collectAsStateWithLifecycle()
    val pendingBookings = remember(bookings) { bookings.filter { it.status == "Pending" || it.status == "pending" } }
    var showNotificationDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CameraAlt,
                            contentDescription = null,
                            tint = AmberGold,
                            modifier = Modifier.size(24.dp)
                        )
                        Text(
                            text = "Fokal Creator Office",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showNotificationDialog = true },
                        modifier = Modifier.testTag("notification_bell_icon")
                    ) {
                        BadgedBox(
                            badge = {
                                if (pendingBookings.isNotEmpty()) {
                                    Badge(
                                        containerColor = Color.Red,
                                        contentColor = Color.White,
                                        modifier = Modifier.testTag("bell_badge_indicator")
                                    ) {
                                        Text(
                                            text = pendingBookings.size.toString(),
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        ) {
                            Icon(
                                imageVector = if (pendingBookings.isNotEmpty()) Icons.Default.NotificationsActive else Icons.Default.Notifications,
                                contentDescription = "Booking Requests Notifications",
                                tint = if (pendingBookings.isNotEmpty()) AmberGold else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                creatorTabs.forEachIndexed { index, tab ->
                    NavigationBarItem(
                        selected = creatorSection == index,
                        onClick = { creatorSection = index },
                        icon = { Icon(tab.icon, contentDescription = tab.title) },
                        label = { Text(tab.title, fontSize = 10.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color.Black,
                            selectedTextColor = AmberGold,
                            indicatorColor = AmberGold
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            when (creatorSection) {
                0 -> CreatorOverviewTab(viewModel, onLogout, onNavigateToChat)
                1 -> CreatorLeadsAlertsTab(viewModel, onNavigateToChat)
                2 -> CreatorPortfolioTab(viewModel)
                3 -> CreatorCalendarTab(viewModel, onNavigateToChat)
                4 -> CreatorEarningsTab(viewModel, onNavigateToPayoutMethods)
            }
        }
    }

    if (showNotificationDialog) {
        CreatorNotificationDialog(
            pendingBookings = pendingBookings,
            viewModel = viewModel,
            onDismiss = { showNotificationDialog = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreatorNotificationDialog(
    pendingBookings: List<Booking>,
    viewModel: FokalViewModel,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.NotificationsActive,
                    contentDescription = null,
                    tint = AmberGold
                )
                Text(
                    text = "Booking Alerts (${pendingBookings.size})",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            }
        },
        text = {
            if (pendingBookings.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = Color(0xFF4CAF50),
                        modifier = Modifier.size(48.dp)
                    )
                    Text(
                        text = "All caught up!",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Text(
                        text = "No pending new booking requests.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 350.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(pendingBookings) { booking ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("notify_booking_item_${booking.id}"),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = booking.eventType,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "₹${booking.price.toInt()}",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = AmberGold
                                    )
                                }
                                
                                Spacer(modifier = Modifier.height(4.dp))
                                
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CalendarToday,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Text(
                                        text = "${booking.date} at ${booking.time}",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    modifier = Modifier.padding(top = 2.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Schedule,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Text(
                                        text = "${booking.hours} Hours Session",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    modifier = Modifier.padding(top = 2.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Person,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Text(
                                        text = "Client ID: ${booking.customerId}",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                
                                Spacer(modifier = Modifier.height(10.dp))
                                
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Button(
                                        onClick = {
                                            viewModel.updateBookingStatus(booking.id, "Accepted")
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2196F3)),
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(32.dp)
                                            .testTag("notify_accept_btn_${booking.id}"),
                                        contentPadding = PaddingValues(0.dp)
                                    ) {
                                        Text("Accept", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                    
                                    OutlinedButton(
                                        onClick = {
                                            viewModel.updateBookingStatus(booking.id, "Cancelled")
                                        },
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Red),
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(32.dp)
                                            .testTag("notify_decline_btn_${booking.id}"),
                                        border = BorderStroke(1.dp, Color.Red.copy(alpha = 0.5f)),
                                        contentPadding = PaddingValues(0.dp)
                                    ) {
                                        Text("Decline", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("notification_close_btn")
            ) {
                Text("Close", color = AmberGold)
            }
        }
    )
}


// ----------------------------------------------------
// CREATOR GIG ALERTS & CUSTOMER LEADS HUB (WORLDWIDE ACCESS)
// ----------------------------------------------------
@Composable
fun CreatorLeadsAlertsTab(
    viewModel: FokalViewModel,
    onNavigateToChat: (String) -> Unit
) {
    val leads by viewModel.clientLeadsList.collectAsStateWithLifecycle()
    val context = LocalContext.current
    
    // Photographer's registered city/location
    val registeredCity = "Mumbai"
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Welcoming header panel
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Live Gig Alerts",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White
                )
                Text(
                    text = "Potential shoot requests looking for creators globally",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            // Badge showing custom worldwide capability
            Card(
                colors = CardDefaults.cardColors(containerColor = AmberGold.copy(alpha = 0.15f)),
                border = BorderStroke(1.dp, AmberGold),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = "WORLDWIDE INLINE",
                    fontSize = 10.sp,
                    color = AmberGold,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        if (leads.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Campaign,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("No live leads or requests posted yet", fontWeight = FontWeight.Bold)
                    Text("Post requests will appear here instantly.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize().testTag("creator_gigs_leads_list")
            ) {
                items(leads) { lead ->
                    val isLocal = registeredCity.equals(lead.location.substringBefore(",").trim(), ignoreCase = true)
                    
                    Card(
                        modifier = Modifier.fillMaxWidth().testTag("lead_alert_card_${lead.id}"),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, if (isLocal) AmberGold else MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            // Top Row: Tag + Budget
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    color = if (isLocal) AmberGold else MaterialTheme.colorScheme.secondaryContainer,
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        text = lead.eventType.uppercase(),
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isLocal) Color.Black else MaterialTheme.colorScheme.onSecondaryContainer,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                                
                                Text(
                                    text = if (lead.budget > 5000.0) "₹${lead.budget.toInt()}" else "$${lead.budget.toInt()}",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Black,
                                    color = AmberGold
                                )
                            }
                            
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            // Content
                            Text(
                                text = lead.description,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 4,
                                overflow = TextOverflow.Ellipsis
                            )
                            
                            Spacer(modifier = Modifier.height(12.dp))
                            Divider()
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            // Meta Row: Location & Date
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Icon(Icons.Default.Place, contentDescription = null, tint = AmberGold, modifier = Modifier.size(14.dp))
                                    Text(
                                        text = lead.location,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Icon(Icons.Default.Schedule, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
                                    Text(
                                        text = lead.dateDetail,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            // Customer name
                            Text(
                                text = "Posted by: ${lead.customerName} (${lead.customerEmail})",
                                fontSize = 10.sp,
                                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                            )
                            
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            // Bottom CTAs
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                OutlinedButton(
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                    onClick = { viewModel.deleteClientLead(lead.id) },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Dismiss", fontSize = 11.sp)
                                }
                                
                                Button(
                                    colors = ButtonDefaults.buttonColors(containerColor = AmberGold),
                                    onClick = {
                                        // Open Chat directly with the client & send a default message
                                        viewModel.selectedChatCreatorId.value = lead.customerId
                                        val promoText = "Hi ${lead.customerName}! I found your Fokal alert for '${lead.eventType}' in ${lead.location}. I would love to support you with this request. Let me know if we can coordinate details!"
                                        viewModel.sendMessage(promoText)
                                        android.widget.Toast.makeText(context, "Interest applied! Connection sent successfully.", android.widget.Toast.LENGTH_LONG).show()
                                        onNavigateToChat(lead.customerId)
                                    },
                                    modifier = Modifier.weight(1.5f)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Icon(Icons.Default.Message, contentDescription = null, tint = Color.Black, modifier = Modifier.size(14.dp))
                                        Text("Apply & Chat", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}


// ----------------------------------------------------
// CREATOR TAB 0: ANALYTICS OVERVIEW
// ----------------------------------------------------
@Composable
fun CreatorOverviewTab(
    viewModel: FokalViewModel,
    onLogout: () -> Unit,
    onNavigateToChat: (String) -> Unit
) {
    val bookings by viewModel.bookingsList.collectAsStateWithLifecycle()
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp)
    ) {
        // Welcoming header panel
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Creator Studio", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("Logged in: Amit Sharma Pro", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            IconButton(
                onClick = { onLogout() },
                modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
            ) {
                Icon(Icons.Default.Logout, contentDescription = "Logout", tint = Color.Red)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Analytics KPIs Cards Grid layout
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            CreatorKPICard(
                title = "Total Revenue",
                value = "₹1,85,000",
                icon = Icons.Default.TrendingUp,
                color = Color(0xFF4CAF50),
                modifier = Modifier.weight(1f)
            )
            CreatorKPICard(
                title = "Bookings",
                value = "12 Active",
                icon = Icons.Default.Event,
                color = AmberGold,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            CreatorKPICard(
                title = "Views Count",
                value = "480 visitors",
                icon = Icons.Default.RemoveRedEye,
                color = Color(0xFF2196F3),
                modifier = Modifier.weight(1f)
            )
            CreatorKPICard(
                title = "Trust Score",
                value = "4.9 ⭐",
                icon = Icons.Default.Star,
                color = AmberGold,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Earnings Monthly Custom Bar Chart Drawing (Standard Canvas implementation)
        Text("Cumulative Revenues (2026)", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp)
                .height(180.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val months = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun")
                    val heights = listOf(0.4f, 0.6f, 0.5f, 0.82f, 0.7f, 0.95f) // Mock revenue heights

                    val barWidth = 32.dp.toPx()
                    val spacing = (size.width - (barWidth * months.size)) / (months.size + 1)

                    // Draw subtle grid lines
                    val lineCount = 4
                    for (i in 1..lineCount) {
                        val y = size.height * (i.toFloat() / lineCount)
                        drawLine(
                            color = Color.White.copy(alpha = 0.05f),
                            start = Offset(0f, y),
                            end = Offset(size.width, y),
                            strokeWidth = 1f
                        )
                    }

                    // Draw styled bars
                    months.forEachIndexed { index, m ->
                        val x = spacing + index * (barWidth + spacing)
                        val h = heights[index] * (size.height - 30.dp.toPx())
                        val y = (size.height - 20.dp.toPx()) - h

                        drawRoundRect(
                            color = if (index == 5) AmberGold else AmberGold.copy(alpha = 0.4f),
                            topLeft = Offset(x, y),
                            size = Size(barWidth, h),
                            cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx())
                        )
                    }
                }
                
                // Labels overlay
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomStart)
                        .padding(horizontal = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    val months = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun")
                    months.forEach { m ->
                        Text(m, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Upcoming client shoots lists queue
        Text("Pending Action shoots bookings", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
        
        val creatorBookings = bookings.filter { it.status == "Pending" || it.status == "Accepted" }
        
        if (creatorBookings.isEmpty()) {
            Text(
                "No bookings currently require actions.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 10.dp)
            )
        } else {
            Column(
                modifier = Modifier.padding(top = 10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                creatorBookings.forEach { booking ->
                    BookingItemCard(
                        booking = booking,
                        viewModel = viewModel,
                        isCreator = true,
                        onNavigateToChat = onNavigateToChat
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(40.dp))
    }
}

@Composable
fun CreatorKPICard(
    title: String,
    value: String,
    icon: Any,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(title, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (icon is androidx.compose.ui.graphics.vector.ImageVector) {
                    Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(value, fontSize = 18.sp, fontWeight = FontWeight.Black)
        }
    }
}


// ----------------------------------------------------
// CREATOR TAB 1: PORTFOLIO MANAGER SECTION
// ----------------------------------------------------
@Composable
fun CreatorPortfolioTab(viewModel: FokalViewModel) {
    var titleInput by remember { mutableStateOf("") }
    var categorySelection by remember { mutableStateOf("Wedding") }
    var imageLinkInput by remember { mutableStateOf("https://images.unsplash.com/photo-1511285560929-80b456fea0bc?auto=format&fit=crop&w=500&q=80") }

    val categoriesList = listOf("Wedding", "Pre-Wedding", "Corporate", "Fashion", "Baby Shoot", "Maternity")
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text("Portfolio Manager", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text("Upload cinematic pictures or films categories to your gallery stream.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

        Spacer(modifier = Modifier.height(16.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Publish Image", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = AmberGold)
                
                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = titleInput,
                    onValueChange = { titleInput = it },
                    label = { Text("Visual title") },
                    placeholder = { Text("e.g. Royal Jaipur mandap walk") },
                    modifier = Modifier.fillMaxWidth().testTag("input_portfolio_title"),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AmberGold)
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text("Visual Category", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    categoriesList.forEach { cat ->
                        val isSelected = categorySelection == cat
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) AmberGold else MaterialTheme.colorScheme.background)
                                .clickable { categorySelection = cat }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                cat,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) Color.Black else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = imageLinkInput,
                    onValueChange = { imageLinkInput = it },
                    label = { Text("Image link / URL source") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AmberGold)
                )

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = {
                        if (titleInput.isEmpty()) {
                            Toast.makeText(context, "Please configure custom shoot titles", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        viewModel.uploadPortfolioImage(titleInput, categorySelection, imageLinkInput)
                        titleInput = ""
                        Toast.makeText(context, "Portfolio image uploaded!", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AmberGold),
                    modifier = Modifier.fillMaxWidth().testTag("button_submit_portfolio_upload")
                ) {
                    Text("Add to Portfolio Gallery", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}


// ----------------------------------------------------
// CREATOR TAB 2: CALENDAR SLOT PLANNER
// ----------------------------------------------------
@Composable
fun CreatorCalendarTab(
    viewModel: FokalViewModel,
    onNavigateToChat: (String) -> Unit
) {
    var selectedCalendarDate by remember { mutableStateOf("2026-10-15") }
    val currentCreatorId by viewModel.currentUserId.collectAsStateWithLifecycle()
    val blockedDatesMap by viewModel.blockedDatesState.collectAsStateWithLifecycle()
    val blockedDates = blockedDatesMap[currentCreatorId] ?: emptyList()
    
    val bookings by viewModel.bookingsList.collectAsStateWithLifecycle()
    val acceptedBookedDates = bookings
        .filter { it.creatorId == currentCreatorId && (it.status == "Accepted" || it.status == "Confirmed" || it.status == "Completed") }
        .map { it.date }

    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text("Availability Calendar Office", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text("Block calendar dates or link updates dynamically to Google Calendar.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

        Spacer(modifier = Modifier.height(20.dp))

        // Planner block cards Date Picker Mock interface
        val plannerDates = listOf("2026-10-14", "2026-10-15", "2026-10-16", "2026-10-17", "2026-10-18")
        
        Text("October 2026 Planner Timeline", fontWeight = FontWeight.Bold, fontSize = 13.sp)
        
        Row(
            modifier = Modifier.padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            plannerDates.forEach { date ->
                val isBlocked = blockedDates.contains(date)
                val isBookedFilled = acceptedBookedDates.contains(date)
                val isOccupied = isBlocked || isBookedFilled
                val isSelected = selectedCalendarDate == date

                Card(
                    modifier = Modifier
                        .weight(1f)
                        .height(84.dp)
                        .clickable { selectedCalendarDate = date }
                        .border(
                            width = if (isSelected) 1.5.dp else 1.dp,
                            color = if (isSelected) AmberGold else Color.Transparent,
                            shape = RoundedCornerShape(12.dp)
                        ),
                    colors = CardDefaults.cardColors(
                        containerColor = when {
                            isBookedFilled -> Color.Red.copy(alpha = 0.25f)
                            isBlocked -> Color.Red.copy(alpha = 0.15f)
                            else -> MaterialTheme.colorScheme.surfaceVariant
                        }
                    )
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(8.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            date.substring(5),
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = if (isOccupied) Color.Red else MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = when {
                                isBookedFilled -> "Booked"
                                isBlocked -> "Blocked"
                                else -> "Available"
                            },
                            fontSize = 10.sp,
                            color = if (isOccupied) Color.Red else Color(0xFF4CAF50),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Option to toggle availability
        val activeDateBlocked = blockedDates.contains(selectedCalendarDate)
        val activeDateBooked = acceptedBookedDates.contains(selectedCalendarDate)
        Button(
            onClick = {
                if (activeDateBooked) {
                    Toast.makeText(context, "$selectedCalendarDate has a confirmed client booking and cannot be unblocked manually.", Toast.LENGTH_LONG).show()
                } else {
                    viewModel.toggleBlockedDate(currentCreatorId, selectedCalendarDate)
                    Toast.makeText(context, "Availability updated for $selectedCalendarDate!", Toast.LENGTH_SHORT).show()
                }
            },
            colors = ButtonDefaults.buttonColors(
                containerColor = when {
                    activeDateBooked -> MaterialTheme.colorScheme.outline
                    activeDateBlocked -> Color(0xFF4CAF50)
                    else -> Color.Red
                }
            ),
            modifier = Modifier.fillMaxWidth().height(48.dp)
        ) {
            Text(
                text = when {
                    activeDateBooked -> "Reserved for Client Bookings"
                    activeDateBlocked -> "Unblock $selectedCalendarDate"
                    else -> "Block Availability on $selectedCalendarDate"
                },
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Google Calendar syncing options
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Sync, contentDescription = null, tint = AmberGold)
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Google Calendar Syncer", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text("Keep your shoots blocked in external calender views instantly.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                
                Button(
                    onClick = {
                        Toast.makeText(context, "Synced successfully with GCal!", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AmberGold),
                    contentPadding = PaddingValues(horizontal = 12.dp)
                ) {
                    Text("Sync", color = Color.Black, fontSize = 11.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        // My Studio Bookings section
        Text("My Studio Bookings Queue", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
        Text("Filter and manage active and historical photo shoots", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        
        Spacer(modifier = Modifier.height(12.dp))
        
        val creatorBookings by viewModel.bookingsList.collectAsStateWithLifecycle()
        var creatorFilterState by remember { mutableStateOf("All") }
        
        val filteredCreatorBookings = remember(creatorBookings, creatorFilterState) {
            when (creatorFilterState) {
                "Upcoming" -> creatorBookings.filter { it.status == "Pending" || it.status == "Accepted" || it.status == "Confirmed" }
                "Past" -> creatorBookings.filter { it.status == "Completed" || it.status == "Cancelled" }
                else -> creatorBookings
            }
        }
        
        // Tab chips for creator
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("All", "Upcoming", "Past").forEach { category ->
                val isSelected = creatorFilterState == category
                FilterChip(
                    selected = isSelected,
                    onClick = { creatorFilterState = category },
                    label = { Text(category) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = AmberGold,
                        selectedLabelColor = Color.Black
                    ),
                    modifier = Modifier.testTag("creator_bookings_filter_${category.lowercase()}")
                )
            }
        }
        
        if (filteredCreatorBookings.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("No bookings found in this category.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                filteredCreatorBookings.forEach { booking ->
                    BookingItemCard(
                        booking = booking,
                        viewModel = viewModel,
                        isCreator = true,
                        onNavigateToChat = onNavigateToChat
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(40.dp))
    }
}


// ----------------------------------------------------
// CREATOR TAB 3: EARNINGS PAYOUT HISTORY
// ----------------------------------------------------
@Composable
fun CreatorEarningsTab(viewModel: FokalViewModel, onNavigateToPayoutMethods: () -> Unit) {
    val context = LocalContext.current

    val payouts = listOf(
        Payout(id = 1, amount = 45000.0, date = "2026-06-02", status = "Transferred"),
        Payout(id = 2, amount = 30000.0, date = "2026-05-18", status = "Transferred"),
        Payout(id = 3, amount = 75000.0, date = "2026-05-12", status = "Transferred"),
        Payout(id = 4, amount = 35000.0, date = "2026-04-30", status = "Held")
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text("Payout Office Ledger", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text("Manage tax brackets, invoice sheets, and cumulative wallet withdrawals.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

        Spacer(modifier = Modifier.height(16.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("PENDING PAYOUT BALANCES", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("₹35,000", fontWeight = FontWeight.Black, fontSize = 24.sp, color = AmberGold)
                    Button(
                        onClick = {
                            Toast.makeText(context, "Withdraw request sent successfully. Processing within 24 hrs.", Toast.LENGTH_LONG).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AmberGold)
                    ) {
                        Text("Withdraw Payout", color = Color.Black)
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f))
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedButton(
                    onClick = onNavigateToPayoutMethods,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = AmberGold)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.AccountBalance,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Manage Payout Methods", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text("Historic transactions bankings", fontWeight = FontWeight.Bold, fontSize = 13.sp)

        LazyColumn(
            modifier = Modifier.padding(top = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(payouts) { payout ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Payout ID: #ID${payout.id}", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text("Date: ${payout.date}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text("₹${payout.amount.toInt()}", fontWeight = FontWeight.Black, color = AmberGold)
                            Text(
                                payout.status,
                                fontSize = 10.sp,
                                color = if (payout.status == "Transferred") Color(0xFF4CAF50) else Color.Red,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}


// Helper model objects
data class TabItem(val title: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)
data class CategoryItem(val name: String, val icon: androidx.compose.ui.graphics.vector.ImageVector, val imgUrl: String)
data class Payout(val id: Int, val amount: Double, val date: String, val status: String)
