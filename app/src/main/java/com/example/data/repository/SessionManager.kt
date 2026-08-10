package com.example.data.repository

import android.content.Context

class SessionManager(
    private val context: Context
) {
    private val prefs = context.getSharedPreferences("fokalpoint_session", Context.MODE_PRIVATE)
    
    fun saveSession(session: Session) {
        prefs.edit().apply {
            putString("access_token", session.accessToken)
            putString("refresh_token", session.refreshToken)
            putString("user_id", session.userId)
            putLong("expires_at", session.expiresAt)
            putBoolean("is_authenticated", true)
            apply()
        }
    }
    
    fun getSession(): Session? {
        val accessToken = prefs.getString("access_token", null) ?: return null
        val refreshToken = prefs.getString("refresh_token", null) ?: return null
        val userId = prefs.getString("user_id", null) ?: return null
        val expiresAt = prefs.getLong("expires_at", 0)
        
        return Session(
            accessToken = accessToken,
            refreshToken = refreshToken,
            userId = userId,
            expiresAt = expiresAt
        )
    }
    
    fun isSessionValid(): Boolean {
        val session = getSession() ?: return false
        return session.expiresAt > System.currentTimeMillis()
    }
    
    fun clearSession() {
        prefs.edit().clear().apply()
    }
    
    fun refreshSessionIfNeeded(): Boolean {
        if (!isSessionValid()) {
            // Implement token refresh logic
            return refreshToken()
        }
        return true
    }
    
    private fun refreshToken(): Boolean {
        // Implement refresh token logic with Supabase
        return false
    }

    data class Session(
        val accessToken: String,
        val refreshToken: String,
        val userId: String,
        val expiresAt: Long
    )
}
