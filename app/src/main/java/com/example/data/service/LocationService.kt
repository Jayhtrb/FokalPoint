package com.example.data.service

import android.Manifest
import android.content.Context
import android.location.Location
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.tasks.await

class LocationService(private val context: Context) {
    
    private val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
    private val _currentLocation = MutableLiveData<Location?>()
    val currentLocation: LiveData<Location?> = _currentLocation
    
    data class CityInfo(
        val name: String,
        val state: String,
        val country: String,
        val latitude: Double,
        val longitude: Double,
        val radius: Int = 50 // km
    )
    
    // Major Indian Cities with coordinates
    val majorCities = listOf(
        CityInfo("Hyderabad", "Telangana", "India", 17.3850, 78.4867, 50),
        CityInfo("Mumbai", "Maharashtra", "India", 19.0760, 72.8777, 50),
        CityInfo("Delhi", "Delhi", "India", 28.7041, 77.1025, 50),
        CityInfo("Bengaluru", "Karnataka", "India", 12.9716, 77.5946, 50),
        CityInfo("Chennai", "Tamil Nadu", "India", 13.0827, 80.2707, 50),
        CityInfo("Kolkata", "West Bengal", "India", 22.5726, 88.3639, 50),
        CityInfo("Pune", "Maharashtra", "India", 18.5204, 73.8567, 50),
        CityInfo("Ahmedabad", "Gujarat", "India", 23.0225, 72.5714, 50),
        CityInfo("Jaipur", "Rajasthan", "India", 26.9124, 75.7873, 50),
        CityInfo("Lucknow", "Uttar Pradesh", "India", 26.8467, 80.9462, 50),
        CityInfo("Nagpur", "Maharashtra", "India", 21.1458, 79.0882, 50),
        CityInfo("Indore", "Madhya Pradesh", "India", 22.7196, 75.8577, 50),
        CityInfo("Bhopal", "Madhya Pradesh", "India", 23.2599, 77.4126, 50),
        CityInfo("Visakhapatnam", "Andhra Pradesh", "India", 17.6868, 83.2185, 50),
        CityInfo("Vijayawada", "Andhra Pradesh", "India", 16.5062, 80.6480, 50)
    )
    
    suspend fun detectUserCity(): CityInfo? {
        return try {
            // Try to get last known location
            val location = fusedLocationClient.lastLocation.await()
            if (location != null) {
                findNearestCity(location)
            } else {
                // Request current location
                val currentLocation = fusedLocationClient.getCurrentLocation(
                    Priority.PRIORITY_HIGH_ACCURACY,
                    null
                ).await()
                if (currentLocation != null) {
                    findNearestCity(currentLocation)
                } else {
                    // Default to Hyderabad if location unavailable
                    majorCities.find { it.name == "Hyderabad" }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            majorCities.find { it.name == "Hyderabad" }
        }
    }
    
    private fun findNearestCity(location: Location): CityInfo? {
        return majorCities.minByOrNull { city ->
            val results = FloatArray(1)
            Location.distanceBetween(
                location.latitude, location.longitude,
                city.latitude, city.longitude,
                results
            )
            results[0]
        }
    }
    
    fun getCitiesWithinRadius(location: CityInfo, radiusKm: Int = 50): List<CityInfo> {
        return majorCities.filter { city ->
            val results = FloatArray(1)
            Location.distanceBetween(
                location.latitude, location.longitude,
                city.latitude, city.longitude,
                results
            )
            results[0] <= radiusKm * 1000 // Convert km to meters
        }
    }
}
