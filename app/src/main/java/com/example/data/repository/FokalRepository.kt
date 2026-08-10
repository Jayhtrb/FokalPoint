package com.example.data.repository

import com.example.data.dao.*
import com.example.data.model.*
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
    private val clientLeadDao: ClientLeadDao,
    private val payoutMethodDao: PayoutMethodDao
) {
    val allCreators: Flow<List<Creator>> = creatorDao.getAllCreators()
    val allLeads: Flow<List<ClientLead>> = clientLeadDao.getAllLeads()

    fun getCreator(id: String): Flow<Creator?> = creatorDao.getCreatorById(id)
    suspend fun getCreatorSync(id: String): Creator? = creatorDao.getCreatorByIdSync(id)

    suspend fun insertLead(lead: ClientLead) = clientLeadDao.insertLead(lead)
    suspend fun deleteLead(id: Long) = clientLeadDao.deleteLeadById(id)

    fun getPortfolio(creatorId: String): Flow<List<Portfolio>> = portfolioDao.getPortfolioByCreator(creatorId)
    suspend fun getPortfolioSync(creatorId: String): List<Portfolio> = portfolioDao.getPortfolioByCreatorSync(creatorId)

    fun getBookingsForCustomer(customerId: String): Flow<List<Booking>> = bookingDao.getBookingsForCustomer(customerId)
    fun getBookingsForCreator(creatorId: String): Flow<List<Booking>> = bookingDao.getBookingsForCreator(creatorId)

    fun getReviews(creatorId: String): Flow<List<Review>> = reviewDao.getReviewsForCreator(creatorId)

    fun getChatMessages(user1: String, user2: String): Flow<List<Message>> = messageDao.getChatMessages(user1, user2)
    fun getChatPartners(userId: String): Flow<List<String>> = messageDao.getChatPartners(userId)

    fun getFavorites(customerId: String): Flow<List<Favorite>> = favoriteDao.getFavoritesForCustomer(customerId)
    fun isFavorite(customerId: String, creatorId: String): Flow<Boolean> = favoriteDao.isFavorite(customerId, creatorId)

    suspend fun insertUser(user: User) = userDao.insertUser(user)
    suspend fun getUser(id: String): User? = userDao.getUserById(id)

    suspend fun insertCreator(creator: Creator) = creatorDao.insertCreator(creator)
    suspend fun insertPortfolio(portfolio: Portfolio) = portfolioDao.insertPortfolio(portfolio)
    suspend fun insertBooking(booking: Booking) = bookingDao.insertBooking(booking)
    suspend fun getBookingByIdSync(id: Long): Booking? = bookingDao.getBookingById(id)
    suspend fun updateBookingStatus(id: Long, status: String) = bookingDao.updateBookingStatus(id, status)
    suspend fun updatePaymentStatus(id: Long, paymentStatus: String) = bookingDao.updatePaymentStatus(id, paymentStatus)

    suspend fun insertReview(review: Review) = reviewDao.insertReview(review)
    suspend fun insertMessage(message: Message) = messageDao.insertMessage(message)
    suspend fun checkMessageExistsSync(senderId: String, receiverId: String, messageText: String, createdAt: Long): Boolean =
        messageDao.checkMessageExists(senderId, receiverId, messageText, createdAt)
    suspend fun addFavorite(customerId: String, creatorId: String) {
        favoriteDao.insertFavorite(Favorite(customerId = customerId, creatorId = creatorId))
    }
    suspend fun removeFavorite(customerId: String, creatorId: String) {
        favoriteDao.removeFavorite(customerId, creatorId)
    }

    suspend fun deletePortfolio(id: Long) = portfolioDao.deletePortfolioById(id)

    fun getPayoutMethods(userId: String): Flow<List<PayoutMethod>> = payoutMethodDao.getPayoutMethodsForUser(userId)
    suspend fun insertPayoutMethod(payoutMethod: PayoutMethod) = payoutMethodDao.insertPayoutMethod(payoutMethod)
    suspend fun deletePayoutMethod(id: String) = payoutMethodDao.deletePayoutMethodById(id)
    suspend fun setDefaultPayoutMethod(userId: String, id: String) = payoutMethodDao.setDefaultPayoutMethod(userId, id)

    // Seeding beautiful high-fidelity photographer & videographer mock data if DB is empty
    suspend fun seedMockDataIfEmpty() {
        val existing = creatorDao.getAllCreators().firstOrNull()?.size ?: 0
        if (existing > 0) return

        // Setup some creators
        val creatorSeed = listOf(
            User(
                id = "amit_sharma_creator",
                name = "Amit Sharma",
                email = "amit@fokalpoint.com",
                phone = "+91 98765 43210",
                role = "Creator",
                profileImage = "https://images.unsplash.com/photo-1534528741775-53994a69daeb?auto=format&fit=crop&w=300&q=80",
                city = "Mumbai",
                state = "Maharashtra",
                country = "India"
            ) to Creator(
                id = "amit_sharma_creator",
                userId = "amit_sharma_creator",
                creatorType = "Both",
                experienceLevel = "Professional",
                bio = "Cinematic wedding filmmaker and traditional photographer with over 7 years capturing grand Indian celebrations around the globe. Specializes in candid moments and luxurious drone cinematography.",
                languages = "English, Hindi, Marathi",
                equipment = "Sony FX3, Sony A7SIII, DJI Mavic 3 Pro, G-Master Lenses",
                rating = 4.9,
                verified = true,
                startingPrice = 45000.0,
                instagram = "amit_sharmaproductions",
                website = "www.amitsharmamedia.com",
                yearsOfExperience = 8,
                skillset = "Photographer, Videographer",
                youtube = "https://youtube.com/c/amitsharmaproductions",
                latitude = 19.0760,
                longitude = 72.8777,
                searchRadius = 50
            ),
            User(
                id = "riya_sen_creator",
                name = "Riya Sen",
                email = "riya@fokalpoint.com",
                phone = "+91 98989 12345",
                role = "Creator",
                profileImage = "https://images.unsplash.com/photo-1544005313-94ddf0286df2?auto=format&fit=crop&w=300&q=80",
                city = "Delhi",
                state = "Delhi NCR",
                country = "India"
            ) to Creator(
                id = "riya_sen_creator",
                userId = "riya_sen_creator",
                creatorType = "Photographer",
                experienceLevel = "Professional",
                bio = "High-fashion, editorial, and maternity fine-art photographer. Adding a touch of elegance and editorial style to everyday portraits and intimate baby shoots.",
                languages = "English, Hindi, Bengali",
                equipment = "Canon EOS R5, EF 85mm f/1.2L, Profoto lighting kits",
                rating = 4.8,
                verified = true,
                startingPrice = 30000.0,
                instagram = "riyasen_portraits",
                website = "www.riyasen.com",
                yearsOfExperience = 5,
                skillset = "Photographer, Reel Creator",
                youtube = "https://youtube.com/c/riyasenportraits",
                latitude = 28.7041,
                longitude = 77.1025,
                searchRadius = 50
            ),
            User(
                id = "kabir_singh_creator",
                name = "Kabir Studios",
                email = "kabir@fokalpoint.com",
                phone = "+91 90000 88888",
                role = "Creator",
                profileImage = "https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?auto=format&fit=crop&w=300&q=80",
                city = "Bengaluru",
                state = "Karnataka",
                country = "India"
            ) to Creator(
                id = "kabir_singh_creator",
                userId = "kabir_singh_creator",
                creatorType = "Both",
                experienceLevel = "Studio",
                bio = "FokalPoint-certified premier photography and sound stage based in Bengaluru. Providing corporate events coverage, product commercial setups, high-definition fashion reels, and music films.",
                languages = "English, Kannada, Hindi",
                equipment = "RED Komodo, Sony FX6, Arri Alexa Mini LF, full grip setup",
                rating = 5.0,
                verified = true,
                startingPrice = 75000.0,
                instagram = "kabir_studio_blr",
                website = "www.kabirstudios.in",
                yearsOfExperience = 12,
                skillset = "Photographer, Videographer, Reel Creator",
                youtube = "https://youtube.com/c/kabirstudios",
                latitude = 12.9716,
                longitude = 77.5946,
                searchRadius = 50
            ),
            User(
                id = "vikram_goa_creator",
                name = "Vikram Fernandes",
                email = "vikram@fokalpoint.com",
                phone = "+91 88888 77777",
                role = "Creator",
                profileImage = "https://images.unsplash.com/photo-1500648767791-00dcc994a43e?auto=format&fit=crop&w=300&q=80",
                city = "Goa",
                state = "Goa",
                country = "India"
            ) to Creator(
                id = "vikram_goa_creator",
                userId = "vikram_goa_creator",
                creatorType = "Photographer",
                experienceLevel = "Beginner",
                bio = "Passionate travel, outdoor event, and beach maternity photographer. Let's capture your golden-hour moments in beautiful coastal Goa!",
                languages = "English, Konkani, Hindi",
                equipment = "Fujifilm XT-5, 56mm f/1.2, 16-55mm f/2.8",
                rating = 4.6,
                verified = false,
                startingPrice = 12000.0,
                instagram = "vikram_goaphotos",
                website = "www.vikramgoa.com",
                yearsOfExperience = 2,
                skillset = "Photographer",
                youtube = "https://youtube.com/c/vikramgoarocks",
                latitude = 15.2993,
                longitude = 74.1240,
                searchRadius = 50
            ),
            User(
                id = "manisha_mehta_creator",
                name = "Manisha Mehta",
                email = "manisha@fokalpoint.com",
                phone = "+91 91234 56789",
                role = "Creator",
                profileImage = "https://images.unsplash.com/photo-1508214751196-bcfd4ca60f91?auto=format&fit=crop&w=300&q=80",
                city = "Jaipur",
                state = "Rajasthan",
                country = "India"
            ) to Creator(
                id = "manisha_mehta_creator",
                userId = "manisha_mehta_creator",
                creatorType = "Photographer",
                experienceLevel = "Professional",
                bio = "Royal palaces portraiture and pre-wedding storyteller. Specializing in warm tones, ethnic aesthetics, and cinematic grand entries set against Rajasthan's heritage.",
                languages = "Hindi, English, Rajasthani",
                equipment = "Nikon Z9, Nikkor Z 85mm f/1.2 S, Nikkor Z 24-70mm f/2.8 S",
                rating = 4.9,
                verified = true,
                startingPrice = 35000.0,
                instagram = "manishamehta_royalshoots",
                website = "www.manishamehta.photography",
                yearsOfExperience = 6,
                skillset = "Photographer, Reel Creator",
                youtube = "https://youtube.com/c/manishamehtadigital",
                latitude = 26.9124,
                longitude = 75.7873,
                searchRadius = 50
            )
        )

        // Insert into database
        creatorSeed.forEach { (user, creator) ->
            userDao.insertUser(user)
            creatorDao.insertCreator(creator)
        }

        // Portfolios
        val portfolios = listOf(
            // Amit Sharma - Wedding, Pre-Wedding
            Portfolio(creatorId = "amit_sharma_creator", title = "The Royal Mandap Celebration", category = "Wedding", mediaUrl = "https://images.unsplash.com/photo-1511285560929-80b456fea0bc?auto=format&fit=crop&w=500&q=80", mediaType = "IMAGE", thumbnail = ""),
            Portfolio(creatorId = "amit_sharma_creator", title = "Golden Hour Couple Walk", category = "Pre-Wedding", mediaUrl = "https://images.unsplash.com/photo-1583939003579-730e3918a45a?auto=format&fit=crop&w=500&q=80", mediaType = "IMAGE", thumbnail = ""),
            Portfolio(creatorId = "amit_sharma_creator", title = "Mehendi Hands Detail", category = "Wedding", mediaUrl = "https://images.unsplash.com/photo-1595777457583-95e059d581b8?auto=format&fit=crop&w=500&q=80", mediaType = "IMAGE", thumbnail = ""),
            
            // Riya Sen - Fashion, Maternity, Kids
            Portfolio(creatorId = "riya_sen_creator", title = "Editorial Crimson Lookbook", category = "Fashion", mediaUrl = "https://images.unsplash.com/photo-1490481651871-ab68de25d43d?auto=format&fit=crop&w=500&q=80", mediaType = "IMAGE", thumbnail = ""),
            Portfolio(creatorId = "riya_sen_creator", title = "Divine Maternity Lace Portrait", category = "Maternity", mediaUrl = "https://images.unsplash.com/photo-1518156677180-95a2893f3e9f?auto=format&fit=crop&w=500&q=80", mediaType = "IMAGE", thumbnail = ""),
            Portfolio(creatorId = "riya_sen_creator", title = "Pure Innocence Baby Studio", category = "Baby Shoot", mediaUrl = "https://images.unsplash.com/photo-1519689680058-324335c77ebe?auto=format&fit=crop&w=500&q=80", mediaType = "IMAGE", thumbnail = ""),
            
            // Kabir - Corporate, Fashion
            Portfolio(creatorId = "kabir_singh_creator", title = "Tech Conference Mainstage Keynote", category = "Corporate", mediaUrl = "https://images.unsplash.com/photo-1515187029135-18ee286d815b?auto=format&fit=crop&w=500&q=80", mediaType = "IMAGE", thumbnail = ""),
            Portfolio(creatorId = "kabir_singh_creator", title = "Modern Monochrome Fashion Spread", category = "Fashion", mediaUrl = "https://images.unsplash.com/photo-1469334031218-e382a71b716b?auto=format&fit=crop&w=500&q=80", mediaType = "IMAGE", thumbnail = ""),
            
            // Vikram - Travel, Kids
            Portfolio(creatorId = "vikram_goa_creator", title = "Sunset Beach Pre-Wedding", category = "Pre-Wedding", mediaUrl = "https://images.unsplash.com/photo-1515934751635-c81c6bc9a2d8?auto=format&fit=crop&w=500&q=80", mediaType = "IMAGE", thumbnail = ""),
            Portfolio(creatorId = "vikram_goa_creator", title = "Goan Beach Birthday Bash", category = "Birthday", mediaUrl = "https://images.unsplash.com/photo-1530103862676-de8c9debad1d?auto=format&fit=crop&w=500&q=80", mediaType = "IMAGE", thumbnail = ""),

            // Manisha - Pre-Wedding, Wedding
            Portfolio(creatorId = "manisha_mehta_creator", title = "Royal Palace Portrait", category = "Pre-Wedding", mediaUrl = "https://images.unsplash.com/photo-1607190074257-dd4b7af0309f?auto=format&fit=crop&w=500&q=80", mediaType = "IMAGE", thumbnail = ""),
            Portfolio(creatorId = "manisha_mehta_creator", title = "Bride Portrait Palace Window", category = "Wedding", mediaUrl = "https://images.unsplash.com/photo-1621184455862-c163dfb30e0f?auto=format&fit=crop&w=500&q=80", mediaType = "IMAGE", thumbnail = "")
        )

        portfolios.forEach { portfolioDao.insertPortfolio(it) }

        // Seed some starter reviews
        val reviews = listOf(
            Review(bookingId = 1, customerId = "cust1", creatorId = "amit_sharma_creator", rating = 5.0, review = "Absolutely breathtaking shots of our wedding! Amit captures emotions like nobody else. Extremely punctual and highly recommended if you value candid filmmaking.", customerName = "Sunita K."),
            Review(bookingId = 2, customerId = "cust2", creatorId = "amit_sharma_creator", rating = 4.8, review = "Fabulous drone shots of our outdoor reception. The highlight video has been played on repeat in our family!", customerName = "Rajeev M."),
            Review(bookingId = 3, customerId = "cust3", creatorId = "riya_sen_creator", rating = 5.0, review = "We booked Riya for our maternity shoot at the studio. Her artistic eye, comforting presence, and custom lighting made us feel like stars. Pictures are dreamlike.", customerName = "Srishti P."),
            Review(bookingId = 4, customerId = "cust4", creatorId = "riya_sen_creator", rating = 4.5, review = "Great eyes for portraits! Took our newborn shoots here. Very patient and handled our little one with care.", customerName = "Ahana D."),
            Review(bookingId = 5, customerId = "cust5", creatorId = "kabir_singh_creator", rating = 5.0, review = "Exceeded expectations. Very highly professional. They covered our tech corporate summit beautifully.", customerName = "Aravind K., VP Product"),
            Review(bookingId = 6, customerId = "cust6", creatorId = "manisha_mehta_creator", rating = 5.0, review = "Manisha provided an incredible pre-wedding shoot experience in Jaipur. Highly creative storytelling and majestic backdrops.", customerName = "Deepika J.")
        )

        reviews.forEach { reviewDao.insertReview(it) }

        // Seed some initial messages
        val messages = listOf(
            Message(senderId = "riya_sen_creator", receiverId = "current_customer_test", message = "Hi! Thanks for checking my page. I am available for maternity portraits next month."),
            Message(senderId = "current_customer_test", receiverId = "riya_sen_creator", message = "Hi Riya, that is great! I will book a Standard Package details."),
            Message(senderId = "amit_sharma_creator", receiverId = "current_customer_test", message = "Greetings! I've reviewed your request for the wedding shoot in Pune. Let's schedule a brief call so we can discuss the lighting arrangements, schedule of entries, and songs you like."),
            Message(senderId = "current_customer_test", receiverId = "current_creator_test", message = "Hi Fokal Creator, when can we schedule the pre-shoot planning session? Let me know your available slots.")
        )

        messages.forEach { messageDao.insertMessage(it) }

        // Seed some initial active global leads
        val leads = listOf(
            ClientLead(
                customerId = "cust_london_test",
                customerName = "Sophie Laurent",
                customerEmail = "sophie@fashionweek.uk",
                eventType = "Fashion Editorial Shoot",
                location = "London, United Kingdom",
                budget = 1450.0,
                description = "Looking for an editorial style commercial portrait photographer for London Fashion Week outdoor capsule coverage. Need 3 high-end retouched looks.",
                dateDetail = "Jul 15, 2026"
            ),
            ClientLead(
                customerId = "cust_nyc_test",
                customerName = "Marcus Thompson",
                customerEmail = "m.thompson@corporaterun.com",
                eventType = "Corporate Brand Launch",
                location = "New York, USA",
                budget = 1200.0,
                description = "Need raw, cinematic video interviews and sleek professional headshots for our fintech startup office opening. Looking for local or traveling creators.",
                dateDetail = "Sep 02, 2026"
            ),
            ClientLead(
                customerId = "cust_paris_test",
                customerName = "Lucas Dubois",
                customerEmail = "lucas.dubois@champs.fr",
                eventType = "Parisian Engagement Shoot",
                location = "Paris, France",
                budget = 950.0,
                description = "Candid sunrise engagement photoshoot around Trocadéro and Eiffel Tower. 2 hours. Love high-contrast, cinematic styles.",
                dateDetail = "Aug 20, 2026"
            ),
            ClientLead(
                customerId = "cust_tokyo_test",
                customerName = "Rena Sato",
                customerEmail = "sato.rena@culture.jp",
                eventType = "Streetwear Lookbook",
                location = "Tokyo, Japan",
                budget = 85000.0,
                description = "Looking for a street videographer to shoot 30s social media reels featuring our new autumn streetwear drop around Shibuya and Harajuku.",
                dateDetail = "Oct 10, 2026"
            )
        )
        leads.forEach { clientLeadDao.insertLead(it) }
    }
}
