package com.example.data.network

import android.util.Log
import com.example.BuildConfig
import com.example.data.model.Booking
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

sealed class EmailEvent {
    data class Success(val recipient: String, val subject: String, val body: String, val provider: String) : EmailEvent()
    data class Failure(val recipient: String, val subject: String, val error: String) : EmailEvent()
}

object EmailNotificationService {
    private const val TAG = "EmailNotification"
    private val client = OkHttpClient()

    private val _emailEvents = MutableSharedFlow<EmailEvent>(replay = 0)
    val emailEvents = _emailEvents.asSharedFlow()

    /**
     * Sends automated email notification to photographers for new bookings.
     */
    suspend fun notifyPhotographerOfNewBooking(
        photographerName: String,
        photographerEmail: String,
        clientName: String,
        clientEmail: String,
        booking: Booking
    ) {
        val subject = "📸 New Fokal Slot Booking Request From $clientName!"
        val htmlContent = """
            <div style="font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto; padding: 20px; border: 1px solid #eee; border-radius: 8px;">
                <h2 style="color: #FFC107; text-align: center;">New Booking Request Received!</h2>
                <p>Hello <strong>$photographerName</strong>,</p>
                <p>You have a new photo/video shoot booking request on Fokal. Here are the booking details:</p>
                
                <table style="width: 100%; border-collapse: collapse; margin: 20px 0;">
                    <tr style="background-color: #f9f9f9;">
                        <td style="padding: 10px; border: 1px solid #ddd; font-weight: bold;">Client Name</td>
                        <td style="padding: 10px; border: 1px solid #ddd;">$clientName ($clientEmail)</td>
                    </tr>
                    <tr>
                        <td style="padding: 10px; border: 1px solid #ddd; font-weight: bold;">Shoot Event/Type</td>
                        <td style="padding: 10px; border: 1px solid #ddd;">${booking.eventType}</td>
                    </tr>
                    <tr style="background-color: #f9f9f9;">
                        <td style="padding: 10px; border: 1px solid #ddd; font-weight: bold;">Date & Time</td>
                        <td style="padding: 10px; border: 1px solid #ddd;">${booking.date} at ${booking.time}</td>
                    </tr>
                    <tr>
                        <td style="padding: 10px; border: 1px solid #ddd; font-weight: bold;">Duration Requested</td>
                        <td style="padding: 10px; border: 1px solid #ddd;">${booking.hours} Hours</td>
                    </tr>
                    <tr style="background-color: #f9f9f9;">
                        <td style="padding: 10px; border: 1px solid #ddd; font-weight: bold;">Payment Plan Settle</td>
                        <td style="padding: 10px; border: 1px solid #ddd; font-weight: bold; color: #4CAF50;">₹${booking.price.toInt()} (${booking.paymentStatus})</td>
                    </tr>
                </table>
                
                <p style="text-align: center;">
                    <a href="https://fokalpoint.com/dashboard" style="display: inline-block; background-color: #FFC107; color: black; font-weight: bold; padding: 12px 24px; text-decoration: none; border-radius: 4px;">Review and Accept Booking</a>
                </p>
                <hr style="border: none; border-top: 1px solid #eee; margin: 20px 0;" />
                <p style="font-size: 11px; color: #888; text-align: center;">This is an automated notification from Fokal Point. Please do not reply directly to this email.</p>
            </div>
        """.trimIndent()

        sendEmail(photographerName, photographerEmail, subject, htmlContent)
    }

    /**
     * Sends automated email notification to clients for booking status updates.
     */
    suspend fun notifyClientOfStatusUpdate(
        clientName: String,
        clientEmail: String,
        photographerName: String,
        booking: Booking,
        newStatus: String
    ) {
        val subject = "✨ Your Fokal Booking Status Updated: $newStatus!"
        val statusThemeColor = when (newStatus.uppercase()) {
            "CONFIRMED", "ACCEPTED" -> "#4CAF50"
            "CANCELLED" -> "#F44336"
            "COMPLETED" -> "#2196F3"
            else -> "#FFC107"
        }

        val htmlContent = """
            <div style="font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto; padding: 20px; border: 1px solid #eee; border-radius: 8px;">
                <h2 style="color: $statusThemeColor; text-align: center;">Booking Update: $newStatus</h2>
                <p>Hello <strong>$clientName</strong>,</p>
                <p>Your booking with photographer <strong>$photographerName</strong> has been updated to <strong style="color: $statusThemeColor;">$newStatus</strong>.</p>
                
                <table style="width: 100%; border-collapse: collapse; margin: 20px 0;">
                    <tr style="background-color: #f9f9f9;">
                        <td style="padding: 10px; border: 1px solid #ddd; font-weight: bold;">Photographer</td>
                        <td style="padding: 10px; border: 1px solid #ddd;">$photographerName</td>
                    </tr>
                    <tr>
                        <td style="padding: 10px; border: 1px solid #ddd; font-weight: bold;">Shoot Event</td>
                        <td style="padding: 10px; border: 1px solid #ddd;">${booking.eventType}</td>
                    </tr>
                    <tr style="background-color: #f9f9f9;">
                        <td style="padding: 10px; border: 1px solid #ddd; font-weight: bold;">Date & Time</td>
                        <td style="padding: 10px; border: 1px solid #ddd;">${booking.date} at ${booking.time}</td>
                    </tr>
                    <tr>
                        <td style="padding: 10px; border: 1px solid #ddd; font-weight: bold;">Booking ID</td>
                        <td style="padding: 10px; border: 1px solid #ddd; font-family: monospace;">#${booking.id}</td>
                    </tr>
                    <tr style="background-color: #f9f9f9;">
                        <td style="padding: 10px; border: 1px solid #ddd; font-weight: bold;">Total Valuation</td>
                        <td style="padding: 10px; border: 1px solid #ddd; font-weight: bold;">₹${booking.price.toInt()} (${booking.paymentStatus})</td>
                    </tr>
                </table>
                
                <p style="text-align: center;">
                    <a href="https://fokalpoint.com/bookings" style="display: inline-block; background-color: $statusThemeColor; color: ${if (statusThemeColor == "#FFC107") "black" else "white"}; font-weight: bold; padding: 12px 24px; text-decoration: none; border-radius: 4px;">View Booking Status</a>
                </p>
                <hr style="border: none; border-top: 1px solid #eee; margin: 20px 0;" />
                <p style="font-size: 11px; color: #888; text-align: center;">This is an automated notification from Fokal Point. Please do not reply directly to this email.</p>
            </div>
        """.trimIndent()

        sendEmail(clientName, clientEmail, subject, htmlContent)
    }

    private suspend fun sendEmail(recipientName: String, recipientEmail: String, subject: String, htmlContent: String) {
        withContext(Dispatchers.IO) {
            val provider = getSafeBuildConfigString("EMAIL_SERVICE_PROVIDER", "mock").lowercase()
            val apiKey = getSafeBuildConfigString("EMAIL_SERVICE_API_KEY", "")
            val senderEmail = getSafeBuildConfigString("EMAIL_SENDER_EMAIL", "no-reply@fokalpoint.com")
            val senderName = getSafeBuildConfigString("EMAIL_SENDER_NAME", "FokalPoint Bookings")

            Log.d(TAG, "Attempting to send email via $provider. Sender: $senderEmail, Recipient: $recipientEmail")

            if (provider == "mock" || apiKey.isEmpty() || apiKey.contains("placeholder") || apiKey.contains("api_key")) {
                // Execute mock transmission
                Log.i(TAG, "\n==================== SIMULATED EMAIL SENT ====================\n" +
                        "PROVIDER: MOCK SMTP SIMULATOR\n" +
                        "SENDER: $senderName <$senderEmail>\n" +
                        "RECIPIENT: $recipientName <$recipientEmail>\n" +
                        "SUBJECT: $subject\n" +
                        "HTML CONTENT LENGTH: ${htmlContent.length} bytes\n" +
                        "================================================================")
                _emailEvents.emit(EmailEvent.Success(recipientEmail, subject, htmlContent, "Mock Sandbox"))
                return@withContext
            }

            try {
                when (provider) {
                    "brevo" -> sendViaBrevo(apiKey, senderName, senderEmail, recipientName, recipientEmail, subject, htmlContent)
                    "sendgrid" -> sendViaSendGrid(apiKey, senderName, senderEmail, recipientName, recipientEmail, subject, htmlContent)
                    "mailgun" -> sendViaMailgun(apiKey, senderName, senderEmail, recipientName, recipientEmail, subject, htmlContent)
                    else -> {
                        val errMsg = "Unsupported email service provider: $provider"
                        Log.e(TAG, errMsg)
                        _emailEvents.emit(EmailEvent.Failure(recipientEmail, subject, errMsg))
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Log.e(TAG, "Failed send email transmission to $recipientEmail: ${e.message}")
                _emailEvents.emit(EmailEvent.Failure(recipientEmail, subject, e.message ?: "Unknown socket error"))
            }
        }
    }

    private suspend fun sendViaBrevo(
        apiKey: String,
        senderName: String,
        senderEmail: String,
        recipientName: String,
        recipientEmail: String,
        subject: String,
        htmlContent: String
    ) {
        val mediaType = "application/json; charset=utf-8".toMediaTypeOrNull()
        
        // Escape content correctly for raw JSON
        val escapedSubject = escapeJson(subject)
        val escapedHtml = escapeJson(htmlContent)

        val jsonBody = """
            {
              "sender": {
                "name": "$senderName",
                "email": "$senderEmail"
              },
              "to": [
                {
                  "email": "$recipientEmail",
                  "name": "$recipientName"
                }
              ],
              "subject": "$escapedSubject",
              "htmlContent": "$escapedHtml"
            }
        """.trimIndent()

        val body = jsonBody.toRequestBody(mediaType)
        val request = Request.Builder()
            .url("https://api.brevo.com/v3/smtp/email")
            .post(body)
            .addHeader("api-key", apiKey)
            .addHeader("Content-Type", "application/json")
            .addHeader("Accept", "application/json")
            .build()

        client.newCall(request).execute().use { response ->
            if (response.isSuccessful) {
                Log.i(TAG, "Email successfully sent via Brevo to $recipientEmail")
                _emailEvents.emit(EmailEvent.Success(recipientEmail, subject, htmlContent, "Brevo API"))
            } else {
                val errBody = response.body?.string() ?: ""
                val errMsg = "Brevo Error API Response code ${response.code}: $errBody"
                Log.e(TAG, errMsg)
                _emailEvents.emit(EmailEvent.Failure(recipientEmail, subject, errMsg))
            }
        }
    }

    private suspend fun sendViaSendGrid(
        apiKey: String,
        senderName: String,
        senderEmail: String,
        recipientName: String,
        recipientEmail: String,
        subject: String,
        htmlContent: String
    ) {
        val mediaType = "application/json; charset=utf-8".toMediaTypeOrNull()
        val escapedSubject = escapeJson(subject)
        val escapedHtml = escapeJson(htmlContent)

        val jsonBody = """
            {
              "personalizations": [
                {
                  "to": [
                    {
                      "email": "$recipientEmail",
                      "name": "$recipientName"
                    }
                  ]
                }
              ],
              "from": {
                "email": "$senderEmail",
                "name": "$senderName"
              },
              "subject": "$escapedSubject",
              "content": [
                {
                  "type": "text/html",
                  "value": "$escapedHtml"
                }
              ]
            }
        """.trimIndent()

        val body = jsonBody.toRequestBody(mediaType)
        val request = Request.Builder()
            .url("https://api.sendgrid.com/v3/mail/send")
            .post(body)
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .build()

        client.newCall(request).execute().use { response ->
            if (response.isSuccessful) {
                Log.i(TAG, "Email successfully sent to SendGrid gateway for $recipientEmail")
                _emailEvents.emit(EmailEvent.Success(recipientEmail, subject, htmlContent, "SendGrid Service"))
            } else {
                val errBody = response.body?.string() ?: ""
                val errMsg = "SendGrid Error API Code ${response.code}: $errBody"
                Log.e(TAG, errMsg)
                _emailEvents.emit(EmailEvent.Failure(recipientEmail, subject, errMsg))
            }
        }
    }

    private suspend fun sendViaMailgun(
        apiKey: String,
        senderName: String,
        senderEmail: String,
        recipientName: String,
        recipientEmail: String,
        subject: String,
        htmlContent: String
    ) {
        // Mailgun endpoint uses x-www-form-urlencoded
        val formBody = okhttp3.FormBody.Builder()
            .add("from", "$senderName <$senderEmail>")
            .add("to", "$recipientName <$recipientEmail>")
            .add("subject", subject)
            .add("html", htmlContent)
            .build()

        val credential = okhttp3.Credentials.basic("api", apiKey)

        // Resolve domain name from sender email domain or fallback
        val domain = senderEmail.substringAfter("@")
        val request = Request.Builder()
            .url("https://api.mailgun.net/v3/$domain/messages")
            .post(formBody)
            .addHeader("Authorization", credential)
            .build()

        client.newCall(request).execute().use { response ->
            if (response.isSuccessful) {
                Log.i(TAG, "Email successfully dispatched to Mailgun sandbox for $recipientEmail")
                _emailEvents.emit(EmailEvent.Success(recipientEmail, subject, htmlContent, "Mailgun API"))
            } else {
                val errBody = response.body?.string() ?: ""
                val errMsg = "Mailgun Service Code ${response.code}: $errBody"
                Log.e(TAG, errMsg)
                _emailEvents.emit(EmailEvent.Failure(recipientEmail, subject, errMsg))
            }
        }
    }

    private fun escapeJson(str: String): String {
        return str.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
    }

    private fun getSafeBuildConfigString(fieldName: String, default: String): String {
        return try {
            val field = BuildConfig::class.java.getField(fieldName)
            val value = field.get(null) as? String ?: ""
            if (value.trim().isEmpty()) default else value
        } catch (e: Exception) {
            default
        }
    }
}
