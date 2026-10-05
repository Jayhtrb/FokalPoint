package com.fokalpoint.app.ui.viewmodel

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.fokalpoint.app.data.database.AppDatabase
import com.fokalpoint.app.data.model.*
import com.fokalpoint.app.data.repository.FokalRepository
import com.fokalpoint.app.data.repository.SearchRepository
import com.fokalpoint.app.data.service.LocationService
import com.fokalpoint.app.data.supabase.RemoteMappers
import com.fokalpoint.app.data.supabase.RemoteQueries
import com.fokalpoint.app.data.supabase.SupabaseAuth
import com.fokalpoint.app.data.supabase.SupabaseClient
import com.fokalpoint.app.data.supabase.SupabaseConfig
import com.fokalpoint.app.ui.utils.BookingDates
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * App-wide marketplace state for the signed-in user. Room is the UI's source of
 * truth; with a live backend every write goes to Supabase first and the server's
 * answer is cached locally.
 */
class FokalViewModel(application: Application) : AndroidViewModel(application) {

    private companion object { const val TAG = "FokalViewModel" }

    private val repository: FokalRepository
    private val searchRepository: SearchRepository
    private val supabase = SupabaseClient.get(application)
    private val rest get() = supabase.rest

    /** True when no Supabase backend is configured: all data is local sample data. */
    val isDemoMode: Boolean = !SupabaseConfig.isConfigured

    // ---------------------------------------------------------------- identity
    val currentUserId = MutableStateFlow("")
    val currentUserRole = MutableStateFlow("Customer")
    val currentUserProfile = MutableStateFlow<User?>(null)

    /** One-shot user feedback (errors, confirmations). The UI shows and clears it. */
    val toast = MutableStateFlow<String?>(null)

    // Snapshot-state profile cache; reading it from composition subscribes to updates.
    private var usersById by mutableStateOf<Map<String, User>>(emptyMap())

    // ---------------------------------------------------------------- selection & search
    val selectedCreatorId = MutableStateFlow<String?>(null)
    val selectedChatCreatorId = MutableStateFlow<String?>(null)
    val searchQuery = MutableStateFlow("")
    val selectedCategory = MutableStateFlow("")
    val filterCity = MutableStateFlow("")
    val filterMaxBudget = MutableStateFlow<Double?>(null)

    val blockedDatesState = MutableStateFlow<Map<String, List<String>>>(
        if (SupabaseConfig.isConfigured) emptyMap() else demoBlockedDates()
    )

    private fun demoBlockedDates(): Map<String, List<String>> {
        val d = BookingDates.upcomingDays(40)
        return mapOf(
            "demo_arjun" to listOf(d[1], d[14]),
            "demo_riya" to listOf(d[3], d[24]),
            "demo_kabir" to listOf(d[2], d[3]),
            "demo_meera" to listOf(d[6]),
            "demo_zara" to listOf(d[20])
        )
    }

    init {
        val db = AppDatabase.getDatabase(application)
        repository = FokalRepository(
            db.userDao(), db.creatorDao(), db.portfolioDao(), db.bookingDao(),
            db.reviewDao(), db.messageDao(), db.favoriteDao(), db.clientLeadDao()
        )
        searchRepository = SearchRepository(application, supabase)
        viewModelScope.launch { if (isDemoMode) repository.seedDemoDataIfEmpty() }
        viewModelScope.launch { repository.allUsers.collect { users -> usersById = users.associateBy { it.id } } }
        startChatSyncLoop()
        startBlockedDatesSyncLoop()
        startActivityRefreshLoop()
    }

    // ---------------------------------------------------------------- catalogue
    val creatorsList: StateFlow<List<Creator>> = repository.allCreators
        .map { list -> list.filter { it.id != currentUserId.value } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    /** Creator Pro = featured placement. */
    val featuredCreators: StateFlow<List<Creator>> = creatorsList
        .map { list -> list.filter { it.isPro }.sortedByDescending { it.rating } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val topRatedCreators: StateFlow<List<Creator>> = creatorsList
        .map { list -> list.sortedWith(compareByDescending<Creator> { it.isPro }.thenByDescending { it.rating }.thenByDescending { it.reviewCount }) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val inspiration: StateFlow<List<Portfolio>> = repository.recentPortfolio(24)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val filteredCreators: StateFlow<List<Creator>> = combine(
        creatorsList, searchQuery, selectedCategory, filterCity, filterMaxBudget
    ) { list, q, cat, city, budget ->
        list.filter { c ->
            val u = usersById[c.id]
            (q.isBlank() || listOf(u?.name, c.headline, c.bio, c.skillset, u?.city).any { it?.contains(q.trim(), true) == true }) &&
                (cat.isBlank() || c.skillset.contains(cat, true) || c.creatorType.contains(cat, true)) &&
                (city.isBlank() || u?.city?.contains(city, true) == true) &&
                (budget == null || c.startingPrice <= budget)
        }.sortedWith(compareByDescending<Creator> { it.isPro }.thenByDescending { it.rating })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val cities: StateFlow<List<String>> = creatorsList
        .map { list -> list.mapNotNull { usersById[it.id]?.city?.takeIf { c -> c.isNotBlank() } }.distinct().sorted() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    fun creator(id: String): Flow<Creator?> = repository.getCreator(id)
    fun portfolio(id: String): Flow<List<Portfolio>> = repository.getPortfolio(id)
    fun reviews(id: String): Flow<List<Review>> = repository.getReviews(id)

    fun clearFilters() {
        searchQuery.value = ""; selectedCategory.value = ""; filterCity.value = ""; filterMaxBudget.value = null
    }

    fun refreshCreatorDetail(creatorId: String) {
        if (!rest.isAvailable) return
        viewModelScope.launch {
            runCatching {
                rest.select("creators", "id" to "eq.$creatorId").optJSONObject(0)?.let { repository.insertCreator(RemoteMappers.creator(it)) }
                val photos = rest.select("portfolios", "creator_id" to "eq.$creatorId", "order" to "created_at.desc")
                for (i in 0 until photos.length()) repository.insertPortfolio(RemoteMappers.portfolio(photos.getJSONObject(i)))
                val rows = rest.select("reviews", "creator_id" to "eq.$creatorId", "order" to "created_at.desc", "limit" to "50")
                val reviewers = (0 until rows.length()).map { rows.getJSONObject(it).optString("customer_id") }.toSet()
                cacheProfiles(reviewers)
                for (i in 0 until rows.length()) {
                    val r = rows.getJSONObject(i)
                    repository.insertReview(RemoteMappers.review(r, usersById[r.optString("customer_id")]?.name ?: "FokalPoint client"))
                }
            }.onFailure { android.util.Log.w(TAG, "Creator detail refresh failed", it) }
        }
    }

    // ---------------------------------------------------------------- profile lookups
    fun getCreatorNameSync(id: String): String = usersById[id]?.name?.takeIf { it.isNotBlank() } ?: "FokalPoint member"
    fun getCreatorCitySync(id: String): String = usersById[id]?.city.orEmpty()
    fun getCreatorAvatarSync(id: String): String = usersById[id]?.profileImage.orEmpty()

    // ---------------------------------------------------------------- session
    val currentCreatorDetails: StateFlow<Creator?> = currentUserId
        .flatMapLatest { id -> if (id.isEmpty()) flowOf(null) else repository.getCreator(id) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val isPro: StateFlow<Boolean> = currentCreatorDetails.map { it?.isPro == true }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    fun setActiveUser(user: User?) {
        if (user == null) {
            currentUserId.value = ""
            currentUserProfile.value = null
            selectedChatCreatorId.value = null
            return
        }
        if (user.id == currentUserId.value && user == currentUserProfile.value) return
        currentUserRole.value = user.role
        currentUserId.value = user.id
        currentUserProfile.value = user
        viewModelScope.launch {
            ensureCreatorProfile()
            refreshRemoteData()
        }
    }

    /** Creators always have a profile row; new ones start honest: unrated, unverified, not Pro. */
    private suspend fun ensureCreatorProfile() {
        val id = currentUserId.value
        if (id.isEmpty() || currentUserRole.value != "Creator") return
        var creator = repository.getCreatorSync(id)
        if (creator == null && rest.isAvailable) {
            creator = runCatching { rest.select("creators", "id" to "eq.$id").optJSONObject(0)?.let(RemoteMappers::creator) }.getOrNull()
        }
        if (creator == null) {
            creator = Creator(
                id = id, userId = id, creatorType = "Photographer", experienceLevel = "Professional", bio = "",
                languages = "English", equipment = "", rating = 0.0, verified = false, startingPrice = 10000.0,
                instagram = "", website = "", yearsOfExperience = 0, headline = "Photographer on FokalPoint"
            )
            pushCreatorProfile(creator)
        }
        repository.insertCreator(creator)
    }

    private suspend fun pushCreatorProfile(creator: Creator): Boolean {
        if (!rest.isAvailable) return true
        return runCatching { rest.insert("creators", RemoteMappers.creatorRow(creator), upsert = true) }
            .onFailure { android.util.Log.w(TAG, "Creator profile sync failed", it) }
            .isSuccess
    }

    /** "Become a creator": switches the signed-in user's own role. */
    fun switchRole(newRole: String) {
        viewModelScope.launch {
            val user = currentUserProfile.value ?: return@launch
            val role = SupabaseAuth.normalizeRole(newRole)
            val updated = user.copy(role = role)
            if (rest.isAvailable) {
                val ok = runCatching { rest.update("users", JSONObject().put("role", role), "id" to "eq.${user.id}") }.isSuccess
                if (!ok) { toast.value = "Couldn't switch account type. Check your connection."; return@launch }
            }
            repository.insertUser(updated)
            currentUserProfile.value = updated
            currentUserRole.value = role
            supabase.auth.updateRole(role)
            ensureCreatorProfile()
        }
    }

    fun refreshRemoteData() {
        if (!rest.isAvailable || currentUserId.value.isEmpty()) return
        viewModelScope.launch {
            runCatching { searchRepository.refreshCatalog() }.onFailure { android.util.Log.w(TAG, "Catalogue refresh failed", it) }
            runCatching {
                val photos = rest.select("portfolios", "order" to "created_at.desc", "limit" to "60")
                for (i in 0 until photos.length()) repository.insertPortfolio(RemoteMappers.portfolio(photos.getJSONObject(i)))
            }
            runCatching {
                val favs = rest.select("favorites", "customer_id" to "eq.${currentUserId.value}")
                for (i in 0 until favs.length()) repository.addFavorite(currentUserId.value, favs.getJSONObject(i).optString("creator_id"))
            }
            refreshActivity()
        }
    }

    /** Bookings, inbox and (for creators) leads. Runs at sign-in and every 30s. */
    suspend fun refreshActivity() {
        if (!rest.isAvailable) return
        val me = currentUserId.value.ifEmpty { return }
        runCatching {
            val rows = rest.select("messages", "or" to "(sender_id.eq.$me,receiver_id.eq.$me)", "order" to "created_at.desc", "limit" to "300")
            val partners = mutableSetOf<String>()
            for (i in 0 until rows.length()) {
                val m = RemoteMappers.message(rows.getJSONObject(i))
                repository.insertMessage(m)
                partners += m.senderId; partners += m.receiverId
            }
            cacheProfiles(partners - me)
        }.onFailure { android.util.Log.w(TAG, "Inbox refresh failed", it) }
        runCatching {
            val rows = rest.select("bookings", *RemoteQueries.bookingsInvolving(me))
            val parties = mutableSetOf<String>()
            for (i in 0 until rows.length()) {
                val b = RemoteMappers.booking(rows.getJSONObject(i))
                repository.insertBooking(b)
                parties += b.customerId; parties += b.creatorId
            }
            cacheProfiles(parties - me)
            val reviews = rest.select("reviews", "customer_id" to "eq.$me")
            for (i in 0 until reviews.length()) repository.insertReview(RemoteMappers.review(reviews.getJSONObject(i)))
        }.onFailure { android.util.Log.w(TAG, "Bookings refresh failed", it) }
        if (currentUserRole.value == "Creator") {
            refreshLeads()
            runCatching {
                rest.select("creators", "id" to "eq.$me").optJSONObject(0)?.let { repository.insertCreator(RemoteMappers.creator(it)) }
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

    private fun startActivityRefreshLoop() {
        viewModelScope.launch {
            while (true) {
                kotlinx.coroutines.delay(30_000L)
                refreshActivity()
            }
        }
    }

    // ---------------------------------------------------------------- favorites
    val favoriteIds: StateFlow<Set<String>> = currentUserId
        .flatMapLatest { id -> repository.getFavorites(id) }
        .map { list -> list.map { it.creatorId }.toSet() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    val favoriteCreators: StateFlow<List<Creator>> = combine(creatorsList, favoriteIds) { list, ids -> list.filter { it.id in ids } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    fun toggleFavorite(creatorId: String) {
        val me = currentUserId.value.ifEmpty { return }
        val isFav = creatorId in favoriteIds.value
        viewModelScope.launch {
            if (isFav) repository.removeFavorite(me, creatorId) else repository.addFavorite(me, creatorId)
            if (rest.isAvailable) {
                runCatching {
                    if (isFav) rest.delete("favorites", "customer_id" to "eq.$me", "creator_id" to "eq.$creatorId")
                    else rest.insert("favorites", JSONObject().put("customer_id", me).put("creator_id", creatorId), upsert = true)
                }.onFailure {
                    if (isFav) repository.addFavorite(me, creatorId) else repository.removeFavorite(me, creatorId)
                    toast.value = "Couldn't update favourites."
                }
            }
        }
    }

    // ---------------------------------------------------------------- bookings
    val bookingsList: StateFlow<List<Booking>> = combine(currentUserId, currentUserRole) { id, role -> id to role }
        .flatMapLatest { (id, role) ->
            if (id.isEmpty()) flowOf(emptyList())
            else if (role == "Creator") repository.getBookingsForCreator(id) else repository.getBookingsForCustomer(id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val creatorBookings: StateFlow<List<Booking>> = currentUserId
        .flatMapLatest { id -> if (id.isEmpty()) flowOf(emptyList()) else repository.getBookingsForCreator(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val reviewedBookingIds: StateFlow<Set<Long>> = currentUserId
        .flatMapLatest { id -> repository.reviewedBookingIds(id) }
        .map { it.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptySet())

    fun activeCreatorBookings(creatorId: String): Flow<List<Booking>> = repository.getBookingsForCreator(creatorId)

    /** Sends a booking request. Returns via [onDone] whether it was placed. */
    fun requestBooking(creatorId: String, pkg: ShootPackage, date: String, time: String, notes: String, eventType: String, onDone: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            val creator = repository.getCreatorSync(creatorId)
            if (creator == null) { onDone(false); return@launch }
            val draft = Booking(
                customerId = currentUserId.value, creatorId = creatorId, eventType = eventType,
                date = date, time = time, hours = pkg.hours, price = pkg.price(creator.startingPrice),
                status = "Pending", paymentStatus = "Pending", packageName = pkg.title, notes = notes.trim()
            )
            val stored = if (rest.isAvailable) {
                try {
                    rest.insert("bookings", RemoteMappers.bookingRow(draft))?.let(RemoteMappers::booking) ?: draft
                } catch (e: Exception) {
                    toast.value = e.message?.takeIf { it.isNotBlank() } ?: "Couldn't send your request. Please try again."
                    onDone(false); return@launch
                }
            } else draft
            repository.insertBooking(stored)
            onDone(true)
        }
    }

    /** Legacy entry point kept for tests and quick bookings. */
    fun createBooking(eventType: String, date: String, time: String, hours: Int, packageType: String, totalCost: Double) {
        val creatorId = selectedCreatorId.value ?: return
        val pkg = ShootPackage.entries.firstOrNull { it.title == packageType } ?: ShootPackage.Essential
        requestBooking(creatorId, pkg, date, time, "", eventType)
    }

    /** Creator accepts / declines / completes, or the customer cancels. The server enforces who may do what. */
    fun updateBookingStatus(bookingId: Long, newStatus: String) {
        viewModelScope.launch {
            val booking = repository.getBookingByIdSync(bookingId) ?: return@launch
            if (rest.isAvailable) {
                val patch = JSONObject().put("status", newStatus)
                val ok = runCatching { rest.update("bookings", patch, "id" to "eq.$bookingId") }
                    .onFailure { toast.value = it.message ?: "Couldn't update this booking." }.isSuccess
                if (!ok) return@launch
            }
            repository.updateBookingStatus(bookingId, newStatus)
            if (newStatus == "Completed" && booking.paymentStatus != "Paid" && booking.creatorId == currentUserId.value) {
                repository.updatePaymentStatus(bookingId, "Paid")
            }
        }
    }

    /** Creator confirms the customer's payment arrived. Only the creator may do this (server-enforced). */
    fun confirmBookingPayment(bookingId: Long, paymentStatus: String = "Paid", newStatus: String = "Confirmed") {
        viewModelScope.launch {
            val booking = repository.getBookingByIdSync(bookingId) ?: return@launch
            if (booking.creatorId != currentUserId.value) return@launch
            if (rest.isAvailable) {
                val ok = runCatching {
                    rest.update("bookings", JSONObject().put("status", newStatus).put("payment_status", "Paid"), "id" to "eq.$bookingId")
                }.onFailure { toast.value = it.message ?: "Couldn't confirm payment." }.isSuccess
                if (!ok) return@launch
            }
            repository.updatePaymentStatus(bookingId, "Paid")
            repository.updateBookingStatus(bookingId, newStatus)
        }
    }

    data class PaymentDetails(val upiId: String, val payeeName: String, val amount: Double)

    /** UPI details for an accepted booking (only visible to that booking's customer). */
    fun loadPaymentDetails(bookingId: Long, onResult: (PaymentDetails?) -> Unit) {
        viewModelScope.launch {
            val booking = repository.getBookingByIdSync(bookingId)
            if (!rest.isAvailable) {
                // Demo: a sample UPI address so the full flow can be tried.
                onResult(booking?.let { PaymentDetails("demo.creator@okaxis", getCreatorNameSync(it.creatorId), it.price) })
                return@launch
            }
            val row = runCatching {
                JSONArray(rest.rpc("get_payment_details", JSONObject().put("p_booking_id", bookingId))).optJSONObject(0)
            }.getOrNull()
            val upi = row?.optString("upi_id")?.takeIf { it.isNotBlank() && it != "null" }
            onResult(upi?.let { PaymentDetails(it, row.optString("payee_name"), row.optDouble("amount")) })
        }
    }

    /** Customer tells the creator they've paid (creator then confirms receipt). */
    fun notifyPaymentSent(booking: Booking) {
        selectedChatCreatorId.value = booking.creatorId
        sendMessage("I've sent ${com.fokalpoint.app.ui.components.formatInr(booking.price)} via UPI for our ${booking.packageName.ifBlank { booking.eventType }} shoot on ${BookingDates.chipLabel(booking.date)}. Please confirm when received!")
    }

    // ---------------------------------------------------------------- reviews
    fun submitReview(booking: Booking, rating: Int, comment: String, onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            val local = Review(
                bookingId = booking.id, customerId = currentUserId.value, creatorId = booking.creatorId,
                rating = rating.toDouble(), review = comment.trim(), customerName = currentUserProfile.value?.name ?: "You"
            )
            if (rest.isAvailable) {
                val ok = runCatching {
                    rest.insert("reviews", JSONObject().put("booking_id", booking.id).put("creator_id", booking.creatorId)
                        .put("customer_id", currentUserId.value).put("rating", rating).put("comment", comment.trim()))
                }.onFailure { toast.value = it.message ?: "Couldn't post your review." }.isSuccess
                if (!ok) { onDone(false); return@launch }
                refreshCreatorDetail(booking.creatorId)
            } else {
                // Demo: keep the sample creator's rating honest-looking.
                repository.getCreatorSync(booking.creatorId)?.let { c ->
                    val n = c.reviewCount + 1
                    repository.insertCreator(c.copy(reviewCount = n, rating = ((c.rating * c.reviewCount) + rating) / n))
                }
            }
            repository.insertReview(local)
            onDone(true)
        }
    }

    // ---------------------------------------------------------------- messaging
    val chatMessages: StateFlow<List<Message>> = combine(currentUserId, selectedChatCreatorId) { me, partner -> me to partner }
        .flatMapLatest { (me, partner) -> if (partner == null) flowOf(emptyList()) else repository.getChatMessages(me, partner) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val conversations: StateFlow<List<Message>> = currentUserId
        .flatMapLatest { id -> if (id.isEmpty()) flowOf(emptyList()) else repository.getConversations(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val chatPartners: StateFlow<List<String>> = currentUserId
        .flatMapLatest { id -> repository.getChatPartners(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    fun sendMessage(text: String) {
        val partner = selectedChatCreatorId.value ?: return
        val body = text.trim()
        if (body.isEmpty()) return
        viewModelScope.launch {
            val draft = Message(senderId = currentUserId.value, receiverId = partner, message = body)
            if (!rest.isAvailable) {
                repository.insertMessage(draft)
                if (partner.startsWith("demo_")) simulateDemoReply(partner, body)
                return@launch
            }
            try {
                val row = rest.insert("messages", JSONObject().put("sender_id", draft.senderId).put("receiver_id", partner).put("message", body))
                repository.insertMessage(row?.let(RemoteMappers::message) ?: draft)
            } catch (e: Exception) {
                toast.value = "Message not sent. Check your connection and try again."
            }
        }
    }

    /** Demo mode only: sample creators reply so the chat can be explored offline. */
    private fun simulateDemoReply(creatorId: String, text: String) {
        viewModelScope.launch {
            kotlinx.coroutines.delay(1500)
            val name = getCreatorNameSync(creatorId).substringBefore(' ')
            val reply = when {
                text.contains("price", true) || text.contains("cost", true) || text.contains("budget", true) ->
                    "My packages start at ${com.fokalpoint.app.ui.components.formatInr(repository.getCreatorSync(creatorId)?.startingPrice ?: 10000.0)}. Happy to tailor one for you!"
                text.contains("paid", true) || text.contains("upi", true) -> "Received, thank you! I'll confirm it in the app now. 🙌"
                else -> "Hi! $name here — thanks for reaching out. Tell me a little about your shoot and the date you have in mind."
            }
            repository.insertMessage(Message(senderId = creatorId, receiverId = currentUserId.value, message = reply))
        }
    }

    private fun startChatSyncLoop() {
        viewModelScope.launch {
            while (true) {
                kotlinx.coroutines.delay(3000L)
                val me = currentUserId.value
                val partner = selectedChatCreatorId.value ?: continue
                if (me.isEmpty() || !rest.isAvailable) continue
                runCatching {
                    val rows = rest.select("messages", *RemoteQueries.conversation(me, partner))
                    for (i in 0 until rows.length()) repository.insertMessage(RemoteMappers.message(rows.getJSONObject(i)))
                }
            }
        }
    }

    // ---------------------------------------------------------------- availability
    fun toggleBlockedDate(date: String) {
        val me = currentUserId.value.ifEmpty { return }
        val current = blockedDatesState.value[me].orEmpty()
        val blocking = date !in current
        blockedDatesState.value = blockedDatesState.value + (me to if (blocking) current + date else current - date)
        if (!rest.isAvailable) return
        viewModelScope.launch {
            runCatching {
                if (blocking) rest.insert("blocked_dates", JSONObject().put("creator_id", me).put("blocked_date", date), upsert = true)
                else rest.delete("blocked_dates", "creator_id" to "eq.$me", "blocked_date" to "eq.$date")
            }.onFailure { toast.value = "Couldn't update availability." }
        }
    }

    private fun startBlockedDatesSyncLoop() {
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

    // ---------------------------------------------------------------- creator studio
    data class ProfileDraft(
        val name: String, val city: String, val headline: String, val bio: String, val startingPrice: Double,
        val skillset: String, val creatorType: String, val yearsOfExperience: Int, val instagram: String, val upiId: String
    )

    val myUpiId = MutableStateFlow("")

    fun loadMyUpiId() {
        val me = currentUserId.value.ifEmpty { return }
        if (!rest.isAvailable) return
        viewModelScope.launch {
            runCatching { rest.select("creator_private", "creator_id" to "eq.$me").optJSONObject(0)?.optString("upi_id") }
                .getOrNull()?.takeIf { it != "null" }?.let { myUpiId.value = it }
        }
    }

    fun saveCreatorProfile(draft: ProfileDraft, onDone: (Boolean) -> Unit) {
        val me = currentUserId.value.ifEmpty { return }
        viewModelScope.launch {
            val existing = repository.getCreatorSync(me) ?: return@launch onDone(false)
            val updated = existing.copy(
                headline = draft.headline.trim(), bio = draft.bio.trim(), startingPrice = draft.startingPrice,
                skillset = draft.skillset, creatorType = draft.creatorType, yearsOfExperience = draft.yearsOfExperience,
                instagram = draft.instagram.trim()
            )
            val user = (currentUserProfile.value ?: repository.getUser(me))?.copy(name = draft.name.trim(), city = draft.city.trim())
            if (rest.isAvailable) {
                val ok = runCatching {
                    rest.insert("creators", RemoteMappers.creatorRow(updated), upsert = true)
                    rest.update("users", JSONObject().put("name", draft.name.trim()).put("city", draft.city.trim()), "id" to "eq.$me")
                    if (draft.upiId.isNotBlank()) {
                        rest.insert("creator_private", JSONObject().put("creator_id", me).put("upi_id", draft.upiId.trim()), upsert = true)
                    }
                }.onFailure { toast.value = it.message ?: "Couldn't save your profile." }.isSuccess
                if (!ok) return@launch onDone(false)
            }
            repository.insertCreator(updated)
            user?.let { repository.insertUser(it); currentUserProfile.value = it }
            myUpiId.value = draft.upiId.trim()
            onDone(true)
        }
    }

    val myPortfolio: StateFlow<List<Portfolio>> = currentUserId
        .flatMapLatest { id -> if (id.isEmpty()) flowOf(emptyList()) else repository.getPortfolio(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val uploading = MutableStateFlow(false)

    /** Adds a photo picked from the gallery. Free profiles hold 12 photos; Pro is unlimited (server-enforced). */
    fun addPortfolioPhoto(uri: Uri, category: String) {
        val me = currentUserId.value.ifEmpty { return }
        if (!isPro.value && myPortfolio.value.size >= 12) {
            toast.value = "Free profiles can show up to 12 photos. Upgrade to Creator Pro for an unlimited portfolio."
            return
        }
        viewModelScope.launch {
            uploading.value = true
            try {
                val url = if (rest.isAvailable) {
                    val bytes = withContext(Dispatchers.IO) { compressForUpload(uri) }
                    val publicUrl = rest.uploadPublic("portfolios", "${UUID.randomUUID()}.jpg", bytes, "image/jpeg")
                    val row = rest.insert("portfolios", JSONObject().put("creator_id", me).put("title", category)
                        .put("category", category).put("media_url", publicUrl).put("media_type", "IMAGE"))
                    row?.let { repository.insertPortfolio(RemoteMappers.portfolio(it)) }
                    null
                } else {
                    // Demo: keep a private copy so the photo survives the picker's permission grant.
                    withContext(Dispatchers.IO) { copyToAppStorage(uri) }
                }
                if (url != null) repository.insertPortfolio(Portfolio(creatorId = me, title = category, category = category, mediaUrl = url))
                toast.value = "Added to your portfolio"
            } catch (e: Exception) {
                toast.value = e.message?.takeIf { it.isNotBlank() } ?: "Upload failed. Please try again."
            } finally {
                uploading.value = false
            }
        }
    }

    fun deletePortfolioPhoto(photo: Portfolio) {
        viewModelScope.launch {
            if (rest.isAvailable) {
                val ok = runCatching { rest.delete("portfolios", "id" to "eq.${photo.id}") }.isSuccess
                if (!ok) { toast.value = "Couldn't remove this photo."; return@launch }
            }
            repository.deletePortfolio(photo.id)
        }
    }

    private fun compressForUpload(uri: Uri): ByteArray {
        val resolver = getApplication<Application>().contentResolver
        val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { android.graphics.BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= 1600 || bounds.outHeight / (sample * 2) >= 1600) sample *= 2
        val bitmap = resolver.openInputStream(uri)?.use {
            android.graphics.BitmapFactory.decodeStream(it, null, android.graphics.BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: throw IllegalStateException("Couldn't read that image.")
        return java.io.ByteArrayOutputStream().use { out ->
            bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, out)
            out.toByteArray()
        }
    }

    private fun copyToAppStorage(uri: Uri): String {
        val app = getApplication<Application>()
        val dir = java.io.File(app.filesDir, "portfolio").apply { mkdirs() }
        val file = java.io.File(dir, "${UUID.randomUUID()}.jpg")
        app.contentResolver.openInputStream(uri)?.use { input -> file.outputStream().use { input.copyTo(it) } }
        return Uri.fromFile(file).toString()
    }

    // ---------------------------------------------------------------- leads (shoot alerts)
    val leads: StateFlow<List<ClientLead>> = repository.allLeads
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())
    /** Total open alerts on the platform (free creators see 3 of them). */
    val leadsTotal = MutableStateFlow(0)

    private suspend fun refreshLeads() {
        runCatching {
            val rows = JSONArray(rest.rpc("list_shoot_alerts", JSONObject()))
            val list = (0 until rows.length()).map { RemoteMappers.lead(rows.getJSONObject(it)) }
            leadsTotal.value = rows.optJSONObject(0)?.optInt("total_available") ?: 0
            repository.replaceLeads(list)
        }.onFailure { android.util.Log.w(TAG, "Leads refresh failed", it) }
    }

    fun refreshLeadsNow() { viewModelScope.launch { if (rest.isAvailable) refreshLeads() } }

    fun postShootAlert(eventType: String, location: String, budget: Double, timeframe: String, description: String, onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            val me = currentUserId.value
            if (rest.isAvailable) {
                val ok = runCatching {
                    rest.insert("shoot_alerts", JSONObject().put("customer_id", me).put("event_type", eventType)
                        .put("location", location).put("city_id", location.substringBefore(',').trim())
                        .put("budget", budget).put("timeframe", timeframe).put("description", description))
                }.onFailure { toast.value = it.message ?: "Couldn't post your request." }.isSuccess
                if (!ok) return@launch onDone(false)
            } else {
                repository.insertLead(ClientLead(customerId = me, customerName = currentUserProfile.value?.name ?: "You",
                    customerEmail = "", eventType = eventType, location = location, budget = budget,
                    description = description, dateDetail = timeframe))
            }
            onDone(true)
        }
    }

    /** Creator responds to a lead by opening a chat with the customer. */
    fun respondToLead(lead: ClientLead) {
        selectedChatCreatorId.value = lead.customerId
        if (usersById[lead.customerId] == null) {
            viewModelScope.launch {
                repository.insertUser(User(lead.customerId, lead.customerName, "", "", "Customer", "", lead.location, "", ""))
            }
        }
    }

    // ---------------------------------------------------------------- earnings
    data class StudioStats(val earned: Double, val upcoming: Double, val pendingRequests: Int, val completed: Int, val monthly: List<Pair<String, Double>>)

    val studioStats: StateFlow<StudioStats> = creatorBookings.map { list ->
        val paid = list.filter { it.paymentStatus == "Paid" || it.status == "Completed" }
        val cal = java.util.Calendar.getInstance().apply { add(java.util.Calendar.MONTH, -5) }
        val keyFmt = java.text.SimpleDateFormat("yyyy-MM", java.util.Locale.US)
        val labelFmt = java.text.SimpleDateFormat("MMM", java.util.Locale.getDefault())
        val monthly = (0 until 6).map {
            val key = keyFmt.format(cal.time)
            val label = labelFmt.format(cal.time)
            cal.add(java.util.Calendar.MONTH, 1)
            label to paid.filter { b -> b.date.startsWith(key) }.sumOf { b -> b.price }
        }
        StudioStats(
            earned = paid.sumOf { it.price },
            upcoming = list.filter { it.status == "Accepted" || it.status == "Confirmed" }.sumOf { it.price },
            pendingRequests = list.count { it.status == "Pending" },
            completed = list.count { it.status == "Completed" },
            monthly = monthly
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), StudioStats(0.0, 0.0, 0, 0, emptyList()))

    // ---------------------------------------------------------------- Pro (demo preview)
    /** Demo mode only: lets testers preview Pro features without Play Billing. */
    fun previewProInDemo() {
        if (!isDemoMode) return
        viewModelScope.launch {
            repository.getCreatorSync(currentUserId.value)?.let {
                repository.insertCreator(it.copy(proUntil = System.currentTimeMillis() + 30L * 86_400_000))
            }
        }
    }

    /** Called after the server verified a Play purchase. */
    fun onProVerified(proUntilMillis: Long) {
        viewModelScope.launch {
            repository.getCreatorSync(currentUserId.value)?.let { repository.insertCreator(it.copy(proUntil = proUntilMillis)) }
            refreshLeads()
        }
    }

    // ---------------------------------------------------------------- location
    fun detectUserCity(onResult: (LocationService.CityInfo?) -> Unit) {
        viewModelScope.launch {
            onResult(runCatching { LocationService(getApplication()).detectUserCity() }.getOrNull())
        }
    }
}
