package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.screens.AuthLoadingScreen
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.FokalAppContent
import com.example.ui.screens.SetNewPasswordDialog
import com.example.ui.theme.FokalAppTheme
import com.example.ui.viewmodel.AuthState
import com.example.ui.viewmodel.AuthViewModel
import com.example.ui.viewmodel.FokalViewModel

class MainActivity : ComponentActivity() {
    private val authViewModel: AuthViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) handleRedirect(intent)

        setContent {
            val fokalViewModel: FokalViewModel = viewModel()
            val isDarkTheme by fokalViewModel.isDarkTheme.collectAsStateWithLifecycle()
            val authState by authViewModel.authState.collectAsStateWithLifecycle()
            val user by authViewModel.user.collectAsStateWithLifecycle()

            // Everything user-scoped in the app keys off the authenticated user.
            LaunchedEffect(user) { fokalViewModel.setActiveUser(user) }

            FokalAppTheme(darkTheme = isDarkTheme) {
                Surface {
                    when (authState) {
                        AuthState.Initializing -> AuthLoadingScreen()
                        AuthState.Authenticated -> if (user != null) {
                            FokalAppContent(
                                viewModel = fokalViewModel,
                                onSignOut = { authViewModel.signOut() }
                            )
                        } else {
                            AuthLoadingScreen()
                        }
                        else -> AuthScreen(viewModel = authViewModel)
                    }

                    if (authState == AuthState.Authenticated) {
                        val recovering by authViewModel.passwordRecovery.collectAsStateWithLifecycle()
                        val notice by authViewModel.notice.collectAsStateWithLifecycle()
                        if (recovering) {
                            SetNewPasswordDialog(
                                onSubmit = { authViewModel.setNewPassword(it) },
                                onDismiss = { authViewModel.dismissPasswordRecovery() }
                            )
                        }
                        notice?.let { message ->
                            AlertDialog(
                                onDismissRequest = { authViewModel.clearNotice() },
                                confirmButton = { TextButton(onClick = { authViewModel.clearNotice() }) { Text("OK") } },
                                text = { Text(message) }
                            )
                        }
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
