package com.fokalpoint.app.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fokalpoint.app.ui.components.*
import com.fokalpoint.app.ui.theme.Aurora
import com.fokalpoint.app.ui.viewmodel.AuthState
import com.fokalpoint.app.ui.viewmodel.AuthViewModel
import kotlinx.coroutines.launch

// ============================================================================ WELCOME

private data class Slide(val image: String, val kicker: String, val title: String, val body: String)

private val Slides = listOf(
    Slide("res:pf_wedding_4", "DISCOVER", "Find the eye\nfor your moment", "Handpicked photographers and filmmakers for weddings, brands, portraits and more."),
    Slide("res:pf_fashion_2", "BOOK", "Book in seconds,\npay them directly", "Pick a package and a date. Creators confirm, you pay by UPI — zero platform fees."),
    Slide("res:pf_wedding_2", "CREATE", "For creators,\na studio in your pocket", "Showcase your portfolio, manage requests and win new leads every day.")
)

@Composable
fun WelcomeScreen(onGetStarted: () -> Unit, onSignIn: () -> Unit) {
    val pager = rememberPagerState { Slides.size }
    val scope = rememberCoroutineScope()
    Box(Modifier.fillMaxSize().background(Aurora.Void).testTag("welcome_screen")) {
        HorizontalPager(pager, Modifier.fillMaxSize()) { page ->
            Box(Modifier.fillMaxSize()) {
                FokalImage(Slides[page].image, null, Modifier.fillMaxSize())
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color(0x8007070B), 0.3f to Color(0x3307070B), 0.62f to Color(0xE607070B), 1f to Aurora.Void)))
            }
        }
        Row(Modifier.statusBarsPadding().padding(24.dp), verticalAlignment = Alignment.CenterVertically) {
            Logo(28.dp)
            Spacer(Modifier.width(10.dp))
            Text("FokalPoint", style = MaterialTheme.typography.titleLarge, color = Color.White)
        }
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(24.dp)) {
            AnimatedContent(pager.currentPage, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "slide") { page ->
                val s = Slides[page]
                Column {
                    GradientText(s.kicker, MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(10.dp))
                    Text(s.title, style = MaterialTheme.typography.displaySmall, color = Aurora.TextPrimary)
                    Spacer(Modifier.height(12.dp))
                    Text(s.body, style = MaterialTheme.typography.bodyLarge, color = Aurora.TextSecondary)
                }
            }
            Spacer(Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                repeat(Slides.size) { i ->
                    Box(Modifier.height(4.dp).width(if (i == pager.currentPage) 28.dp else 10.dp).clip(CircleShape)
                        .background(if (i == pager.currentPage) Aurora.Signature else Brush.linearGradient(listOf(Aurora.Hairline, Aurora.Hairline))))
                }
            }
            Spacer(Modifier.height(28.dp))
            GradientButton(if (pager.currentPage == Slides.lastIndex) "Get started" else "Next", {
                if (pager.currentPage == Slides.lastIndex) onGetStarted() else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) }
            }, Modifier.fillMaxWidth().testTag("welcome_next"), icon = Icons.Rounded.ArrowForward)
            Spacer(Modifier.height(12.dp))
            Text("I already have an account", style = MaterialTheme.typography.labelLarge, color = Aurora.TextSecondary,
                modifier = Modifier.align(Alignment.CenterHorizontally).clip(CircleShape).clickable(onClick = onSignIn).padding(12.dp).testTag("welcome_sign_in"))
        }
    }
}

@Composable
fun Logo(size: androidx.compose.ui.unit.Dp) {
    Box(Modifier.size(size).clip(RoundedCornerShape(size * 0.32f)).background(Aurora.Signature), contentAlignment = Alignment.Center) {
        Box(Modifier.size(size * 0.5f).border(size * 0.08f, Color.White, CircleShape), contentAlignment = Alignment.Center) {
            Box(Modifier.size(size * 0.14f).clip(CircleShape).background(Color.White))
        }
    }
}

// ============================================================================ AUTH

@Composable
fun AuthScreen(viewModel: AuthViewModel, startWithSignUp: Boolean = true) {
    val authState by viewModel.authState.collectAsStateWithLifecycle()
    val notice by viewModel.notice.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var isSignUp by rememberSaveableBool(startWithSignUp)
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var role by remember { mutableStateOf("Customer") }
    var code by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    val loading = authState is AuthState.Loading
    val awaiting = authState as? AuthState.AwaitingEmailConfirmation
    val error = (authState as? AuthState.Error)?.message

    AuroraBackground(Modifier.testTag("auth_screen")) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().statusBarsPadding().navigationBarsPadding().padding(24.dp)
        ) {
            Spacer(Modifier.height(12.dp))
            Logo(52.dp)
            Spacer(Modifier.height(28.dp))
            AnimatedContent(Triple(isSignUp, awaiting != null, Unit), label = "title") { (signUp, otp, _) ->
                Column {
                    Text(
                        when { otp -> "Check your inbox"; signUp -> "Create your account"; else -> "Welcome back" },
                        style = MaterialTheme.typography.headlineLarge, color = Aurora.TextPrimary
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        when {
                            otp -> "Enter the 6-digit code we sent to ${awaiting?.email}, or tap the link in the email."
                            signUp -> "Book photographers and filmmakers — or get booked yourself."
                            else -> "Sign in to manage your shoots."
                        },
                        style = MaterialTheme.typography.bodyLarge, color = Aurora.TextSecondary
                    )
                }
            }
            if (viewModel.isDemoMode) {
                Spacer(Modifier.height(14.dp))
                Pill("Demo mode · data stays on this device", Aurora.Amber)
            }
            Spacer(Modifier.height(28.dp))

            if (awaiting != null) {
                AuroraTextField(code, { if (it.length <= 6 && it.all(Char::isDigit)) code = it }, "Verification code", placeholder = "123456",
                    leadingIcon = Icons.Rounded.Pin, keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done,
                    onImeAction = { viewModel.verifyOTP(code) }, testTag = "auth_code")
                Spacer(Modifier.height(20.dp))
                GradientButton("Verify", { viewModel.verifyOTP(code) }, Modifier.fillMaxWidth().testTag("auth_verify"),
                    enabled = code.length == 6, loading = loading)
                Spacer(Modifier.height(12.dp))
                Text("Use a different email", style = MaterialTheme.typography.labelLarge, color = Aurora.VioletSoft,
                    modifier = Modifier.align(Alignment.CenterHorizontally).clip(CircleShape).clickable { viewModel.resetForm() }.padding(10.dp))
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    GlassButton("Google", { viewModel.signInWithGoogle(context) }, Modifier.weight(1f), icon = Icons.Rounded.AccountCircle, enabled = !loading)
                    GlassButton("GitHub", { viewModel.signInWithGitHub(context) }, Modifier.weight(1f), icon = Icons.Rounded.Code, enabled = !loading)
                }
                Spacer(Modifier.height(22.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Divider(Modifier.weight(1f))
                    Text("  or with email  ", style = MaterialTheme.typography.labelMedium, color = Aurora.TextTertiary)
                    Divider(Modifier.weight(1f))
                }
                Spacer(Modifier.height(22.dp))
                AnimatedVisibility(isSignUp) {
                    Column {
                        Text("I want to", style = MaterialTheme.typography.labelMedium, color = Aurora.TextSecondary)
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            RoleCard("Book a shoot", Icons.Rounded.Search, role == "Customer", Modifier.weight(1f).testTag("role_customer")) { role = "Customer" }
                            RoleCard("Get booked", Icons.Rounded.CameraAlt, role == "Creator", Modifier.weight(1f).testTag("role_creator")) { role = "Creator" }
                        }
                        Spacer(Modifier.height(16.dp))
                        AuroraTextField(name, { name = it.take(60); viewModel.dismissError() }, "Full name", placeholder = "Your name",
                            leadingIcon = Icons.Rounded.Person, testTag = "auth_name")
                        Spacer(Modifier.height(16.dp))
                    }
                }
                AuroraTextField(email, { email = it.trim(); viewModel.dismissError() }, "Email", placeholder = "you@example.com",
                    leadingIcon = Icons.Rounded.AlternateEmail, keyboardType = KeyboardType.Email, testTag = "auth_email")
                Spacer(Modifier.height(16.dp))
                AuroraTextField(password, { password = it; viewModel.dismissError() }, "Password",
                    placeholder = if (isSignUp) "8+ characters, letters & numbers" else "Your password",
                    leadingIcon = Icons.Rounded.Lock, isPassword = !showPassword, keyboardType = KeyboardType.Password, imeAction = ImeAction.Done,
                    trailing = {
                        Icon(if (showPassword) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, "Show password", tint = Aurora.TextTertiary,
                            modifier = Modifier.size(20.dp).clickable { showPassword = !showPassword })
                    }, testTag = "auth_password")
                if (!isSignUp) {
                    Text("Forgot password?", style = MaterialTheme.typography.labelMedium, color = Aurora.VioletSoft,
                        modifier = Modifier.align(Alignment.End).clip(CircleShape).clickable { viewModel.sendPasswordReset(email) }.padding(10.dp))
                } else Spacer(Modifier.height(20.dp))
                error?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = Aurora.Coral, modifier = Modifier.padding(bottom = 12.dp).testTag("auth_error"))
                }
                GradientButton(
                    if (isSignUp) "Create account" else "Sign in",
                    { if (isSignUp) viewModel.signUpWithEmail(email, password, name, role) else viewModel.signInWithEmail(email, password) },
                    Modifier.fillMaxWidth().testTag("auth_submit"),
                    enabled = email.isNotBlank() && password.isNotBlank() && (!isSignUp || name.isNotBlank()),
                    loading = loading
                )
                Spacer(Modifier.height(18.dp))
                Row(Modifier.align(Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
                    Text(if (isSignUp) "Already have an account?" else "New to FokalPoint?", style = MaterialTheme.typography.bodyMedium, color = Aurora.TextSecondary)
                    Text(if (isSignUp) "Sign in" else "Create one", style = MaterialTheme.typography.labelLarge, color = Aurora.VioletSoft,
                        modifier = Modifier.clip(CircleShape).clickable { isSignUp = !isSignUp; viewModel.resetForm() }.padding(8.dp).testTag("auth_toggle"))
                }
                if (isSignUp) {
                    Text("By continuing you agree to the Terms and Privacy Policy.", style = MaterialTheme.typography.bodySmall,
                        color = Aurora.TextTertiary, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                }
            }
        }
    }

    notice?.let { message ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { viewModel.clearNotice() },
            containerColor = Aurora.Elevated,
            confirmButton = { androidx.compose.material3.TextButton({ viewModel.clearNotice() }) { Text("OK") } },
            text = { Text(message) }
        )
    }
}

@Composable
private fun RoleCard(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    GlassCard(modifier, highlight = selected, onClick = onClick, contentPadding = PaddingValues(14.dp)) {
        Icon(icon, null, tint = if (selected) Aurora.Cyan else Aurora.TextTertiary)
        Spacer(Modifier.height(8.dp))
        Text(label, style = MaterialTheme.typography.titleSmall, color = if (selected) Aurora.TextPrimary else Aurora.TextSecondary)
    }
}

@Composable
private fun rememberSaveableBool(initial: Boolean): MutableState<Boolean> =
    androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(initial) }

/** Shown after opening a password-recovery link. */
@Composable
fun SetNewPasswordDialog(onSubmit: (String) -> Unit, onDismiss: () -> Unit) {
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        GlassCard(Modifier.fillMaxWidth(), highlight = true, contentPadding = PaddingValues(22.dp)) {
            Text("Choose a new password", style = MaterialTheme.typography.headlineSmall, color = Aurora.TextPrimary)
            Spacer(Modifier.height(16.dp))
            AuroraTextField(password, { password = it }, "New password", isPassword = true, placeholder = "8+ characters, letters & numbers")
            Spacer(Modifier.height(12.dp))
            AuroraTextField(confirm, { confirm = it }, "Confirm password", isPassword = true, isError = confirm.isNotEmpty() && confirm != password)
            Spacer(Modifier.height(18.dp))
            GradientButton("Update password", { onSubmit(password) }, Modifier.fillMaxWidth(), enabled = password.isNotEmpty() && confirm == password)
            Text("Later", style = MaterialTheme.typography.labelLarge, color = Aurora.TextSecondary,
                modifier = Modifier.align(Alignment.CenterHorizontally).clip(CircleShape).clickable(onClick = onDismiss).padding(12.dp))
        }
    }
}

@Composable
fun SplashLoading() {
    AuroraBackground {
        Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
            Logo(72.dp)
            Spacer(Modifier.height(16.dp))
            Text("FokalPoint", style = MaterialTheme.typography.headlineSmall, color = Aurora.TextPrimary)
        }
    }
}

@Suppress("unused")
private val ko = KeyboardOptions.Default
