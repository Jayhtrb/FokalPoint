package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.screens.AuthLoadingScreen
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.FokalAppContent
import com.example.ui.theme.FokalAppTheme
import com.example.ui.viewmodel.AuthViewModel
import com.example.ui.viewmodel.AuthState
import com.example.ui.viewmodel.FokalViewModel

class MainActivity : ComponentActivity() {
    private val authViewModel: AuthViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Edge to edge rendering configuration
        enableEdgeToEdge()
        
        // Parse any startup deep-link intents
        intent?.let { handleDeepLink(it) }
        
        setContent {
            val isDarkTheme = true // Default dark theme for the app aesthetic
            val authState by authViewModel.authState.collectAsState()
            
            FokalAppTheme(darkTheme = isDarkTheme) {
                // Navigation based on auth state
                when (authState) {
                    is AuthState.Authenticated -> {
                        val fokalViewModel: FokalViewModel = viewModel()
                        val user by authViewModel.user.collectAsState()
                        
                        // Sync current user role to FokalViewModel
                        LaunchedEffect(user) {
                            user?.let {
                                fokalViewModel.currentUserRole.value = it.role
                            }
                        }
                        
                        Surface {
                            FokalAppContent(viewModel = fokalViewModel)
                        }
                    }
                    is AuthState.Loading -> {
                        Surface {
                            AuthLoadingScreen()
                        }
                    }
                    else -> {
                        Surface {
                            AuthScreen(
                                navController = androidx.navigation.compose.rememberNavController(),
                                viewModel = authViewModel
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleDeepLink(intent)
    }

    private fun handleDeepLink(intent: Intent?) {
        if (intent?.action == Intent.ACTION_VIEW) {
            val data = intent.data
            if (data != null && data.scheme == "fokalpoint") {
                authViewModel.handleDeepLink(this)
            }
        }
    }
}
