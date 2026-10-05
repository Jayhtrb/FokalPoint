package com.fokalpoint.app.data.supabase

import androidx.annotation.VisibleForTesting
import com.fokalpoint.app.BuildConfig
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Central Supabase configuration.
 *
 * When SUPABASE_URL / SUPABASE_ANON_KEY are missing or still the `.env.example`
 * placeholders, the app runs in **demo mode**: auth creates local-only accounts and
 * all data lives in the on-device Room database. Nothing is sent to a backend.
 */
object SupabaseConfig {
    const val OAUTH_REDIRECT = "fokalpoint://login-callback"

    /** Integration tests point the app at a local backend (url, anonKey). Never set in the app. */
    @VisibleForTesting
    @Volatile
    var testOverride: Pair<String, String>? = null

    val url: String get() = (testOverride?.first ?: BuildConfig.SUPABASE_URL).trim().trimEnd('/')
    val anonKey: String get() = (testOverride?.second ?: BuildConfig.SUPABASE_ANON_KEY).trim()

    val isConfigured: Boolean get() = testOverride != null || isValid(url, anonKey)

    fun isValid(url: String, anonKey: String): Boolean =
        url.startsWith("https://") &&
            !url.contains("placeholder", ignoreCase = true) &&
            anonKey.isNotBlank() &&
            !anonKey.contains("placeholder", ignoreCase = true)
}

/** One shared HTTP client for the whole app (connection pooling, consistent timeouts). */
object Http {
    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }
}

class SupabaseException(val status: Int, message: String) : Exception(message)
