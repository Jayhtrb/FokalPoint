package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "users")
data class User(
    @PrimaryKey val id: String, // email or UUID
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

@Entity(tableName = "creators")
data class Creator(
    @PrimaryKey val id: String, // corresponds to User.id if role is Creator
    val userId: String,
    val creatorType: String, // "Photographer", "Videographer", "Both"
    val experienceLevel: String, // "Beginner", "Professional", "Studio"
    val bio: String,
    val languages: String, // e.g. "English, Hindi"
    val equipment: String, // "Sony A7IV, Mavic 3"
    val rating: Double,
    val verified: Boolean,
    val startingPrice: Double,
    val instagram: String,
    val website: String,
    val yearsOfExperience: Int,
    val skillset: String = "Photographer", // e.g., "Photographer, Videographer, Reel Creator"
    val youtube: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null,
    val searchRadius: Int = 50,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "portfolios")
data class Portfolio(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val creatorId: String,
    val title: String,
    val category: String, // "Wedding", "Birthday", "Corporate", etc.
    val mediaUrl: String, // Image URL or local resource ID
    val mediaType: String, // "IMAGE", "VIDEO"
    val thumbnail: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "bookings")
data class Booking(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val customerId: String,
    val creatorId: String,
    val eventType: String, // e.g., "Wedding", "Birthday"
    val date: String, // "YYYY-MM-DD"
    val time: String, // "14:00"
    val hours: Int,
    val price: Double,
    val status: String, // "Pending", "Accepted", "Confirmed", "Completed", "Cancelled"
    val paymentStatus: String, // "Pending", "Paid"
    val createdAt: Long = System.currentTimeMillis()
)

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

@Entity(tableName = "favorites")
data class Favorite(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val customerId: String,
    val creatorId: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "client_leads")
data class ClientLead(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val customerId: String,
    val customerName: String,
    val customerEmail: String,
    val eventType: String,
    val location: String, // Dynamic location entered by client (e.g. city, country)
    val budget: Double,
    val description: String,
    val dateDetail: String, // e.g. "Next month", "July 12"
    val createdAt: Long = System.currentTimeMillis(),
    val referenceImages: String? = null
)

enum class PayoutPeriod { Daily, Weekly, Monthly }

data class PayoutStats(
    val available: Double,
    val pending: Double,
    val totalEarned: Double,
    val earningsData: List<EarningsDataPoint>
)

data class EarningsDataPoint(
    val date: String,
    val amount: Double
)

data class PendingPayout(
    val id: String,
    val bookingId: Long,
    val eventType: String,
    val date: String,
    val amount: Double,
    val status: String
)

data class PayoutHistory(
    val id: String,
    val description: String,
    val date: String,
    val method: String,
    val amount: Double,
    val status: String
)

data class Payment(
    val id: String,
    val type: String, // "booking", "payout", "refund"
    val description: String,
    val date: String,
    val transactionId: String,
    val amount: Double,
    val status: String // "completed", "pending", "failed"
)

data class PayoutNotificationPreferences(
    val payoutNotifications: Boolean = true,
    val payoutProcessed: Boolean = true,
    val payoutFailed: Boolean = true,
    val weeklyReport: Boolean = false
)

