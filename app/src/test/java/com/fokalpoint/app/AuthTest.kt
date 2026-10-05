package com.fokalpoint.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.fokalpoint.app.data.repository.SessionManager
import com.fokalpoint.app.data.supabase.SignUpResult
import com.fokalpoint.app.data.supabase.SupabaseAuth
import com.fokalpoint.app.data.supabase.SupabaseConfig
import com.fokalpoint.app.ui.viewmodel.AuthViewModel
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
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

    private lateinit var sessions: SessionManager
    private lateinit var auth: SupabaseAuth

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        sessions = SessionManager(context)
        sessions.clearSession()
        auth = SupabaseAuth(sessions)
    }

    @Test
    fun sessionRoundTripsAndClears() {
        val session = SessionManager.Session(
            accessToken = "a", refreshToken = "r", userId = "u1",
            expiresAt = System.currentTimeMillis() + 3_600_000,
            email = "x@y.com", name = "X", role = "Creator"
        )
        assertNull(sessions.getSession())
        sessions.saveSession(session)
        assertEquals(session, sessions.getSession())
        assertTrue(sessions.isSessionValid())
        sessions.clearSession()
        assertNull(sessions.getSession())
    }

    @Test
    fun expiresSoonUsesSafetyMargin() {
        val now = 1_000_000L
        val s = SessionManager.Session("a", "r", "u", expiresAt = now + 30_000)
        assertTrue(s.expiresSoon(now))
        assertFalse(s.copy(expiresAt = now + 600_000).expiresSoon(now))
    }

    @Test
    fun placeholderConfigMeansDemoMode() {
        assertFalse(SupabaseConfig.isValid("https://placeholder-project-ref.supabase.co", "key"))
        assertFalse(SupabaseConfig.isValid("https://abc.supabase.co", "placeholder-anon-key"))
        assertFalse(SupabaseConfig.isValid("http://abc.supabase.co", "key"))
        assertTrue(SupabaseConfig.isValid("https://abc.supabase.co", "eyJhbGciOi"))
        // The test build uses .env.example, so the app must be in demo mode.
        assertFalse(SupabaseConfig.isConfigured)
    }

    @Test
    fun rolesAreNormalizedToDatabaseValues() {
        assertEquals("Creator", SupabaseAuth.normalizeRole("creator"))
        assertEquals("Creator", SupabaseAuth.normalizeRole("CREATOR"))
        assertEquals("Customer", SupabaseAuth.normalizeRole("customer"))
        assertEquals("Customer", SupabaseAuth.normalizeRole(null))
        assertEquals("Customer", SupabaseAuth.normalizeRole("admin"))
    }

    @Test
    fun demoUserIdIsStableAndCaseInsensitive() {
        val a = SupabaseAuth.demoUserId("Ana@Example.com ")
        assertEquals(a, SupabaseAuth.demoUserId("ana@example.com"))
        assertNotEquals(a, SupabaseAuth.demoUserId("bob@example.com"))
        assertTrue(a.startsWith("demo_"))
    }

    @Test
    fun demoSignUpSignInAndSignOut() = runBlocking {
        val result = auth.signUp("ana@example.com", "secret123", "Ana Rao", role = "creator")
        val session = (result as SignUpResult.SignedIn).session
        assertTrue(session.isDemo)
        assertEquals("Creator", session.role)
        assertEquals("Ana Rao", session.name)

        // Restoring keeps the demo account; signing in again keeps its role.
        assertEquals(session.userId, auth.restoreSession()?.userId)
        assertEquals("Creator", auth.signIn("ana@example.com", "whatever1").role)

        // Demo sessions never produce a bearer token for the backend.
        assertNull(auth.validAccessToken())

        auth.signOut()
        assertNull(auth.restoreSession())
    }

    @Test
    fun parsesOAuthRedirectFragment() {
        val params = SupabaseAuth.parseRedirectParams(
            "access_token=abc.def&expires_in=3600&refresh_token=r%2Ftok&token_type=bearer&type=recovery"
        )
        assertEquals("abc.def", params["access_token"])
        assertEquals("r/tok", params["refresh_token"])
        assertEquals("3600", params["expires_in"])
        assertEquals("recovery", params["type"])
    }

    @Test
    fun buildsSessionFromGoTrueUser() {
        val user = JSONObject(
            """{"id":"u-1","email":"pri@lens.in","user_metadata":{"full_name":"Priya Lens","role":"creator","avatar_url":"https://a/p.png"}}"""
        )
        val s = SupabaseAuth.sessionFrom("tok", "ref", 3600, user)
        assertEquals("u-1", s.userId)
        assertEquals("Priya Lens", s.name)
        assertEquals("Creator", s.role)
        assertEquals("https://a/p.png", s.avatarUrl)
        assertFalse(s.expiresSoon())
    }

    @Test
    fun mapsServerErrorsToFriendlyMessages() {
        assertEquals(
            "Incorrect email or password.",
            SupabaseAuth.errorMessage("""{"error":"invalid_grant","error_description":"Invalid login credentials"}""", 400)
        )
        assertEquals(
            "An account with this email already exists. Try signing in.",
            SupabaseAuth.errorMessage("""{"msg":"User already registered"}""", 422)
        )
        assertEquals("Too many attempts. Please wait a moment and try again.", SupabaseAuth.errorMessage("", 429))
        assertNotNull(SupabaseAuth.errorMessage("<html>", 502))
    }

    @Test
    fun validatesCredentialsBeforeCallingServer() {
        assertEquals("Please enter a valid email address.", AuthViewModel.validateCredentials("nope", "abc12345", null))
        assertEquals("Please enter your password.", AuthViewModel.validateCredentials("a@b.co", "", null))
        assertEquals("Please enter your name.", AuthViewModel.validateCredentials("a@b.co", "abc12345", " "))
        assertEquals("Password must be at least 8 characters.", AuthViewModel.validateCredentials("a@b.co", "ab1", "Ann"))
        assertEquals(
            "Password must contain both letters and numbers.",
            AuthViewModel.validateCredentials("a@b.co", "abcdefgh", "Ann")
        )
        assertNull(AuthViewModel.validateCredentials("a@b.co", "abcd1234", "Ann"))
        // Sign-in doesn't enforce sign-up password rules (server decides).
        assertNull(AuthViewModel.validateCredentials("a@b.co", "short", null))
    }
}
