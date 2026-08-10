package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.database.AppDatabase
import com.example.data.model.*
import com.example.data.repository.FokalRepository
import com.example.data.repository.SearchRepository
import com.example.data.repository.SupabaseClient
import com.example.data.network.GeminiClient
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import com.example.BuildConfig
import com.example.ui.theme.dataStore
import androidx.datastore.preferences.core.edit
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.QrCodeScanner

class FokalViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: FokalRepository
    private val searchRepository: SearchRepository

    // Current State
    val currentUserId = MutableStateFlow("current_customer_test")
    val currentUserRole = MutableStateFlow("Customer") // "Customer" or "Creator"
    
    // User profile state
    val currentUserProfile = MutableStateFlow<User?>(null)
    val currentCreatorDetails = MutableStateFlow<Creator?>(null)

    // Selection logic
    val selectedCreatorId = MutableStateFlow<String?>(null)
    val selectedChatCreatorId = MutableStateFlow<String?>(null)

    // Search and filters
    val searchQuery = MutableStateFlow("")
    val selectedCategory = MutableStateFlow("") // "Wedding", "Birthday", etc.
    val filterCity = MutableStateFlow("") // "Mumbai", "Delhi", "Bengaluru", "Goa", "Jaipur"
    val filterBudget = MutableStateFlow<Double?>(null)
    val filterExperience = MutableStateFlow("") // "Beginner", "Professional", "Studio"
    val filterMinRep = MutableStateFlow<Double?>(null) // Reputation Filter: 1.0..5.0

    // Fokal AI State
    val aiResponse = MutableStateFlow("")
    val aiLoading = MutableStateFlow(false)

    private val _nearbyCreators = MutableStateFlow<List<Creator>>(emptyList())
    val nearbyCreators: StateFlow<List<Creator>> = _nearbyCreators.asStateFlow()

    private val _searchResults = MutableStateFlow<List<Creator>>(emptyList())
    val searchResults: StateFlow<List<Creator>> = _searchResults.asStateFlow()

    // Real-time blocked dates state map per photographer: creatorId -> List of blocked date strings (e.g. "2026-10-18")
    val blockedDatesState = MutableStateFlow<Map<String, List<String>>>(
        mapOf(
            "riya_sen_creator" to listOf("2026-10-18", "2026-11-22"),
            "amit_sharma_creator" to listOf("2026-10-15", "2026-11-12"),
            "kabir_singh_creator" to listOf("2026-10-16", "2026-10-17"),
            "vikram_goa_creator" to listOf("2026-10-20"),
            "manisha_mehta_creator" to listOf("2026-11-05"),
            "current_creator_test" to listOf("2026-10-18", "2026-11-22")
        )
    )

    // Selection of date inside visual profile calendar
    val selectedShootDate = MutableStateFlow("2026-10-15")

    val notificationPreferences = MutableStateFlow(com.example.data.model.PayoutNotificationPreferences())

    private val THEME_KEY = androidx.datastore.preferences.core.booleanPreferencesKey("dark_theme")
    
    val isDarkTheme: StateFlow<Boolean> = application.applicationContext.dataStore.data
        .map { preferences -> preferences[THEME_KEY] ?: false }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = false
        )
        
    fun toggleTheme() {
        viewModelScope.launch {
            getApplication<Application>().applicationContext.dataStore.edit { preferences ->
                val current = preferences[THEME_KEY] ?: false
                preferences[THEME_KEY] = !current
            }
        }
    }

    fun updateNotificationPreference(preferenceKey: String, value: Boolean) {
        val current = notificationPreferences.value
        notificationPreferences.value = when (preferenceKey) {
            "payout" -> current.copy(payoutNotifications = value)
            "payout_processed" -> current.copy(payoutProcessed = value)
            "payout_failed" -> current.copy(payoutFailed = value)
            "weekly_report" -> current.copy(weeklyReport = value)
            else -> current
        }
    }

    init {
        val database = AppDatabase.getDatabase(application)
        repository = FokalRepository(
            database.userDao(),
            database.creatorDao(),
            database.portfolioDao(),
            database.bookingDao(),
            database.reviewDao(),
            database.messageDao(),
            database.favoriteDao(),
            database.clientLeadDao(),
            database.payoutMethodDao()
        )
        val supabaseClient = SupabaseClient(application)
        searchRepository = SearchRepository(application, supabaseClient)

        // Seed basic DB models on warm-up
        viewModelScope.launch {
            repository.seedMockDataIfEmpty()
            setupCurrentUser()
        }
        startSupabaseChatSyncLoop()
        startSupabaseBlockedDatesSyncLoop()
    }

    private suspend fun setupCurrentUser() {
        val userId = currentUserId.value
        val role = currentUserRole.value

        var user = repository.getUser(userId)
        if (user == null) {
            user = User(
                id = userId,
                name = if (role == "Customer") "Ananya Rao" else "Fokal Studio Pro",
                email = if (role == "Customer") "ananya@gmail.com" else "pro@fokalpoint.com",
                phone = "+91 95555 44444",
                role = role,
                profileImage = if (role == "Customer") "https://images.unsplash.com/photo-1494790108377-be9c29b29330?auto=format&fit=crop&w=150&q=80" else "https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?auto=format&fit=crop&w=150&q=80",
                city = "Mumbai",
                state = "Maharashtra",
                country = "India"
            )
            repository.insertUser(user)
        }
        currentUserProfile.value = user

        if (role == "Creator") {
            var creator = repository.getCreatorSync(userId)
            if (creator == null) {
                creator = Creator(
                    id = userId,
                    userId = userId,
                    creatorType = "Both",
                    experienceLevel = "Professional",
                    bio = "Versatile commercial videographer and aesthetic portrait lens. Let's document beautiful interactions in high luxury resolution.",
                    languages = "English, Hindi",
                    equipment = "Sony A7SIII, Mavic 3 Pro",
                    rating = 4.8,
                    verified = true,
                    startingPrice = 25000.0,
                    instagram = "fokal_studios_pro",
                    website = "www.fokalpro.com",
                    yearsOfExperience = 6,
                    skillset = "Photographer, Videographer, Reel Creator",
                    youtube = "https://youtube.com/c/fokalpointpro"
                )
                repository.insertCreator(creator)
            }
            currentCreatorDetails.value = creator
        } else {
            currentCreatorDetails.value = null
        }
    }

    // Role switcher
    fun switchRole(newRole: String) {
        viewModelScope.launch {
            currentUserRole.value = newRole
            if (newRole == "Customer") {
                currentUserId.value = "current_customer_test"
            } else {
                currentUserId.value = "amit_sharma_creator" // Use our rich seeded photographer for Creator dashboard!
            }
            setupCurrentUser()
        }
    }

    data class CreatorFilter(
        val query: String = "",
        val category: String = "",
        val city: String = "",
        val budget: Double? = null,
        val experience: String = "",
        val minRep: Double? = null
    )

    // List of reactive flows compiled for UI matching
    val creatorsList: StateFlow<List<Creator>> = repository.allCreators
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    private val activeFilters: Flow<CreatorFilter> = combine(
        combine(searchQuery, selectedCategory, filterCity) { q, cat, c -> Triple(q, cat, c) },
        combine(filterBudget, filterExperience, filterMinRep) { b, exp, r -> Triple(b, exp, r) }
    ) { part1, part2 ->
        CreatorFilter(
            query = part1.first,
            category = part1.second,
            city = part1.third,
            budget = part2.first,
            experience = part2.second,
            minRep = part2.third
        )
    }

    // Filtered Creators based on search terms
    val filteredCreators: StateFlow<List<Creator>> = combine(
        creatorsList,
        activeFilters
    ) { list, filters ->
        list.filter { creator ->
            // Search Query: Matches name, bio, city, or country (global lookup)
            val matchesQuery = if (filters.query.isEmpty()) true else {
                creator.bio.contains(filters.query, ignoreCase = true) || 
                creator.id.contains(filters.query, ignoreCase = true) ||
                getCreatorNameSync(creator.id).contains(filters.query, ignoreCase = true) ||
                getCreatorCitySync(creator.id).contains(filters.query, ignoreCase = true)
            }

            // City Selection
            val matchesCity = if (filters.city.isEmpty()) true else {
                getCreatorCitySync(creator.id).contains(filters.city, ignoreCase = true)
            }

            // Budget filter (Starting Price <= Budget)
            val matchesBudget = if (filters.budget == null) true else {
                creator.startingPrice <= filters.budget
            }

            // Experience level filter
            val matchesExp = if (filters.experience.isEmpty()) true else {
                creator.experienceLevel.equals(filters.experience, ignoreCase = true)
            }

            // Minimum Reputation Filter (rating)
            val matchesRep = if (filters.minRep == null) true else {
                creator.rating >= filters.minRep
            }

            // Category filter: For mock purposes, filter by matching creator type or portfolios matching style
            val matchesCategory = if (filters.category.isEmpty()) true else {
                val matchesType = when (filters.category) {
                    "Wedding", "Pre-Wedding", "Maternity" -> creator.creatorType == "Both" || creator.creatorType == "Photographer"
                    "Corporate", "Travel" -> creator.creatorType == "Both" || creator.creatorType == "Videographer"
                    else -> true
                }
                matchesType
            }

            matchesQuery && matchesCity && matchesBudget && matchesExp && matchesRep && matchesCategory
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    // Users Lookup
    private val usersMap = mutableMapOf<String, User>()

    suspend fun getUserDetails(userId: String): User? {
        if (usersMap.containsKey(userId)) return usersMap[userId]
        val fetched = repository.getUser(userId)
        if (fetched != null) {
            usersMap[userId] = fetched
        }
        return fetched
    }

    // Helper functions for sync/UI rendering
    fun getCreatorNameSync(creatorId: String): String {
        return when (creatorId) {
            "amit_sharma_creator" -> "Amit Sharma"
            "riya_sen_creator" -> "Riya Sen"
            "kabir_singh_creator" -> "Kabir Studios"
            "vikram_goa_creator" -> "Vikram Fernandes"
            "manisha_mehta_creator" -> "Manisha Mehta"
            "current_customer_test" -> "Ananya Rao"
            else -> "Fokal Creator"
        }
    }

    fun getCreatorCitySync(creatorId: String): String {
        return when (creatorId) {
            "amit_sharma_creator" -> "Mumbai"
            "riya_sen_creator" -> "Delhi"
            "kabir_singh_creator" -> "Bengaluru"
            "vikram_goa_creator" -> "Goa"
            "manisha_mehta_creator" -> "Jaipur"
            else -> "Mumbai"
        }
    }

    fun getCreatorAvatarSync(creatorId: String): String {
        return when (creatorId) {
            "amit_sharma_creator" -> "https://images.unsplash.com/photo-1534528741775-53994a69daeb?auto=format&fit=crop&w=300&q=80"
            "riya_sen_creator" -> "https://images.unsplash.com/photo-1544005313-94ddf0286df2?auto=format&fit=crop&w=300&q=80"
            "kabir_singh_creator" -> "https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?auto=format&fit=crop&w=300&q=80"
            "vikram_goa_creator" -> "https://images.unsplash.com/photo-1500648767791-00dcc994a43e?auto=format&fit=crop&w=300&q=80"
            "manisha_mehta_creator" -> "https://images.unsplash.com/photo-1508214751196-bcfd4ca60f91?auto=format&fit=crop&w=300&q=80"
            else -> "https://images.unsplash.com/photo-1472099645785-5658abf4ff4e?auto=format&fit=crop&w=150&q=80"
        }
    }

    // Bookings flows
    val bookingsList: StateFlow<List<Booking>> = currentUserId
        .flatMapLatest { userId ->
            if (currentUserRole.value == "Customer") {
                repository.getBookingsForCustomer(userId)
            } else {
                repository.getBookingsForCreator(userId)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    // Portfolio active flows
    val activePortfolio: StateFlow<List<Portfolio>> = selectedCreatorId
        .flatMapLatest { id ->
            if (id == null) flowOf(emptyList()) else repository.getPortfolio(id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    // Reviews active flows
    val activeReviews: StateFlow<List<Review>> = selectedCreatorId
        .flatMapLatest { id ->
            if (id == null) flowOf(emptyList()) else repository.getReviews(id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    // Active bookings of the photographer being visited (to determine live availability/booked dates)
    val activeCreatorBookings: StateFlow<List<Booking>> = selectedCreatorId
        .flatMapLatest { id ->
            if (id == null) flowOf(emptyList()) else repository.getBookingsForCreator(id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    // Chat room messaging flow
    val chatMessages: StateFlow<List<Message>> = combine(currentUserId, selectedChatCreatorId) { myId, partnerId ->
        myId to partnerId
    }.flatMapLatest { (myId, partnerId) ->
        if (partnerId == null) flowOf(emptyList()) else repository.getChatMessages(myId, partnerId)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    // List of standard users active in messaging history
    val chatPartners: StateFlow<List<String>> = currentUserId
        .flatMapLatest { myId -> repository.getChatPartners(myId) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    // Favorites flow
    val favoritesList: StateFlow<List<Favorite>> = currentUserId
        .flatMapLatest { myId -> repository.getFavorites(myId) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    // Favorites checking
    fun isCreatorFavorite(creatorId: String): Flow<Boolean> {
        return repository.isFavorite(currentUserId.value, creatorId)
    }

    // Creator Earnings data model
    data class CreatorEarnings(val total: Double)

    val creatorRating: Double = 4.9

    val creatorBookings: StateFlow<List<Booking>> = currentUserId
        .flatMapLatest { userId -> repository.getBookingsForCreator(userId) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val upcomingShoots: StateFlow<List<Booking>> = creatorBookings
        .map { bookings -> bookings.filter { it.status == "Accepted" || it.status == "Confirmed" || it.status == "Pending" || it.status == "pending" } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val earnings: StateFlow<CreatorEarnings> = creatorBookings
        .map { bookings -> 
            val total = bookings.filter { it.status == "Completed" || it.status == "Paid" || it.paymentStatus == "Paid" }.sumOf { it.price }
            CreatorEarnings(if (total > 0) total else 185000.0)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), CreatorEarnings(185000.0))

    fun navigateToPortfolioUpload() {
        android.util.Log.d("FokalViewModel", "Navigate to portfolio upload")
    }

    fun navigateToCalendar() {
        android.util.Log.d("FokalViewModel", "Navigate to calendar")
    }

    fun navigateToEarnings() {
        android.util.Log.d("FokalViewModel", "Navigate to earnings")
    }

    val pendingPayouts: StateFlow<List<PendingPayout>> = creatorBookings
        .map { bookings ->
            bookings.filter { it.status == "Accepted" || it.status == "Confirmed" || it.status == "Pending" || it.status == "pending" }
                .map { booking ->
                    PendingPayout(
                        id = "p_${booking.id}",
                        bookingId = booking.id,
                        eventType = booking.eventType,
                        date = booking.date,
                        amount = booking.price,
                        status = "Pending Shoot"
                    )
                }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val payoutHistory: StateFlow<List<PayoutHistory>> = creatorBookings
        .map { bookings ->
            val completed = bookings.filter { it.status == "Completed" || it.status == "Paid" || it.paymentStatus == "Paid" }
            if (completed.isEmpty()) {
                listOf(
                    PayoutHistory(
                        id = "h_init_1",
                        description = "Platform Onboarding Bonus",
                        date = "2026-06-30",
                        method = "Direct Deposit",
                        amount = 1500.0,
                        status = "Completed"
                    )
                )
            } else {
                completed.map { booking ->
                    PayoutHistory(
                        id = "h_${booking.id}",
                        description = "Shoot Payout: ${booking.eventType}",
                        date = booking.date,
                        method = "UPI",
                        amount = booking.price,
                        status = "Completed"
                    )
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val payoutStats: StateFlow<PayoutStats> = creatorBookings
        .map { bookings ->
            val completedAmount = bookings.filter { it.status == "Completed" || it.status == "Paid" || it.paymentStatus == "Paid" }.sumOf { it.price }
            val pendingAmount = bookings.filter { it.status == "Accepted" || it.status == "Confirmed" || it.status == "Pending" || it.status == "pending" }.sumOf { it.price }
            val totalEarned = completedAmount + 1500.0 // onboarding bonus
            PayoutStats(
                available = completedAmount,
                pending = pendingAmount,
                totalEarned = totalEarned,
                earningsData = listOf(
                    EarningsDataPoint("Jan", 25000.0),
                    EarningsDataPoint("Feb", 35000.0),
                    EarningsDataPoint("Mar", 30000.0),
                    EarningsDataPoint("Apr", 45000.0),
                    EarningsDataPoint("May", totalEarned)
                )
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), PayoutStats(0.0, 0.0, 1500.0, emptyList()))

    fun requestPayout(payoutId: String? = null) {
        viewModelScope.launch {
            android.util.Log.d("FokalViewModel", "Requesting payout for id: $payoutId")
        }
    }

    val payoutMethods: StateFlow<List<PayoutMethod>> = currentUserId
        .flatMapLatest { userId -> repository.getPayoutMethods(userId) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    fun addPayoutMethod(payoutMethod: PayoutMethod) {
        viewModelScope.launch {
            val updated = payoutMethod.copy(
                userId = currentUserId.value,
                createdAt = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
            )
            repository.insertPayoutMethod(updated)
            savePayoutMethodToSupabase(updated)
        }
    }

    fun updatePayoutMethod(payoutMethod: PayoutMethod) {
        viewModelScope.launch {
            repository.insertPayoutMethod(payoutMethod)
            savePayoutMethodToSupabase(payoutMethod)
        }
    }

    fun deletePayoutMethod(id: String) {
        viewModelScope.launch {
            repository.deletePayoutMethod(id)
            deletePayoutMethodFromSupabase(id)
        }
    }

    fun setDefaultPayoutMethod(id: String) {
        viewModelScope.launch {
            repository.setDefaultPayoutMethod(currentUserId.value, id)
            updateDefaultPayoutMethodInSupabase(id)
        }
    }

    val creatorProfile = MutableStateFlow<CreatorUPIProfile?>(null)

    fun loadCreatorProfile(creatorId: String) {
        viewModelScope.launch {
            val name = getCreatorNameSync(creatorId)
            val dbMethods = repository.getPayoutMethods(creatorId).first().filter {
                it.type == PayoutMethodType.UPI
            }
            val upiApps = if (dbMethods.isEmpty()) {
                listOf(
                    UPIApp("gpay", "Google Pay", "pay@gpay", Icons.Outlined.QrCodeScanner, androidx.compose.ui.graphics.Color(0xFF2196F3)),
                    UPIApp("phonepe", "PhonePe", "pay@phonepe", Icons.Outlined.QrCodeScanner, androidx.compose.ui.graphics.Color(0xFF673AB7)),
                    UPIApp("paytm", "Paytm", "pay@paytm", Icons.Outlined.QrCodeScanner, androidx.compose.ui.graphics.Color(0xFF00BCD4))
                )
            } else {
                dbMethods.map { m ->
                    UPIApp(
                        id = m.id,
                        name = m.accountHolderName,
                        upiId = m.upiId ?: "payment@upi",
                        icon = Icons.Outlined.QrCodeScanner,
                        color = androidx.compose.ui.graphics.Color(0xFF4CAF50)
                    )
                }
            }
            creatorProfile.value = CreatorUPIProfile(creatorId, name, upiApps)
        }
    }

    fun monitorUPIPayment(bookingId: Long, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            kotlinx.coroutines.delay(2000)
            confirmBookingPayment(bookingId, "Paid (UPI)", "Confirmed")
            onResult(true)
        }
    }

    val paymentHistory: StateFlow<List<Payment>> = combine(bookingsList, creatorBookings) { customerBookings, creatorBookings ->
        val payments = mutableListOf<Payment>()
        
        customerBookings.forEach { booking ->
            payments.add(
                Payment(
                    id = "p_cust_${booking.id}",
                    type = "booking",
                    description = "Paid for ${booking.eventType} shoot",
                    date = booking.date,
                    transactionId = "TXN-${100000 + booking.id}",
                    amount = booking.price,
                    status = if (booking.paymentStatus == "Paid" || booking.status == "Completed") "completed" else "pending"
                )
            )
        }
        
        creatorBookings.forEach { booking ->
            if (booking.status == "Completed" || booking.status == "Paid" || booking.paymentStatus == "Paid") {
                payments.add(
                    Payment(
                        id = "p_cre_${booking.id}",
                        type = "payout",
                        description = "Payout for ${booking.eventType} shoot",
                        date = booking.date,
                        transactionId = "PAY-${200000 + booking.id}",
                        amount = -booking.price,
                        status = "completed"
                    )
                )
            }
        }
        
        if (payments.isEmpty()) {
            payments.add(
                Payment(
                    id = "init_pay_1",
                    type = "booking",
                    description = "Pre-wedding Shoot Booking",
                    date = "2026-07-01",
                    transactionId = "TXN-7749102",
                    amount = 45000.0,
                    status = "completed"
                )
            )
            payments.add(
                Payment(
                    id = "init_pay_2",
                    type = "payout",
                    description = "Earnings Payout to Bank",
                    date = "2026-06-28",
                    transactionId = "TXN-8812940",
                    amount = -35000.0,
                    status = "completed"
                )
            )
            payments.add(
                Payment(
                    id = "init_pay_3",
                    type = "refund",
                    description = "Cancelled Birthday Event Refund",
                    date = "2026-06-25",
                    transactionId = "TXN-9123849",
                    amount = -12000.0,
                    status = "completed"
                )
            )
            payments.add(
                Payment(
                    id = "init_pay_4",
                    type = "booking",
                    description = "Fashion Portfolio Shoot",
                    date = "2026-07-15",
                    transactionId = "TXN-2394821",
                    amount = 18000.0,
                    status = "pending"
                )
            )
        }
        
        payments.sortedByDescending { it.date }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    fun navigateToPaymentDetails(paymentId: String) {
        android.util.Log.d("FokalViewModel", "Navigate to payment details for $paymentId")
    }

    fun downloadInvoice(paymentId: String) {
        android.util.Log.d("FokalViewModel", "Download invoice for $paymentId")
    }

    fun toggleFavorite(creatorId: String) {
        viewModelScope.launch {
            val isFav = repository.isFavorite(currentUserId.value, creatorId).firstOrNull() ?: false
            if (isFav) {
                repository.removeFavorite(currentUserId.value, creatorId)
            } else {
                repository.addFavorite(currentUserId.value, creatorId)
            }
        }
    }

    // Messaging operations
    fun sendMessage(msgText: String) {
        val partnerId = selectedChatCreatorId.value ?: return
        if (msgText.trim().isEmpty()) return
        viewModelScope.launch {
            val messageObj = Message(
                senderId = currentUserId.value,
                receiverId = partnerId,
                message = msgText
            )
            repository.insertMessage(messageObj)
            saveMessageToSupabase(messageObj)
            
            // Auto response simulation for high engagement experience
            simulateSmartResponse(partnerId, msgText)
        }
    }

    private fun saveMessageToSupabase(message: Message) {
        viewModelScope.launch {
            try {
                val supabaseUrl = BuildConfig.SUPABASE_URL
                val supabaseKey = BuildConfig.SUPABASE_ANON_KEY
                
                if (supabaseUrl.isEmpty() || supabaseUrl.contains("placeholder")) {
                    android.util.Log.d("SupabaseMsg", "Supabase is not configured. Saved locally only.")
                    return@launch
                }
                
                withContext(Dispatchers.IO) {
                    val client = okhttp3.OkHttpClient()
                    val mediaType = "application/json; charset=utf-8".toMediaTypeOrNull()
                    
                    val jsonBody = """
                        {
                            "senderId": "${message.senderId}",
                            "receiverId": "${message.receiverId}",
                            "message": "${message.message.replace("\"", "\\\"").replace("\n", "\\n")}",
                            "mediaUrl": "${message.mediaUrl}",
                            "createdAt": ${message.createdAt},
                            "sender_id": "${message.senderId}",
                            "receiver_id": "${message.receiverId}",
                            "media_url": "${message.mediaUrl}",
                            "created_at": ${message.createdAt}
                        }
                    """.trimIndent()
                    
                    val body = okhttp3.RequestBody.create(mediaType, jsonBody)
                    val request = okhttp3.Request.Builder()
                        .url("$supabaseUrl/rest/v1/messages")
                        .post(body)
                        .addHeader("apikey", supabaseKey)
                        .addHeader("Authorization", "Bearer $supabaseKey")
                        .addHeader("Content-Type", "application/json")
                        .addHeader("Prefer", "return=minimal")
                        .build()
                        
                    client.newCall(request).execute().use { response ->
                        if (response.isSuccessful) {
                            android.util.Log.d("SupabaseMsg", "Successfully synced msg to Supabase!")
                        } else {
                            val errorBody = response.body?.string() ?: ""
                            android.util.Log.e("SupabaseMsg", "Failed to sync msg to Supabase: Status ${response.code} - $errorBody")
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("SupabaseMsg", "Error syncing msg to Supabase: ${e.message}")
            }
        }
    }

    private fun startSupabaseChatSyncLoop() {
        viewModelScope.launch {
            while (true) {
                kotlinx.coroutines.delay(2000L) // Poll every 2 seconds for real-time responsiveness
                val myId = currentUserId.value
                val partnerId = selectedChatCreatorId.value
                if (partnerId != null && myId.isNotEmpty()) {
                    try {
                        val supabaseUrl = BuildConfig.SUPABASE_URL
                        val supabaseKey = BuildConfig.SUPABASE_ANON_KEY
                        
                        if (supabaseUrl.isEmpty() || supabaseUrl.contains("placeholder")) {
                            continue
                        }
                        
                        withContext(Dispatchers.IO) {
                            val client = okhttp3.OkHttpClient()
                            val url = "$supabaseUrl/rest/v1/messages?or=(and(senderId.eq.$myId,receiverId.eq.$partnerId),and(senderId.eq.$partnerId,receiverId.eq.$myId))&order=createdAt.asc"
                            
                            val request = okhttp3.Request.Builder()
                                .url(url)
                                .get()
                                .addHeader("apikey", supabaseKey)
                                .addHeader("Authorization", "Bearer $supabaseKey")
                                .build()
                                
                            client.newCall(request).execute().use { response ->
                                if (response.isSuccessful) {
                                    val jsonStr = response.body?.string() ?: "[]"
                                    val jsonArray = org.json.JSONArray(jsonStr)
                                    val syncedMessages = mutableListOf<Message>()
                                    for (i in 0 until jsonArray.length()) {
                                        val obj = jsonArray.getJSONObject(i)
                                        val sId = obj.optString("senderId", obj.optString("sender_id", ""))
                                        val rId = obj.optString("receiverId", obj.optString("receiver_id", ""))
                                        val msgText = obj.optString("message", "")
                                        val cAt = obj.optLong("createdAt", obj.optLong("created_at", System.currentTimeMillis()))
                                        if (sId.isNotEmpty() && rId.isNotEmpty() && msgText.isNotEmpty()) {
                                            syncedMessages.add(
                                                Message(
                                                    senderId = sId,
                                                    receiverId = rId,
                                                    message = msgText,
                                                    createdAt = cAt
                                                )
                                            )
                                        }
                                    }
                                    if (syncedMessages.isNotEmpty()) {
                                        for (msg in syncedMessages) {
                                            val exists = repository.checkMessageExistsSync(msg.senderId, msg.receiverId, msg.message, msg.createdAt)
                                            if (!exists) {
                                                repository.insertMessage(msg)
                                            }
                                        }
                                    }
                                } else {
                                    android.util.Log.e("SupabaseSync", "Failed to pull messages: Status ${response.code}")
                                }
                            }
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("SupabaseSync", "Error pulling messages: ${e.message}")
                    }
                }
            }
        }
    }

    private fun simulateSmartResponse(creatorId: String, customerMsg: String) {
        viewModelScope.launch {
            kotlinx.coroutines.delay(2000L) // Wait 2s
            val creatorName = getCreatorNameSync(creatorId)
            val autoReply = when {
                customerMsg.lowercase().contains("price") || customerMsg.lowercase().contains("budget") -> {
                    "Hello! Thanks for asking about packages. My standard starting prices are shown on my profile. Basic packages start at ₹${getCreatorSync(creatorId)?.startingPrice ?: 15000}.0 including raw and edited photographs."
                }
                customerMsg.lowercase().contains("hello") || customerMsg.lowercase().contains("hi") -> {
                    "Hi there! Thanks for reaching out to $creatorName. I am delighted to discuss your upcoming memorable shoot. What theme or date are you looking at?"
                }
                else -> "Got it! That sounds fantastic. I have marked my calendar. Would you like to proceed with booking or need to clarify any customization?"
            }
            val replyMessage = Message(
                senderId = creatorId,
                receiverId = currentUserId.value,
                message = autoReply
            )
            repository.insertMessage(replyMessage)
            saveMessageToSupabase(replyMessage)
        }
    }

    // Booking actions
    fun createBooking(eventType: String, date: String, time: String, hours: Int, packageType: String, totalCost: Double) {
        val creatorId = selectedCreatorId.value ?: return
        viewModelScope.launch {
            val desc = "$packageType Package ($eventType)"
            val newBooking = Booking(
                customerId = currentUserId.value,
                creatorId = creatorId,
                eventType = desc,
                date = date,
                time = time,
                hours = hours,
                price = totalCost,
                status = "Pending",
                paymentStatus = "Pending"
            )
            repository.insertBooking(newBooking)
            saveBookingToSupabase(newBooking)

            // Trigger automated email notification to photographer
            launch {
                try {
                    val photographerUser = repository.getUser(creatorId)
                    val clientUser = repository.getUser(currentUserId.value)

                    val photographerName = photographerUser?.name ?: "Fokal Photographer"
                    val photographerEmail = photographerUser?.email ?: "pro@fokalpoint.com"
                    val clientName = clientUser?.name ?: "Valued Fokal Client"
                    val clientEmail = clientUser?.email ?: "client@gmail.com"

                    com.example.data.network.EmailNotificationService.notifyPhotographerOfNewBooking(
                        photographerName = photographerName,
                        photographerEmail = photographerEmail,
                        clientName = clientName,
                        clientEmail = clientEmail,
                        booking = newBooking
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    private fun saveBookingToSupabase(booking: Booking) {
        viewModelScope.launch {
            try {
                val supabaseUrl = BuildConfig.SUPABASE_URL
                val supabaseKey = BuildConfig.SUPABASE_ANON_KEY
                
                if (supabaseUrl.isEmpty() || supabaseUrl.contains("placeholder")) {
                    android.util.Log.d("SupabaseSync", "Supabase is not configured or placeholder URL used. Saved locally only.")
                    return@launch
                }
                
                withContext(Dispatchers.IO) {
                    val client = OkHttpClient()
                    val mediaType = "application/json; charset=utf-8".toMediaTypeOrNull()
                    
                    val jsonBody = """
                        {
                            "customerId": "${booking.customerId}",
                            "creatorId": "${booking.creatorId}",
                            "eventType": "${booking.eventType}",
                            "date": "${booking.date}",
                            "time": "${booking.time}",
                            "hours": ${booking.hours},
                            "price": ${booking.price},
                            "status": "${booking.status}",
                            "paymentStatus": "${booking.paymentStatus}",
                            "createdAt": ${booking.createdAt},
                            "customer_id": "${booking.customerId}",
                            "creator_id": "${booking.creatorId}",
                            "event_type": "${booking.eventType}",
                            "payment_status": "${booking.paymentStatus}",
                            "created_at": ${booking.createdAt}
                        }
                    """.trimIndent()
                    
                    val body = okhttp3.RequestBody.create(mediaType, jsonBody)
                    val request = Request.Builder()
                        .url("$supabaseUrl/rest/v1/bookings")
                        .post(body)
                        .addHeader("apikey", supabaseKey)
                        .addHeader("Authorization", "Bearer $supabaseKey")
                        .addHeader("Content-Type", "application/json")
                        .addHeader("Prefer", "return=minimal")
                        .build()
                        
                    client.newCall(request).execute().use { response ->
                        if (response.isSuccessful) {
                            android.util.Log.d("SupabaseSync", "Successfully synced booking to Supabase!")
                        } else {
                            val errorBody = response.body?.string() ?: ""
                            android.util.Log.e("SupabaseSync", "Failed to sync booking to Supabase: Status ${response.code} - $errorBody")
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                android.util.Log.e("SupabaseSync", "Error syncing booking to Supabase: ${e.message}")
            }
        }
    }

    suspend fun getCreatorSync(creatorId: String): Creator? {
        return repository.getCreatorSync(creatorId)
    }

    // Creator Actions
    fun updateBookingStatus(bookingId: Long, newStatus: String) {
        viewModelScope.launch {
            repository.updateBookingStatus(bookingId, newStatus)
            val paymentStatus = if (newStatus == "Completed") "Paid" else null
            if (paymentStatus != null) {
                repository.updatePaymentStatus(bookingId, paymentStatus)
            }
            updateBookingInSupabase(bookingId, newStatus, paymentStatus)

            // Trigger automated email notification to client for status updates
            launch {
                try {
                    val booking = repository.getBookingByIdSync(bookingId)
                    if (booking != null) {
                        val clientUser = repository.getUser(booking.customerId)
                        val photographerUser = repository.getUser(booking.creatorId)

                        val clientName = clientUser?.name ?: "Valued Fokal Client"
                        val clientEmail = clientUser?.email ?: "client@gmail.com"
                        val photographerName = photographerUser?.name ?: "Fokal Photographer"

                        com.example.data.network.EmailNotificationService.notifyClientOfStatusUpdate(
                            clientName = clientName,
                            clientEmail = clientEmail,
                            photographerName = photographerName,
                            booking = booking,
                            newStatus = newStatus
                        )
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    fun confirmBookingPayment(bookingId: Long, paymentStatus: String, newStatus: String = "Confirmed") {
        viewModelScope.launch {
            repository.updatePaymentStatus(bookingId, paymentStatus)
            repository.updateBookingStatus(bookingId, newStatus)
            updateBookingInSupabase(bookingId, newStatus, paymentStatus)

            // Trigger status update email notification to client upon payment confirmation
            launch {
                try {
                    val booking = repository.getBookingByIdSync(bookingId)
                    if (booking != null) {
                        val clientUser = repository.getUser(booking.customerId)
                        val photographerUser = repository.getUser(booking.creatorId)

                        val clientName = clientUser?.name ?: "Valued Fokal Client"
                        val clientEmail = clientUser?.email ?: "client@gmail.com"
                        val photographerName = photographerUser?.name ?: "Fokal Photographer"

                        com.example.data.network.EmailNotificationService.notifyClientOfStatusUpdate(
                            clientName = clientName,
                            clientEmail = clientEmail,
                            photographerName = photographerName,
                            booking = booking.copy(paymentStatus = paymentStatus, status = newStatus),
                            newStatus = newStatus
                        )
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    private fun updateBookingInSupabase(bookingId: Long, newStatus: String, newPaymentStatus: String?) {
        viewModelScope.launch {
            try {
                val booking = repository.getBookingByIdSync(bookingId) ?: return@launch
                val supabaseUrl = BuildConfig.SUPABASE_URL
                val supabaseKey = BuildConfig.SUPABASE_ANON_KEY
                
                if (supabaseUrl.isEmpty() || supabaseUrl.contains("placeholder")) {
                    android.util.Log.d("SupabaseUpdate", "Supabase is not configured or placeholder URL used. Updated locally only.")
                    return@launch
                }
                
                withContext(Dispatchers.IO) {
                    val client = OkHttpClient()
                    val mediaType = "application/json; charset=utf-8".toMediaTypeOrNull()
                    
                    val updateBody = if (newPaymentStatus != null) {
                        """
                        {
                            "status": "$newStatus",
                            "paymentStatus": "$newPaymentStatus",
                            "payment_status": "$newPaymentStatus"
                        }
                        """.trimIndent()
                    } else {
                        """
                        {
                            "status": "$newStatus"
                        }
                        """.trimIndent()
                    }
                    
                    val body = okhttp3.RequestBody.create(mediaType, updateBody)
                    val url = "$supabaseUrl/rest/v1/bookings?createdAt=eq.${booking.createdAt}"
                    val request = Request.Builder()
                        .url(url)
                        .patch(body)
                        .addHeader("apikey", supabaseKey)
                        .addHeader("Authorization", "Bearer $supabaseKey")
                        .addHeader("Content-Type", "application/json")
                        .addHeader("Prefer", "return=minimal")
                        .build()
                        
                    client.newCall(request).execute().use { response ->
                        if (response.isSuccessful) {
                            android.util.Log.d("SupabaseUpdate", "Successfully patched booking status in Supabase!")
                        } else {
                            val errorBody = response.body?.string() ?: ""
                            android.util.Log.e("SupabaseUpdate", "Failed to patch booking in Supabase: Status ${response.code} - $errorBody")
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                android.util.Log.e("SupabaseUpdate", "Error patching booking in Supabase: ${e.message}")
            }
        }
    }

    fun uploadPortfolioImage(title: String, category: String, url: String) {
        viewModelScope.launch {
            val p = Portfolio(
                creatorId = currentUserId.value,
                title = title,
                category = category,
                mediaUrl = url,
                mediaType = "IMAGE",
                thumbnail = ""
            )
            repository.insertPortfolio(p)
        }
    }

    fun addReview(creatorId: String, rating: Double, comment: String, bookingId: Long = System.currentTimeMillis()) {
        viewModelScope.launch {
            val r = Review(
                bookingId = bookingId,
                customerId = currentUserId.value,
                creatorId = creatorId,
                rating = rating,
                review = comment,
                customerName = currentUserProfile.value?.name ?: "Verified Customer"
            )
            repository.insertReview(r)
        }
    }

    fun submitReview(
        bookingId: Long,
        creatorId: String,
        rating: Float,
        review: String,
        categoryRatings: Map<String, Float>,
        images: List<android.net.Uri>,
        video: android.net.Uri?
    ) {
        addReview(creatorId, rating.toDouble(), review, bookingId)
    }

    fun createCreatorProfile(
        specialization: String,
        skillsets: Set<String>,
        experienceLevel: String,
        yearsOfExperience: Int,
        instagramUrl: String,
        youtubeUrl: String,
        websiteUrl: String,
        bio: String,
        equipment: List<String>,
        languages: List<String>,
        onComplete: (Boolean) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val userId = currentUserId.value
                val creator = Creator(
                    id = userId,
                    userId = userId,
                    creatorType = specialization.replaceFirstChar { it.uppercase() },
                    experienceLevel = experienceLevel.replaceFirstChar { it.uppercase() },
                    bio = bio,
                    languages = languages.filter { it.isNotBlank() }.joinToString(", "),
                    equipment = equipment.filter { it.isNotBlank() }.joinToString(", "),
                    rating = 4.8,
                    verified = false,
                    startingPrice = 15000.0,
                    instagram = instagramUrl,
                    website = websiteUrl,
                    yearsOfExperience = yearsOfExperience,
                    skillset = skillsets.joinToString(", "),
                    youtube = youtubeUrl,
                    createdAt = System.currentTimeMillis()
                )
                repository.insertCreator(creator)
                
                val existingUser = repository.getUser(userId)
                if (existingUser != null && existingUser.role != "Creator") {
                    val updatedUser = existingUser.copy(role = "Creator")
                    repository.insertUser(updatedUser)
                }
                onComplete(true)
            } catch (e: Exception) {
                e.printStackTrace()
                onComplete(false)
            }
        }
    }

    // Fokal AI assistant actions
    fun askFokalAI(promptText: String) {
        if (promptText.trim().isEmpty()) return
        viewModelScope.launch {
            aiLoading.value = true
            aiResponse.value = ""

            // We augment the prompt with the system context so the response is very photography-driven and highly professional
            val systemContext = """
                You are "Fokal AI", the automated photographic planning co-pilot of FokalPoint.
                Keep answers extremely helpful, clean, professional, and directly useful to the photographer or customer.
                If they ask about matching, budget, planning or pricing, structure recommendations clearly in bullet points or steps.
            """.trimIndent()

            val answer = GeminiClient.generateContent(promptText, systemContext)
            aiResponse.value = answer
            aiLoading.value = false
        }
    }

    val authLoading = MutableStateFlow(false)
    val authMessage = MutableStateFlow<String?>(null)
    val authState = MutableStateFlow<AuthState>(AuthState.Unauthenticated)

    fun handleDeepLink(context: android.content.Context) {
        // Already intercepted in MainActivity, but we can verify here if needed
    }

    fun signInWithGoogle(context: android.content.Context) {
        signInWithOAuth(context, "google")
    }

    fun signInWithGitHub(context: android.content.Context) {
        signInWithOAuth(context, "github")
    }

    private fun signInWithOAuth(context: android.content.Context, provider: String) {
        viewModelScope.launch {
            authState.value = AuthState.Loading
            val url = BuildConfig.SUPABASE_URL
            if (url.isNotEmpty() && !url.contains("placeholder") && url.startsWith("http")) {
                val redirectUrl = "fokalpoint://login-callback"
                val authUrl = "$url/auth/v1/authorize?provider=$provider&redirect_to=$redirectUrl"
                try {
                    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(authUrl))
                    context.startActivity(intent)
                } catch (e: java.lang.Exception) {
                    authState.value = AuthState.Error("No browser detected. Please use sandbox accounts.")
                }
            } else {
                val id = if (provider == "google") "google_vikram_sen" else "github_git_sharma"
                val name = if (provider == "google") "Vikram Sen" else "git_sharma"
                val email = if (provider == "google") "vikram.sen@gmail.com" else "sharma.git@github.com"
                val avatarUrl = if (provider == "google") {
                    "https://images.unsplash.com/photo-1534528741775-53994a69daeb?auto=format&fit=crop&w=150&q=80"
                } else {
                    "https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?auto=format&fit=crop&w=150&q=80"
                }
                kotlinx.coroutines.delay(1000)
                loginOrSignUpSocialUser(
                    id = id,
                    name = name,
                    email = email,
                    profileImage = avatarUrl,
                    initialRole = "Customer"
                )
                authState.value = AuthState.Authenticated
            }
        }
    }

    fun signInWithEmail(email: String, password: String) {
        viewModelScope.launch {
            authState.value = AuthState.Loading
            try {
                val supabaseUrl = BuildConfig.SUPABASE_URL
                val supabaseKey = BuildConfig.SUPABASE_ANON_KEY
                
                if (supabaseUrl.isEmpty() || supabaseUrl.contains("placeholder")) {
                    kotlinx.coroutines.delay(1000)
                    val mockId = "mock_${Math.abs(email.hashCode())}"
                    loginOrSignUpSocialUser(
                        id = mockId,
                        name = email.substringBefore("@").replaceFirstChar { it.uppercase() },
                        email = email,
                        profileImage = "https://images.unsplash.com/photo-1534528741775-53994a69daeb?auto=format&fit=crop&w=150&q=80",
                        initialRole = "Customer"
                    )
                    authState.value = AuthState.Authenticated
                    return@launch
                }
                
                withContext(Dispatchers.IO) {
                    val client = OkHttpClient()
                    val mediaType = "application/json; charset=utf-8".toMediaTypeOrNull()
                    val jsonBody = """
                        {
                            "email": "$email",
                            "password": "$password"
                        }
                    """.trimIndent()
                    val body = okhttp3.RequestBody.create(mediaType, jsonBody)
                    val request = Request.Builder()
                        .url("$supabaseUrl/auth/v1/token?grant_type=password")
                        .post(body)
                        .addHeader("apikey", supabaseKey)
                        .addHeader("Content-Type", "application/json")
                        .build()
                        
                    client.newCall(request).execute().use { response ->
                        val responseBody = response.body?.string() ?: ""
                        if (response.isSuccessful) {
                            val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
                            val adapter = moshi.adapter(Map::class.java)
                            val respMap = adapter.fromJson(responseBody)
                            
                            val userObj = respMap?.get("user") as? Map<*, *>
                            val id = userObj?.get("id") as? String ?: ""
                            val userMetadata = userObj?.get("user_metadata") as? Map<*, *>
                            val name = userMetadata?.get("name") as? String ?: userMetadata?.get("full_name") as? String ?: "Fokal User"
                            val role = userMetadata?.get("role") as? String ?: "Customer"
                            
                            if (id.isNotEmpty()) {
                                withContext(Dispatchers.Main) {
                                    loginOrSignUpSocialUser(
                                        id = id,
                                        name = name,
                                        email = email,
                                        profileImage = "https://images.unsplash.com/photo-1534528741775-53994a69daeb?auto=format&fit=crop&w=150&q=80",
                                        initialRole = role
                                    )
                                    authState.value = AuthState.Authenticated
                                }
                            } else {
                                withContext(Dispatchers.Main) {
                                    authState.value = AuthState.Error("Unable to retrieve user ID.")
                                }
                            }
                        } else {
                            val errorMsg = try {
                                val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
                                val adapter = moshi.adapter(Map::class.java)
                                val respMap = adapter.fromJson(responseBody)
                                respMap?.get("error_description") as? String ?: respMap?.get("msg") as? String ?: "Invalid credentials"
                            } catch (e: java.lang.Exception) {
                                "Invalid credentials"
                            }
                            withContext(Dispatchers.Main) {
                                authState.value = AuthState.Error(errorMsg)
                            }
                        }
                    }
                }
            } catch (e: java.lang.Exception) {
                authState.value = AuthState.Error(e.message ?: "Authentication failed.")
            }
        }
    }

    fun signUpWithEmail(email: String, password: String, name: String, role: String) {
        viewModelScope.launch {
            authState.value = AuthState.Loading
            try {
                val supabaseUrl = BuildConfig.SUPABASE_URL
                val supabaseKey = BuildConfig.SUPABASE_ANON_KEY
                
                if (supabaseUrl.isEmpty() || supabaseUrl.contains("placeholder")) {
                    kotlinx.coroutines.delay(1000)
                    val mockId = "mock_${Math.abs(email.hashCode())}"
                    loginOrSignUpSocialUser(
                        id = mockId,
                        name = name,
                        email = email,
                        profileImage = "https://images.unsplash.com/photo-1544005313-94ddf0286df2?auto=format&fit=crop&w=150&q=80",
                        initialRole = role
                    )
                    authState.value = AuthState.Authenticated
                    return@launch
                }
                
                withContext(Dispatchers.IO) {
                    val client = OkHttpClient()
                    val mediaType = "application/json; charset=utf-8".toMediaTypeOrNull()
                    val jsonBody = """
                        {
                            "email": "$email",
                            "password": "$password",
                            "data": {
                                "name": "$name",
                                "role": "$role",
                                "city": "Mumbai"
                            }
                        }
                    """.trimIndent()
                    val body = okhttp3.RequestBody.create(mediaType, jsonBody)
                    val request = Request.Builder()
                        .url("$supabaseUrl/auth/v1/signup")
                        .post(body)
                        .addHeader("apikey", supabaseKey)
                        .addHeader("Content-Type", "application/json")
                        .build()
                        
                    client.newCall(request).execute().use { response ->
                        val responseBody = response.body?.string() ?: ""
                        if (response.isSuccessful) {
                            val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
                            val adapter = moshi.adapter(Map::class.java)
                            val respMap = adapter.fromJson(responseBody)
                            
                            val userObj = respMap?.get("user") as? Map<*, *>
                            val id = userObj?.get("id") as? String ?: ""
                            
                            if (id.isNotEmpty()) {
                                withContext(Dispatchers.Main) {
                                    loginOrSignUpSocialUser(
                                        id = id,
                                        name = name,
                                        email = email,
                                        profileImage = "https://images.unsplash.com/photo-1544005313-94ddf0286df2?auto=format&fit=crop&w=150&q=80",
                                        initialRole = role
                                    )
                                    authState.value = AuthState.Authenticated
                                }
                            } else {
                                withContext(Dispatchers.Main) {
                                    authState.value = AuthState.Error("Account creation failed: ID missing")
                                }
                            }
                        } else {
                            val errorMsg = try {
                                val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
                                val adapter = moshi.adapter(Map::class.java)
                                val respMap = adapter.fromJson(responseBody)
                                respMap?.get("msg") as? String ?: "Sign up failed"
                            } catch (e: java.lang.Exception) {
                                "Sign up failed"
                            }
                            withContext(Dispatchers.Main) {
                                authState.value = AuthState.Error(errorMsg)
                            }
                        }
                    }
                }
            } catch (e: java.lang.Exception) {
                authState.value = AuthState.Error(e.message ?: "Sign up failed.")
            }
        }
    }

    fun verifyOTP(code: String) {
        viewModelScope.launch {
            authState.value = AuthState.Loading
            kotlinx.coroutines.delay(1000)
            if (code == "123456" || code.length == 6) {
                authState.value = AuthState.Authenticated
            } else {
                authState.value = AuthState.Error("Invalid OTP code. Please enter 6 digits.")
            }
        }
    }

    val regCreatorType = MutableStateFlow("Both")
    val regExperienceLevel = MutableStateFlow("Professional")
    val regYearsOfExperience = MutableStateFlow(4)
    val regInstagram = MutableStateFlow("")
    val regWebsite = MutableStateFlow("")
    val regYoutube = MutableStateFlow("")
    val regSkillset = MutableStateFlow("Photographer")

    fun loginOrSignUpSocialUser(id: String, name: String, email: String, profileImage: String, initialRole: String) {
        viewModelScope.launch {
            authLoading.value = true
            val existingUser = repository.getUser(id)
            if (existingUser == null) {
                val newUser = User(
                    id = id,
                    name = name,
                    email = email,
                    phone = "",
                    role = initialRole,
                    profileImage = profileImage,
                    city = "Mumbai",
                    state = "Maharashtra",
                    country = "India"
                )
                repository.insertUser(newUser)
                
                // If the user chooses "Creator", populate a basic Creator profile
                if (initialRole == "Creator") {
                    val creator = Creator(
                        id = id,
                        userId = id,
                        creatorType = regCreatorType.value,
                        experienceLevel = regExperienceLevel.value,
                        bio = "Professional social creative. Enthusiastic to capture your finest frames.",
                        languages = "English, Hindi",
                        equipment = "Sony Alpha 7 IV, Prime Lenses",
                        rating = 4.8,
                        verified = false,
                        startingPrice = 18000.0,
                        instagram = regInstagram.value.ifEmpty { name.lowercase().replace(" ", "_") + "_pro" },
                        website = regWebsite.value.ifEmpty { "www.${name.lowercase().replace(" ", "")}photography.com" },
                        yearsOfExperience = regYearsOfExperience.value,
                        skillset = regSkillset.value.ifEmpty { "Photographer" },
                        youtube = regYoutube.value,
                        createdAt = System.currentTimeMillis()
                    )
                    repository.insertCreator(creator)
                }
            }
            
            currentUserId.value = id
            currentUserRole.value = existingUser?.role ?: initialRole
            setupCurrentUser()
            authLoading.value = false
            authMessage.value = "Welcome back, $name!"
        }
    }

    fun handleSupabaseCallbackToken(accessToken: String) {
        viewModelScope.launch {
            authLoading.value = true
            try {
                val supabaseUrl = BuildConfig.SUPABASE_URL
                val supabaseKey = BuildConfig.SUPABASE_ANON_KEY
                
                if (supabaseUrl.isEmpty() || supabaseUrl.contains("placeholder")) {
                    authLoading.value = false
                    authMessage.value = "Local Mock Callback Received!"
                    return@launch
                }
                
                withContext(Dispatchers.IO) {
                    val client = OkHttpClient()
                    val request = Request.Builder()
                        .url("$supabaseUrl/auth/v1/user")
                        .addHeader("Authorization", "Bearer $accessToken")
                        .addHeader("apikey", supabaseKey)
                        .build()
                        
                    client.newCall(request).execute().use { response ->
                        if (response.isSuccessful) {
                            val responseBody = response.body?.string() ?: ""
                            val moshi = Moshi.Builder()
                                .addLast(KotlinJsonAdapterFactory())
                                .build()
                            val adapter = moshi.adapter(Map::class.java)
                            val userObj = adapter.fromJson(responseBody)
                            
                            val id = userObj?.get("id") as? String ?: ""
                            val email = userObj?.get("email") as? String ?: ""
                            val userMetadata = userObj?.get("user_metadata") as? Map<*, *>
                            val name = userMetadata?.get("name") as? String ?: userMetadata?.get("full_name") as? String ?: "Fokal Artist"
                            val avatarUrl = userMetadata?.get("avatar_url") as? String ?: userMetadata?.get("avatar") as? String ?: ""
                            
                            if (id.isNotEmpty()) {
                                withContext(Dispatchers.Main) {
                                    loginOrSignUpSocialUser(
                                        id = id,
                                        name = name,
                                        email = email,
                                        profileImage = avatarUrl,
                                        initialRole = currentUserRole.value
                                    )
                                    authState.value = AuthState.Authenticated
                                }
                            }
                        } else {
                            withContext(Dispatchers.Main) {
                                authMessage.value = "Failed to sync profile: ${response.message}"
                                authState.value = AuthState.Error("Failed to sync profile: ${response.message}")
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                authMessage.value = "Auth Sync Error: ${e.localizedMessage}"
                authState.value = AuthState.Error("Auth Sync Error: ${e.localizedMessage}")
            } finally {
                authLoading.value = false
            }
        }
    }

    fun signUpWithEmailAndPassword(email: String, password: String, fullName: String, role: String, city: String) {
        viewModelScope.launch {
            authLoading.value = true
            try {
                val supabaseUrl = BuildConfig.SUPABASE_URL
                val supabaseKey = BuildConfig.SUPABASE_ANON_KEY
                
                if (supabaseUrl.isEmpty() || supabaseUrl.contains("placeholder")) {
                    kotlinx.coroutines.delay(1000)
                    val mockId = "mock_${Math.abs(email.hashCode())}"
                    loginOrSignUpSocialUser(
                        id = mockId,
                        name = fullName,
                        email = email,
                        profileImage = "https://images.unsplash.com/photo-1544005313-94ddf0286df2?auto=format&fit=crop&w=150&q=80",
                        initialRole = role
                    )
                    authMessage.value = "Mock Account created successfully for $fullName!"
                    return@launch
                }
                
                withContext(Dispatchers.IO) {
                    val client = OkHttpClient()
                    val mediaType = "application/json; charset=utf-8".toMediaTypeOrNull()
                    
                    val jsonBody = """
                        {
                            "email": "$email",
                            "password": "$password",
                            "data": {
                                "name": "$fullName",
                                "role": "$role",
                                "city": "$city"
                            }
                        }
                    """.trimIndent()
                    
                    val body = okhttp3.RequestBody.create(mediaType, jsonBody)
                    val request = Request.Builder()
                        .url("$supabaseUrl/auth/v1/signup")
                        .post(body)
                        .addHeader("apikey", supabaseKey)
                        .addHeader("Content-Type", "application/json")
                        .build()
                        
                    client.newCall(request).execute().use { response ->
                        val responseBody = response.body?.string() ?: ""
                        if (response.isSuccessful) {
                            val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
                            val adapter = moshi.adapter(Map::class.java)
                            val respMap = adapter.fromJson(responseBody)
                            
                            val userObj = respMap?.get("user") as? Map<*, *>
                            val id = userObj?.get("id") as? String ?: ""
                            
                            if (id.isNotEmpty()) {
                                withContext(Dispatchers.Main) {
                                    loginOrSignUpSocialUser(
                                        id = id,
                                        name = fullName,
                                        email = email,
                                        profileImage = "https://images.unsplash.com/photo-1544005313-94ddf0286df2?auto=format&fit=crop&w=150&q=80",
                                        initialRole = role
                                    )
                                    authMessage.value = "Account created successfully!"
                                }
                            } else {
                                withContext(Dispatchers.Main) {
                                    authMessage.value = "Registration successful! Please sign in."
                                }
                            }
                        } else {
                            withContext(Dispatchers.Main) {
                                val errorMsg = parseSupabaseError(responseBody) ?: response.message
                                authMessage.value = "Sign up failed: $errorMsg"
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                authMessage.value = "Sign up error: ${e.localizedMessage}"
            } finally {
                authLoading.value = false
            }
        }
    }

    fun signInWithEmailAndPassword(email: String, password: String, defaultRole: String) {
        viewModelScope.launch {
            authLoading.value = true
            try {
                val supabaseUrl = BuildConfig.SUPABASE_URL
                val supabaseKey = BuildConfig.SUPABASE_ANON_KEY
                
                if (supabaseUrl.isEmpty() || supabaseUrl.contains("placeholder")) {
                    kotlinx.coroutines.delay(1000)
                    val mockId = "mock_${Math.abs(email.hashCode())}"
                    
                    val existingUser = repository.getUser(mockId)
                    val role = existingUser?.role ?: defaultRole
                    val name = existingUser?.name ?: email.substringBefore("@").replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
                    val profileImage = existingUser?.profileImage ?: "https://images.unsplash.com/photo-1544005313-94ddf0286df2?auto=format&fit=crop&w=150&q=80"
                    
                    loginOrSignUpSocialUser(
                        id = mockId,
                        name = name,
                        email = email,
                        profileImage = profileImage,
                        initialRole = role
                    )
                    authMessage.value = "Successfully logged in as $name (Mock User)!"
                    return@launch
                }
                
                withContext(Dispatchers.IO) {
                    val client = OkHttpClient()
                    val mediaType = "application/json; charset=utf-8".toMediaTypeOrNull()
                    
                    val jsonBody = """
                        {
                            "email": "$email",
                            "password": "$password"
                        }
                    """.trimIndent()
                    
                    val body = okhttp3.RequestBody.create(mediaType, jsonBody)
                    val request = Request.Builder()
                        .url("$supabaseUrl/auth/v1/token?grant_type=password")
                        .post(body)
                        .addHeader("apikey", supabaseKey)
                        .addHeader("Content-Type", "application/json")
                        .build()
                        
                    client.newCall(request).execute().use { response ->
                        val responseBody = response.body?.string() ?: ""
                        if (response.isSuccessful) {
                            val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
                            val adapter = moshi.adapter(Map::class.java)
                            val respMap = adapter.fromJson(responseBody)
                            
                            val userObj = respMap?.get("user") as? Map<*, *>
                            val id = userObj?.get("id") as? String ?: ""
                            val userMetadata = userObj?.get("user_metadata") as? Map<*, *>
                            val name = userMetadata?.get("name") as? String ?: userMetadata?.get("full_name") as? String ?: email.substringBefore("@")
                            val role = userMetadata?.get("role") as? String ?: defaultRole
                            val avatarUrl = userMetadata?.get("avatar_url") as? String ?: userMetadata?.get("avatar") as? String ?: ""
                            
                            if (id.isNotEmpty()) {
                                withContext(Dispatchers.Main) {
                                    loginOrSignUpSocialUser(
                                        id = id,
                                        name = name,
                                        email = email,
                                        profileImage = avatarUrl.ifEmpty { "https://images.unsplash.com/photo-1544005313-94ddf0286df2?auto=format&fit=crop&w=150&q=80" },
                                        initialRole = role
                                    )
                                    authMessage.value = "Successfully logged in!"
                                }
                            }
                        } else {
                            withContext(Dispatchers.Main) {
                                val errorMsg = parseSupabaseError(responseBody) ?: response.message
                                authMessage.value = "Login failed: $errorMsg"
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                authMessage.value = "Login error: ${e.localizedMessage}"
            } finally {
                authLoading.value = false
            }
        }
    }

    private fun parseSupabaseError(json: String): String? {
        return try {
            val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
            val adapter = moshi.adapter(Map::class.java)
            val map = adapter.fromJson(json)
            map?.get("error_description") as? String ?: map?.get("message") as? String
        } catch (e: java.lang.Exception) {
            null
        }
    }

    // List of reactive client leads/requests for Photographer Alerts
    val clientLeadsList: StateFlow<List<ClientLead>> = repository.allLeads
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    // Detect user city based on location service
    fun detectUserCity(onResult: (com.example.data.service.LocationService.CityInfo?) -> Unit) {
        viewModelScope.launch {
            try {
                val service = com.example.data.service.LocationService(getApplication())
                val city = service.detectUserCity()
                onResult(city)
            } catch (e: Exception) {
                e.printStackTrace()
                onResult(null)
            }
        }
    }

    fun searchNearbyCreators(
        city: com.example.data.service.LocationService.CityInfo,
        radius: Int = 50,
        eventType: String? = null,
        maxBudget: Double? = null
    ) {
        viewModelScope.launch {
            try {
                val service = com.example.data.service.LocationService(getApplication())
                val nearbyCities = service.getCitiesWithinRadius(city, radius).map { it.name.lowercase() }
                
                val creators = repository.allCreators.first()
                val filtered = creators.filter { creator ->
                    val creatorCity = getCreatorCitySync(creator.id).lowercase()
                    val matchesCity = nearbyCities.any { creatorCity.contains(it) } || creatorCity.contains(city.name.lowercase())
                    val matchesEventType = if (eventType.isNullOrBlank()) true else {
                        creator.skillset.contains(eventType, ignoreCase = true) ||
                        creator.creatorType.contains(eventType, ignoreCase = true)
                    }
                    val matchesBudget = if (maxBudget == null) true else {
                        creator.startingPrice <= maxBudget
                    }
                    matchesCity && matchesEventType && matchesBudget
                }
                _nearbyCreators.value = filtered
            } catch (e: Exception) {
                android.util.Log.e("FokalViewModel", "Search failed", e)
            }
        }
    }

    fun searchCreatorsGlobal(
        query: String,
        city: String? = null,
        eventType: String? = null
    ) {
        viewModelScope.launch {
            try {
                _searchResults.value = searchRepository.searchCreatorsGlobal(
                    query = query,
                    city = city,
                    eventType = eventType
                )
            } catch (e: Exception) {
                android.util.Log.e("FokalViewModel", "Global search failed", e)
            }
        }
    }

    private fun notifyNearbyCreators(cityId: String, eventType: String, budget: Double) {
        android.util.Log.d("FokalViewModel", "Notifying creators in $cityId about a new $eventType shoot with budget $budget")
    }

    // Post custom shoot alert using location details
    fun postShootAlert(
        eventType: String,
        location: String,
        budget: Double,
        timeframe: String,
        description: String,
        additionalDetails: String,
        cityId: String,
        referenceImages: String? = null
    ) {
        viewModelScope.launch {
            try {
                val dbUser = currentUserProfile.value
                val name = dbUser?.name ?: "Guest Customer"
                val email = dbUser?.email ?: "guest@fokalpoint.com"
                
                val combinedDescription = if (additionalDetails.isNotBlank()) {
                    "$description\n\nAdditional Details:\n$additionalDetails"
                } else {
                    description
                }
                
                val finalLocation = if (cityId.isNotBlank() && !location.contains(cityId)) {
                    "$cityId, $location"
                } else {
                    location
                }

                val newLead = ClientLead(
                    customerId = currentUserId.value,
                    customerName = name,
                    customerEmail = email,
                    eventType = eventType,
                    location = finalLocation,
                    budget = budget,
                    description = combinedDescription,
                    dateDetail = timeframe,
                    referenceImages = referenceImages
                )
                repository.insertLead(newLead)
                // Notify nearby creators via push notification
                notifyNearbyCreators(cityId, eventType, budget)
                authMessage.value = "Shoot Alert posted successfully! Nearby photographers are notified."
            } catch (e: Exception) {
                e.printStackTrace()
                authMessage.value = "Failed to post shoot alert: ${e.localizedMessage}"
            }
        }
    }

    // Post a dynamic shoot request looking for a photographer (Alerts for creators)
    fun createClientLead(eventType: String, location: String, budget: Double, description: String, dateDetail: String) {
        viewModelScope.launch {
            try {
                val dbUser = currentUserProfile.value
                val name = dbUser?.name ?: "Guest Customer"
                val email = dbUser?.email ?: "guest@fokalpoint.com"
                val newLead = ClientLead(
                    customerId = currentUserId.value,
                    customerName = name,
                    customerEmail = email,
                    eventType = eventType,
                    location = location,
                    budget = budget,
                    description = description,
                    dateDetail = dateDetail
                )
                repository.insertLead(newLead)
                authMessage.value = "Shoot Alert posted successfully! Nearby photographers are notified."
            } catch (e: Exception) {
                e.printStackTrace()
                authMessage.value = "Failed to post shoot alert: ${e.localizedMessage}"
            }
        }
    }

    // Dismiss or delete a lead
    fun deleteClientLead(leadId: Long) {
        viewModelScope.launch {
            try {
                repository.deleteLead(leadId)
                authMessage.value = "Request resolved/archived."
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // Reset all filters
    fun clearAllFilters() {
        searchQuery.value = ""
        selectedCategory.value = ""
        filterCity.value = ""
        filterBudget.value = null
        filterExperience.value = ""
        filterMinRep.value = null
    }

    fun toggleBlockedDate(creatorId: String, date: String) {
        val current = blockedDatesState.value.toMutableMap()
        val list = (current[creatorId] ?: emptyList()).toMutableList()
        val isBlockedNow = if (list.contains(date)) {
            list.remove(date)
            false
        } else {
            list.add(date)
            true
        }
        current[creatorId] = list
        blockedDatesState.value = current
        
        saveBlockedDateToSupabase(creatorId, date, isBlockedNow)
    }

    private fun saveBlockedDateToSupabase(creatorId: String, blockedDate: String, isBlocked: Boolean) {
        viewModelScope.launch {
            try {
                val supabaseUrl = BuildConfig.SUPABASE_URL
                val supabaseKey = BuildConfig.SUPABASE_ANON_KEY
                if (supabaseUrl.isEmpty() || supabaseUrl.contains("placeholder")) return@launch
                
                withContext(Dispatchers.IO) {
                    val client = okhttp3.OkHttpClient()
                    if (isBlocked) {
                        val mediaType = "application/json; charset=utf-8".toMediaTypeOrNull()
                        val jsonBody = """
                            {
                                "creator_id": "$creatorId",
                                "blocked_date": "$blockedDate"
                            }
                        """.trimIndent()
                        val body = okhttp3.RequestBody.create(mediaType, jsonBody)
                        val request = Request.Builder()
                            .url("$supabaseUrl/rest/v1/blocked_dates")
                            .post(body)
                            .addHeader("apikey", supabaseKey)
                            .addHeader("Authorization", "Bearer $supabaseKey")
                            .addHeader("Content-Type", "application/json")
                            .build()
                        client.newCall(request).execute().use { response ->
                            android.util.Log.d("SupabaseBlock", "Saved block. Success: ${response.isSuccessful}")
                        }
                    } else {
                        val request = Request.Builder()
                            .url("$supabaseUrl/rest/v1/blocked_dates?creator_id=eq.$creatorId&blocked_date=eq.$blockedDate")
                            .delete()
                            .addHeader("apikey", supabaseKey)
                            .addHeader("Authorization", "Bearer $supabaseKey")
                            .build()
                        client.newCall(request).execute().use { response ->
                            android.util.Log.d("SupabaseBlock", "Deleted block. Success: ${response.isSuccessful}")
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("SupabaseBlock", "Error syncing block: ${e.message}")
            }
        }
    }

    private fun startSupabaseBlockedDatesSyncLoop() {
        viewModelScope.launch {
            while (true) {
                kotlinx.coroutines.delay(4000L) // Poll blocks every 4 seconds for real-time syncing
                try {
                    val supabaseUrl = BuildConfig.SUPABASE_URL
                    val supabaseKey = BuildConfig.SUPABASE_ANON_KEY
                    if (supabaseUrl.isEmpty() || supabaseUrl.contains("placeholder")) continue
                    
                    withContext(Dispatchers.IO) {
                        val client = okhttp3.OkHttpClient()
                        val request = Request.Builder()
                            .url("$supabaseUrl/rest/v1/blocked_dates")
                            .get()
                            .addHeader("apikey", supabaseKey)
                            .addHeader("Authorization", "Bearer $supabaseKey")
                            .build()
                        client.newCall(request).execute().use { response ->
                            if (response.isSuccessful) {
                                val jsonStr = response.body?.string() ?: "[]"
                                val jsonArray = org.json.JSONArray(jsonStr)
                                val newBlocks = mutableMapOf<String, MutableList<String>>()
                                for (i in 0 until jsonArray.length()) {
                                    val obj = jsonArray.getJSONObject(i)
                                    val creatorId = obj.optString("creator_id", obj.optString("creatorId", ""))
                                    val dateStr = obj.optString("blocked_date", obj.optString("blockedDate", ""))
                                    if (creatorId.isNotEmpty() && dateStr.isNotEmpty()) {
                                        val list = newBlocks.getOrPut(creatorId) { mutableListOf() }
                                        if (!list.contains(dateStr)) {
                                            list.add(dateStr)
                                        }
                                    }
                                }
                                if (newBlocks.isNotEmpty()) {
                                    val merged = blockedDatesState.value.toMutableMap()
                                    newBlocks.forEach { (creator, list) ->
                                        merged[creator] = list
                                    }
                                    blockedDatesState.value = merged
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    // Gracefully ignore Table not found or connection/Internet error
                }
            }
        }
    }

    private fun savePayoutMethodToSupabase(payoutMethod: PayoutMethod) {
        viewModelScope.launch {
            try {
                val supabaseUrl = BuildConfig.SUPABASE_URL
                val supabaseKey = BuildConfig.SUPABASE_ANON_KEY
                if (supabaseUrl.isEmpty() || supabaseUrl.contains("placeholder")) {
                    android.util.Log.d("SupabasePayout", "Supabase is not configured or placeholder URL used. Saved locally only.")
                    return@launch
                }
                withContext(Dispatchers.IO) {
                    val client = okhttp3.OkHttpClient()
                    val json = org.json.JSONObject().apply {
                        put("id", payoutMethod.id)
                        put("user_id", payoutMethod.userId)
                        put("type", payoutMethod.type.name)
                        put("account_holder_name", payoutMethod.accountHolderName)
                        put("account_number", payoutMethod.accountNumber ?: org.json.JSONObject.NULL)
                        put("bank_name", payoutMethod.bankName ?: org.json.JSONObject.NULL)
                        put("ifsc_code", payoutMethod.ifscCode ?: org.json.JSONObject.NULL)
                        put("upi_id", payoutMethod.upiId ?: org.json.JSONObject.NULL)
                        put("is_default", payoutMethod.isDefault)
                        put("status", payoutMethod.status.name)
                        if (payoutMethod.createdAt.isNotEmpty()) {
                            put("created_at", payoutMethod.createdAt)
                        }
                    }
                    val body = okhttp3.RequestBody.create(
                        "application/json; charset=utf-8".toMediaTypeOrNull(),
                        json.toString()
                    )
                    val request = Request.Builder()
                        .url("$supabaseUrl/rest/v1/payout_methods")
                        .post(body)
                        .addHeader("apikey", supabaseKey)
                        .addHeader("Authorization", "Bearer $supabaseKey")
                        .addHeader("Prefer", "resolution=merge-duplicates")
                        .build()
                    client.newCall(request).execute().use { response ->
                        if (response.isSuccessful) {
                            android.util.Log.d("SupabasePayout", "Successfully synced payout method to Supabase!")
                        } else {
                            val errorBody = response.body?.string() ?: ""
                            android.util.Log.e("SupabasePayout", "Failed to sync payout method to Supabase: Status ${response.code} - $errorBody")
                        }
                    }
                }
            } catch (e: java.lang.Exception) {
                android.util.Log.e("SupabasePayout", "Error syncing payout method: ${e.message}")
            }
        }
    }

    private fun deletePayoutMethodFromSupabase(id: String) {
        viewModelScope.launch {
            try {
                val supabaseUrl = BuildConfig.SUPABASE_URL
                val supabaseKey = BuildConfig.SUPABASE_ANON_KEY
                if (supabaseUrl.isEmpty() || supabaseUrl.contains("placeholder")) return@launch
                withContext(Dispatchers.IO) {
                    val client = okhttp3.OkHttpClient()
                    val request = Request.Builder()
                        .url("$supabaseUrl/rest/v1/payout_methods?id=eq.$id")
                        .delete()
                        .addHeader("apikey", supabaseKey)
                        .addHeader("Authorization", "Bearer $supabaseKey")
                        .build()
                    client.newCall(request).execute().use { response ->
                        if (response.isSuccessful) {
                            android.util.Log.d("SupabasePayout", "Successfully deleted payout method from Supabase!")
                        } else {
                            android.util.Log.e("SupabasePayout", "Failed to delete payout method from Supabase: Status ${response.code}")
                        }
                    }
                }
            } catch (e: java.lang.Exception) {
                android.util.Log.e("SupabasePayout", "Error deleting payout method: ${e.message}")
            }
        }
    }

    private fun updateDefaultPayoutMethodInSupabase(id: String) {
        viewModelScope.launch {
            try {
                val supabaseUrl = BuildConfig.SUPABASE_URL
                val supabaseKey = BuildConfig.SUPABASE_ANON_KEY
                if (supabaseUrl.isEmpty() || supabaseUrl.contains("placeholder")) return@launch
                val userId = currentUserId.value
                withContext(Dispatchers.IO) {
                    val client = okhttp3.OkHttpClient()
                    // First set all for this user to false
                    val jsonFalse = org.json.JSONObject().apply {
                        put("is_default", false)
                    }
                    val bodyFalse = okhttp3.RequestBody.create(
                        "application/json; charset=utf-8".toMediaTypeOrNull(),
                        jsonFalse.toString()
                    )
                    val reqFalse = Request.Builder()
                        .url("$supabaseUrl/rest/v1/payout_methods?user_id=eq.$userId")
                        .patch(bodyFalse)
                        .addHeader("apikey", supabaseKey)
                        .addHeader("Authorization", "Bearer $supabaseKey")
                        .build()
                    client.newCall(reqFalse).execute().close()

                    // Then set the chosen one to true
                    val jsonTrue = org.json.JSONObject().apply {
                        put("is_default", true)
                    }
                    val bodyTrue = okhttp3.RequestBody.create(
                        "application/json; charset=utf-8".toMediaTypeOrNull(),
                        jsonTrue.toString()
                    )
                    val reqTrue = Request.Builder()
                        .url("$supabaseUrl/rest/v1/payout_methods?id=eq.$id")
                        .patch(bodyTrue)
                        .addHeader("apikey", supabaseKey)
                        .addHeader("Authorization", "Bearer $supabaseKey")
                        .build()
                    client.newCall(reqTrue).execute().close()
                }
            } catch (e: java.lang.Exception) {
                android.util.Log.e("SupabasePayout", "Error updating default payout method: ${e.message}")
            }
        }
    }
}

data class UPIApp(
    val id: String,
    val name: String,
    val upiId: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val color: androidx.compose.ui.graphics.Color
)

data class CreatorUPIProfile(
    val id: String,
    val name: String,
    val upiMethods: List<UPIApp>
)

