package com.example.data.repository

import android.content.Context
import android.location.Location
import com.example.BuildConfig
import com.example.data.model.Creator
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.tasks.await
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first

class SupabaseResponse(val data: List<Any>)

class SupabaseClient(val context: Context) {
    private val client = OkHttpClient()
    val auth = SupabaseAuth(context)
    
    suspend fun rpc(functionName: String, parameters: Map<String, Any>): SupabaseResponse {
        val supabaseUrl = try { BuildConfig.SUPABASE_URL } catch (e: Exception) { "" }
        val supabaseKey = try { BuildConfig.SUPABASE_ANON_KEY } catch (e: Exception) { "" }
        
        if (supabaseUrl.isEmpty() || supabaseUrl.contains("placeholder") || !supabaseUrl.startsWith("http") || supabaseKey.isEmpty()) {
            return SupabaseResponse(emptyList())
        }
        
        return withContext(Dispatchers.IO) {
            try {
                val jsonBody = JSONObject()
                parameters.forEach { (key, value) ->
                    jsonBody.put(key, value)
                }
                
                val requestBody = jsonBody.toString().toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
                val request = Request.Builder()
                    .url("$supabaseUrl/rest/v1/rpc/$functionName")
                    .post(requestBody)
                    .addHeader("apikey", supabaseKey)
                    .addHeader("Authorization", "Bearer $supabaseKey")
                    .build()
                
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val bodyString = response.body?.string() ?: "[]"
                        val jsonArray = JSONArray(bodyString)
                        val resultList = mutableListOf<Creator>()
                        for (i in 0 until jsonArray.length()) {
                            val obj = jsonArray.getJSONObject(i)
                            
                            // Map JSONObject to Creator
                            val creator = Creator(
                                id = obj.optString("id", ""),
                                userId = obj.optString("user_id", obj.optString("id", "")),
                                creatorType = obj.optString("creator_type", "Photographer"),
                                experienceLevel = obj.optString("experience_level", "Professional"),
                                bio = obj.optString("bio", ""),
                                languages = obj.optString("languages", "English"),
                                equipment = obj.optString("equipment", ""),
                                rating = obj.optDouble("rating", 4.5),
                                verified = obj.optBoolean("verified", false),
                                startingPrice = obj.optDouble("starting_price", 20000.0),
                                instagram = obj.optString("instagram", ""),
                                website = obj.optString("website", ""),
                                yearsOfExperience = obj.optInt("years_of_experience", 3),
                                skillset = obj.optString("skillset", "Photographer"),
                                youtube = obj.optString("youtube", ""),
                                latitude = if (obj.isNull("latitude")) null else obj.optDouble("latitude"),
                                longitude = if (obj.isNull("longitude")) null else obj.optDouble("longitude"),
                                searchRadius = obj.optInt("search_radius", 50),
                                createdAt = obj.optLong("created_at", System.currentTimeMillis())
                            )
                            resultList.add(creator)
                        }
                        SupabaseResponse(resultList)
                    } else {
                        android.util.Log.e("SupabaseClient", "RPC call failed with code: ${response.code}")
                        SupabaseResponse(emptyList())
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("SupabaseClient", "RPC call error", e)
                SupabaseResponse(emptyList())
            }
        }
    }
}

class SearchRepository(private val context: Context, private val supabase: SupabaseClient) {
    
    // Auxiliary constructor to support the requested single parameter signature
    constructor(supabase: SupabaseClient) : this(supabase.context, supabase)

    private suspend fun getCurrentLocation(): Location? {
        return withContext(Dispatchers.IO) {
            try {
                val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
                fusedLocationClient.lastLocation.await()
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }

    suspend fun searchCreatorsGlobal(
        query: String,
        city: String? = null,
        eventType: String? = null,
        radius: Int = 50
    ): List<Creator> {
        // First, search locally based on user's location
        val location = getCurrentLocation()
        
        val supabaseUrl = try { BuildConfig.SUPABASE_URL } catch (e: Exception) { "" }
        val supabaseKey = try { BuildConfig.SUPABASE_ANON_KEY } catch (e: Exception) { "" }
        
        // If Supabase is unconfigured, run offline/sandbox search on local Room DB
        if (supabaseUrl.isEmpty() || supabaseUrl.contains("placeholder") || !supabaseUrl.startsWith("http") || supabaseKey.isEmpty()) {
            return withContext(Dispatchers.IO) {
                try {
                    val db = com.example.data.database.AppDatabase.getDatabase(context)
                    val creators = db.creatorDao().getAllCreators().first()
                    val results = mutableListOf<Creator>()
                    for (creator in creators) {
                        val user = db.userDao().getUserById(creator.id) ?: continue
                        
                        // Filter by city
                        if (city != null && !user.city.contains(city, ignoreCase = true)) {
                            continue
                        }
                        
                        // Filter by event type / specialty / skillset / creatorType
                        if (eventType != null) {
                            val matchesEvent = creator.skillset.contains(eventType, ignoreCase = true) ||
                                               creator.creatorType.contains(eventType, ignoreCase = true)
                            if (!matchesEvent) continue
                        }
                        
                        // Filter by query
                        if (query.isNotBlank()) {
                            val matchesQuery = user.name.contains(query, ignoreCase = true) ||
                                               creator.bio.contains(query, ignoreCase = true) ||
                                               creator.skillset.contains(query, ignoreCase = true)
                            if (!matchesQuery) continue
                        }
                        
                        // Filter by distance if coordinates are present
                        if (location != null && creator.latitude != null && creator.longitude != null) {
                            val distResults = FloatArray(1)
                            Location.distanceBetween(
                                location.latitude, location.longitude,
                                creator.latitude!!, creator.longitude!!,
                                distResults
                            )
                            val distanceKm = distResults[0] / 1000.0
                            if (distanceKm > radius) {
                                continue
                            }
                        }
                        
                        results.add(creator)
                    }
                    results
                } catch (e: Exception) {
                    android.util.Log.e("SearchRepository", "Local search fallback error", e)
                    emptyList()
                }
            }
        }

        // Build query for Supabase
        var sqlQuery = """
            SELECT 
                c.*,
                u.name,
                u.city,
                u.state,
                u.country,
                (
                    6371 * acos(
                        cos(radians(${location?.latitude ?: 17.3850})) * 
                        cos(radians(c.latitude)) *
                        cos(radians(c.longitude) - radians(${location?.longitude ?: 78.4867})) +
                        sin(radians(${location?.latitude ?: 17.3850})) * 
                        sin(radians(c.latitude))
                    )
                ) AS distance
            FROM creators c
            JOIN users u ON u.id = c.id
            WHERE 1=1
        """
        
        if (city != null) {
            sqlQuery += " AND u.city ILIKE '%$city%'"
        }
        
        if (eventType != null) {
            sqlQuery += " AND (c.skillset ILIKE '%$eventType%' OR c.creator_type ILIKE '%$eventType%')"
        }
        
        if (query.isNotBlank()) {
            sqlQuery += " AND (u.name ILIKE '%$query%' OR c.bio ILIKE '%$query%' OR c.skillset ILIKE '%$query%')"
        }
        
        sqlQuery += " ORDER BY distance LIMIT 50"
        
        val response = supabase.rpc("execute_sql", mapOf("query" to sqlQuery))
        @Suppress("UNCHECKED_CAST")
        return response.data as List<Creator>
    }
}

class Session(val user: AuthUser)
class AuthUser(val id: String, val email: String = "", val userMetadata: Map<String, Any?> = emptyMap())
class AuthResult(val url: String)
class AuthResponse(val user: AuthUser?)
class AuthUserResponse(val user: AuthUser?)

class SupabaseAuth(private val context: Context) {
    private val client = OkHttpClient()
    private val moshi = com.squareup.moshi.Moshi.Builder().addLast(com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory()).build()
    
    suspend fun getSession(): Session? {
        val supabaseUrl = try { BuildConfig.SUPABASE_URL } catch (e: Exception) { "" }
        if (supabaseUrl.isEmpty() || supabaseUrl.contains("placeholder") || !supabaseUrl.startsWith("http")) {
            // Return mock session if in mock mode
            return Session(AuthUser("current_customer_test", "ananya@gmail.com", mapOf("name" to "Ananya Rao", "role" to "Customer")))
        }
        return null
    }
    
    suspend fun signInWithGoogle(redirectUrl: String): AuthResult {
        val url = try { BuildConfig.SUPABASE_URL } catch (e: Exception) { "" }
        val authUrl = if (url.isNotEmpty() && !url.contains("placeholder") && url.startsWith("http")) {
            "$url/auth/v1/authorize?provider=google&redirect_to=$redirectUrl"
        } else {
            "fokalpoint://login-callback?access_token=mock_google_token"
        }
        return AuthResult(authUrl)
    }
    
    suspend fun signInWithGitHub(redirectUrl: String): AuthResult {
        val url = try { BuildConfig.SUPABASE_URL } catch (e: Exception) { "" }
        val authUrl = if (url.isNotEmpty() && !url.contains("placeholder") && url.startsWith("http")) {
            "$url/auth/v1/authorize?provider=github&redirect_to=$redirectUrl"
        } else {
            "fokalpoint://login-callback?access_token=mock_github_token"
        }
        return AuthResult(authUrl)
    }
    
    suspend fun signIn(email: String, password: String): AuthResponse {
        val supabaseUrl = try { BuildConfig.SUPABASE_URL } catch (e: Exception) { "" }
        val supabaseKey = try { BuildConfig.SUPABASE_ANON_KEY } catch (e: Exception) { "" }
        
        if (supabaseUrl.isEmpty() || supabaseUrl.contains("placeholder") || !supabaseUrl.startsWith("http")) {
            val mockId = "mock_${Math.abs(email.hashCode())}"
            return AuthResponse(AuthUser(mockId, email, mapOf("name" to email.substringBefore("@"), "role" to "Customer")))
        }
        
        return withContext(Dispatchers.IO) {
            val mediaType = "application/json; charset=utf-8".toMediaTypeOrNull()
            val jsonBody = """
                {
                    "email": "$email",
                    "password": "$password"
                }
            """.trimIndent()
            val body = jsonBody.toRequestBody(mediaType)
            val request = Request.Builder()
                .url("$supabaseUrl/auth/v1/token?grant_type=password")
                .post(body)
                .addHeader("apikey", supabaseKey)
                .addHeader("Content-Type", "application/json")
                .build()
                
            client.newCall(request).execute().use { response ->
                val responseBody = response.body?.string() ?: ""
                if (response.isSuccessful) {
                    val adapter = moshi.adapter(Map::class.java)
                    val respMap = adapter.fromJson(responseBody)
                    val userObj = respMap?.get("user") as? Map<*, *>
                    val id = userObj?.get("id") as? String ?: ""
                    val userMetadata = userObj?.get("user_metadata") as? Map<*, *> ?: emptyMap<Any, Any>()
                    
                    AuthResponse(AuthUser(id, email, userMetadata as Map<String, Any?>))
                } else {
                    val errorMsg = try {
                        val adapter = moshi.adapter(Map::class.java)
                        val respMap = adapter.fromJson(responseBody)
                        respMap?.get("error_description") as? String ?: respMap?.get("msg") as? String ?: "Invalid credentials"
                    } catch (e: Exception) {
                        "Invalid credentials"
                    }
                    throw Exception(errorMsg)
                }
            }
        }
    }
    
    suspend fun signUp(email: String, password: String, userMetadata: Map<String, Any>): AuthResponse {
        val supabaseUrl = try { BuildConfig.SUPABASE_URL } catch (e: Exception) { "" }
        val supabaseKey = try { BuildConfig.SUPABASE_ANON_KEY } catch (e: Exception) { "" }
        
        if (supabaseUrl.isEmpty() || supabaseUrl.contains("placeholder") || !supabaseUrl.startsWith("http")) {
            val mockId = "mock_${Math.abs(email.hashCode())}"
            return AuthResponse(AuthUser(mockId, email, userMetadata))
        }
        
        return withContext(Dispatchers.IO) {
            val mediaType = "application/json; charset=utf-8".toMediaTypeOrNull()
            val metadataObj = org.json.JSONObject(userMetadata)
            val jsonBody = org.json.JSONObject().apply {
                put("email", email)
                put("password", password)
                put("data", metadataObj)
            }.toString()
            
            val body = jsonBody.toRequestBody(mediaType)
            val request = Request.Builder()
                .url("$supabaseUrl/auth/v1/signup")
                .post(body)
                .addHeader("apikey", supabaseKey)
                .addHeader("Content-Type", "application/json")
                .build()
                
            client.newCall(request).execute().use { response ->
                val responseBody = response.body?.string() ?: ""
                if (response.isSuccessful) {
                    val adapter = moshi.adapter(Map::class.java)
                    val respMap = adapter.fromJson(responseBody)
                    val userObj = respMap?.get("user") as? Map<*, *>
                    val id = userObj?.get("id") as? String ?: ""
                    val metadata = userObj?.get("user_metadata") as? Map<*, *> ?: emptyMap<Any, Any>()
                    
                    AuthResponse(AuthUser(id, email, metadata as Map<String, Any?>))
                } else {
                    val errorMsg = try {
                        val adapter = moshi.adapter(Map::class.java)
                        val respMap = adapter.fromJson(responseBody)
                        respMap?.get("msg") as? String ?: "Sign up failed"
                    } catch (e: Exception) {
                        "Sign up failed"
                    }
                    throw Exception(errorMsg)
                }
            }
        }
    }
    
    suspend fun getUser(): AuthUserResponse {
        return AuthUserResponse(AuthUser("current_customer_test", "ananya@gmail.com", mapOf("name" to "Ananya Rao", "role" to "Customer")))
    }
    
    suspend fun signOut() {
        // Sign out clear session
    }
}
