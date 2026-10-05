package com.fokalpoint.app.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Local cache of `public.users`. */
@Entity(tableName = "users")
data class User(
    @PrimaryKey val id: String,
    val name: String,
    val email: String,
    val phone: String,
    val role: String, // "Customer" or "Creator"
    val profileImage: String,
    val city: String,
    val state: String,
    val country: String,
    val createdAt: Long = System.currentTimeMillis()
)

/** Local cache of `public.creators` (public profile fields only). */
@Entity(tableName = "creators")
data class Creator(
    @PrimaryKey val id: String,
    val userId: String,
    val creatorType: String, // "Photographer", "Videographer", "Both"
    val experienceLevel: String, // "Beginner", "Professional", "Studio"
    val bio: String,
    val languages: String,
    val equipment: String,
    val rating: Double,
    val verified: Boolean,
    val startingPrice: Double,
    val instagram: String,
    val website: String,
    val yearsOfExperience: Int,
    val skillset: String = "Photographer",
    val youtube: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null,
    val searchRadius: Int = 50,
    val headline: String = "",
    val coverImage: String = "",
    val reviewCount: Int = 0,
    /** Epoch millis until which Creator Pro is active; 0 = not Pro. Server-controlled. */
    val proUntil: Long = 0,
    val createdAt: Long = System.currentTimeMillis()
) {
    val isPro: Boolean get() = proUntil > System.currentTimeMillis()
    val specialties: List<String> get() = skillset.split(',').map { it.trim() }.filter { it.isNotEmpty() }
}

@Entity(tableName = "portfolios")
data class Portfolio(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val creatorId: String,
    val title: String,
    val category: String,
    val mediaUrl: String,
    val mediaType: String = "IMAGE",
    val thumbnail: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "bookings")
data class Booking(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val customerId: String,
    val creatorId: String,
    val eventType: String,
    val date: String, // yyyy-MM-dd
    val time: String, // HH:mm
    val hours: Int,
    val price: Double,
    val status: String, // Pending, Accepted, Confirmed, Completed, Cancelled
    val paymentStatus: String, // Pending, Paid
    val packageName: String = "",
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis()
) {
    val isActive: Boolean get() = status in setOf("Pending", "Accepted", "Confirmed")
}

@Entity(tableName = "reviews")
data class Review(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bookingId: Long,
    val customerId: String,
    val creatorId: String,
    val rating: Double,
    val review: String,
    val customerName: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "messages")
data class Message(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val senderId: String,
    val receiverId: String,
    val message: String,
    val mediaUrl: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "favorites", primaryKeys = ["customerId", "creatorId"])
data class Favorite(
    val customerId: String,
    val creatorId: String,
    val createdAt: Long = System.currentTimeMillis()
)

/** A customer's open "shoot alert" that creators can respond to (leads). */
@Entity(tableName = "client_leads")
data class ClientLead(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val customerId: String,
    val customerName: String,
    val customerEmail: String,
    val eventType: String,
    val location: String,
    val budget: Double,
    val description: String,
    val dateDetail: String,
    val createdAt: Long = System.currentTimeMillis(),
    val referenceImages: String? = null
)

/** The three packages every creator offers, priced from their starting price. */
enum class ShootPackage(val title: String, val hours: Int, val multiplier: Double, val perks: List<String>) {
    Essential("Essential", 2, 1.0, listOf("2 hours coverage", "40+ edited photos", "Online gallery")),
    Signature("Signature", 4, 1.8, listOf("4 hours coverage", "120+ edited photos", "Highlight reel", "Online gallery")),
    Luxe("Luxe", 8, 3.2, listOf("Full-day coverage", "300+ edited photos", "Cinematic film", "Second shooter", "Priority delivery"));

    fun price(startingPrice: Double): Double = (startingPrice * multiplier / 100).let { kotlin.math.round(it) * 100 }
}
