# FunnyMail / DrawMail

Cross-platform handwritten drawing letters for Android and iOS.

## Stack

- Kotlin Multiplatform and Compose Multiplatform
- Clean Architecture, feature-first organization, MVVM
- Kotlin Coroutines and Flow
- Kotlin Serialization for drawing JSON
- Koin for dependency injection
- GitLive Firebase Kotlin SDK for shared Firebase Authentication
- Cloud Firestore (repository adapters pending)
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
- [x] Shared email/password sign-in, registration, sign-out and auth-state observation
- [x] Firebase config checks that keep an unconfigured checkout launchable
- [x] Auth, chat, invite and letter domain models/repository contracts
- [ ] Firestore adapters for chats, invites and letters
- [ ] Room KMP database and local-first repositories
- [ ] Drawing canvas UI
- [ ] Offline synchronization
- [ ] Security Rules, server-controlled expiry and TTL
- [ ] End-to-end platform verification

## Firebase setup

1. Create or select a project in Firebase Console and register the Android app with application ID `com.jaroapps.funnymail`.
2. Enable **Authentication → Sign-in method → Email/Password**.
3. Download the Android config file to `androidApp/google-services.json`.
4. Register the iOS app using the bundle identifier configured in Xcode, then download `GoogleService-Info.plist` to `iosApp/iosApp/GoogleService-Info.plist`.
5. Sync/rebuild the project. The Android Google Services plugin is applied only when its JSON file exists, so an unconfigured checkout can still build and launch; authentication needs the real Firebase project config.
6. For iOS, this project uses the Kotlin 2.4 SwiftPM integration path required by the current GitLive Firebase SDK alpha. Build with Kotlin 2.4+ and Xcode 26.2+; keep the shared framework static.

The two Firebase config files are intentionally ignored by Git in this repository. They identify the Firebase project but are specific to each app; never commit service-account credentials or private OAuth secrets. You must also enable Email/Password in Firebase Console for authentication to work.

## Build and test

Android:

```bash
./gradlew :androidApp:assembleDebug
```

Shared unit tests:

```bash
./gradlew :shared:allTests
```

For iOS, open `iosApp/iosApp.xcodeproj` in Xcode and run on an iOS simulator or device. The generated Xcode build phase invokes `:shared:embedAndSignAppleFrameworkForXcode` for direct framework integration.

## Implementation notes

- Presentation and domain code depend on repository interfaces, not Firebase SDK types.
- The repository layer should expose data through `Flow`, so the UI can react to local and remote changes.
- The Firestore letter adapter must encode a drawing into a JSON string field in the letter document; it must not upload images or use Firebase Storage.
- Letter creation/expiry timestamps and invite validation must be controlled by trusted backend logic, not only by the client clock.
- The target retention period for letters is seven days, with Firestore TTL configured on the expiry field.
- Client-side validation is not a substitute for Firestore Security Rules and server-side authorization.
