package com.example.data.supabase

import android.net.Uri
import com.example.data.repository.SessionManager
import com.example.data.repository.SessionManager.Session
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.security.MessageDigest

enum class OAuthProvider(val id: String) { Google("google"), GitHub("github") }

/** Result of a sign-up: either signed in right away, or the email must be confirmed first. */
sealed class SignUpResult {
    data class SignedIn(val session: Session) : SignUpResult()
    data class ConfirmationRequired(val email: String) : SignUpResult()
}

/**
 * Supabase GoTrue client over plain REST.
 *
 * All request bodies are built with [JSONObject] (never string interpolation), the
 * session is persisted through [SessionManager], and access tokens are refreshed
 * transparently by [validAccessToken]. In demo mode (see [SupabaseConfig]) the same API
 * creates local-only sessions so the app is fully explorable without a backend.
 */
class SupabaseAuth(private val sessions: SessionManager) {

    private val json = "application/json; charset=utf-8".toMediaType()

    fun currentSession(): Session? = sessions.getSession()

    // ---------------------------------------------------------------- password auth

    suspend fun signIn(email: String, password: String): Session {
        if (!SupabaseConfig.isConfigured) {
            return saveDemo(email = email, name = email.substringBefore("@").titleCase(), role = null)
        }
        val body = JSONObject().put("email", email).put("password", password)
        val response = post("/auth/v1/token?grant_type=password", body)
        return persist(parseSession(response))
    }

    suspend fun signUp(email: String, password: String, name: String, role: String): SignUpResult {
        val normalizedRole = normalizeRole(role)
        if (!SupabaseConfig.isConfigured) {
            return SignUpResult.SignedIn(saveDemo(email, name, normalizedRole))
        }
        val body = JSONObject()
            .put("email", email)
            .put("password", password)
            .put("data", JSONObject().put("name", name).put("role", normalizedRole))
        val response = post("/auth/v1/signup", body)
        // With "Confirm email" enabled Supabase returns the user without a session.
        return if (response.has("access_token")) {
            SignUpResult.SignedIn(persist(parseSession(response)))
        } else {
            SignUpResult.ConfirmationRequired(email)
        }
    }

    /** Verifies the 6-digit code from the confirmation email (Supabase `{{ .Token }}`). */
    suspend fun verifyEmailOtp(email: String, code: String): Session {
        if (!SupabaseConfig.isConfigured) {
            throw SupabaseException(400, "Email verification is only available with a Supabase backend.")
        }
        val body = JSONObject().put("type", "signup").put("email", email).put("token", code)
        return persist(parseSession(post("/auth/v1/verify", body)))
    }

    /** Sets a new password for the signed-in user (used after a recovery link). */
    suspend fun updatePassword(newPassword: String) {
        val session = sessions.getSession() ?: throw SupabaseException(401, "Not signed in")
        if (session.isDemo || !SupabaseConfig.isConfigured) return
        val token = validAccessToken() ?: throw SupabaseException(401, "Your reset link has expired. Please request a new one.")
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url("${SupabaseConfig.url}/auth/v1/user")
                .put(JSONObject().put("password", newPassword).toString().toRequestBody(json))
                .header("apikey", SupabaseConfig.anonKey)
                .header("Authorization", "Bearer $token")
                .build()
            Http.client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw SupabaseException(response.code, errorMessage(response.body?.string().orEmpty(), response.code))
                }
            }
        }
    }

    suspend fun sendPasswordReset(email: String) {
        if (!SupabaseConfig.isConfigured) return
        post(
            "/auth/v1/recover?redirect_to=${Uri.encode(SupabaseConfig.OAUTH_REDIRECT)}",
            JSONObject().put("email", email)
        )
    }

    // ---------------------------------------------------------------- OAuth

    /** Browser URL for the provider's consent screen, or null in demo mode. */
    fun oauthUrl(provider: OAuthProvider): String? {
        if (!SupabaseConfig.isConfigured) return null
        return "${SupabaseConfig.url}/auth/v1/authorize?provider=${provider.id}" +
            "&redirect_to=${Uri.encode(SupabaseConfig.OAUTH_REDIRECT)}"
    }

    /** Demo-mode stand-in for OAuth so the flow is still demonstrable offline. */
    fun demoOAuthSession(provider: OAuthProvider): Session =
        saveDemo("demo.${provider.id}@fokalpoint.app", "${provider.name} Demo", role = null)

    /**
     * Completes an OAuth (implicit flow) or password-recovery redirect:
     * `fokalpoint://login-callback#access_token=…&refresh_token=…&expires_in=3600`.
     */
    suspend fun completeRedirect(uri: Uri): Session {
        val params = parseRedirectParams(uri.fragment ?: uri.query ?: "")
        params["error_description"]?.let { throw SupabaseException(400, it) }
        val access = params["access_token"] ?: throw SupabaseException(400, "Sign-in link is missing a token.")
        val refresh = params["refresh_token"] ?: throw SupabaseException(400, "Sign-in link is missing a refresh token.")
        val expiresIn = params["expires_in"]?.toLongOrNull() ?: 3600
        val user = fetchUser(access)
        return persist(sessionFrom(access, refresh, expiresIn, user))
    }

    // ---------------------------------------------------------------- session upkeep

    /**
     * Returns a non-expired access token, refreshing it if needed. Returns null when
     * signed out or in demo mode. If the refresh token was revoked the session is cleared.
     */
    suspend fun validAccessToken(): String? = refreshLock.withLock {
        val session = sessions.getSession() ?: return null
        if (session.isDemo || !SupabaseConfig.isConfigured) return null
        if (!session.expiresSoon()) return session.accessToken
        return try {
            val response = post(
                "/auth/v1/token?grant_type=refresh_token",
                JSONObject().put("refresh_token", session.refreshToken)
            )
            persist(parseSession(response)).accessToken
        } catch (e: SupabaseException) {
            if (e.status in 400..499) sessions.clearSession()
            null
        }
    }

    /** Restores the stored session on app start, refreshing it if it is about to expire. */
    suspend fun restoreSession(): Session? {
        val session = sessions.getSession() ?: return null
        if (session.isDemo) return if (SupabaseConfig.isConfigured) null.also { sessions.clearSession() } else session
        if (!SupabaseConfig.isConfigured) return null
        return try {
            validAccessToken()?.let { sessions.getSession() }
        } catch (e: Exception) {
            // Offline: keep the user signed in; requests will retry the refresh later.
            session
        }
    }

    suspend fun updateRole(role: String) {
        val session = sessions.getSession() ?: return
        sessions.saveSession(session.copy(role = normalizeRole(role)))
    }

    suspend fun signOut() {
        val session = sessions.getSession()
        sessions.clearSession()
        if (session == null || session.isDemo || !SupabaseConfig.isConfigured) return
        runCatching {
            withContext(Dispatchers.IO) {
                val request = Request.Builder()
                    .url("${SupabaseConfig.url}/auth/v1/logout")
                    .post(ByteArray(0).toRequestBody(null))
                    .header("apikey", SupabaseConfig.anonKey)
                    .header("Authorization", "Bearer ${session.accessToken}")
                    .build()
                Http.client.newCall(request).execute().close()
            }
        }
    }

    // ---------------------------------------------------------------- internals

    private suspend fun fetchUser(accessToken: String): JSONObject = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("${SupabaseConfig.url}/auth/v1/user")
            .header("apikey", SupabaseConfig.anonKey)
            .header("Authorization", "Bearer $accessToken")
            .build()
        Http.client.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw SupabaseException(response.code, errorMessage(text, response.code))
            JSONObject(text)
        }
    }

    private suspend fun post(path: String, body: JSONObject): JSONObject = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(SupabaseConfig.url + path)
            .post(body.toString().toRequestBody(json))
            .header("apikey", SupabaseConfig.anonKey)
            .build()
        Http.client.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw SupabaseException(response.code, errorMessage(text, response.code))
            if (text.isBlank()) JSONObject() else JSONObject(text)
        }
    }

    private fun parseSession(response: JSONObject): Session {
        val access = response.optString("access_token")
        if (access.isEmpty()) throw SupabaseException(500, "Authentication server returned no session.")
        return sessionFrom(
            access,
            response.optString("refresh_token"),
            response.optLong("expires_in", 3600),
            response.optJSONObject("user") ?: JSONObject()
        )
    }

    private fun persist(session: Session): Session = session.also { sessions.saveSession(it) }

    private fun saveDemo(email: String, name: String, role: String?): Session {
        val id = demoUserId(email)
        val existing = sessions.getSession()?.takeIf { it.userId == id }
        return persist(
            Session(
                accessToken = "demo",
                refreshToken = "demo",
                userId = id,
                expiresAt = Long.MAX_VALUE,
                email = email,
                name = name.ifBlank { email.substringBefore("@") },
                role = role ?: existing?.role ?: "Customer",
                isDemo = true
            )
        )
    }

    companion object {
        private val refreshLock = Mutex()

        fun normalizeRole(raw: String?): String =
            if (raw.equals("creator", ignoreCase = true)) "Creator" else "Customer"

        fun demoUserId(email: String): String {
            val digest = MessageDigest.getInstance("SHA-256").digest(email.trim().lowercase().toByteArray())
            return "demo_" + digest.take(8).joinToString("") { "%02x".format(it) }
        }

        fun sessionFrom(access: String, refresh: String, expiresIn: Long, user: JSONObject): Session {
            val meta = user.optJSONObject("user_metadata") ?: JSONObject()
            val email = user.optString("email")
            return Session(
                accessToken = access,
                refreshToken = refresh,
                userId = user.optString("id"),
                expiresAt = System.currentTimeMillis() + expiresIn * 1000,
                email = email,
                name = meta.optString("name").ifBlank { meta.optString("full_name") }
                    .ifBlank { email.substringBefore("@") },
                role = normalizeRole(meta.optString("role")),
                avatarUrl = meta.optString("avatar_url").ifBlank { meta.optString("picture") }
            )
        }

        fun parseRedirectParams(raw: String): Map<String, String> =
            raw.split('&').mapNotNull { part ->
                val idx = part.indexOf('=')
                if (idx <= 0) null else Uri.decode(part.substring(0, idx)) to Uri.decode(part.substring(idx + 1))
            }.toMap()

        fun errorMessage(body: String, status: Int): String {
            val parsed = runCatching { JSONObject(body) }.getOrNull()
            val raw = parsed?.let {
                it.optString("error_description").ifBlank { it.optString("msg") }
                    .ifBlank { it.optString("message") }.ifBlank { it.optString("error") }
            }.orEmpty()
            return when {
                raw.contains("Invalid login credentials", true) -> "Incorrect email or password."
                raw.contains("Email not confirmed", true) -> "Please confirm your email address before signing in."
                raw.contains("already registered", true) -> "An account with this email already exists. Try signing in."
                raw.contains("expired", true) || raw.contains("invalid", true) && raw.contains("otp", true) ->
                    "That code is invalid or has expired."
                raw.isNotBlank() -> raw
                status == 429 -> "Too many attempts. Please wait a moment and try again."
                status >= 500 -> "The server is having trouble. Please try again shortly."
                else -> "Request failed ($status)."
            }
        }

        private fun String.titleCase() = replaceFirstChar { it.uppercase() }
    }
}
