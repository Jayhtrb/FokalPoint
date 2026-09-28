package com.example.ui.viewmodel

import android.app.Application
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.database.AppDatabase
import com.example.data.model.*
import com.example.data.network.GeminiClient
import com.example.data.repository.FokalRepository
import com.example.data.repository.SearchRepository
import com.example.data.supabase.RemoteMappers
import com.example.data.supabase.RemoteQueries
import com.example.data.supabase.SupabaseAuth
import com.example.data.supabase.SupabaseClient
import com.example.data.supabase.SupabaseConfig
import com.example.ui.theme.dataStore
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.json.JSONObject

class FokalViewModel(application: Application) : AndroidViewModel(application) {

    private companion object { const val TAG = "FokalViewModel" }

    private val repository: FokalRepository
    private val searchRepository: SearchRepository

    // Current State
    val currentUserId = MutableStateFlow("")
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
        if (SupabaseConfig.isConfigured) emptyMap() else demoBlockedDates()
    )

    /** Sample unavailability for the demo creators, a few days out from today. */
    private fun demoBlockedDates(): Map<String, List<String>> {
        val d = com.example.ui.utils.BookingDates.upcomingDays(40)
        return mapOf(
            "riya_sen_creator" to listOf(d[3], d[24]),
            "amit_sharma_creator" to listOf(d[1], d[14]),
            "kabir_singh_creator" to listOf(d[2], d[3]),
            "vikram_goa_creator" to listOf(d[6]),
            "manisha_mehta_creator" to listOf(d[20])
        )
    }

    /** True when no Supabase backend is configured: all data is local sample data. */
    val isDemoMode: Boolean = !SupabaseConfig.isConfigured

    private val supabase = SupabaseClient.get(application)
    private val rest get() = supabase.rest

    // Snapshot-state cache of every known profile. Reading it from composition
    // (via getCreatorNameSync & co.) subscribes the UI to profile updates.
    private var usersById by mutableStateOf<Map<String, User>>(emptyMap())

    // Selection of date inside visual profile calendar
    val selectedShootDate = MutableStateFlow(com.example.ui.utils.BookingDates.firstBookableDate())

    val notificationPreferences = MutableStateFlow(com.example.data.model.PayoutNotificationPreferences())

    private val THEME_KEY = androidx.datastore.preferences.core.booleanPreferencesKey("dark_theme")
    
    val isDarkTheme: StateFlow<Boolean> = application.applicationContext.dataStore.data
        .map { preferences -> preferences[THEME_KEY] ?: true } // brand default: dark
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = true
        )
        
    fun toggleTheme() {
        viewModelScope.launch {
            getApplication<Application>().applicationContext.dataStore.edit { preferences ->
                val current = preferences[THEME_KEY] ?: true
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
        searchRepository = SearchRepository(application, supabase)

        viewModelScope.launch {
            // Sample creators are only for demo mode; a live backend shows real creators.
            if (isDemoMode) repository.seedMockDataIfEmpty()
        }
        viewModelScope.launch {
            repository.allUsers.collect { users -> usersById = users.associateBy { it.id } }
        }
        startSupabaseChatSyncLoop()
        startSupabaseBlockedDatesSyncLoop()
        startLiveActivityRefreshLoop()
    }

    /** Called by MainActivity whenever the authenticated user changes (null = signed out). */
    fun setActiveUser(user: User?) {
        if (user == null) {
            currentUserId.value = ""
            currentUserProfile.value = null
            currentCreatorDetails.value = null
            selectedChatCreatorId.value = null
            return
        }
        if (user.id == currentUserId.value && user == currentUserProfile.value) return
        currentUserRole.value = user.role
        currentUserId.value = user.id
        currentUserProfile.value = user
        viewModelScope.launch {
            setupCurrentUser()
            refreshRemoteData()
        }
    }

    /** Pulls the signed-in user's server data into the local cache. No-op in demo mode. */
    fun refreshRemoteData() {
        if (!rest.isAvailable) return
        currentUserId.value.ifEmpty { return }
        viewModelScope.launch {
            runCatching { searchRepository.refreshCatalog() }
                .onFailure { android.util.Log.w(TAG, "Creator catalogue refresh failed", it) }
            refreshActivity()
        }
    }

    /**
     * Bookings, inbox and (for creators) shoot alerts. Runs at sign-in and then
     * periodically, so new booking requests and first messages from new customers appear.
     */
    suspend fun refreshActivity() {
        if (!rest.isAvailable) return
        val me = currentUserId.value.ifEmpty { return }
        run {
            runCatching {
                val rows = rest.select(
                    "messages",
                    "or" to "(sender_id.eq.$me,receiver_id.eq.$me)",
                    "order" to "created_at.desc",
                    "limit" to "300"
                )
                val partners = mutableSetOf<String>()
                for (i in 0 until rows.length()) {
                    val message = RemoteMappers.message(rows.getJSONObject(i))
                    repository.insertMessage(message)
                    partners += message.senderId
                    partners += message.receiverId
                }
                cacheProfiles(partners - me)
            }.onFailure { android.util.Log.w(TAG, "Inbox refresh failed", it) }
            runCatching {
                val rows = rest.select("bookings", *RemoteQueries.bookingsInvolving(me))
                val counterparties = mutableSetOf<String>()
                for (i in 0 until rows.length()) {
                    val booking = RemoteMappers.booking(rows.getJSONObject(i))
                    repository.insertBooking(booking)
                    counterparties += booking.customerId
                    counterparties += booking.creatorId
                }
                cacheProfiles(counterparties - me)
            }.onFailure { android.util.Log.w(TAG, "Bookings refresh failed", it) }
            runCatching {
                val rows = rest.select("payout_methods", "user_id" to "eq.$me")
                for (i in 0 until rows.length()) repository.insertPayoutMethod(RemoteMappers.payoutMethod(rows.getJSONObject(i)))
            }.onFailure { android.util.Log.w(TAG, "Payout methods refresh failed", it) }
            if (currentUserRole.value == "Creator") refreshShootAlerts()
        }
    }

    private fun startLiveActivityRefreshLoop() {
        viewModelScope.launch {
            while (true) {
                kotlinx.coroutines.delay(30_000L)
                refreshActivity()
            }
        }
    }

    private suspend fun cacheProfiles(ids: Collection<String>) {
        val missing = ids.filter { it.isNotBlank() && usersById[it]?.name.isNullOrBlank() }
        if (missing.isEmpty() || !rest.isAvailable) return
        runCatching {
            val rows = rest.select("users", "id" to RemoteQueries.idIn(missing))
            for (i in 0 until rows.length()) repository.insertUser(RemoteMappers.user(rows.getJSONObject(i)))
        }
    }

    private suspend fun refreshShootAlerts() {
        runCatching {
            val rows = rest.select("shoot_alerts", "status" to "eq.pending", "order" to "created_at.desc", "limit" to "100")
            for (i in 0 until rows.length()) repository.insertLead(RemoteMappers.lead(rows.getJSONObject(i)))
        }.onFailure { android.util.Log.w(TAG, "Shoot alerts refresh failed", it) }
    }

    private suspend fun setupCurrentUser() {
        val userId = currentUserId.value
        if (userId.isEmpty()) return
        val user = repository.getUser(userId) ?: currentUserProfile.value ?: return
        currentUserProfile.value = user

        if (currentUserRole.value == "Creator") {
            var creator = repository.getCreatorSync(userId)
            if (creator == null && rest.isAvailable) {
                creator = runCatching {
                    rest.select("creators", "id" to "eq.$userId", "limit" to "1").optJSONObject(0)
                        ?.let(SearchRepository::creatorFromRow)
                }.getOrNull()
            }
            if (creator == null) {
                // New creator: start with an honest, unverified, unrated profile.
                creator = Creator(
                    id = userId,
                    userId = userId,
                    creatorType = "Photographer",
                    experienceLevel = "Professional",
                    bio = "",
                    languages = "English",
                    equipment = "",
                    rating = 0.0,
                    verified = false,
                    startingPrice = 10000.0,
                    instagram = "",
                    website = "",
                    yearsOfExperience = 0
                )
                pushCreatorProfile(creator)
            }
            repository.insertCreator(creator)
            currentCreatorDetails.value = creator
        } else {
            currentCreatorDetails.value = null
        }
    }

    private suspend fun pushCreatorProfile(creator: Creator) {
        if (!rest.isAvailable) return
        runCatching { rest.insert("creators", RemoteMappers.creatorRow(creator), upsert = true) }
            .onFailure { android.util.Log.w(TAG, "Creator profile sync failed", it) }
    }

    // Role switcher
    // Role switcher: changes the signed-in user's own role (e.g. "Become a creator").
    fun switchRole(newRole: String) {
        viewModelScope.launch {
            val user = currentUserProfile.value ?: return@launch
            val role = SupabaseAuth.normalizeRole(newRole)
            val updated = user.copy(role = role)
            repository.insertUser(updated)
            currentUserProfile.value = updated
            currentUserRole.value = role
            supabase.auth.updateRole(role)
            if (rest.isAvailable) {
                runCatching { rest.update("users", JSONObject().put("role", role), "id" to "eq.${user.id}") }
                    .onFailure { android.util.Log.w(TAG, "Role sync failed", it) }
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
    // Profile lookups for rendering. Backed by snapshot state, so composables recompose
    // as profiles arrive from Room / Supabase.
    fun getCreatorNameSync(creatorId: String): String =
        usersById[creatorId]?.name?.takeIf { it.isNotBlank() } ?: "FokalPoint Creator"

    fun getCreatorCitySync(creatorId: String): String = usersById[creatorId]?.city.orEmpty()

    fun getCreatorAvatarSync(creatorId: String): String =
        usersById[creatorId]?.profileImage?.takeIf { it.isNotBlank() }
            ?: "https://ui-avatars.com/api/?size=256&bold=true&background=1C1C1E&color=E0A526&name=" +
                android.net.Uri.encode(getCreatorNameSync(creatorId))

    // Bookings flows
    val bookingsList: StateFlow<List<Booking>> = combine(currentUserId, currentUserRole) { id, role -> id to role }
        .flatMapLatest { (userId, role) ->
            if (role == "Customer") {
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
            CreatorEarnings(if (total > 0 || !isDemoMode) total else 185000.0)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), CreatorEarnings(0.0))

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
            if (completed.isEmpty() && isDemoMode) {
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
            val totalEarned = completedAmount + if (isDemoMode) 1500.0 else 0.0 // demo onboarding bonus
            PayoutStats(
                available = completedAmount,
                pending = pendingAmount,
                totalEarned = totalEarned,
                earningsData = monthlyEarnings(bookings)
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), PayoutStats(0.0, 0.0, 0.0, emptyList()))

    /** Paid earnings for the last six calendar months, oldest first. */
    private fun monthlyEarnings(bookings: List<Booking>): List<EarningsDataPoint> {
        val paid = bookings.filter { it.status == "Completed" || it.paymentStatus == "Paid" }
        val cal = java.util.Calendar.getInstance()
        val keyFmt = java.text.SimpleDateFormat("yyyy-MM", java.util.Locale.US)
        val labelFmt = java.text.SimpleDateFormat("MMM", java.util.Locale.getDefault())
        cal.add(java.util.Calendar.MONTH, -5)
        return (0 until 6).map {
            val key = keyFmt.format(cal.time)
            val point = EarningsDataPoint(labelFmt.format(cal.time), paid.filter { b -> b.date.startsWith(key) }.sumOf { b -> b.price })
            cal.add(java.util.Calendar.MONTH, 1)
            point
        }
    }

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

    /**
     * Waits for a UPI payment to be confirmed. Demo mode simulates success. With a live
     * backend the booking is only marked paid once the creator confirms receipt (or a
     * server-side payment webhook updates it); we poll for up to two minutes.
     */
    fun monitorUPIPayment(bookingId: Long, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            if (!rest.isAvailable) {
                kotlinx.coroutines.delay(2000)
                confirmBookingPayment(bookingId, "Paid", "Confirmed")
                onResult(true)
                return@launch
            }
            repeat(24) {
                val row = runCatching {
                    rest.select("bookings", "id" to "eq.$bookingId", "select" to "status,payment_status").optJSONObject(0)
                }.getOrNull()
                if (row?.optString("payment_status") == "Paid") {
                    repository.updatePaymentStatus(bookingId, "Paid")
                    repository.updateBookingStatus(bookingId, row.optString("status", "Confirmed"))
                    onResult(true)
                    return@launch
                }
                kotlinx.coroutines.delay(5000)
            }
            onResult(false)
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
        
        if (payments.isEmpty() && isDemoMode) {
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
    // Messaging operations
    fun sendMessage(msgText: String) {
        val partnerId = selectedChatCreatorId.value ?: return
        val text = msgText.trim()
        if (text.isEmpty()) return
        viewModelScope.launch {
            val draft = Message(senderId = currentUserId.value, receiverId = partnerId, message = text)
            if (!rest.isAvailable) {
                repository.insertMessage(draft)
                // Demo mode only: sample creators reply automatically.
                simulateSmartResponse(partnerId, text)
                return@launch
            }
            try {
                val row = rest.insert(
                    "messages",
                    JSONObject()
                        .put("sender_id", draft.senderId)
                        .put("receiver_id", draft.receiverId)
                        .put("message", draft.message)
                )
                repository.insertMessage(row?.let(RemoteMappers::message) ?: draft)
            } catch (e: Exception) {
                android.util.Log.w(TAG, "Message send failed", e)
                authMessage.value = "Message not sent. Check your connection and try again."
            }
        }
    }

    /** Polls the open conversation for new messages while a live backend is available. */
    private fun startSupabaseChatSyncLoop() {
        viewModelScope.launch {
            while (true) {
                kotlinx.coroutines.delay(3000L)
                val myId = currentUserId.value
                val partnerId = selectedChatCreatorId.value ?: continue
                if (myId.isEmpty() || !rest.isAvailable) continue
                try {
                    val rows = rest.select("messages", *RemoteQueries.conversation(myId, partnerId))
                    for (i in 0 until rows.length()) repository.insertMessage(RemoteMappers.message(rows.getJSONObject(i)))
                } catch (e: Exception) {
                    android.util.Log.d(TAG, "Chat sync skipped: ${e.message}")
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
            val stored = if (rest.isAvailable) {
                try {
                    rest.insert("bookings", RemoteMappers.bookingRow(newBooking))?.let(RemoteMappers::booking) ?: newBooking
                } catch (e: Exception) {
                    android.util.Log.w(TAG, "Booking sync failed", e)
                    authMessage.value = "We couldn't reach FokalPoint to place this booking. Please try again."
                    return@launch
                }
            } else newBooking
            repository.insertBooking(stored)

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
            // With a live backend only the booking's creator can confirm a payment was received.
            if (rest.isAvailable && repository.getBookingByIdSync(bookingId)?.creatorId != currentUserId.value) {
                return@launch
            }
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

    /**
     * Pushes a status change. `payment_status` is only sent by the booking's creator
     * (confirming receipt); the database trigger rejects anything else.
     */
    private fun updateBookingInSupabase(bookingId: Long, newStatus: String, newPaymentStatus: String?) {
        if (!rest.isAvailable) return
        viewModelScope.launch {
            val booking = repository.getBookingByIdSync(bookingId) ?: return@launch
            val patch = JSONObject().put("status", newStatus)
            if (newPaymentStatus == "Paid" && booking.creatorId == currentUserId.value) {
                patch.put("payment_status", "Paid")
            }
            runCatching { rest.update("bookings", patch, "id" to "eq.$bookingId") }
                .onFailure {
                    android.util.Log.w(TAG, "Booking update failed", it)
                    authMessage.value = "Couldn't sync this booking update. Pull to refresh and try again."
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
                    rating = 0.0,
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
                pushCreatorProfile(creator)
                currentCreatorDetails.value = creator
                switchRole("Creator")
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

    // General one-shot feedback surfaced by screens (historically named for auth).
    val authLoading = MutableStateFlow(false)
    val authMessage = MutableStateFlow<String?>(null)
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
                if (rest.isAvailable) {
                    rest.insert(
                        "shoot_alerts",
                        JSONObject()
                            .put("customer_id", currentUserId.value)
                            .put("event_type", eventType)
                            .put("location", finalLocation)
                            .put("city_id", cityId.ifBlank { finalLocation })
                            .put("budget", budget)
                            .put("timeframe", timeframe)
                            .put("description", description)
                            .put("additional_details", additionalDetails)
                    )
                }
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
        if (!rest.isAvailable || creatorId != currentUserId.value) return
        viewModelScope.launch {
            runCatching {
                if (isBlocked) {
                    rest.insert(
                        "blocked_dates",
                        JSONObject().put("creator_id", creatorId).put("blocked_date", blockedDate),
                        upsert = true
                    )
                } else {
                    rest.delete("blocked_dates", "creator_id" to "eq.$creatorId", "blocked_date" to "eq.$blockedDate")
                }
            }.onFailure { android.util.Log.w(TAG, "Blocked date sync failed", it) }
        }
    }

    /** Keeps availability fresh for the creator being viewed and for the signed-in creator. */
    private fun startSupabaseBlockedDatesSyncLoop() {
        viewModelScope.launch {
            combine(selectedCreatorId, currentUserId) { a, b -> listOfNotNull(a, b.takeIf { it.isNotEmpty() }).distinct() }
                .collectLatest { ids ->
                    while (ids.isNotEmpty()) {
                        if (rest.isAvailable) {
                            runCatching {
                                val rows = rest.select("blocked_dates", "creator_id" to RemoteQueries.idIn(ids))
                                val fetched = ids.associateWith { mutableListOf<String>() }
                                for (i in 0 until rows.length()) {
                                    val row = rows.getJSONObject(i)
                                    fetched[row.optString("creator_id")]?.add(row.optString("blocked_date"))
                                }
                                blockedDatesState.value = blockedDatesState.value + fetched
                            }
                        }
                        kotlinx.coroutines.delay(15_000L)
                    }
                }
        }
    }

    private fun savePayoutMethodToSupabase(payoutMethod: PayoutMethod) {
        if (!rest.isAvailable) return
        viewModelScope.launch {
            runCatching { rest.insert("payout_methods", RemoteMappers.payoutMethodRow(payoutMethod), upsert = true) }
                .onFailure {
                    android.util.Log.w(TAG, "Payout method sync failed", it)
                    authMessage.value = "Couldn't save this payout method to your account."
                }
        }
    }

    private fun deletePayoutMethodFromSupabase(id: String) {
        if (!rest.isAvailable) return
        viewModelScope.launch {
            runCatching { rest.delete("payout_methods", "id" to "eq.$id") }
                .onFailure { android.util.Log.w(TAG, "Payout method delete failed", it) }
        }
    }

    private fun updateDefaultPayoutMethodInSupabase(id: String) {
        if (!rest.isAvailable) return
        val userId = currentUserId.value
        viewModelScope.launch {
            runCatching {
                // Clear first: a unique partial index allows only one default per user.
                rest.update("payout_methods", JSONObject().put("is_default", false), "user_id" to "eq.$userId")
                rest.update("payout_methods", JSONObject().put("is_default", true), "id" to "eq.$id")
            }.onFailure { android.util.Log.w(TAG, "Default payout method sync failed", it) }
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

