# FokalPoint — Google Play launch kit

Everything needed to publish. Steps marked **(you)** need your accounts; everything else is in this repo.

## 1. Backend (Supabase) — ~20 min (you)

1. Create a project at supabase.com (region: Mumbai `ap-south-1` for Indian users).
2. Install the Supabase CLI, then from the repo root:
   ```bash
   supabase link --project-ref <your-ref>
   supabase db push                                   # applies supabase/migrations/*
   supabase functions deploy verify-play-subscription
   supabase functions deploy delete-account
   supabase secrets set PLAY_PACKAGE_NAME=com.fokalpoint.app \
     GOOGLE_SERVICE_ACCOUNT_JSON="$(cat play-service-account.json)"
   ```
3. Auth → URL configuration → add redirect URL `fokalpoint://login-callback`.
4. Auth → Providers → enable Google (and GitHub if wanted). Auth → Email → keep "Confirm email" on;
   add `{{ .Token }}` to the *Confirm signup* template so users can type the 6-digit code.
5. Put the project URL and anon key in GitHub secrets `SUPABASE_URL` / `SUPABASE_ANON_KEY`.

## 2. Signing key (you)

```bash
keytool -genkeypair -v -keystore upload.jks -alias upload -keyalg RSA -keysize 2048 -validity 10000
base64 -w0 upload.jks   # → GitHub secret UPLOAD_KEYSTORE_BASE64
```
Add secrets `KEYSTORE_PASSWORD`, `KEY_ALIAS` (`upload`), `KEY_PASSWORD`. Keep `upload.jks` safe and
**never commit it**. Use Play App Signing (default) so Google holds the app signing key.

Then run **Actions → Release bundle** (or push tag `v1.0.0`) and download `fokalpoint-release-aab`.

## 3. Play Console setup (you)

- Create app → name **FokalPoint**, default language English (India), App, Free.
- **Monetization → Subscriptions**: create product `creator_pro` with base plans
  `monthly` (suggested ₹499, auto-renewing, 1 month) and `yearly` (suggested ₹3,999, 1 year).
  Optional: a 7-day free-trial offer on `yearly`. The app shows whatever prices you set.
- **Setup → API access**: link a Google Cloud project, create a service account, grant it
  *View financial data* + *Manage orders and subscriptions*, download its JSON → the
  `GOOGLE_SERVICE_ACCOUNT_JSON` secret above.
- Upload the AAB to **Internal testing** first, add your email as a tester, and buy Pro with a
  license-tester account (test purchases are free).

## 4. Store listing (copy-paste)

**App name:** FokalPoint: Book Photographers

**Short description (80):**
Book top photographers & filmmakers for weddings, fashion & events. Pay via UPI.

**Full description:**
```
FokalPoint is the beautiful way to find and book photographers and filmmakers.

DISCOVER CREATORS YOU'LL LOVE
Browse handpicked portfolios for weddings, pre-wedding shoots, fashion, portraits, events, corporate, maternity and product photography. Filter by style, city and budget.

BOOK IN SECONDS
Pick a package (Essential, Signature or Luxe), choose a date from the creator's live availability and send a request. No payment until they confirm.

PAY THEM DIRECTLY — ZERO FEES
Once accepted, pay the creator straight from your favourite UPI app (Google Pay, PhonePe, Paytm and more). FokalPoint never holds your money.

CHAT, PLAN, REVIEW
Message creators to plan every detail, track your bookings, and leave a review after your shoot.

POST A SHOOT REQUEST
Describe your event and let matching creators come to you.

FOR CREATORS: A STUDIO IN YOUR POCKET
• Beautiful profile with portfolio, packages and reviews
• Booking requests, availability calendar and earnings at a glance
• Shoot leads from clients near you
• Get paid directly by UPI — 0% commission

CREATOR PRO
Get discovered first with featured placement, unlimited leads, an unlimited portfolio and a Pro badge. Monthly or yearly subscription via Google Play; cancel anytime.
```

**Category:** Lifestyle (alt: Photography) · **Tags:** photography, wedding, events
**Contact email:** support@fokalpoint.app (change to yours)
**Privacy policy URL:** publish `docs/privacy-policy.html` (e.g. GitHub Pages:
Settings → Pages → Deploy from branch → `/docs`) and use that URL.
**Account deletion URL** (required): same page — it documents in-app deletion + email.

**Graphics:** `store/play-icon-512.png`, `store/feature-graphic-1024x500.png`,
phone screenshots in `store/screenshots/` (1080×2400 JPEG; upload 4–8, e.g. 01, 02, 03, 04, 05, 08, 09). Re-render with `./gradlew recordRoborazziDebug --tests com.fokalpoint.app.StoreScreenshots` (writes PNG).

## 5. App content answers

- **Ads:** No ads.
- **Target audience:** 18+.
- **Content rating:** Questionnaire → category "Social/communication"; users can interact and share
  content (chat, photos); no violence/sexual content → typically rated Teen/12+.
- **Financial features:** none (P2P payments happen in external UPI apps). Subscriptions via Play Billing.
- **Data safety:**

| Data | Collected | Shared | Purpose | Optional |
|---|---|---|---|---|
| Name, email | Yes | No | Account management, app functionality | No |
| User ID | Yes | No | Account management | No |
| Approximate location | Yes (on device, not stored) | No | App functionality (city autofill) | Yes |
| Photos | Yes (creator uploads) | No | App functionality | Yes |
| Messages (in-app) | Yes | No | App functionality | No |
| Other user-generated content (reviews, bookings) | Yes | No | App functionality | No |
| Purchase history | Yes | No | App functionality (Pro status) | Yes |
| Financial info (UPI ID, creators only) | Yes | Shown to that creator's accepted clients | App functionality | Yes |

Data is encrypted in transit: **Yes**. Users can request deletion: **Yes** (in-app).

## 6. Pre-launch checklist

- [ ] Supabase migrations + both edge functions deployed, secrets set
- [ ] Google sign-in: Google provider enabled in Supabase with a Web OAuth client (Supabase handles the redirect)
- [ ] `creator_pro` subscription active in Play Console with `monthly` / `yearly`
- [ ] Internal test: sign up as client + creator on two phones, book → accept → UPI pay →
      confirm → complete → review; buy Pro with a license tester; delete a test account
- [ ] Privacy policy + terms published; replace support@fokalpoint.app with your address
- [ ] Have a lawyer review `docs/privacy-policy.md` and `docs/terms.md`
- [ ] Seed your marketplace: onboard 10–20 real creators in one city before public launch

## Recommended next (post-launch)

- Firebase Cloud Messaging for instant push (the app already notifies via a 15-min background check)
- In-app payments with commission (Razorpay Route) if you want a second revenue stream
- Analytics (privacy-friendly) to measure search → request → booking conversion
