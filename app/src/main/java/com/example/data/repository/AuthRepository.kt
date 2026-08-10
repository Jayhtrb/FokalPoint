package com.example.data.repository

import android.content.Context
import android.util.Log
import kotlinx.coroutines.delay

class AuthRepository(
    private val supabase: SupabaseClient,
    private val context: Context
) {
    
    companion object {
        private const val TAG = "AuthRepository"
    }
    
    suspend fun handleOAuthError(exception: Exception): String {
        return when {
            exception.message?.contains("network") == true ||
            exception.message?.contains("timeout") == true -> 
                "Network error. Please check your internet connection."
            
            exception.message?.contains("cancel") == true ->
                "Sign in was cancelled."
            
            exception.message?.contains("redirect_uri") == true ||
            exception.message?.contains("callback") == true ->
                "Authentication callback failed. Please try again."
            
            exception.message?.contains("provider") == true ->
                "The selected provider is not available. Please try another method."
            
            exception.message?.contains("permission") == true ->
                "Permissions denied. Please check app permissions in settings."
            
            else -> "Authentication failed: ${exception.message ?: "Unknown error"}"
        }
    }
    
    suspend fun retryOAuth(provider: OAuthProvider): Boolean {
        return try {
            // Implement retry logic with exponential backoff
            var attempts = 0
            var success = false
            
            while (attempts < 3 && !success) {
                try {
                    when (provider) {
                        OAuthProvider.Google -> supabase.auth.signInWithGoogle(
                            redirectUrl = "fokalpoint://login-callback"
                        )
                        OAuthProvider.GitHub -> supabase.auth.signInWithGitHub(
                            redirectUrl = "fokalpoint://login-callback"
                        )
                    }
                    success = true
                } catch (e: Exception) {
                    attempts++
                    if (attempts >= 3) throw e
                    delay(1000L * attempts) // Exponential backoff
                }
            }
            success
        } catch (e: Exception) {
            Log.e(TAG, "OAuth retry failed", e)
            false
        }
    }
}

enum class OAuthProvider {
    Google, GitHub
}
