package com.fokalpoint.app.data.supabase

import com.fokalpoint.app.data.model.Booking
import com.fokalpoint.app.data.model.ClientLead
import com.fokalpoint.app.data.model.Creator
import com.fokalpoint.app.data.model.Message
import com.fokalpoint.app.data.model.Review
import com.fokalpoint.app.data.model.User
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
        packageName = o.str("package_name"),
        notes = o.str("notes"),
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
        .put("package_name", b.packageName)
        .put("notes", b.notes)

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
        .put("headline", c.headline)
        .put("cover_image", c.coverImage)
    // rating / verified / review_count / pro_until are server-controlled: never sent.

    /** Parses a creators row or a search_creators() row. */
    fun creator(obj: JSONObject): Creator = Creator(
        id = obj.optString("id", ""),
        userId = obj.optString("id", ""),
        creatorType = obj.str("creator_type").ifEmpty { "Photographer" },
        experienceLevel = obj.str("experience_level").ifEmpty { "Professional" },
        bio = obj.str("bio"),
        languages = obj.str("languages"),
        equipment = obj.str("equipment"),
        rating = obj.optDouble("rating", 0.0).takeUnless { it.isNaN() } ?: 0.0,
        verified = obj.optBoolean("verified", false),
        startingPrice = obj.optDouble("starting_price", 0.0).takeUnless { it.isNaN() } ?: 0.0,
        instagram = obj.str("instagram"),
        website = obj.str("website"),
        yearsOfExperience = obj.optInt("years_of_experience", 0),
        skillset = obj.str("skillset").ifEmpty { "Photographer" },
        youtube = obj.str("youtube"),
        latitude = if (obj.isNull("latitude")) null else obj.optDouble("latitude"),
        longitude = if (obj.isNull("longitude")) null else obj.optDouble("longitude"),
        searchRadius = obj.optInt("search_radius", 50),
        headline = obj.str("headline"),
        coverImage = obj.str("cover_image"),
        reviewCount = obj.optInt("review_count", 0),
        proUntil = when {
            obj.str("pro_until").isNotEmpty() -> parseTimestamp(obj.str("pro_until"))
            obj.optBoolean("is_pro", false) -> System.currentTimeMillis() + 86_400_000L
            else -> 0L
        }
    )

    fun review(o: JSONObject, customerName: String = "FokalPoint client") = Review(
        id = o.optLong("id"),
        bookingId = o.optLong("booking_id"),
        customerId = o.str("customer_id"),
        creatorId = o.str("creator_id"),
        rating = o.optDouble("rating", 0.0),
        review = o.str("comment"),
        customerName = customerName,
        createdAt = parseTimestamp(o.str("created_at"))
    )

    fun portfolio(o: JSONObject) = com.fokalpoint.app.data.model.Portfolio(
        id = o.optLong("id"),
        creatorId = o.str("creator_id"),
        title = o.str("title"),
        category = o.str("category"),
        mediaUrl = o.str("media_url"),
        mediaType = o.str("media_type").ifEmpty { "IMAGE" },
        thumbnail = o.str("thumbnail"),
        createdAt = parseTimestamp(o.str("created_at"))
    )

    fun lead(o: JSONObject): ClientLead {
        val details = o.str("additional_details")
        val description = o.str("description")
        return ClientLead(
            id = stableLongId(o.optString("id")),
            customerId = o.str("customer_id"),
            customerName = o.str("customer_name").ifEmpty { "FokalPoint client" },
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

/** PostgREST filters shared by the ViewModel sync code and the integration tests. */
object RemoteQueries {
    /** Bookings where [userId] is either the customer or the creator. */
    fun bookingsInvolving(userId: String): Array<Pair<String, String>> = arrayOf(
        "or" to "(customer_id.eq.$userId,creator_id.eq.$userId)",
        "order" to "created_at.desc"
    )

    /** Both directions of a one-to-one conversation, oldest first. */
    fun conversation(me: String, partner: String): Array<Pair<String, String>> = arrayOf(
        "or" to "(and(sender_id.eq.$me,receiver_id.eq.$partner),and(sender_id.eq.$partner,receiver_id.eq.$me))",
        "order" to "created_at.asc",
        "limit" to "500"
    )

    fun idIn(ids: Collection<String>): String = "in.(${ids.joinToString(",")})"
}
