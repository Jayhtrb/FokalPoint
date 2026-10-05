package com.fokalpoint.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fokalpoint.app.data.supabase.SupabaseConfig
import com.fokalpoint.app.data.sync.ActivityNotifier
import com.fokalpoint.app.ui.navigation.FokalApp
import com.fokalpoint.app.ui.screens.AuthScreen
import com.fokalpoint.app.ui.screens.SetNewPasswordDialog
import com.fokalpoint.app.ui.screens.SplashLoading
import com.fokalpoint.app.ui.screens.WelcomeScreen
import com.fokalpoint.app.ui.theme.Aurora
import com.fokalpoint.app.ui.theme.FokalTheme
import com.fokalpoint.app.ui.viewmodel.AuthState
import com.fokalpoint.app.ui.viewmodel.AuthViewModel
import com.fokalpoint.app.ui.viewmodel.FokalViewModel

class MainActivity : ComponentActivity() {
    private val authViewModel: AuthViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        splash.setKeepOnScreenCondition { authViewModel.authState.value == AuthState.Initializing }
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        if (savedInstanceState == null) handleRedirect(intent)
        val prefs = getSharedPreferences("fokal_prefs", Context.MODE_PRIVATE)

        setContent {
            FokalTheme {
                val appViewModel: FokalViewModel = viewModel()
                val authState by authViewModel.authState.collectAsStateWithLifecycle()
                val user by authViewModel.user.collectAsStateWithLifecycle()
                var seenWelcome by remember { mutableStateOf(prefs.getBoolean("seen_welcome", false)) }
                var startWithSignUp by remember { mutableStateOf(true) }

                LaunchedEffect(user) {
                    appViewModel.setActiveUser(user)
                    // Background checks only make sense with a live backend.
                    if (SupabaseConfig.isConfigured) {
                        if (user != null) ActivityNotifier.schedule(applicationContext) else ActivityNotifier.cancel(applicationContext)
                    }
                }

                // Ask for notification permission once the user is in (Android 13+).
                val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
                LaunchedEffect(authState) {
                    if (authState == AuthState.Authenticated && Build.VERSION.SDK_INT >= 33 && !prefs.getBoolean("asked_notifications", false)) {
                        prefs.edit().putBoolean("asked_notifications", true).apply()
                        notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }

                val screen = when {
                    authState == AuthState.Initializing -> "splash"
                    authState == AuthState.Authenticated && user != null -> "app"
                    authState == AuthState.Authenticated -> "splash"
                    !seenWelcome -> "welcome"
                    else -> "auth"
                }
                AnimatedContent(screen, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "root") { s ->
                    when (s) {
                        "splash" -> SplashLoading()
                        "welcome" -> WelcomeScreen(
                            onGetStarted = { prefs.edit().putBoolean("seen_welcome", true).apply(); startWithSignUp = true; seenWelcome = true },
                            onSignIn = { prefs.edit().putBoolean("seen_welcome", true).apply(); startWithSignUp = false; seenWelcome = true }
                        )
                        "auth" -> AuthScreen(authViewModel, startWithSignUp)
                        else -> FokalApp(appViewModel, onSignOut = { authViewModel.signOut() }, onDeleteAccount = { authViewModel.deleteAccount() })
                    }
                }

                if (authState == AuthState.Authenticated) {
                    val recovering by authViewModel.passwordRecovery.collectAsStateWithLifecycle()
                    val notice by authViewModel.notice.collectAsStateWithLifecycle()
                    if (recovering) {
                        SetNewPasswordDialog(onSubmit = { authViewModel.setNewPassword(it) }, onDismiss = { authViewModel.dismissPasswordRecovery() })
                    }
                    notice?.let { message ->
                        AlertDialog(
                            onDismissRequest = { authViewModel.clearNotice() },
                            containerColor = Aurora.Elevated,
                            confirmButton = { TextButton(onClick = { authViewModel.clearNotice() }) { Text("OK") } },
                            text = { Text(message) }
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleRedirect(intent)
    }

    private fun handleRedirect(intent: Intent?) {
        val data = intent?.data ?: return
        if (intent.action == Intent.ACTION_VIEW && data.scheme == "fokalpoint") {
            authViewModel.handleRedirect(data)
        }
    }
}
