package com.fokalpoint.app

import android.app.Application
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import com.fokalpoint.app.data.database.AppDatabase
import com.fokalpoint.app.data.model.Booking
import com.fokalpoint.app.data.model.Message
import com.fokalpoint.app.data.model.User
import com.fokalpoint.app.ui.components.AuroraBackground
import com.fokalpoint.app.ui.screens.*
import com.fokalpoint.app.ui.theme.FokalTheme
import com.fokalpoint.app.ui.utils.BookingDates
import com.fokalpoint.app.ui.viewmodel.FokalViewModel
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLooper

/**
 * Renders the key screens with the demo marketplace to PNGs in store/screenshots/
 * (Pixel 8, 1080×2400) — used for design review and as Play Store screenshots.
 * Run: ./gradlew testDebugUnitTest --tests com.fokalpoint.app.StoreScreenshots
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
class StoreScreenshots {

    @get:Rule val compose = createComposeRule()

    private val app: Application get() = ApplicationProvider.getApplicationContext()
    private val customer = User("shot_customer", "Ananya Rao", "ananya@example.com", "", "Customer", "res:av_ananya", "Mumbai", "", "India")

    private fun viewModel(user: User): FokalViewModel {
        val vm = FokalViewModel(app)
        runBlocking { AppDatabase.getDatabase(app).userDao().insertUser(user) }
        vm.setActiveUser(user)
        settle { runBlocking { AppDatabase.getDatabase(app).creatorDao().getCreatorByIdSync("demo_arjun") } != null }
        return vm
    }

    /** Lets Room, flows and Coil finish before capturing. */
    private fun settle(condition: () -> Boolean = { true }) {
        val end = System.currentTimeMillis() + 10_000
        while (System.currentTimeMillis() < end && !condition()) { ShadowLooper.idleMainLooper(); Thread.sleep(50) }
        repeat(30) { ShadowLooper.idleMainLooper(); compose.mainClock.advanceTimeBy(100); Thread.sleep(40) }
    }

    private fun shoot(name: String, content: @Composable () -> Unit) {
        compose.setContent { FokalTheme { AuroraBackground { content() } } }
        settle()
        compose.waitForIdle()
        settle()
        compose.onRoot().captureRoboImage(filePath = "../store/screenshots/$name.png")
    }

    private fun seedBookings(customerId: String) = runBlocking {
        val db = AppDatabase.getDatabase(app)
        val d = BookingDates.upcomingDays(30)
        db.bookingDao().insertBooking(Booking(9001, customerId, "demo_arjun", "Wedding reception", d[9], "16:00", 4, 63000.0, "Accepted", "Pending", "Signature", "Sunset ceremony at Taj Lands End"))
        db.bookingDao().insertBooking(Booking(9002, customerId, "demo_riya", "Brand lookbook", d[18], "11:00", 2, 22000.0, "Pending", "Pending", "Essential"))
        db.bookingDao().insertBooking(Booking(9003, customerId, "demo_meera", "Pre-wedding", d[25], "07:00", 8, 153600.0, "Confirmed", "Paid", "Luxe"))
    }

    @Test fun s01_welcome() = shoot("01_welcome") { WelcomeScreen({}, {}) }

    @Test fun s02_discover() {
        val vm = viewModel(customer)
        shoot("02_discover") { HomeScreen(vm, {}, {}, {}, {}) }
    }

    @Test fun s03_creator_profile() {
        val vm = viewModel(customer)
        shoot("03_creator_profile") { CreatorProfileScreen("demo_arjun", vm, {}, {}, {}) }
    }

    @Test fun s04_booking() {
        val vm = viewModel(customer)
        shoot("04_booking_request") { BookingRequestScreen("demo_meera", vm, {}, {}) }
    }

    @Test fun s05_bookings() {
        val vm = viewModel(customer)
        seedBookings(customer.id)
        shoot("05_bookings") { BookingsScreen(vm, {}, {}, {}) }
    }

    @Test fun s06_chat() {
        val vm = viewModel(customer)
        runBlocking {
            val dao = AppDatabase.getDatabase(app).messageDao()
            val t = System.currentTimeMillis() - 3_600_000
            dao.insertMessage(Message(senderId = customer.id, receiverId = "demo_arjun", message = "Hi Arjun! Loved your Goa wedding film. Are you free on the 14th?", createdAt = t))
            dao.insertMessage(Message(senderId = "demo_arjun", receiverId = customer.id, message = "Thank you! Yes, the 14th is open. Is it a full-day ceremony or just the reception?", createdAt = t + 300_000))
            dao.insertMessage(Message(senderId = customer.id, receiverId = "demo_arjun", message = "Just the reception, about 4 hours at sunset 🌅", createdAt = t + 600_000))
            dao.insertMessage(Message(senderId = "demo_arjun", receiverId = customer.id, message = "Perfect — the Signature package covers that, with a highlight reel. Send a request and I'll confirm right away.", createdAt = t + 900_000))
        }
        shoot("06_chat") { ChatScreen("demo_arjun", vm, {}, {}) }
    }

    @Test fun s07_explore() {
        val vm = viewModel(customer)
        vm.selectedCategory.value = "Wedding"
        shoot("07_explore") { ExploreScreen(vm) {} }
    }

    @Test fun s08_studio() {
        val creator = User("demo_arjun", "Arjun Mehta", "arjun@example.com", "", "Creator", "res:av_arjun", "Mumbai", "", "India")
        val vm = viewModel(creator)
        runBlocking {
            val db = AppDatabase.getDatabase(app)
            val cal = java.util.Calendar.getInstance()
            val fmt = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
            listOf(0 to 82000.0, 1 to 145000.0, 2 to 96000.0, 3 to 188000.0, 4 to 121000.0, 5 to 214000.0).forEach { (i, amt) ->
                val c = (cal.clone() as java.util.Calendar).apply { add(java.util.Calendar.MONTH, i - 5); set(java.util.Calendar.DAY_OF_MONTH, 8) }
                db.bookingDao().insertBooking(Booking(9100L + i, "c$i", "demo_arjun", "Wedding", fmt.format(c.time), "10:00", 6, amt, "Completed", "Paid", "Luxe"))
            }
            val d = BookingDates.upcomingDays(20)
            db.bookingDao().insertBooking(Booking(9200, "c9", "demo_arjun", "Engagement", d[5], "17:00", 4, 63000.0, "Pending", "Pending", "Signature"))
            db.bookingDao().insertBooking(Booking(9201, "c8", "demo_arjun", "Sangeet", d[12], "18:00", 4, 63000.0, "Confirmed", "Paid", "Signature"))
        }
        shoot("08_creator_studio") { StudioScreen(vm, {}, {}, {}, {}, {}, {}, {}) }
    }

    @Test fun s09_pro() {
        val creator = User("demo_kabir", "Kabir Singh", "kabir@example.com", "", "Creator", "res:av_kabir", "Bengaluru", "", "India")
        val vm = viewModel(creator)
        shoot("09_creator_pro") { ProScreen(vm, {}, {}) }
    }

    @Test fun s10_leads() {
        val creator = User("demo_kabir", "Kabir Singh", "kabir@example.com", "", "Creator", "res:av_kabir", "Bengaluru", "", "India")
        val vm = viewModel(creator)
        shoot("10_leads") { LeadsScreen(vm, {}, {}) }
    }

    @Test fun s11_auth() {
        val auth = com.fokalpoint.app.ui.viewmodel.AuthViewModel(app)
        settle()
        compose.setContent { FokalTheme { AuthScreen(auth, startWithSignUp = true) } }
        settle(); compose.waitForIdle()
        compose.onRoot().captureRoboImage(filePath = "../store/screenshots/11_sign_up.png")
    }
}
