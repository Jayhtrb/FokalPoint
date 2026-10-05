package com.fokalpoint.app.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.fokalpoint.app.data.model.*
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {
    @Query("SELECT * FROM users WHERE id = :id")
    suspend fun getUserById(id: String): User?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: User)

    @Query("SELECT * FROM users")
    fun getAllUsers(): Flow<List<User>>
}

@Dao
interface CreatorDao {
    @Query("SELECT * FROM creators")
    fun getAllCreators(): Flow<List<Creator>>

    @Query("SELECT * FROM creators WHERE id = :id")
    fun getCreatorById(id: String): Flow<Creator?>

    @Query("SELECT * FROM creators WHERE id = :id")
    suspend fun getCreatorByIdSync(id: String): Creator?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCreator(creator: Creator)
}

@Dao
interface PortfolioDao {
    @Query("SELECT * FROM portfolios WHERE creatorId = :creatorId ORDER BY createdAt DESC")
    fun getPortfolioByCreator(creatorId: String): Flow<List<Portfolio>>

    @Query("SELECT * FROM portfolios WHERE creatorId = :creatorId ORDER BY createdAt DESC")
    suspend fun getPortfolioByCreatorSync(creatorId: String): List<Portfolio>

    @Query("SELECT * FROM portfolios ORDER BY createdAt DESC LIMIT :limit")
    fun getRecentPortfolio(limit: Int): Flow<List<Portfolio>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPortfolio(portfolio: Portfolio)

    @Query("DELETE FROM portfolios WHERE id = :id")
    suspend fun deletePortfolioById(id: Long)
}

@Dao
interface BookingDao {
    @Query("SELECT * FROM bookings WHERE customerId = :customerId ORDER BY date ASC, time ASC")
    fun getBookingsForCustomer(customerId: String): Flow<List<Booking>>

    @Query("SELECT * FROM bookings WHERE creatorId = :creatorId ORDER BY date ASC, time ASC")
    fun getBookingsForCreator(creatorId: String): Flow<List<Booking>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBooking(booking: Booking): Long

    @Query("UPDATE bookings SET status = :status WHERE id = :id")
    suspend fun updateBookingStatus(id: Long, status: String)

    @Query("UPDATE bookings SET paymentStatus = :paymentStatus WHERE id = :id")
    suspend fun updatePaymentStatus(id: Long, paymentStatus: String)

    @Query("SELECT * FROM bookings WHERE id = :id")
    suspend fun getBookingById(id: Long): Booking?
}

@Dao
interface ReviewDao {
    @Query("SELECT * FROM reviews WHERE creatorId = :creatorId ORDER BY createdAt DESC")
    fun getReviewsForCreator(creatorId: String): Flow<List<Review>>

    @Query("SELECT bookingId FROM reviews WHERE customerId = :customerId")
    fun getReviewedBookingIds(customerId: String): Flow<List<Long>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReview(review: Review)
}

@Dao
interface MessageDao {
    @Query("""
        SELECT * FROM messages
        WHERE (senderId = :user1 AND receiverId = :user2)
           OR (senderId = :user2 AND receiverId = :user1)
        ORDER BY createdAt ASC
    """)
    fun getChatMessages(user1: String, user2: String): Flow<List<Message>>

    /** Latest message per conversation partner, newest conversation first. */
    @Query("""
        SELECT * FROM messages m
        WHERE (m.senderId = :userId OR m.receiverId = :userId)
          AND m.createdAt = (
            SELECT MAX(x.createdAt) FROM messages x
            WHERE (x.senderId = m.senderId AND x.receiverId = m.receiverId)
               OR (x.senderId = m.receiverId AND x.receiverId = m.senderId)
          )
        ORDER BY m.createdAt DESC
    """)
    fun getConversations(userId: String): Flow<List<Message>>

    @Query("""
        SELECT DISTINCT
            CASE WHEN senderId = :userId THEN receiverId ELSE senderId END
        FROM messages
        WHERE senderId = :userId OR receiverId = :userId
    """)
    fun getChatPartners(userId: String): Flow<List<String>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: Message)
}

@Dao
interface FavoriteDao {
    @Query("SELECT * FROM favorites WHERE customerId = :customerId ORDER BY createdAt DESC")
    fun getFavoritesForCustomer(customerId: String): Flow<List<Favorite>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFavorite(favorite: Favorite)

    @Query("DELETE FROM favorites WHERE customerId = :customerId AND creatorId = :creatorId")
    suspend fun removeFavorite(customerId: String, creatorId: String)
}

@Dao
interface ClientLeadDao {
    @Query("SELECT * FROM client_leads ORDER BY createdAt DESC")
    fun getAllLeads(): Flow<List<ClientLead>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLead(lead: ClientLead)

    @Query("DELETE FROM client_leads")
    suspend fun clearLeads()
}
