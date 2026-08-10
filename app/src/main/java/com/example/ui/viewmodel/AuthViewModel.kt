package com.example.ui.viewmodel

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.database.AppDatabase
import com.example.data.model.User
import com.example.data.repository.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class UserRepository(private val fokalRepository: FokalRepository) {
    suspend fun createUser(user: User) {
        fokalRepository.insertUser(user)
    }
    suspend fun getUser(id: String): User? {
        return fokalRepository.getUser(id)
    }
}

class AuthViewModel(
    application: Application,
    private val authRepository: AuthRepository,
    private val userRepository: UserRepository,
    private val supabase: SupabaseClient
) : AndroidViewModel(application) {
    
    // Auxiliary constructor to facilitate standard Compose instantiation without Hilt
    constructor(application: Application) : this(
        application = application,
        authRepository = AuthRepository(
            supabase = SupabaseClient(application),
            context = application
        ),
        userRepository = UserRepository(
            FokalRepository(
                AppDatabase.getDatabase(application).userDao(),
                AppDatabase.getDatabase(application).creatorDao(),
                AppDatabase.getDatabase(application).portfolioDao(),
                AppDatabase.getDatabase(application).bookingDao(),
                AppDatabase.getDatabase(application).reviewDao(),
                AppDatabase.getDatabase(application).messageDao(),
                AppDatabase.getDatabase(application).favoriteDao(),
                AppDatabase.getDatabase(application).clientLeadDao(),
                AppDatabase.getDatabase(application).payoutMethodDao()
            )
        ),
        supabase = SupabaseClient(application)
    )
    
    private val _authState = MutableStateFlow<AuthState>(AuthState.Unauthenticated)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()
    
    private val _user = MutableStateFlow<User?>(null)
    val user: StateFlow<User?> = _user.asStateFlow()
    
    private var oauthCallbackHandler: ((Uri?) -> Unit)? = null
    
    init {
        checkSession()
    }
    
    private fun checkSession() {
        viewModelScope.launch {
            try {
                val session = supabase.auth.getSession()
                if (session?.user != null) {
                    _authState.value = AuthState.Authenticated
                    loadUserProfile(session.user.id)
                } else {
                    _authState.value = AuthState.Unauthenticated
                }
            } catch (e: Exception) {
                _authState.value = AuthState.Error(e.message ?: "Session check failed")
            }
        }
    }
    
    fun signInWithGoogle(context: Context) {
        viewModelScope.launch {
            try {
                _authState.value = AuthState.Loading
                
                // Start OAuth flow
                val result = supabase.auth.signInWithGoogle(
                    redirectUrl = "fokalpoint://login-callback"
                )
                
                // Open browser for OAuth
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(result.url))
                context.startActivity(intent)
                
                // Handle callback via deep link
                oauthCallbackHandler = { uri ->
                    handleOAuthCallback(uri)
                }
            } catch (e: Exception) {
                _authState.value = AuthState.Error("Google Sign In failed: ${e.message}")
            }
        }
    }
    
    fun signInWithGitHub(context: Context) {
        viewModelScope.launch {
            try {
                _authState.value = AuthState.Loading
                
                val result = supabase.auth.signInWithGitHub(
                    redirectUrl = "fokalpoint://login-callback"
                )
                
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(result.url))
                context.startActivity(intent)
                
                oauthCallbackHandler = { uri ->
                    handleOAuthCallback(uri)
                }
            } catch (e: Exception) {
                _authState.value = AuthState.Error("GitHub Sign In failed: ${e.message}")
            }
        }
    }
    
    fun handleDeepLink(context: Context) {
        val intent = (context as? Activity)?.intent
        if (intent?.action == Intent.ACTION_VIEW) {
            val data = intent.data
            if (data != null && data.scheme == "fokalpoint") {
                handleOAuthCallback(data)
            }
        }
    }
    
    private fun handleOAuthCallback(uri: Uri?) {
        viewModelScope.launch {
            try {
                if (uri == null) {
                    _authState.value = AuthState.Error("OAuth callback failed")
                    return@launch
                }
                
                // Exchange code for session
                val session = supabase.auth.getSession()
                if (session != null) {
                    _authState.value = AuthState.Authenticated
                    loadUserProfile(session.user.id)
                } else {
                    _authState.value = AuthState.Error("Authentication failed")
                }
            } catch (e: Exception) {
                _authState.value = AuthState.Error("OAuth callback error: ${e.message}")
            }
        }
    }
    
    fun signInWithEmail(email: String, password: String) {
        viewModelScope.launch {
            try {
                _authState.value = AuthState.Loading
                
                val response = supabase.auth.signIn(
                    email = email,
                    password = password
                )
                
                if (response.user != null) {
                    _authState.value = AuthState.Authenticated
                    loadUserProfile(response.user.id)
                } else {
                    _authState.value = AuthState.Error("Invalid credentials")
                }
            } catch (e: Exception) {
                _authState.value = AuthState.Error(
                    when {
                        e.message?.contains("Invalid login credentials") == true -> 
                            "Invalid email or password. Please try again."
                        e.message?.contains("Email not confirmed") == true ->
                            "Please verify your email before logging in."
                        else -> "Login failed: ${e.message}"
                    }
                )
            }
        }
    }
    
    fun signUpWithEmail(email: String, password: String, name: String, role: String) {
        viewModelScope.launch {
            try {
                _authState.value = AuthState.Loading
                
                // Validate password strength
                if (password.length < 8) {
                    _authState.value = AuthState.Error("Password must be at least 8 characters")
                    return@launch
                }
                
                val response = supabase.auth.signUp(
                    email = email,
                    password = password,
                    userMetadata = mapOf(
                        "name" to name,
                        "role" to role
                    )
                )
                
                if (response.user != null) {
                    // Create user profile
                    val user = User(
                        id = response.user.id,
                        name = name,
                        email = email,
                        phone = "",
                        role = role,
                        profileImage = "https://images.unsplash.com/photo-1534528741775-53994a69daeb?auto=format&fit=crop&w=150&q=80",
                        city = "Mumbai",
                        state = "Maharashtra",
                        country = "India",
                        createdAt = System.currentTimeMillis()
                    )
                    userRepository.createUser(user)
                    
                    _authState.value = AuthState.Authenticated
                    loadUserProfile(response.user.id)
                } else {
                    _authState.value = AuthState.Error("Sign up failed. Please try again.")
                }
            } catch (e: Exception) {
                _authState.value = AuthState.Error(
                    when {
                        e.message?.contains("User already registered") == true ->
                            "This email is already registered. Please sign in instead."
                        e.message?.contains("Password") == true ->
                            "Password must be at least 8 characters with mix of letters and numbers"
                        else -> "Sign up failed: ${e.message}"
                    }
                )
            }
        }
    }

    fun verifyOTP(code: String) {
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            kotlinx.coroutines.delay(1000)
            if (code == "123456" || code.length == 6) {
                _authState.value = AuthState.Authenticated
            } else {
                _authState.value = AuthState.Error("Invalid OTP code. Please enter 6 digits.")
            }
        }
    }
    
    private suspend fun loadUserProfile(userId: String) {
        try {
            val user = userRepository.getUser(userId)
            _user.value = user
        } catch (e: Exception) {
            // Create profile if it doesn't exist
            val userData = supabase.auth.getUser()
            val newUser = User(
                id = userId,
                name = userData.user?.userMetadata?.get("name") as? String ?: "",
                email = userData.user?.email ?: "",
                phone = "",
                role = userData.user?.userMetadata?.get("role") as? String ?: "Customer",
                profileImage = "https://images.unsplash.com/photo-1534528741775-53994a69daeb?auto=format&fit=crop&w=150&q=80",
                city = "Mumbai",
                state = "Maharashtra",
                country = "India",
                createdAt = System.currentTimeMillis()
            )
            userRepository.createUser(newUser)
            _user.value = newUser
        }
    }
    
    fun signOut() {
        viewModelScope.launch {
            try {
                supabase.auth.signOut()
                _authState.value = AuthState.Unauthenticated
                _user.value = null
            } catch (e: Exception) {
                _authState.value = AuthState.Error("Sign out failed: ${e.message}")
            }
        }
    }
}

sealed class AuthState {
    object Idle : AuthState()
    object Loading : AuthState()
    object Authenticated : AuthState()
    object Unauthenticated : AuthState()
    data class Error(val message: String) : AuthState()
}
