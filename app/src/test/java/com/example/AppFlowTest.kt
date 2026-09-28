package com.example

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.example.data.repository.SessionManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * End-to-end smoke test of the real MainActivity in demo mode: the user lands on the
 * auth screen (no silent auto-login), signs up, reaches the customer home with the
 * session persisted under their own identity, then signs out again.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h914dp-xxhdpi")
class AppFlowTest {

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun demoSignUpReachesHomeAndSignsOut() {
        // Signed out on first launch: the auth form is shown.
        compose.waitUntilAtLeastOneExists(hasText("Create Account") or hasText("Sign In"), 15_000)
        compose.onAllNodesWithText("Sign Up")[0].performClick()

        fun field(label: String) = compose.onNode(hasSetTextAction() and hasText(label))
        field("Email").performTextInput("ana@example.com")
        field("Full Name").performTextInput("Ana Rao")
        field("Password").performTextInput("secret123")
        compose.onNode(hasText("Create Account")).performScrollTo().performClick()

        // Lands on the customer home screen.
        compose.waitUntilAtLeastOneExists(hasTestTag("home_search_service_input"), 20_000)

        val session = SessionManager(ApplicationProvider.getApplicationContext()).getSession()
        assertNotNull(session)
        assertEquals("Ana Rao", session!!.name)
        assertEquals("Customer", session.role)

        // Home has an infinite shimmer placeholder, so Compose never idles on its own:
        // drive the frame clock manually from here on.
        compose.mainClock.autoAdvance = false
        compose.onNode(hasTestTag("account_menu_button")).performClick()
        compose.mainClock.advanceTimeBy(500)
        compose.onNode(hasTestTag("account_sign_out")).performClick()
        repeat(50) {
            compose.mainClock.advanceTimeBy(100)
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
        }
        assertTrue(compose.onAllNodes(hasText("Sign In")).fetchSemanticsNodes().isNotEmpty())
        assertNull(SessionManager(ApplicationProvider.getApplicationContext()).getSession())
    }
}
