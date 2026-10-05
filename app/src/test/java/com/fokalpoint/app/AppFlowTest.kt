package com.fokalpoint.app

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.fokalpoint.app.data.repository.SessionManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * End-to-end smoke test of the real MainActivity in demo mode: onboarding → sign-up →
 * Discover (with the sample marketplace) → open a creator → request a booking →
 * see it in Bookings → sign out.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h914dp-xxhdpi")
class AppFlowTest {

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private fun tag(t: String) = compose.onNode(hasTestTag(t))
    private fun waitFor(t: String, ms: Long = 20_000) = compose.waitUntilAtLeastOneExists(hasTestTag(t), ms)

    @Test
    fun onboardSignUpBookAndSignOut() {
        // First launch: onboarding, then sign-up.
        waitFor("welcome_screen")
        repeat(3) { tag("welcome_next").performClick(); compose.waitForIdle() }
        waitFor("auth_screen")
        tag("auth_name").performTextInput("Ana Rao")
        tag("auth_email").performTextInput("ana@example.com")
        tag("auth_password").performTextInput("secret123")
        tag("auth_submit").performScrollTo().performClick()

        // Discover screen with the demo marketplace.
        waitFor("home_screen")
        waitFor("featured_demo_arjun")
        val session = SessionManager(ApplicationProvider.getApplicationContext()).getSession()
        assertNotNull(session)
        assertEquals("Ana Rao", session!!.name)
        assertEquals("Customer", session.role)

        // Open a creator and request a booking.
        tag("featured_demo_arjun").performClick()
        waitFor("book_cta")
        tag("book_cta").performClick()
        waitFor("send_request")
        val firstDate = com.fokalpoint.app.ui.utils.BookingDates.upcomingDays(45)
            .first { it !in (com.fokalpoint.app.ui.utils.BookingDates.upcomingDays(40).let { d -> setOf(d[1], d[14]) }) }
        tag("date_$firstDate").performClick()
        tag("send_request").performClick()
        waitFor("view_bookings")
        tag("view_bookings").performClick()
        waitFor("bookings_screen")
        compose.waitUntil(10_000) {
            compose.onAllNodes(hasTestTag("bookings_screen")).fetchSemanticsNodes().isNotEmpty() &&
                compose.onAllNodes(androidx.compose.ui.test.hasText("Awaiting")).fetchSemanticsNodes().isNotEmpty()
        }

        // Sign out from the profile tab.
        tag("tab_profile").performClick()
        waitFor("sign_out")
        tag("sign_out").performScrollTo().performClick()
        waitFor("auth_screen")
        assertNull(SessionManager(ApplicationProvider.getApplicationContext()).getSession())
    }
}
