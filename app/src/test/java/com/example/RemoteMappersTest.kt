package com.example

import com.example.data.supabase.RemoteMappers
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class RemoteMappersTest {

    @Test
    fun parsesPostgresTimestampsWithOffsets() {
        val utc = RemoteMappers.parseTimestamp("2026-09-28T10:15:30.123456+00:00")
        val ist = RemoteMappers.parseTimestamp("2026-09-28T15:45:30+05:30")
        assertEquals(utc, ist)
        assertEquals(1790590530000L, utc)
    }

    @Test
    fun mapsBookingRowAndBack() {
        val row = JSONObject(
            """{"id":42,"customer_id":"c","creator_id":"k","event_type":"Wedding","date":"2026-12-01",
               "time":"14:00:00","hours":4,"price":40000,"status":"Accepted","payment_status":"Pending",
               "created_at":"2026-09-28T10:00:00+00:00"}"""
        )
        val booking = RemoteMappers.booking(row)
        assertEquals(42L, booking.id)
        assertEquals("14:00", booking.time)
        assertEquals("Accepted", booking.status)

        val outgoing = RemoteMappers.bookingRow(booking)
        // Only snake_case columns that exist; status fields are server-controlled.
        assertEquals("c", outgoing.getString("customer_id"))
        assertFalse(outgoing.has("status"))
        assertFalse(outgoing.has("payment_status"))
        assertFalse(outgoing.has("customerId"))
    }

    @Test
    fun creatorRowNeverSendsServerOwnedFields() {
        val creatorRow = RemoteMappers.creatorRow(
            com.example.data.model.Creator(
                id = "k", userId = "k", creatorType = "Both", experienceLevel = "Studio", bio = "b",
                languages = "English", equipment = "", rating = 5.0, verified = true, startingPrice = 1.0,
                instagram = "", website = "", yearsOfExperience = 3
            )
        )
        assertFalse(creatorRow.has("rating"))
        assertFalse(creatorRow.has("verified"))
    }

    @Test
    fun uuidIdsMapToStablePositiveLongs() {
        val id = "0f8fad5b-d9cb-469f-a165-70867728950e"
        assertEquals(RemoteMappers.stableLongId(id), RemoteMappers.stableLongId(id))
        assertTrue(RemoteMappers.stableLongId(id) >= 0)
    }

    @Test
    fun nullJsonFieldsBecomeEmptyStrings() {
        val user = RemoteMappers.user(JSONObject("""{"id":"u","name":"N","phone":null,"role":"creator"}"""))
        assertEquals("", user.phone)
        assertEquals("Creator", user.role)
    }
}
