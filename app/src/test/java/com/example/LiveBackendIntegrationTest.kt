package com.example

import android.app.Application
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.example.data.database.AppDatabase
import com.example.data.repository.SearchRepository
import com.example.data.repository.SessionManager
import com.example.data.supabase.RemoteMappers
import com.example.data.supabase.RemoteQueries
import com.example.data.supabase.SignUpResult
import com.example.data.supabase.SupabaseAuth
import com.example.data.supabase.SupabaseClient
import com.example.data.supabase.SupabaseConfig
import com.example.data.supabase.SupabaseException
import com.example.data.model.Booking
import com.example.data.model.PayoutMethod
import com.example.data.model.PayoutMethodStatus
import com.example.data.model.PayoutMethodType
import com.example.ui.viewmodel.AuthState
import com.example.ui.viewmodel.AuthViewModel
import com.example.ui.viewmodel.FokalViewModel
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper
import java.net.URL
import java.util.UUID

/**
 * Live-mode validation against a local Supabase stand-in (real PostgREST + Postgres with
 * all migrations and RLS; strict fake GoTrue issuing real JWTs). Skipped unless
 * FOKAL_LIVE_URL and FOKAL_LIVE_KEY are set, e.g.:
 *
 *   FOKAL_LIVE_URL=http://127.0.0.1:3998 FOKAL_LIVE_KEY=<anon jwt> ./gradlew testDebugUnitTest
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LiveBackendIntegrationTest {

    private val liveUrl: String? = System.getenv("FOKAL_LIVE_URL")
    private val liveKey: String? = System.getenv("FOKAL_LIVE_KEY")
    private val run = UUID.randomUUID().toString().take(8)
    private lateinit var app: Application
    private lateinit var client: SupabaseClient

    @Before
    fun setUp() {
        assumeTrue("Local Supabase not configured", liveUrl != null && liveKey != null)
        SupabaseConfig.testOverride = liveUrl!! to liveKey!!
        app = ApplicationProvider.getApplicationContext()
        client = SupabaseClient.get(app)
        client.sessions.clearSession()
    }

    @After
    fun tearDown() {
        SupabaseConfig.testOverride = null
    }

    private val auth get() = client.auth
    private val rest get() = client.rest

    private fun email(who: String) = "$who.$run@fokal.test"

    /** Signs in as [who] (the single app session switches identity). */
    private fun actAs(who: String) = runBlocking { auth.signIn(email(who), "Passw0rd!$run") }

    private fun <T : Any> await(what: String, timeoutMs: Long = 15_000, probe: () -> T?): T {
        val end = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < end) {
            ShadowLooper.idleMainLooper()
            probe()?.let { return it }
            Thread.sleep(50)
        }
        throw AssertionError("Timed out waiting for: $what")
    }

    private inline fun expectServerError(step: String, contains: String, block: () -> Unit) {
        try {
            block()
            fail("$step: expected the server to reject this")
        } catch (e: SupabaseException) {
            assertTrue("$step: unexpected message '${e.message}'", e.message!!.contains(contains, ignoreCase = true))
        }
    }

    @Test
    fun liveModeEndToEnd() = runBlocking {
        val pw = "Passw0rd!$run"

        // ---- Auth: sign-up (role casing), wrong password, sign-in
        val creator = (auth.signUp(email("creator"), pw, "Priya Lens $run", role = "creator") as SignUpResult.SignedIn).session
        assertEquals("Creator", creator.role)
        assertFalse(creator.isDemo)
        assertTrue("rest must be live once signed in", rest.isAvailable)
        val customer = (auth.signUp(email("customer"), pw, "Cara $run", role = "Customer") as SignUpResult.SignedIn).session
        try {
            auth.signIn(email("customer"), "wrong-password")
            fail("wrong password accepted")
        } catch (e: SupabaseException) {
            assertEquals("Incorrect email or password.", e.message)
        }
        expectServerError("duplicate sign-up", "already exists") { auth.signUp(email("customer"), pw, "X", "Customer") }

        // ---- Email confirmation (OTP) flow
        val pending = auth.signUp("confirm.$run@fokal.test", pw, "Otto", "Customer")
        assertTrue(pending is SignUpResult.ConfirmationRequired)
        expectServerError("unconfirmed sign-in", "confirm your email") { auth.signIn("confirm.$run@fokal.test", pw) }
        expectServerError("bad otp", "invalid or has expired") { auth.verifyEmailOtp("confirm.$run@fokal.test", "000000") }
        assertEquals("confirm.$run@fokal.test", auth.verifyEmailOtp("confirm.$run@fokal.test", "246810").email)
        auth.sendPasswordReset(email("customer"))

        // ---- OAuth redirect completion (implicit-flow fragment)
        val redirect = JSONObject(URL("$liveUrl/auth/v1/_test/oauth_redirect?email=g.$run@fokal.test").openConnection()
            .apply { setRequestProperty("apikey", liveKey) }.getInputStream().bufferedReader().readText()).getString("redirect")
        val oauth = auth.completeRedirect(Uri.parse(redirect))
        assertEquals("Google User", oauth.name)
        assertEquals("https://img/g.png", oauth.avatarUrl)

        // ---- Password recovery: link signs in, new password is set and works
        val recoveryLink = JSONObject(URL("$liveUrl/auth/v1/_test/oauth_redirect?email=${email("customer")}&type=recovery").openConnection()
            .apply { setRequestProperty("apikey", liveKey) }.getInputStream().bufferedReader().readText()).getString("redirect")
        assertEquals("recovery", SupabaseAuth.parseRedirectParams(Uri.parse(recoveryLink).fragment!!)["type"])
        auth.completeRedirect(Uri.parse(recoveryLink))
        auth.updatePassword("N3wPassword!$run")
        expectServerError("old password after reset", "Incorrect email or password") { auth.signIn(email("customer"), pw) }
        auth.signIn(email("customer"), "N3wPassword!$run")
        auth.updatePassword(pw) // restore for the rest of the test

        // ---- Creator: profile upsert (server ignores self-assigned rating/verified), availability
        actAs("creator")
        val creatorProfile = SearchRepository.creatorFromRow(
            JSONObject().put("id", creator.userId).put("bio", "Weddings in Mumbai $run")
                .put("starting_price", 25000).put("skillset", "Photographer, Wedding").put("rating", 5).put("verified", true)
        )
        rest.insert("creators", RemoteMappers.creatorRow(creatorProfile), upsert = true)
        rest.insert("creators", RemoteMappers.creatorRow(creatorProfile.copy(bio = "Updated $run")), upsert = true)
        val storedCreator = rest.select("creators", "id" to "eq.${creator.userId}").getJSONObject(0)
        assertEquals("Updated $run", storedCreator.getString("bio"))
        assertEquals(0.0, storedCreator.getDouble("rating"), 0.0)
        assertFalse(storedCreator.getBoolean("verified"))
        rest.update("users", JSONObject().put("city", "Mumbai"), "id" to "eq.${creator.userId}")
        val blocked = JSONObject().put("creator_id", creator.userId).put("blocked_date", "2027-01-26")
        rest.insert("blocked_dates", blocked, upsert = true)
        rest.insert("blocked_dates", blocked, upsert = true) // idempotent re-block
        assertEquals(1, rest.select("blocked_dates", "creator_id" to RemoteQueries.idIn(listOf(creator.userId))).length())

        // ---- Payout method: upsert, server forces verification status, default toggling
        val methodId = UUID.randomUUID().toString()
        val method = PayoutMethod(methodId, creator.userId, PayoutMethodType.UPI, "Priya", null, null, null,
            "priya@okaxis", true, PayoutMethodStatus.ACTIVE, "")
        rest.insert("payout_methods", RemoteMappers.payoutMethodRow(method), upsert = true)
        val storedMethod = RemoteMappers.payoutMethod(rest.select("payout_methods", "id" to "eq.$methodId").getJSONObject(0))
        assertEquals(PayoutMethodStatus.PENDING_VERIFICATION, storedMethod.status)
        rest.update("payout_methods", JSONObject().put("is_default", false), "user_id" to "eq.${creator.userId}")
        rest.update("payout_methods", JSONObject().put("is_default", true), "id" to "eq.$methodId")

        // ---- Customer: search via the real repository (parameterized RPC) + Room caching
        actAs("customer")
        val search = SearchRepository(app, client)
        val found = search.searchCreatorsGlobal(query = "Updated $run", city = "mumbai", eventType = "wedding")
        assertEquals(listOf(creator.userId), found.map { it.id })
        assertEquals("Priya Lens $run", AppDatabase.getDatabase(app).userDao().getUserById(creator.userId)?.name)
        assertTrue(search.searchCreatorsGlobal(query = "' OR 1=1; --").none { it.id == creator.userId })

        // ---- Customer books: server forces Pending, blocked date rejected
        val draft = Booking(customerId = customer.userId, creatorId = creator.userId, eventType = "Signature Package (Wedding)",
            date = "2027-02-14", time = "16:30", hours = 6, price = 60000.0, status = "Confirmed", paymentStatus = "Paid")
        val booking = RemoteMappers.booking(rest.insert("bookings", RemoteMappers.bookingRow(draft))!!)
        assertTrue(booking.id > 0)
        assertEquals("Pending" to "Pending", booking.status to booking.paymentStatus)
        assertEquals("16:30", booking.time)
        expectServerError("blocked date", "not available") {
            rest.insert("bookings", RemoteMappers.bookingRow(draft.copy(date = "2027-01-26")))
        }
        expectServerError("customer self-confirm", "Only the creator") {
            rest.update("bookings", JSONObject().put("status", "Confirmed"), "id" to "eq.${booking.id}")
        }
        expectServerError("customer marks paid", "Only the creator") {
            rest.update("bookings", JSONObject().put("payment_status", "Paid"), "id" to "eq.${booking.id}")
        }

        // ---- Chat: send + conversation filter; RLS blocks spoofing
        val sent = RemoteMappers.message(rest.insert("messages", JSONObject()
            .put("sender_id", customer.userId).put("receiver_id", creator.userId).put("message", "Hi! \"Quotes\" & émojis 📸\nnew line"))!!)
        assertEquals("Hi! \"Quotes\" & émojis 📸\nnew line", sent.message)
        expectServerError("spoofed sender", "row-level security") {
            rest.insert("messages", JSONObject().put("sender_id", creator.userId).put("receiver_id", customer.userId).put("message", "fake"))
        }

        // ---- Shoot alert, exactly as FokalViewModel.postShootAlert builds it
        rest.insert("shoot_alerts", JSONObject().put("customer_id", customer.userId).put("event_type", "Maternity")
            .put("location", "Bandra, Mumbai").put("city_id", "Mumbai").put("budget", 30000.0)
            .put("timeframe", "Next month").put("description", "Golden hour").put("additional_details", ""))

        // ---- Creator side: sees booking + conversation, accepts, completes + confirms payment
        actAs("creator")
        val creatorBookings = rest.select("bookings", *RemoteQueries.bookingsInvolving(creator.userId))
        assertEquals(1, creatorBookings.length())
        val convo = rest.select("messages", *RemoteQueries.conversation(creator.userId, customer.userId))
        assertEquals(1, convo.length())
        assertTrue(rest.select("shoot_alerts", "status" to "eq.pending").length() >= 1)
        rest.update("bookings", JSONObject().put("status", "Accepted"), "id" to "eq.${booking.id}")
        rest.update("bookings", JSONObject().put("status", "Completed").put("payment_status", "Paid"), "id" to "eq.${booking.id}")
        val done = RemoteMappers.booking(rest.select("bookings", "id" to "eq.${booking.id}").getJSONObject(0))
        assertEquals("Completed" to "Paid", done.status to done.paymentStatus)

        // ---- An outsider sees none of it
        auth.signUp(email("outsider"), pw, "Eve", "Customer")
        assertEquals(0, rest.select("bookings", *RemoteQueries.bookingsInvolving(creator.userId)).length())
        assertEquals(0, rest.select("messages", *RemoteQueries.conversation(creator.userId, customer.userId)).length())

        // ---- Token refresh: an expired access token is refreshed transparently
        actAs("customer")
        val stale = client.sessions.getSession()!!
        client.sessions.saveSession(stale.copy(expiresAt = System.currentTimeMillis() - 1000))
        assertEquals(1, rest.select("bookings", *RemoteQueries.bookingsInvolving(customer.userId)).length())
        val refreshed = client.sessions.getSession()!!
        assertTrue("refresh token must rotate", refreshed.refreshToken != stale.refreshToken)
        assertTrue(refreshed.accessToken != stale.accessToken && !refreshed.expiresSoon())
        // A revoked/used refresh token signs the user out instead of looping.
        client.sessions.saveSession(refreshed.copy(expiresAt = 0, refreshToken = "revoked"))
        expectServerError("revoked refresh", "Not signed in") { rest.select("bookings") }
        assertEquals(null, client.sessions.getSession())
        actAs("customer")
        auth.signOut()
        assertFalse(rest.isAvailable)
    }

    @Test
    fun viewModelsWorkAgainstLiveBackend() {
        val pw = "Passw0rd!$run"
        // Creator exists with a profile so the customer can book them.
        val creatorId = runBlocking {
            val s = (auth.signUp(email("vmcreator"), pw, "Vik Studio $run", "Creator") as SignUpResult.SignedIn).session
            rest.insert("creators", JSONObject().put("id", s.userId).put("bio", "VM $run").put("skillset", "Photographer"), upsert = true)
            auth.signOut()
            s.userId
        }

        // AuthViewModel: live sign-up -> Authenticated, profile synced from public.users
        val authVm = AuthViewModel(app)
        await("initial state") { authVm.authState.value.takeIf { it != AuthState.Initializing } }
        authVm.signUpWithEmail(email("vmcustomer"), pw, "Nia $run", "customer")
        val state = await("sign-up result") { authVm.authState.value.takeIf { it != AuthState.Loading && it != AuthState.Unauthenticated } }
        assertEquals(AuthState.Authenticated, state)
        val user = authVm.user.value!!
        assertEquals("Nia $run", user.name)
        assertEquals("Customer", user.role)

        // FokalViewModel: signed-in identity drives sync; booking + message go to the server
        val vm = FokalViewModel(app)
        vm.setActiveUser(user)
        await("creator catalogue refreshed into Room") {
            runBlocking { AppDatabase.getDatabase(app).creatorDao().getCreatorByIdSync(creatorId) }
        }
        // Names reach the UI through the Room users Flow (asynchronous), so wait for it.
        await("creator name in profile cache") {
            vm.getCreatorNameSync(creatorId).takeIf { it == "Vik Studio $run" }
        }

        vm.selectedCreatorId.value = creatorId
        vm.createBooking("Portrait", "2027-03-03", "10:00", 2, "Essential", 12000.0)
        val serverBooking = await("booking on server") {
            runBlocking { rest.select("bookings", *RemoteQueries.bookingsInvolving(user.id)) }.optJSONObject(0)
        }
        assertEquals("Pending", serverBooking.getString("status"))
        val localBooking = await("booking cached locally with server id") {
            runBlocking { AppDatabase.getDatabase(app).bookingDao().getBookingById(serverBooking.getLong("id")) }
        }
        assertEquals(12000.0, localBooking.price, 0.0)

        vm.selectedChatCreatorId.value = creatorId
        vm.sendMessage("Is March 3rd free?")
        await("message on server") {
            runBlocking { rest.select("messages", *RemoteQueries.conversation(user.id, creatorId)) }.optJSONObject(0)
        }
        // No simulated auto-reply may appear in live mode.
        Thread.sleep(2500)
        ShadowLooper.idleMainLooper()
        val convo = runBlocking { rest.select("messages", *RemoteQueries.conversation(user.id, creatorId)) }
        assertEquals(1, convo.length())

        // Customer "confirming payment" locally is a no-op in live mode.
        vm.confirmBookingPayment(serverBooking.getLong("id"), "Paid")
        Thread.sleep(500); ShadowLooper.idleMainLooper()
        assertEquals("Pending", runBlocking { AppDatabase.getDatabase(app).bookingDao().getBookingById(serverBooking.getLong("id")) }!!.paymentStatus)

        authVm.signOut()
        await("signed out") { authVm.authState.value.takeIf { it == AuthState.Unauthenticated } }

        // The creator (who never opened this chat) sees the new request and message.
        authVm.signInWithEmail(email("vmcreator"), pw)
        await("creator signed in") { authVm.authState.value.takeIf { it == AuthState.Authenticated } }
        val creatorUser = authVm.user.value!!
        assertEquals("Creator", creatorUser.role)
        val creatorVm = FokalViewModel(app)
        // Subscribe like the UI does (these StateFlows are WhileSubscribed).
        val uiScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main)
        uiScope.launch { creatorVm.creatorBookings.collect {} }
        uiScope.launch { creatorVm.chatPartners.collect {} }
        creatorVm.setActiveUser(creatorUser)
        runBlocking { creatorVm.refreshActivity() }
        await("incoming booking visible to creator") {
            creatorVm.creatorBookings.value.firstOrNull { it.id == serverBooking.getLong("id") }
        }
        await("new customer appears in creator inbox") {
            creatorVm.chatPartners.value.firstOrNull { it == user.id }
        }
        assertEquals("Nia $run", creatorVm.getCreatorNameSync(user.id))

        // Creator accepts through the ViewModel; the server records it.
        creatorVm.updateBookingStatus(serverBooking.getLong("id"), "Accepted")
        await("accepted on server") {
            runBlocking { rest.select("bookings", "id" to "eq.${serverBooking.getLong("id")}") }
                .optJSONObject(0)?.takeIf { it.getString("status") == "Accepted" }
        }
        uiScope.cancel()
        authVm.signOut()
        await("creator signed out") { authVm.authState.value.takeIf { it == AuthState.Unauthenticated } }
        assertNotNull(SessionManager(app))
        assertEquals(null, client.sessions.getSession())
    }
}
