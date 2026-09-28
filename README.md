# FokalPoint

> **Every Moment in Focus.**
> Airbnb + Instagram + Urban Company for photography & videography.

FokalPoint is a modern Android application built with Kotlin and Jetpack Compose. It connects clients with professional creators (photographers, videographers) and offers portfolio showcases, shoot booking, creator management, payout dashboards, and AI-assisted workflows.

---

## 📱 Features

- **Creator & Portfolio Showcase**: Explore creator profiles, image galleries, services, and rates.
- **Shoot Booking & Alerts**: Request shoots, manage calendar bookings, and receive real-time notifications.
- **Payout Dashboard**: Financial tracking and payout management for creators.
- **Authentication**: Supabase email/password, email OTP, Google & GitHub OAuth, password reset, persisted + auto-refreshed sessions.
- **Supabase Backend**: Bookings, chat, availability, payout methods and shoot alerts sync with Row Level Security and server-side guard triggers.
- **Gemini AI Features**: Server-side AI integration for intelligent content assistance.

---

## 🛠 Tech Stack & Architecture

- **Language**: Kotlin 100%
- **UI Framework**: Jetpack Compose (Material Design 3)
- **Architecture**: MVVM + Clean Architecture principles
- **State Management**: StateFlow, ViewModel, `collectAsStateWithLifecycle`
- **Asynchronous Processing**: Kotlin Coroutines & Flow
- **Build System**: Gradle (Kotlin DSL - `build.gradle.kts`)
- **Backend / Database**: Supabase (migrations provided under `supabase/migrations`), Room / Local Persistence
- **AI Integration**: Google Gemini API via server-side endpoints

---

## 🚀 Getting Started

### Prerequisites

- **Android Studio** Ladybug or newer, **JDK 17+** (CI uses 21)
- Android SDK platform **36.1** (`compileSdk`), min SDK 24
- The Gradle wrapper is committed — use `./gradlew`, no local Gradle install needed.

### Build & test

```bash
cp .env.example .env          # optional: fill in real keys (see below)
./gradlew assembleDebug       # APK in app/build/outputs/apk/debug/
./gradlew testDebugUnitTest   # unit + Robolectric UI tests
```

CI (`.github/workflows/android.yml`) runs the build, unit tests and Android Lint on
every push and pull request and uploads the debug APK as an artifact.

`LiveBackendIntegrationTest` exercises live mode (auth, RLS, bookings, chat, search,
password reset, token refresh) against a Supabase-compatible backend. It is skipped
unless `FOKAL_LIVE_URL` and `FOKAL_LIVE_KEY` point at one — e.g. a local
`supabase start` stack with the migrations applied:

```bash
FOKAL_LIVE_URL=http://127.0.0.1:54321 FOKAL_LIVE_KEY=<anon key> ./gradlew testDebugUnitTest
```

### Demo mode vs. live mode

| | **Demo mode** (default) | **Live mode** |
|---|---|---|
| When | `SUPABASE_URL` / `SUPABASE_ANON_KEY` missing or placeholders | Real Supabase project configured in `.env` |
| Accounts | Any email + password creates a local account | Supabase Auth (email/password, email OTP, Google, GitHub, password reset) |
| Data | Sample creators, bookings, chats on-device (Room) | Your Supabase database, cached in Room |
| Payments / chat replies | Simulated, labelled "(demo)" | **Never simulated** – see *Payments* below |

A banner on the sign-in screen tells you which mode you're in.

### Setting up the Supabase backend (live mode)

1. Create a Supabase project and put its URL and anon key in `.env`.
2. Apply the migrations in `supabase/migrations/` in filename order
   (`supabase db push`, or paste them into the SQL editor).
3. **Auth → URL configuration**: add `fokalpoint://login-callback` to *Redirect URLs*.
4. **Auth → Providers**: enable Google / GitHub if you want social sign-in.
5. Optional: to let users type the 6-digit code instead of tapping the confirmation
   link, include `{{ .Token }}` in the *Confirm signup* email template.

The database enforces the business rules, not the app: clients cannot set their own
`rating` / `verified` badge, change a booking's price or dates after creation, mark a
booking paid (only its creator can confirm receipt), book a creator's blocked date,
insert payment records, or send messages as someone else. Creator search goes through
the parameterized `search_creators()` function.

### Payments

No payment gateway is integrated yet. In live mode, checkout sends a **booking
request**; the creator accepts it, the customer pays them (e.g. via UPI), and the
creator marks it complete/paid. Integrating Razorpay or Stripe requires a merchant
account and a server-side webhook (a Supabase Edge Function running with the service
role can update `payments` / `bookings.payment_status`, which the guard triggers allow).

### AI assistant

`GEMINI_API_KEY` is compiled into the app via `BuildConfig`, so anyone with the APK can
extract it. Use a key restricted to the Android app (package + SHA-1) in the Google
Cloud console, or move the call behind a Supabase Edge Function before release.
Without a key, Fokal AI returns built-in offline suggestions.

### Release builds

Release signing is enabled only when `KEYSTORE_PATH`, `STORE_PASSWORD`, `KEY_PASSWORD`
(and optionally `KEY_ALIAS`, default `upload`) are set in the environment.

---

## 📁 Repository Structure

```
.
├── app/                          # Main Android application module
│   ├── src/main/java/com/example/# Kotlin source code (UI, ViewModels, Services, Data)
│   ├── src/main/res/             # Android resources (Strings, Drawables, Layouts, Values)
│   └── build.gradle.kts          # Module-level Gradle configuration
├── gradle/                       # Gradle wrapper files and version catalogs
│   └── libs.versions.toml        # Dependency versions and library declarations
├── supabase/                     # Database migrations & schemas
│   └── migrations/               # SQL migrations (apply in filename order)
├── .github/workflows/            # CI: build + unit tests
├── build.gradle.kts              # Root build script
├── settings.gradle.kts           # Root settings script
├── .env.example                  # Environment variables template
├── .gitignore                    # Git ignore file excluding build artifacts and secrets
└── README.md                     # Project documentation
```

---

## 🔒 Security & Excluded Files

To protect sensitive credentials:
- Secrets, API keys, passwords, and private tokens are excluded via `.gitignore`.
- Keystore files (`debug.keystore`, `debug.keystore.base64`) are intentionally excluded.
- Environment configurations should be managed via `.env` files locally or through environment variables in your deployment pipeline.

---

## 📄 License

This project is proprietary and intended for internal or authorized use.
