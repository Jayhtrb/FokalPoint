package com.fokalpoint.app.data.repository

import android.content.Context

/**
 * Persists the signed-in session in app-private SharedPreferences.
 * The file is excluded from cloud backup / device transfer (see res/xml backup rules)
 * so refresh tokens never leave the device.
 */
class SessionManager(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun saveSession(session: Session) {
        prefs.edit()
            .putString("access_token", session.accessToken)
            .putString("refresh_token", session.refreshToken)
            .putString("user_id", session.userId)
            .putLong("expires_at", session.expiresAt)
            .putString("email", session.email)
            .putString("name", session.name)
            .putString("role", session.role)
            .putString("avatar_url", session.avatarUrl)
            .putBoolean("is_demo", session.isDemo)
            .apply()
    }

    fun getSession(): Session? {
        val accessToken = prefs.getString("access_token", null) ?: return null
        val refreshToken = prefs.getString("refresh_token", null) ?: return null
        val userId = prefs.getString("user_id", null) ?: return null
        return Session(
            accessToken = accessToken,
            refreshToken = refreshToken,
            userId = userId,
            expiresAt = prefs.getLong("expires_at", 0),
            email = prefs.getString("email", "") ?: "",
            name = prefs.getString("name", "") ?: "",
            role = prefs.getString("role", "Customer") ?: "Customer",
            avatarUrl = prefs.getString("avatar_url", "") ?: "",
            isDemo = prefs.getBoolean("is_demo", false)
        )
    }

    fun isSessionValid(): Boolean {
        val session = getSession() ?: return false
        return session.expiresAt > System.currentTimeMillis()
    }

    fun clearSession() {
        prefs.edit().clear().apply()
    }

    data class Session(
        val accessToken: String,
        val refreshToken: String,
        val userId: String,
        val expiresAt: Long,
        val email: String = "",
        val name: String = "",
        val role: String = "Customer",
        val avatarUrl: String = "",
        val isDemo: Boolean = false
    ) {
        /** True when the access token expires within [marginMs]. */
        fun expiresSoon(now: Long = System.currentTimeMillis(), marginMs: Long = 60_000): Boolean =
            expiresAt - marginMs <= now
    }

    companion object {
        const val PREFS_NAME = "fokalpoint_session"
    }
}
