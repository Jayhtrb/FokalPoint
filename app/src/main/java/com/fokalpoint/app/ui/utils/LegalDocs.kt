package com.fokalpoint.app.ui.utils

/**
 * In-app copies of the legal documents. The same text is published at
 * docs/privacy-policy.md and docs/terms.md for the Play Console URLs.
 * Review with your own legal counsel before launch.
 */
object LegalDocs {
    const val PRIVACY = "privacy"
    const val TERMS = "terms"
    const val UPDATED = "5 October 2026"

    fun get(doc: String): Pair<String, String> = when (doc) {
        TERMS -> "Terms of service" to TERMS_TEXT
        else -> "Privacy policy" to PRIVACY_TEXT
    }

    val PRIVACY_TEXT = """
Last updated: $UPDATED

FokalPoint ("we", "us") connects people with photographers and videographers. This policy explains what we collect, why, and the choices you have.

## What we collect
Account details: your name, email address, role (client or creator) and, if you sign in with Google or GitHub, the basic profile those services share (name, email, profile photo).

Creator profile: headline, bio, city, prices, specialties, portfolio photos you upload, social handles, and the UPI ID you choose to add for receiving payments.

Activity: booking requests (dates, times, package, notes), messages you exchange with other users, reviews, saved creators, shoot requests you post, and availability you set.

Location (optional): if you tap "Use my location", we read your approximate location on your device to fill in your city. We do not store your precise location.

Purchases: if you subscribe to Creator Pro, Google Play processes the payment. We receive a purchase token and subscription status to activate your benefits; we never receive your card or bank details.

Device data: basic diagnostics needed to run the service (such as app version). We do not use advertising identifiers and we do not show ads.

## How we use it
To create and secure your account; to show creator profiles and portfolios to clients; to deliver booking requests, messages and notifications between the people involved; to show your UPI ID to a client only after you accept their booking; to verify and maintain Creator Pro subscriptions; to prevent fraud and abuse; and to provide support.

## Who can see what
Public: creator profiles, portfolio photos, prices, ratings and reviews.
Only the people involved: bookings and messages.
Only after you accept a booking: a creator's UPI ID, shown to that booking's client.
We do not sell your personal data.

## Service providers
We use Supabase (database, authentication and file storage) and Google Play (subscriptions). They process data on our behalf under their own security and privacy commitments.

## Payments between users
Clients pay creators directly through their own UPI apps. FokalPoint does not process, hold or receive those payments.

## Retention and deletion
We keep your data while your account is active. You can delete your account at any time in the app under You → Delete account. This permanently removes your profile, bookings, messages, reviews, favourites and uploaded photos. You can also request deletion by emailing support@fokalpoint.app.

## Security
Data is encrypted in transit (HTTPS). Access to each record is restricted at the database level so users can only read and change what they are allowed to.

## Children
FokalPoint is not intended for children under 18.

## Your rights
You can access, correct or delete your information in the app, or contact us for help. If you are in a region with additional rights (such as the EU or India's DPDP Act), contact us to exercise them.

## Contact
support@fokalpoint.app
""".trimIndent()

    val TERMS_TEXT = """
Last updated: $UPDATED

These terms govern your use of FokalPoint. By creating an account you agree to them.

## The service
FokalPoint is a marketplace where clients discover photographers and videographers ("creators"), request bookings and communicate. Creators are independent professionals, not employees or agents of FokalPoint.

## Accounts
You must be at least 18 and provide accurate information. You are responsible for activity on your account. We may suspend accounts that break these terms.

## Bookings and payments
A booking request becomes an agreement between the client and the creator once the creator accepts it. Clients pay creators directly by UPI or another method they agree on. FokalPoint is not a party to that payment and does not handle refunds between users; creators set their own cancellation terms and should state them before accepting.

## Creator Pro
Creator Pro is an auto-renewing subscription sold through Google Play. Prices are shown before purchase. It renews until cancelled in your Google Play account. Benefits include featured placement in search, unlimited shoot leads and an unlimited portfolio. Refunds follow Google Play's policies.

## Content
You keep ownership of the photos and text you upload and grant FokalPoint a licence to display them in the service. Only upload content you have the right to share. Do not post anything unlawful, misleading, hateful or infringing.

## Reviews
Reviews must reflect a genuine completed booking. We may remove reviews that are fake or abusive.

## Disclaimers
The service is provided "as is". We do not guarantee any creator's availability, quality or conduct, or any client's payment. To the extent permitted by law, FokalPoint's liability is limited to the amount you paid us in the 12 months before a claim.

## Changes
We may update these terms; we will notify you of material changes in the app.

## Contact
support@fokalpoint.app
""".trimIndent()
}
