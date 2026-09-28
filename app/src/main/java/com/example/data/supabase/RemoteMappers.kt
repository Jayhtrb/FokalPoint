package com.example.data.supabase

import com.example.data.model.Booking
import com.example.data.model.ClientLead
import com.example.data.model.Creator
import com.example.data.model.Message
import com.example.data.model.PayoutMethod
import com.example.data.model.PayoutMethodStatus
import com.example.data.model.PayoutMethodType
import com.example.data.model.User
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

/** Converts between Supabase (snake_case JSON rows) and the local Room entities. */
object RemoteMappers {

    fun user(o: JSONObject) = User(
        id = o.optString("id"),
        name = o.str("name"),
        email = o.str("email"),
        phone = o.str("phone"),
        role = SupabaseAuth.normalizeRole(o.str("role")),
        profileImage = o.str("profile_image"),
        city = o.str("city"),
        state = o.str("state"),
        country = o.str("country"),
        createdAt = parseTimestamp(o.str("created_at"))
    )

    fun booking(o: JSONObject) = Booking(
        id = o.optLong("id"),
        customerId = o.str("customer_id"),
        creatorId = o.str("creator_id"),
        eventType = o.str("event_type"),
        date = o.str("date"),
        time = o.str("time").take(5), // "14:00:00" -> "14:00"
        hours = o.optInt("hours", 1),
        price = o.optDouble("price", 0.0),
        status = o.str("status").ifEmpty { "Pending" },
        paymentStatus = o.str("payment_status").ifEmpty { "Pending" },
        createdAt = parseTimestamp(o.str("created_at"))
    )

    fun bookingRow(b: Booking): JSONObject = JSONObject()
        .put("customer_id", b.customerId)
        .put("creator_id", b.creatorId)
        .put("event_type", b.eventType)
        .put("date", b.date)
        .put("time", b.time)
        .put("hours", b.hours)
        .put("price", b.price)

    fun message(o: JSONObject) = Message(
        id = o.optLong("id"),
        senderId = o.str("sender_id"),
        receiverId = o.str("receiver_id"),
        message = o.str("message"),
        mediaUrl = o.str("media_url"),
        createdAt = parseTimestamp(o.str("created_at"))
    )

    fun creatorRow(c: Creator): JSONObject = JSONObject()
        .put("id", c.id)
        .put("creator_type", c.creatorType)
        .put("experience_level", c.experienceLevel)
        .put("bio", c.bio)
        .put("languages", c.languages)
        .put("equipment", c.equipment)
        .put("starting_price", c.startingPrice)
        .put("instagram", c.instagram)
        .put("website", c.website)
        .put("youtube", c.youtube)
        .put("skillset", c.skillset)
        .put("years_of_experience", c.yearsOfExperience)
        .put("latitude", c.latitude ?: JSONObject.NULL)
        .put("longitude", c.longitude ?: JSONObject.NULL)
        .put("search_radius", c.searchRadius)
    // rating / verified are deliberately omitted: they are server-controlled.

    fun payoutMethod(o: JSONObject) = PayoutMethod(
        id = o.optString("id"),
        userId = o.str("user_id"),
        type = runCatching { PayoutMethodType.valueOf(o.str("type")) }.getOrDefault(PayoutMethodType.UPI),
        accountHolderName = o.str("account_holder_name"),
        accountNumber = o.str("account_number").ifEmpty { null },
        bankName = o.str("bank_name").ifEmpty { null },
        ifscCode = o.str("ifsc_code").ifEmpty { null },
        upiId = o.str("upi_id").ifEmpty { null },
        isDefault = o.optBoolean("is_default"),
        status = runCatching { PayoutMethodStatus.valueOf(o.str("status")) }
            .getOrDefault(PayoutMethodStatus.PENDING_VERIFICATION),
        createdAt = o.str("created_at")
    )

    fun payoutMethodRow(p: PayoutMethod): JSONObject = JSONObject()
        .put("id", p.id)
        .put("user_id", p.userId)
        .put("type", p.type.name)
        .put("account_holder_name", p.accountHolderName)
        .put("account_number", p.accountNumber ?: JSONObject.NULL)
        .put("bank_name", p.bankName ?: JSONObject.NULL)
        .put("ifsc_code", p.ifscCode ?: JSONObject.NULL)
        .put("upi_id", p.upiId ?: JSONObject.NULL)
        .put("is_default", p.isDefault)
    // status is server-controlled (verification happens out of band).

    fun lead(o: JSONObject): ClientLead {
        val details = o.str("additional_details")
        val description = o.str("description")
        return ClientLead(
            id = stableLongId(o.optString("id")),
            customerId = o.str("customer_id"),
            customerName = "FokalPoint client",
            customerEmail = "",
            eventType = o.str("event_type"),
            location = o.str("location"),
            budget = o.optDouble("budget", 0.0),
            description = if (details.isBlank()) description else "$description\n\nAdditional Details:\n$details",
            dateDetail = o.str("timeframe"),
            createdAt = parseTimestamp(o.str("created_at"))
        )
    }

    /** Maps a UUID primary key onto a stable positive Long for Room's Long ids. */
    fun stableLongId(uuid: String): Long = runCatching {
        UUID.fromString(uuid).mostSignificantBits and Long.MAX_VALUE
    }.getOrElse { uuid.hashCode().toLong() and Long.MAX_VALUE }

    /** Parses Postgres `timestamptz` text (e.g. 2026-09-28T10:15:30.123456+00:00) to epoch millis. */
    fun parseTimestamp(raw: String): Long {
        if (raw.length < 19) return System.currentTimeMillis()
        return runCatching {
            val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            val base = fmt.parse(raw.substring(0, 19).replace(' ', 'T'))!!.time
            val offset = Regex("([+-])(\\d{2}):?(\\d{2})$").find(raw)?.let { m ->
                val sign = if (m.groupValues[1] == "-") -1 else 1
                sign * (m.groupValues[2].toLong() * 3_600_000 + m.groupValues[3].toLong() * 60_000)
            } ?: 0L
            base - offset
        }.getOrDefault(System.currentTimeMillis())
    }

    /** optString that maps JSON null to "". */
    private fun JSONObject.str(key: String): String =
        if (isNull(key)) "" else optString(key, "")
}
