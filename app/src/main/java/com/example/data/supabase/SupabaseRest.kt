package com.example.data.supabase

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

/**
 * Minimal PostgREST client. Every request carries the signed-in user's JWT so Row
 * Level Security policies see `auth.uid()`; filter values go through [HttpUrl] so they
 * are always URL-encoded. Callers pass filters PostgREST-style, e.g. `"id" to "eq.42"`.
 *
 * Throws [SupabaseException] for non-2xx responses, and when not signed in to a
 * configured backend (callers should check [isAvailable] first for optional syncs).
 */
class SupabaseRest(private val auth: SupabaseAuth) {

    private val json = "application/json; charset=utf-8".toMediaType()

    /** True when a backend is configured and a real (non-demo) user is signed in. */
    val isAvailable: Boolean
        get() = SupabaseConfig.isConfigured && auth.currentSession()?.isDemo == false

    suspend fun select(table: String, vararg filters: Pair<String, String>): JSONArray =
        JSONArray(execute("GET", table, filters.toList(), null, prefer = null).ifBlank { "[]" })

    suspend fun insert(table: String, row: JSONObject, upsert: Boolean = false): JSONObject? {
        val prefer = buildString {
            append("return=representation")
            if (upsert) append(",resolution=merge-duplicates")
        }
        val text = execute("POST", table, emptyList(), row.toString(), prefer)
        return JSONArray(text.ifBlank { "[]" }).optJSONObject(0)
    }

    suspend fun update(table: String, patch: JSONObject, vararg filters: Pair<String, String>) {
        require(filters.isNotEmpty()) { "Refusing to PATCH $table without a filter" }
        execute("PATCH", table, filters.toList(), patch.toString(), "return=minimal")
    }

    suspend fun delete(table: String, vararg filters: Pair<String, String>) {
        require(filters.isNotEmpty()) { "Refusing to DELETE from $table without a filter" }
        execute("DELETE", table, filters.toList(), null, "return=minimal")
    }

    /** Calls a Postgres function; returns the raw JSON text. */
    suspend fun rpc(function: String, params: JSONObject): String =
        execute("POST", "rpc/$function", emptyList(), params.toString(), prefer = null)

    private suspend fun execute(
        method: String,
        path: String,
        filters: List<Pair<String, String>>,
        body: String?,
        prefer: String?
    ): String {
        if (!SupabaseConfig.isConfigured) throw SupabaseException(0, "Supabase is not configured")
        val token = auth.validAccessToken() ?: throw SupabaseException(401, "Not signed in")
        val url = "${SupabaseConfig.url}/rest/v1/$path".toHttpUrl().newBuilder().apply {
            filters.forEach { (key, value) -> addQueryParameter(key, value) }
        }.build()
        val request = Request.Builder()
            .url(url)
            .method(method, body?.toRequestBody(json))
            .header("apikey", SupabaseConfig.anonKey)
            .header("Authorization", "Bearer $token")
            .apply { if (prefer != null) header("Prefer", prefer) }
            .build()
        return withContext(Dispatchers.IO) {
            Http.client.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw SupabaseException(response.code, SupabaseAuth.errorMessage(text, response.code))
                }
                text
            }
        }
    }
}
