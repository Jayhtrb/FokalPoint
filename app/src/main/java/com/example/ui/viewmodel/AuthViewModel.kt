package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.database.AppDatabase
import com.example.data.model.User
import com.example.data.repository.SessionManager.Session
import com.example.data.supabase.OAuthProvider
import com.example.data.supabase.SignUpResult
import com.example.data.supabase.SupabaseAuth
import com.example.data.supabase.SupabaseClient
import com.example.data.supabase.SupabaseConfig
import com.example.data.supabase.SupabaseException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.IOException

/**
 * Single source of truth for authentication. The signed-in [User] it exposes is what
 * the rest of the app keys all data on.
 */
class AuthViewModel(application: Application) : AndroidViewModel(application) {

    private val supabase = SupabaseClient.get(application)
    private val auth: SupabaseAuth = supabase.auth
    private val userDao = AppDatabase.getDatabase(application).userDao()

    private val _authState = MutableStateFlow<AuthState>(AuthState.Initializing)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private val _user = MutableStateFlow<User?>(null)
    val user: StateFlow<User?> = _user.asStateFlow()

    /** One-shot informational message (e.g. "reset link sent"), cleared by the UI. */
    private val _notice = MutableStateFlow<String?>(null)
    val notice: StateFlow<String?> = _notice.asStateFlow()

    val isDemoMode: Boolean get() = !SupabaseConfig.isConfigured

    /** True after opening a password-recovery link: the UI must collect a new password. */
    private val _passwordRecovery = MutableStateFlow(false)
    val passwordRecovery: StateFlow<Boolean> = _passwordRecovery.asStateFlow()

    init {
        viewModelScope.launch {
            val restored = runCatching { auth.restoreSession() }.getOrNull()
            // A sign-in link may have been handled while we were refreshing; don't clobber it.
            if (_authState.value != AuthState.Initializing) return@launch
            if (restored != null) onSignedIn(restored) else _authState.value = AuthState.Unauthenticated
        }
    }

    fun signInWithEmail(email: String, password: String) {
        validateCredentials(email, password, name = null)?.let { return fail(it) }
        run { onSignedIn(auth.signIn(email.trim(), password)) }
    }

    fun signUpWithEmail(email: String, password: String, name: String, role: String) {
        validateCredentials(email, password, name)?.let { return fail(it) }
        run {
            when (val result = auth.signUp(email.trim(), password, name.trim(), role)) {
                is SignUpResult.SignedIn -> onSignedIn(result.session)
                is SignUpResult.ConfirmationRequired ->
                    _authState.value = AuthState.AwaitingEmailConfirmation(result.email)
            }
        }
    }

    fun verifyOTP(code: String) {
        val email = (authState.value as? AuthState.AwaitingEmailConfirmation)?.email
            ?: return fail("Please sign up again to receive a new code.")
        if (code.length != 6 || !code.all(Char::isDigit)) return fail("Enter the 6-digit code from your email.")
        run(onErrorState = AuthState.AwaitingEmailConfirmation(email)) {
            onSignedIn(auth.verifyEmailOtp(email, code))
        }
    }

    fun sendPasswordReset(email: String) {
        if (!EMAIL_REGEX.matches(email.trim())) {
            _notice.value = "Enter your account email above first."
            return
        }
        viewModelScope.launch {
            _notice.value = try {
                auth.sendPasswordReset(email.trim())
                if (isDemoMode) "Password reset isn't available in demo mode."
                else "If an account exists for ${email.trim()}, a reset link is on its way."
            } catch (e: Exception) {
                friendlyMessage(e)
            }
        }
    }

    fun signInWithGoogle(context: Context) = signInWithOAuth(context, OAuthProvider.Google)
    fun signInWithGitHub(context: Context) = signInWithOAuth(context, OAuthProvider.GitHub)

    private fun signInWithOAuth(context: Context, provider: OAuthProvider) {
        val url = auth.oauthUrl(provider)
        if (url == null) {
            run { onSignedIn(auth.demoOAuthSession(provider)) }
            return
        }
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (e: Exception) {
            fail("No browser is available to complete ${provider.name} sign-in.")
        }
    }

    /** Called by MainActivity for `fokalpoint://login-callback…` redirects. */
    fun handleRedirect(uri: Uri) {
        if (uri.scheme != "fokalpoint") return
        val isRecovery = SupabaseAuth.parseRedirectParams(uri.fragment ?: uri.query ?: "")["type"] == "recovery"
        run {
            onSignedIn(auth.completeRedirect(uri))
            _passwordRecovery.value = isRecovery
        }
    }

    fun setNewPassword(password: String) {
        validateCredentials("user@example.com", password, name = "-")?.let { _notice.value = it; return }
        viewModelScope.launch {
            try {
                auth.updatePassword(password)
                _passwordRecovery.value = false
                _notice.value = "Your password has been updated."
            } catch (e: Exception) {
                _notice.value = friendlyMessage(e)
            }
        }
    }

    fun clearNotice() { _notice.value = null }

    fun dismissPasswordRecovery() { _passwordRecovery.value = false }

    fun dismissError() {
        if (_authState.value is AuthState.Error) _authState.value = AuthState.Unauthenticated
    }

    /** Leaves the email-code step (e.g. the user switches to Sign In instead). */
    fun resetForm() {
        val s = _authState.value
        if (s is AuthState.Error || s is AuthState.AwaitingEmailConfirmation) _authState.value = AuthState.Unauthenticated
    }

    fun signOut() {
        viewModelScope.launch {
            auth.signOut()
            _passwordRecovery.value = false
            _user.value = null
            _authState.value = AuthState.Unauthenticated
        }
    }

    // ------------------------------------------------------------------ internals

    private suspend fun onSignedIn(session: Session) {
        _user.value = syncProfile(session)
        _authState.value = AuthState.Authenticated
    }

    /** Ensures a local profile exists, preferring the server's `public.users` row when online. */
    private suspend fun syncProfile(session: Session): User {
        val local = userDao.getUserById(session.userId)
        val remote = if (supabase.rest.isAvailable) {
            runCatching {
                supabase.rest.select("users", "id" to "eq.${session.userId}", "limit" to "1").optJSONObject(0)
            }.getOrNull()
        } else null

        val user = User(
            id = session.userId,
            name = remote?.optString("name")?.takeIf { it.isNotBlank() } ?: local?.name ?: session.name,
            email = session.email.ifBlank { local?.email.orEmpty() },
            phone = remote?.optString("phone")?.takeIf { it != "null" } ?: local?.phone.orEmpty(),
            role = SupabaseAuth.normalizeRole(remote?.optString("role") ?: local?.role ?: session.role),
            profileImage = remote?.optString("profile_image")?.takeIf { it.isNotBlank() && it != "null" }
                ?: local?.profileImage?.takeIf { it.isNotBlank() } ?: session.avatarUrl,
            city = remote?.optString("city")?.takeIf { it != "null" } ?: local?.city.orEmpty(),
            state = remote?.optString("state")?.takeIf { it != "null" } ?: local?.state.orEmpty(),
            country = remote?.optString("country")?.takeIf { it != "null" } ?: local?.country ?: "India",
            createdAt = local?.createdAt ?: System.currentTimeMillis()
        )
        userDao.insertUser(user)
        auth.updateRole(user.role)
        return user
    }

    private fun run(onErrorState: AuthState? = null, block: suspend () -> Unit) {
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            try {
                block()
            } catch (e: Exception) {
                _authState.value = onErrorState ?: AuthState.Error(friendlyMessage(e))
                if (onErrorState != null) _notice.value = friendlyMessage(e)
            }
        }
    }

    private fun fail(message: String) {
        _authState.value = AuthState.Error(message)
    }

    companion object {
        fun validateCredentials(email: String, password: String, name: String?): String? = when {
            name != null && name.isBlank() -> "Please enter your name."
            !EMAIL_REGEX.matches(email.trim()) -> "Please enter a valid email address."
            name != null && password.length < 8 -> "Password must be at least 8 characters."
            name != null && (password.none(Char::isLetter) || password.none(Char::isDigit)) ->
                "Password must contain both letters and numbers."
            password.isEmpty() -> "Please enter your password."
            else -> null
        }

        private val EMAIL_REGEX = Regex("^[A-Za-z0-9._%+'-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")

        fun friendlyMessage(e: Exception): String = when (e) {
            is SupabaseException -> e.message ?: "Something went wrong."
            is IOException -> "Can't reach the server. Check your connection and try again."
            else -> e.message ?: "Something went wrong."
        }
    }
}

sealed class AuthState {
    /** Restoring a saved session at app start. */
    object Initializing : AuthState()
    object Unauthenticated : AuthState()
    object Loading : AuthState()
    data class AwaitingEmailConfirmation(val email: String) : AuthState()
    object Authenticated : AuthState()
    data class Error(val message: String) : AuthState()
}
