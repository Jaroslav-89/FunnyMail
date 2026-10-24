# FunnyMail / DrawMail

Cross-platform handwritten drawing letters for Android and iOS.

## Stack

- Kotlin Multiplatform and Compose Multiplatform
- Clean Architecture, feature-first organization, MVVM
- Kotlin Coroutines and Flow
- Kotlin Serialization
- Firebase Authentication and Cloud Firestore (integration pending)
- Room KMP for local-first letter storage (integration pending)
- Koin for dependency injection (planned)

**Storage rule:** drawing data is structured JSON stored directly in Cloud Firestore. Firebase Cloud Storage and PNG-based letter storage are not used.

## Project structure

- `androidApp/` — Android application entry point
- `iosApp/` — iOS application entry point
- `shared/` — shared Compose UI and multiplatform code
- `shared/src/commonMain/kotlin/com/jaroapps/funnymail/features/` — feature-first shared implementation

## Current implementation

- [x] KMP Android and iOS targets
- [x] Versioned drawing domain model
- [x] JSON codec with validation for format version, normalized coordinates, colors, stroke width, canvas dimensions, stroke/point counts and a 256 KiB default JSON limit
- [x] Unit tests for drawing serialization and validation
- [ ] Authentication and profile
- [ ] Room KMP database
- [ ] Chats and invite links/codes
- [ ] Drawing canvas UI
- [ ] Firestore letters and synchronization
- [ ] Security Rules, server-controlled expiry and TTL
- [ ] End-to-end platform verification

## Build and test

Android:

```bash
./gradlew :androidApp:assembleDebug
```

Shared unit tests:

```bash
./gradlew :shared:allTests
```

For iOS, open `iosApp/iosApp.xcodeproj` in Xcode and run on an iOS simulator or device.

Firebase configuration and provider credentials must be supplied separately for each platform. Never commit service-account credentials or private OAuth secrets.

## Implementation notes

- UI should read local data through repositories/Flow; Firebase SDK calls belong in the data layer.
- Server letter documents should use trusted `createdAt` and `expiresAt` timestamps, with a seven-day retention policy and Firestore TTL.
- Client-side validation is not a substitute for Firestore Security Rules and server-side authorization.
