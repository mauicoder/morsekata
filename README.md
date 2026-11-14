# MorseLab

MorseLab is an experimental Android application designed for learning, improving, and experimenting with Morse code. It focuses on high-fidelity audio generation and modern Android development practices.

## 🚀 Modern Tech Stack
- **Language:** Kotlin 2.x (Primary)
- **UI Framework:** Jetpack Compose (100% Target) / ViewBinding
- **Build System:** Gradle Kotlin DSL with AGP 9.0 (Built-in Kotlin support)
- **Asynchronous Logic:** Kotlin Coroutines & Flow
- **Dependency Injection:** Hilt (Dagger)
- **Data Persistence:** Jetpack DataStore with Kotlinx Serialization (JSON)
- **Processing:** KSP (Kotlin Symbol Processing)

## ✨ Features
- **Text to Morse Audio:** Generate high-quality wave audio from text strings.
- **Audio Sharing:** Export and share generated Morse wave files.
- **Training** Q-Codes and CW Abbreviations listening training
- **Configurable Parameters:**
  - **Frequency:** Default 800 Hz (Customizable)
  - **WPM (Words per minute):** Default 20
  - **Farnsworth WPM:** Control spacing independently for effective learning.
- **Reactive Settings:** Preferences are updated in real-time across the app without requiring restarts.

## 🛠 Architecture
The project follows **Clean Architecture** principles and the **MVVM** (Model-View-ViewModel) pattern:
- **UI Layer:** Jetpack Compose for a reactive UI.
- **Domain Layer:** Morse encoding/decoding logic and business rules.
- **Data Layer:** Reactive repository pattern using DataStore for persistent user preferences.

## 📋 Requirements & Environment
- **Minimum SDK:** Android 7.0 (API 24, Nougat)
- **IDE:** Android Studio Panda 1 | 2025.3.1 or newer
- **Gradle:** 9.3.1+
- **Android Gradle Plugin (AGP):** 9.0.1 or newer

## 🏗 Setup & Build
1. Clone the repository.
2. Ensure you have the latest stable/canary version of Android Studio.
3. The project uses **Version Catalogs** (`libs.versions.toml`) for dependency management.
4. Built-in Kotlin is enabled via `gradle.properties`:

## 🛤 Roadmap
- [x] Implement Morse code encoder (Text ➔ Audio)
- [x] Migrate Protobuf to Kotlin Serialization
- [x] Implement real-time preference updates
- [ ] Refine Morse code decoder (Audio ➔ Text)
- [ ] Real-time signal visualizer
- [ ] Expand unit test coverage for complex Morse sequences
