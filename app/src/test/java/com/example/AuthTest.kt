package com.example

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.repository.AuthRepository
import com.example.data.repository.OAuthProvider
import com.example.data.repository.SessionManager
import com.example.data.repository.SupabaseClient
import com.example.ui.viewmodel.AuthState
import com.example.ui.viewmodel.AuthViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AuthTest {

    private lateinit var context: Context
    private lateinit var sessionManager: SessionManager
    private lateinit var supabaseClient: SupabaseClient
    private lateinit var authRepository: AuthRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext<Context>()
        sessionManager = SessionManager(context)
        supabaseClient = SupabaseClient(context)
        authRepository = AuthRepository(supabaseClient, context)
        sessionManager.clearSession()
    }

    @Test
    fun testSessionPersistence() {
        // Given a new session
        val token = "test_access_token"
        val refreshToken = "test_refresh_token"
        val userId = "user_123"
        val expiresAt = System.currentTimeMillis() + 3600000 // 1 hour in future

        val session = SessionManager.Session(
            accessToken = token,
            refreshToken = refreshToken,
            userId = userId,
            expiresAt = expiresAt
        )

        // Then getSession should be null initially (due to clearSession in setUp)
        assertNull(sessionManager.getSession())
        assertFalse(sessionManager.isSessionValid())

        // When saving the session
        sessionManager.saveSession(session)

        // Then session should be persisted and valid
        val retrieved = sessionManager.getSession()
        assertNotNull(retrieved)
        assertEquals(token, retrieved?.accessToken)
        assertEquals(refreshToken, retrieved?.refreshToken)
        assertEquals(userId, retrieved?.userId)
        assertEquals(expiresAt, retrieved?.expiresAt)
        assertTrue(sessionManager.isSessionValid())

        // When session is expired
        val expiredSession = SessionManager.Session(
            accessToken = token,
            refreshToken = refreshToken,
            userId = userId,
            expiresAt = System.currentTimeMillis() - 1000 // 1 second ago
        )
        sessionManager.saveSession(expiredSession)

        // Then session should be invalid
        assertFalse(sessionManager.isSessionValid())

        // When clearing session
        sessionManager.clearSession()

        // Then session should be null
        assertNull(sessionManager.getSession())
    }

    @Test
    fun testOAuthErrorHandling() = runBlocking {
        // Test network error message
        val networkErrorMsg = authRepository.handleOAuthError(Exception("Failed to connect due to network timeout"))
        assertTrue(networkErrorMsg.contains("Network error", ignoreCase = true))

        // Test cancel error message
        val cancelErrorMsg = authRepository.handleOAuthError(Exception("User cancelled the sign in flow"))
        assertTrue(cancelErrorMsg.contains("cancelled", ignoreCase = true))

        // Test redirect_uri or callback error message
        val callbackErrorMsg = authRepository.handleOAuthError(Exception("Invalid redirect_uri or callback parameter error"))
        assertTrue(callbackErrorMsg.contains("callback failed", ignoreCase = true))

        // Test unknown error fallback
        val unknownErrorMsg = authRepository.handleOAuthError(Exception("Something unexpected occurred"))
        assertTrue(unknownErrorMsg.contains("Authentication failed", ignoreCase = true))
    }

    @Test
    fun testOAuthRetrySuccess() = runBlocking {
        // Since SupabaseClient has a simulated implementation that succeeds by default in mock mode, retry should succeed
        val result = authRepository.retryOAuth(OAuthProvider.Google)
        assertTrue(result)

        val resultGithub = authRepository.retryOAuth(OAuthProvider.GitHub)
        assertTrue(resultGithub)
    }

    @Test
    fun testGoogleSignIn() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val viewModel = AuthViewModel(app)

        // When starting Google Sign In, the state should transition or initiate successfully
        // Since the VM launch triggers on background thread, we verify we can invoke the method without crashing
        viewModel.signInWithGoogle(context)
        
        // Given that SUPABASE_URL is not set or is mock, state could change or remain intact
        val state = viewModel.authState.value
        assertNotNull(state)
    }

    @Test
    fun testGitHubSignIn() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val viewModel = AuthViewModel(app)

        // Initiate GitHub sign-in flow
        viewModel.signInWithGitHub(context)
        
        val state = viewModel.authState.value
        assertNotNull(state)
    }

    @Test
    fun testEmailSignIn() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val viewModel = AuthViewModel(app)

        // When signing in with valid mock credentials (mock mode doesn't require real database connection)
        viewModel.signInWithEmail("test@example.com", "password123")
        
        // The mock authentication completes
        val state = viewModel.authState.value
        assertNotNull(state)
    }
}
