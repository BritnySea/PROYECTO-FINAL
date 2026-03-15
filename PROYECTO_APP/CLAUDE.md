# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Android app for "Refugio WOOF" (animal shelter). Kotlin + Jetpack Compose, Firebase backend, Clean Architecture with Hilt DI.

## Build & Test Commands

```bash
# Build
./gradlew assembleDebug
./gradlew assembleRelease

# Install on device/emulator
./gradlew installDebug

# Unit tests
./gradlew testDebugUnitTest

# Instrumented tests (requires device/emulator)
./gradlew connectedDebugAndroidTest

# Android lint
./gradlew lintDebug
```

## Architecture

**Clean Architecture with 3 layers** under `app/src/main/java/com/univalle/proyectov1/`:

- **`domain/`** — Interfaces (`AuthRepository`) and models (`User`). No framework dependencies.
- **`data/`** — Firebase implementations (`AuthRepositoryImpl`). Firestore for persistence, Firebase Auth for authentication.
- **`ui/`** — Jetpack Compose screens + ViewModels (MVVM). Organized by feature (`auth/`, `admin/`, `user/`).
- **`di/`** — Hilt modules (`AppModule`) providing Firebase instances and repository bindings.
- **`core/`** — Shared utilities (`UiState` sealed class for Loading/Success/Error states).

**Single Activity** (`MainActivity`) with Compose Navigation (`NavHost`). No XML layouts.

## Key Technical Details

- **Min SDK 26 / Target SDK 36**, Compose BOM 2024.09.00, Hilt 2.48
- **Firebase services**: Auth (email/password + Google Sign-In), Firestore, Storage, Analytics
- **State management**: `UiState<T>` sealed class (Idle/Loading/Success/Error) consumed by Compose screens via ViewModel StateFlows
- **Auth flow**: Registration → email verification required → login. Google Sign-In also supported. User docs stored in Firestore `users` collection.
- **User model** has `role` field (USER/ADMIN) and `bloqueado` (blocked) flag
- **UI theme**: Dark background (#0A0A0A) with gold accents (#D4AF37), glassmorphism style (transparent cards with borders)
- **App language**: Spanish (UI strings, error messages, validation text)

## Dependency Management

Version catalog at `gradle/libs.versions.toml`. All dependency versions defined there; referenced in `build.gradle.kts` files via `libs.*` aliases.
