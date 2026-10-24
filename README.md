# FunnyMail / DrawMail

Cross-platform handwritten drawing letters for Android and iOS.

## Stack

- Kotlin Multiplatform and Compose Multiplatform
- Clean Architecture, feature-first organization, MVVM
- Kotlin Coroutines and Flow
- Kotlin Serialization for drawing JSON
- Koin for dependency injection
- Firebase Authentication and Cloud Firestore (adapter integration pending)
- Room KMP for local-first letter storage (integration pending)

**Storage rule:** drawing data is structured JSON stored directly in the Firestore letter document. Firebase Cloud Storage and PNG-based letter storage are not used.

## Project structure

- `androidApp/` — Android application entry point
- `iosApp/` — iOS application entry point
- `shared/` — shared Compose UI and multiplatform code
- `shared/src/commonMain/kotlin/com/jaroapps/funnymail/core/di/` — shared Koin dependency graph
- `shared/src/commonMain/kotlin/com/jaroapps/funnymail/features/` — feature-first implementation

## Current implementation

- [x] KMP Android and iOS targets
- [x] Versioned drawing domain model
- [x] JSON codec with validation for format version, normalized coordinates, colors, stroke width, canvas dimensions, stroke/point counts and a 256 KiB default JSON limit
- [x] Unit tests for drawing serialization and validation
- [x] Koin initialization from the shared Compose root
- [x] Koin module test for drawing services
- [x] Auth, chat, invite and letter domain models/repository contracts
- [ ] Authentication UI and Firebase Auth adapter
- [ ] Firestore adapters for chats, invites and letters
- [ ] Room KMP database and local-first repositories
- [ ] Drawing canvas UI
- [ ] Offline synchronization
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

- Presentation and domain code depend on repository interfaces, not Firebase SDK types.
- The repository layer should expose data through `Flow`, so the UI can react to local and remote changes.
- The Firestore letter adapter must encode a drawing into a JSON string field in the letter document; it must not upload images or use Firebase Storage.
- Letter creation/expiry timestamps and invite validation must be controlled by trusted backend logic, not only by the client clock.
- The target retention period for letters is seven days, with Firestore TTL configured on the expiry field.
- Client-side validation is not a substitute for Firestore Security Rules and server-side authorization.
