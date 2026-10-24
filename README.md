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
- [x] Google Sign-In on Android (Credential Manager) and iOS (GoogleSignIn SDK)
- [x] Native Sign in with Apple on iOS with nonce validation
- [x] Firebase config checks that keep an unconfigured checkout launchable
- [x] Auth, chat, invite and letter domain models/repository contracts
- [ ] Firestore adapters for chats, invites and letters
- [ ] Room KMP database and local-first repositories
- [ ] Drawing canvas UI
- [ ] Offline synchronization
- [ ] Security Rules, server-controlled expiry and TTL
- [ ] End-to-end platform verification

Apple ID sign-in is currently exposed on iOS only. Google sign-in is available on Android and iOS.

## Firebase and identity-provider setup

1. Register the Android app with application ID `com.jaroapps.funnymail` and the iOS app with bundle ID `com.jaroapps.funnymail.FunnyMail` in the Firebase project.
2. In Firebase Console, enable **Authentication → Sign-in method → Email/Password** and **Google**.
3. Download the latest Android config to `androidApp/google-services.json`. The Google Services plugin generates `default_web_client_id`, which Android Credential Manager uses as the server client ID.
4. Download the iOS config to `iosApp/iosApp/GoogleService-Info.plist`.
5. For Google Sign-In on iOS, copy the `REVERSED_CLIENT_ID` value from `GoogleService-Info.plist` into `GOOGLE_REVERSED_CLIENT_ID` in `iosApp/Configuration/Config.xcconfig`. The iOS app routes OAuth callback URLs through `GIDSignIn`.
6. In Firebase Console, enable **Apple** as an Authentication provider. Configure the required Apple Team ID, Service ID, key ID and private key as requested by Firebase. In the Apple Developer portal, enable **Sign in with Apple** for the same App ID.
7. In Xcode, check the app's signing team and bundle identifier and ensure the **Sign in with Apple** capability is enabled for the target. The repository contains an entitlements file, but Apple Developer signing/provisioning must authorize that entitlement.

### iOS Swift Package Manager integration

The shared module now declares GoogleSignIn as a SwiftPM dependency. The current GitLive Firebase Kotlin SDK also uses Kotlin's SwiftPM integration. On a Mac with Kotlin 2.4+ and Xcode 26.2+, run this once after syncing Gradle so the generated linkage package is integrated into the Xcode project:

```bash
XCODEPROJ_PATH="$PWD/iosApp/iosApp.xcodeproj" ./gradlew :shared:integrateLinkagePackage
```

Then build from Xcode. Keep the shared framework static. This SwiftPM import feature is still alpha in Kotlin's toolchain; test it with your exact Xcode version before shipping.

The Firebase config files are intentionally ignored by Git. Never commit service-account credentials or private OAuth keys. Google OAuth client IDs and the reversed callback scheme are app configuration values, not service-account secrets.

## Build and test

Android:

```bash
./gradlew :androidApp:assembleDebug
```

Shared unit tests:

```bash
./gradlew :shared:allTests
```

For iOS, open `iosApp/iosApp.xcodeproj` in Xcode and run on an iOS simulator or device. The Xcode build phase invokes `:shared:embedAndSignAppleFrameworkForXcode` for direct framework integration.

## Implementation notes

- Presentation and domain code depend on repository interfaces, not Firebase SDK types.
- The repository layer should expose data through `Flow`, so the UI can react to local and remote changes.
- The Firestore letter adapter must encode a drawing into a JSON string field in the letter document; it must not upload images or use Firebase Storage.
- Letter creation/expiry timestamps and invite validation must be controlled by trusted backend logic, not only by the client clock.
- The target retention period for letters is seven days, with Firestore TTL configured on the expiry field.
- Client-side validation is not a substitute for Firestore Security Rules and server-side authorization.
