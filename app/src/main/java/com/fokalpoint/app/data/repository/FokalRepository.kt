package com.fokalpoint.app.data.repository

import com.fokalpoint.app.data.dao.*
import com.fokalpoint.app.data.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull

class FokalRepository(
    private val userDao: UserDao,
    private val creatorDao: CreatorDao,
    private val portfolioDao: PortfolioDao,
    private val bookingDao: BookingDao,
    private val reviewDao: ReviewDao,
    private val messageDao: MessageDao,
    private val favoriteDao: FavoriteDao,
    private val clientLeadDao: ClientLeadDao
) {
    val allCreators: Flow<List<Creator>> = creatorDao.getAllCreators()
    val allLeads: Flow<List<ClientLead>> = clientLeadDao.getAllLeads()
    val allUsers: Flow<List<User>> = userDao.getAllUsers()
    fun recentPortfolio(limit: Int): Flow<List<Portfolio>> = portfolioDao.getRecentPortfolio(limit)

    fun getCreator(id: String): Flow<Creator?> = creatorDao.getCreatorById(id)
    suspend fun getCreatorSync(id: String): Creator? = creatorDao.getCreatorByIdSync(id)
    suspend fun insertCreator(creator: Creator) = creatorDao.insertCreator(creator)

    suspend fun insertUser(user: User) = userDao.insertUser(user)
    suspend fun getUser(id: String): User? = userDao.getUserById(id)

    fun getPortfolio(creatorId: String): Flow<List<Portfolio>> = portfolioDao.getPortfolioByCreator(creatorId)
    suspend fun getPortfolioSync(creatorId: String): List<Portfolio> = portfolioDao.getPortfolioByCreatorSync(creatorId)
    suspend fun insertPortfolio(portfolio: Portfolio) = portfolioDao.insertPortfolio(portfolio)
    suspend fun deletePortfolio(id: Long) = portfolioDao.deletePortfolioById(id)

    fun getBookingsForCustomer(customerId: String): Flow<List<Booking>> = bookingDao.getBookingsForCustomer(customerId)
    fun getBookingsForCreator(creatorId: String): Flow<List<Booking>> = bookingDao.getBookingsForCreator(creatorId)
    suspend fun insertBooking(booking: Booking): Long = bookingDao.insertBooking(booking)
    suspend fun getBookingByIdSync(id: Long): Booking? = bookingDao.getBookingById(id)
    suspend fun updateBookingStatus(id: Long, status: String) = bookingDao.updateBookingStatus(id, status)
    suspend fun updatePaymentStatus(id: Long, paymentStatus: String) = bookingDao.updatePaymentStatus(id, paymentStatus)

    fun getReviews(creatorId: String): Flow<List<Review>> = reviewDao.getReviewsForCreator(creatorId)
    fun reviewedBookingIds(customerId: String): Flow<List<Long>> = reviewDao.getReviewedBookingIds(customerId)
    suspend fun insertReview(review: Review) = reviewDao.insertReview(review)

    fun getChatMessages(user1: String, user2: String): Flow<List<Message>> = messageDao.getChatMessages(user1, user2)
    fun getConversations(userId: String): Flow<List<Message>> = messageDao.getConversations(userId)
    fun getChatPartners(userId: String): Flow<List<String>> = messageDao.getChatPartners(userId)
    suspend fun insertMessage(message: Message) = messageDao.insertMessage(message)

    fun getFavorites(customerId: String): Flow<List<Favorite>> = favoriteDao.getFavoritesForCustomer(customerId)
    suspend fun addFavorite(customerId: String, creatorId: String) = favoriteDao.insertFavorite(Favorite(customerId, creatorId))
    suspend fun removeFavorite(customerId: String, creatorId: String) = favoriteDao.removeFavorite(customerId, creatorId)

    suspend fun insertLead(lead: ClientLead) = clientLeadDao.insertLead(lead)
    suspend fun replaceLeads(leads: List<ClientLead>) {
        clientLeadDao.clearLeads()
        leads.forEach { clientLeadDao.insertLead(it) }
    }

    /** Demo mode only: a believable sample marketplace, using photos bundled in the APK. */
    suspend fun seedDemoDataIfEmpty() {
        if ((creatorDao.getAllCreators().firstOrNull()?.size ?: 0) > 0) return
        val year = 365L * 24 * 3600 * 1000
        val now = System.currentTimeMillis()
        data class Seed(
            val id: String, val name: String, val avatar: String, val city: String, val state: String,
            val type: String, val level: String, val headline: String, val bio: String, val price: Double,
            val years: Int, val skills: String, val pro: Boolean, val cover: String, val portfolio: List<Pair<String, String>>,
            val rating: Double, val reviews: List<Pair<String, String>>
        )
        val seeds = listOf(
            Seed("demo_arjun", "Arjun Mehta", "res:av_arjun", "Mumbai", "Maharashtra", "Both", "Studio",
                "Cinematic weddings that feel like films",
                "Ten years documenting weddings across India with a two-camera cinema crew. Candid, emotional, never staged.",
                35000.0, 10, "Wedding, Pre-Wedding, Films", true, "res:pf_wedding_1",
                listOf("res:pf_wedding_1" to "Wedding", "res:pf_wedding_4" to "Wedding", "res:pf_rings" to "Details", "res:pf_wedding_5" to "Pre-Wedding"),
                4.9, listOf("Priya S." to "Every frame looked like a movie still. Our families cried watching the film.",
                    "Rohan K." to "Calm, invisible on the day, and delivered two weeks early.")),
            Seed("demo_riya", "Riya Kapoor", "res:av_riya", "Delhi", "Delhi", "Photographer", "Professional",
                "Editorial fashion & brand campaigns",
                "Fashion and product photographer for labels and creators. Bold colour, clean light, magazine-ready retouching.",
                22000.0, 6, "Fashion, Product, Portrait", true, "res:pf_fashion_2",
                listOf("res:pf_fashion_2" to "Fashion", "res:pf_fashion_1" to "Fashion", "res:pf_fashion_3" to "Editorial", "res:pf_product_1" to "Product"),
                4.8, listOf("Label Noor" to "Our lookbook sold out the first week. Riya just gets it.",
                    "Aditi M." to "Brought a mood board to life better than I imagined.")),
            Seed("demo_meera", "Meera Nair", "res:av_meera", "Goa", "Goa", "Both", "Studio",
                "Destination weddings by the sea",
                "Golden-hour specialist for beach and destination weddings. Small team, big heart, film-inspired colour.",
                48000.0, 12, "Wedding, Destination, Films", false, "res:pf_wedding_2",
                listOf("res:pf_wedding_2" to "Wedding", "res:pf_wedding_3" to "Wedding", "res:pf_wedding_5" to "Pre-Wedding"),
                4.9, listOf("Neha & Sam" to "Meera made a stormy beach day look magical.")),
            Seed("demo_kabir", "Kabir Singh", "res:av_kabir", "Bengaluru", "Karnataka", "Videographer", "Professional",
                "Events, launches & corporate stories",
                "Fast-turnaround event coverage and recap films for startups, conferences and celebrations.",
                18000.0, 7, "Corporate, Events, Birthday", false, "res:pf_event_1",
                listOf("res:pf_event_1" to "Corporate", "res:pf_birthday_1" to "Birthday"),
                4.7, listOf("Startup Grind BLR" to "Same-night recap reel. Our sponsors loved it.")),
            Seed("demo_zara", "Zara Khan", "res:av_zara", "Jaipur", "Rajasthan", "Photographer", "Professional",
                "Fine-art portraits with moody light",
                "Portrait artist focused on light, texture and stillness. Personal branding, headshots and fine-art series.",
                12000.0, 4, "Portrait, Fine Art, Headshots", false, "res:pf_fineart_1",
                listOf("res:pf_fineart_1" to "Fine Art", "res:av_ananya" to "Portrait", "res:av_riya" to "Portrait"),
                4.8, listOf("Ishaan V." to "The best headshots I've ever had. Booked again for my team.")),
            Seed("demo_ananya", "Ananya Rao", "res:av_ananya", "Hyderabad", "Telangana", "Photographer", "Beginner",
                "Maternity, newborn & family moments",
                "Warm, gentle family sessions at home or outdoors. Patient with little ones, quick gallery delivery.",
                9000.0, 2, "Maternity, Family, Birthday", false, "res:pf_birthday_1",
                listOf("res:pf_birthday_1" to "Birthday", "res:pf_wedding_3" to "Family"),
                4.6, listOf("Kavya R." to "So patient with our twins. Lovely photos!"))
        )
        seeds.forEachIndexed { index, s ->
            userDao.insertUser(User(s.id, s.name, "", "", "Creator", s.avatar, s.city, s.state, "India"))
            creatorDao.insertCreator(
                Creator(
                    id = s.id, userId = s.id, creatorType = s.type, experienceLevel = s.level, bio = s.bio,
                    languages = "English, Hindi", equipment = "", rating = s.rating, verified = s.years >= 5,
                    startingPrice = s.price, instagram = "", website = "", yearsOfExperience = s.years,
                    skillset = s.skills, headline = s.headline, coverImage = s.cover,
                    reviewCount = s.reviews.size * 9 + index * 3, proUntil = if (s.pro) now + year else 0
                )
            )
            s.portfolio.forEachIndexed { i, (img, cat) ->
                portfolioDao.insertPortfolio(Portfolio(creatorId = s.id, title = cat, category = cat, mediaUrl = img, createdAt = now - i * 3_600_000L))
            }
            s.reviews.forEachIndexed { i, (who, text) ->
                reviewDao.insertReview(
                    Review(bookingId = -(index * 10L + i + 1), customerId = "demo_customer_$i", creatorId = s.id,
                        rating = if (i == 0) 5.0 else 4.0, review = text, customerName = who, createdAt = now - (i + 1) * 86_400_000L * 9)
                )
            }
        }
        listOf(
            ClientLead(customerId = "demo_c1", customerName = "Sneha P.", customerEmail = "", eventType = "Wedding",
                location = "Udaipur, Rajasthan", budget = 150000.0, description = "Three-day palace wedding, need photo + film team.", dateDetail = "February"),
            ClientLead(customerId = "demo_c2", customerName = "Vikram R.", customerEmail = "", eventType = "Corporate",
                location = "Bengaluru", budget = 25000.0, description = "Product launch evening, 150 guests, same-day reel.", dateDetail = "Next week"),
            ClientLead(customerId = "demo_c3", customerName = "Fatima S.", customerEmail = "", eventType = "Maternity",
                location = "Mumbai", budget = 15000.0, description = "Outdoor golden-hour maternity shoot.", dateDetail = "This month"),
            ClientLead(customerId = "demo_c4", customerName = "Dev & Isha", customerEmail = "", eventType = "Pre-Wedding",
                location = "Goa", budget = 40000.0, description = "Beach pre-wedding shoot, sunrise preferred.", dateDetail = "December")
        ).forEach { clientLeadDao.insertLead(it) }
    }
}
