package com.example.data.dao

import androidx.room.*
import com.example.data.model.*
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {
    @Query("SELECT * FROM users WHERE id = :id")
    suspend fun getUserById(id: String): User?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: User)
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
    @Query("SELECT * FROM portfolios WHERE creatorId = :creatorId")
    fun getPortfolioByCreator(creatorId: String): Flow<List<Portfolio>>

    @Query("SELECT * FROM portfolios WHERE creatorId = :creatorId")
    suspend fun getPortfolioByCreatorSync(creatorId: String): List<Portfolio>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPortfolio(portfolio: Portfolio)

    @Query("DELETE FROM portfolios WHERE id = :id")
    suspend fun deletePortfolioById(id: Long)
}

@Dao
interface BookingDao {
    @Query("SELECT * FROM bookings WHERE customerId = :customerId ORDER BY createdAt DESC")
    fun getBookingsForCustomer(customerId: String): Flow<List<Booking>>

    @Query("SELECT * FROM bookings WHERE creatorId = :creatorId ORDER BY createdAt DESC")
    fun getBookingsForCreator(creatorId: String): Flow<List<Booking>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBooking(booking: Booking)

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

    @Query("""
        SELECT DISTINCT 
            CASE WHEN senderId = :userId THEN receiverId ELSE senderId END 
        FROM messages 
        WHERE senderId = :userId OR receiverId = :userId
    """)
    fun getChatPartners(userId: String): Flow<List<String>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: Message)

    @Query("""
        SELECT EXISTS(
            SELECT 1 FROM messages 
            WHERE senderId = :senderId 
              AND receiverId = :receiverId 
              AND message = :message 
              AND createdAt = :createdAt
        )
    """)
    suspend fun checkMessageExists(senderId: String, receiverId: String, message: String, createdAt: Long): Boolean
}

@Dao
interface FavoriteDao {
    @Query("SELECT * FROM favorites WHERE customerId = :customerId")
    fun getFavoritesForCustomer(customerId: String): Flow<List<Favorite>>

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE customerId = :customerId AND creatorId = :creatorId)")
    fun isFavorite(customerId: String, creatorId: String): Flow<Boolean>

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

    @Query("DELETE FROM client_leads WHERE id = :id")
    suspend fun deleteLeadById(id: Long)
}

@Dao
interface PayoutMethodDao {
    @Query("SELECT * FROM payout_methods WHERE userId = :userId ORDER BY createdAt DESC")
    fun getPayoutMethodsForUser(userId: String): Flow<List<PayoutMethod>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPayoutMethod(payoutMethod: PayoutMethod)

    @Query("DELETE FROM payout_methods WHERE id = :id")
    suspend fun deletePayoutMethodById(id: String)

    @Query("UPDATE payout_methods SET isDefault = (id = :id) WHERE userId = :userId")
    suspend fun setDefaultPayoutMethod(userId: String, id: String)
}
