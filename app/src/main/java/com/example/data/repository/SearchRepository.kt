package com.example.data.repository

import android.content.Context
import android.location.Location
import com.example.data.database.AppDatabase
import com.example.data.model.Creator
import com.example.data.model.User
import com.example.data.supabase.SupabaseClient
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class SearchRepository(private val context: Context, private val supabase: SupabaseClient) {

    @android.annotation.SuppressLint("MissingPermission") // guarded below
    private suspend fun getCurrentLocation(): Location? {
        if (!com.example.data.service.hasLocationPermission(context)) return null
        return withContext(Dispatchers.IO) {
            try {
                val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
                fusedLocationClient.lastLocation.await()
            } catch (e: SecurityException) {
                null // Location permission not granted: search without distance ranking.
            } catch (e: Exception) {
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
        val location = getCurrentLocation()

        // Demo mode (or signed out): search the on-device catalogue.
        if (!supabase.rest.isAvailable) {
            return withContext(Dispatchers.IO) {
                try {
                    val db = AppDatabase.getDatabase(context)
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

        // Live backend: parameterized RPC (see supabase/migrations/*_search_creators.sql).
        // Inputs are passed as JSON arguments, never concatenated into SQL.
        val params = JSONObject()
            .put("p_query", query.trim())
            .put("p_city", city?.takeIf { it.isNotBlank() } ?: JSONObject.NULL)
            .put("p_event_type", eventType?.takeIf { it.isNotBlank() } ?: JSONObject.NULL)
            .put("p_lat", location?.latitude ?: JSONObject.NULL)
            .put("p_lng", location?.longitude ?: JSONObject.NULL)
            .put("p_radius_km", radius)
        return try {
            val rows = JSONArray(supabase.rest.rpc("search_creators", params))
            val db = AppDatabase.getDatabase(context)
            (0 until rows.length()).map { i -> cacheRow(db, rows.getJSONObject(i)) }
        } catch (e: Exception) {
            android.util.Log.e("SearchRepository", "Remote creator search failed", e)
            emptyList()
        }
    }

    /** Refreshes the local creator catalogue from the backend (all creators, no location filter). */
    suspend fun refreshCatalog() {
        if (!supabase.rest.isAvailable) return
        val rows = JSONArray(
            supabase.rest.rpc(
                "search_creators",
                JSONObject().put("p_query", "").put("p_radius_km", 20000)
            )
        )
        val db = AppDatabase.getDatabase(context)
        for (i in 0 until rows.length()) cacheRow(db, rows.getJSONObject(i))
    }

    private suspend fun cacheRow(db: AppDatabase, row: JSONObject): Creator {
        val creator = creatorFromRow(row)
        db.userDao().insertUser(
            User(
                id = creator.id,
                name = row.optString("name", "FokalPoint Creator"),
                email = "",
                phone = "",
                role = "Creator",
                profileImage = row.optString("profile_image", "").takeUnless { it == "null" }.orEmpty(),
                city = row.optString("city", "").takeUnless { it == "null" }.orEmpty(),
                state = row.optString("state", "").takeUnless { it == "null" }.orEmpty(),
                country = row.optString("country", "").takeUnless { it == "null" }.orEmpty()
            )
        )
        db.creatorDao().insertCreator(creator)
        return creator
    }

    companion object {
        fun creatorFromRow(obj: JSONObject): Creator = Creator(
            id = obj.optString("id", ""),
            userId = obj.optString("id", ""),
            creatorType = obj.optString("creator_type", "Photographer"),
            experienceLevel = obj.optString("experience_level", "Professional"),
            bio = obj.optString("bio", ""),
            languages = obj.optString("languages", ""),
            equipment = obj.optString("equipment", ""),
            rating = obj.optDouble("rating", 0.0),
            verified = obj.optBoolean("verified", false),
            startingPrice = obj.optDouble("starting_price", 0.0),
            instagram = obj.optString("instagram", ""),
            website = obj.optString("website", ""),
            yearsOfExperience = obj.optInt("years_of_experience", 0),
            skillset = obj.optString("skillset", "Photographer"),
            youtube = obj.optString("youtube", ""),
            latitude = if (obj.isNull("latitude")) null else obj.optDouble("latitude"),
            longitude = if (obj.isNull("longitude")) null else obj.optDouble("longitude"),
            searchRadius = obj.optInt("search_radius", 50)
        )
    }
}
