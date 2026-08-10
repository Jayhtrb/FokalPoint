# FokalPoint

> **Every Moment in Focus.**
> Airbnb + Instagram + Urban Company for photography & videography.

FokalPoint is a modern Android application built with Kotlin and Jetpack Compose. It connects clients with professional creators (photographers, videographers) and offers portfolio showcases, shoot booking, creator management, payout dashboards, and AI-assisted workflows.

---

## 📱 Features

- **Creator & Portfolio Showcase**: Explore creator profiles, image galleries, services, and rates.
- **Shoot Booking & Alerts**: Request shoots, manage calendar bookings, and receive real-time notifications.
- **Payout Dashboard**: Financial tracking and payout management for creators.
- **Authentication Flow**: Multi-state auth flow (loading, onboarding, main app).
- **Supabase Integration**: Backend data sync, migrations, and database schema support.
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

- **Android Studio**: Ladybug / Hedgehog or newer (recommended)
- **JDK**: JDK 17 or higher
- **Android SDK**: API Level 34 (Android 14) minimum compile SDK

### Installation & Setup

1. **Clone the repository**:
   ```bash
   git clone https://github.com/<your-username>/FokalPoint.git
   cd FokalPoint
   ```

2. **Environment Configuration**:
   Copy `.env.example` to `.env` and fill in your API keys and configuration values:
   ```bash
   cp .env.example .env
   ```
   *Required variables include:*
   - `GEMINI_API_KEY`: API key for Google Gemini services.
   - `SUPABASE_URL`: URL of your Supabase instance.
   - `SUPABASE_ANON_KEY`: Anonymous public key for Supabase.
   - `EMAIL_SERVICE_PROVIDER` & `EMAIL_SERVICE_API_KEY`: Optional email gateway configuration.

3. **Build the Project**:
   Open the project in Android Studio or run the Gradle build task:
   ```bash
   ./gradlew assembleDebug
   ```

4. **Run Unit Tests**:
   ```bash
   ./gradlew testDebugUnitTest
   ```

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
│   └── migrations/               # SQL migration files
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
