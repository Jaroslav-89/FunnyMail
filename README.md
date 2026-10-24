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
- [x] Google Sign-In on Android (Credential Manager)
- [x] Native Sign in with Apple on iOS with nonce validation
- [x] Firebase config checks that keep an unconfigured checkout launchable
- [x] Auth, chat, invite and letter domain models/repository contracts
- [ ] Firestore adapters for chats, invites and letters
- [ ] Room KMP database and local-first repositories
- [ ] Drawing canvas UI
- [ ] Offline synchronization
- [ ] Security Rules, server-controlled expiry and TTL
- [ ] End-to-end platform verification

Platform sign-in options are intentionally separate: Android offers email/password and Google; iOS offers email/password and Apple ID.

## Firebase and identity-provider setup

1. Register the Android app with application ID `com.jaroapps.funnymail` and the iOS app with bundle ID `com.jaroapps.funnymail.FunnyMail` in the Firebase project.
2. In Firebase Console, enable **Authentication → Sign-in method → Email/Password** for both platforms, **Google** for Android, and **Apple** for iOS.
3. Download the latest Android config to `androidApp/google-services.json`. The Google Services plugin generates `default_web_client_id`, which Android Credential Manager uses as the server client ID.
4. Download the iOS config to `iosApp/iosApp/GoogleService-Info.plist`.
6. In Firebase Console, enable **Apple** as an Authentication provider. Configure the required Apple Team ID, Service ID, key ID and private key as requested by Firebase. In the Apple Developer portal, enable **Sign in with Apple** for the same App ID.
7. In Xcode, check the app's signing team and bundle identifier and ensure the **Sign in with Apple** capability is enabled for the target. The repository contains an entitlements file, but Apple Developer signing/provisioning must authorize that entitlement.

### iOS Swift Package Manager integration

The GitLive Firebase Kotlin SDK `3.0.0-alpha02` brings the native `firebase-ios-sdk` through Swift Package Manager (SwiftPM) metadata. For this dependency, use Kotlin Multiplatform **direct Xcode integration**; Swift Export integration (`embedSwiftExport`) is not supported for this SDK setup. The Xcode build phase must call `:shared:embedAndSignAppleFrameworkForXcode`.

Run this one-time setup from the repository root on macOS if the linkage package has not yet been generated, or after changing native SwiftPM dependencies:

```bash
XCODEPROJ_PATH="$PWD/iosApp/iosApp.xcodeproj" \
  ./gradlew :shared:integrateEmbedAndSign :shared:integrateLinkagePackage
```

The command updates `iosApp/iosApp.xcodeproj/project.pbxproj` and generates `iosApp/KotlinMultiplatformLinkedPackage/`. Commit those generated files and any generated `.swiftpm-locks/**/Package.resolved` files. Then in Xcode select **File → Packages → Resolve Package Versions**, run **Product → Clean Build Folder**, and rebuild. If Xcode still reports `embedSwiftExport`, verify that the active build phase is the direct-integration script and that you opened this branch's `iosApp/iosApp.xcodeproj`.

The Firebase config files are intentionally ignored by Git. Never commit service-account credentials or private OAuth keys. Android Google Sign-In uses the Web client ID generated from `google-services.json`.

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
