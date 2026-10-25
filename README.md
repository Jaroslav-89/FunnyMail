# FunnyMail / DrawMail

Cross-platform handwritten drawing letters for Android and iOS.

## Stack

- Kotlin Multiplatform and Compose Multiplatform
- Clean Architecture, feature-first organization, MVVM
- Kotlin Coroutines and Flow
- Kotlin Serialization for drawing JSON
- Koin for dependency injection
- GitLive Firebase Kotlin SDK for shared Firebase Authentication
- Cloud Firestore (chat, invite, and letter repository adapters implemented; Firestore rules must be published)
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
- [x] Firestore adapters for chats and invites (`firestore.rules` must be published in the Firebase project)
- [ ] Room KMP database and local-first repositories
- [x] Shared drawing canvas with color palette, adjustable brush, eraser, undo and clear controls
- [x] Drawing JSON validation and draft preview
- [x] Local draft persistence on Android (SharedPreferences) and iOS (NSUserDefaults)
- [x] Firestore letter adapter, sending drawing drafts to chats, and live letter feed
- [ ] Server-controlled letter expiry and automatic Firestore TTL cleanup
- [ ] Offline synchronization
- [x] Firestore Security Rules for chat and letter membership plus seven-day invite expiry (publish them to the Firebase project)
- [ ] End-to-end platform verification

Platform sign-in options are intentionally separate: Android offers email/password and Google; iOS offers email/password and Apple ID.

## Firebase and identity-provider setup

1. Register the Android app with application ID `com.jaroapps.funnymail` and the iOS app with bundle ID `com.jaroapps.funnymail.FunnyMail` in the Firebase project.
2. In Firebase Console, enable **Authentication → Sign-in method → Email/Password** for both platforms, **Google** for Android, and **Apple** for iOS.
3. For Android Google Sign-In, open **Firebase Console → Project settings → Your apps → Android app** and add the SHA-1 and SHA-256 certificate fingerprints for the signing key used to install the app (include debug and release/Play App Signing fingerprints as applicable). The Android package name must exactly match `com.jaroapps.funnymail`.
4. After enabling Google and adding the fingerprints, download a fresh `google-services.json` to `androidApp/google-services.json`. Confirm it includes a Web OAuth client (`oauth_client` with `client_type: 3`); the Google Services plugin generates `default_web_client_id` from this file, and Credential Manager uses that Web client ID, not the Android client ID.
5. In Firebase Console, register the iOS app with the exact, case-sensitive bundle ID `com.jaroapps.funnymail.FunnyMail`. Download its `GoogleService-Info.plist` to `iosApp/iosApp/GoogleService-Info.plist` (the filename must not include a suffix such as `(2)`). In Xcode, select the file and confirm **Target Membership → iosApp**. The file is intentionally ignored by Git and must be added to each developer's local checkout. After adding it, stop and rebuild/reinstall the app so the plist is copied into the app bundle.
6. In Firebase Console, enable **Apple** under **Authentication → Sign-in method** and configure the required Apple Team ID, Service ID, key ID and private key as requested by Firebase. In the Apple Developer portal, enable **Sign in with Apple** for the same App ID.
7. In Xcode, open the app target's **Signing & Capabilities**, select your development team and add **Sign in with Apple**. The bundle identifier should remain `com.jaroapps.funnymail.FunnyMail`; the development Team ID is for signing and must not be appended to the bundle identifier. Apple Developer signing/provisioning must authorize the Apple sign-in entitlement.

### Cloud Firestore setup

1. Create a Cloud Firestore database in the same Firebase project used for Authentication.
2. Publish the rules from `firestore.rules` in Firebase Console → Firestore Database → Rules. Until the rules are published, chat, invite, and letter writes will be rejected.
3. The app creates `chats` and `chatInvites` documents and stores sent drawings as JSON strings in `chats/{chatId}/letters/{letterId}`. Invite validity lasts seven days from the server-assigned `createdAt` timestamp; the security rules use server `request.time`, not the device clock. Expired invite documents are denied but are not automatically deleted yet; invite TTL cleanup is still pending. Letter lists query the last seven days on the client; server-controlled letter expiry and automatic TTL cleanup are not configured yet.
4. Firestore document access is limited to signed-in users. Chat and letter reads are limited to chat members, and joining requires a live invite created by a current member. Do not replace these rules with open read/write rules.
5. Since Firestore adds a native iOS Firebase dependency, rerun the SwiftPM linkage generation commands below on macOS, commit generated linkage files if they change, and resolve package versions in Xcode before the iOS build.

### iOS Swift Package Manager integration

The GitLive Firebase Kotlin SDK `3.0.0-alpha02` brings the native `firebase-ios-sdk` through Swift Package Manager (SwiftPM) metadata. For this dependency, use Kotlin Multiplatform **direct Xcode integration**; Swift Export integration (`embedSwiftExport`) is not supported for this SDK setup. The Xcode build phase must call `:shared:embedAndSignAppleFrameworkForXcode`.

Run this one-time setup from the repository root on macOS if the linkage package has not yet been generated, or after changing native SwiftPM dependencies:

```bash
./gradlew --stop

GRADLE_PROJECT_PATH=":shared" \
XCODEPROJ_PATH="$PWD/iosApp/iosApp.xcodeproj" \
./gradlew -p "$PWD" --no-configuration-cache \
  :shared:integrateEmbedAndSign :shared:integrateLinkagePackage
```


Stop existing Gradle daemons first, then pass both variables inline to the Gradle process and disable the configuration cache for this one-time integration. The `GRADLE_PROJECT_PATH` variable is required by `:shared:integrateEmbedAndSign`; `-p "$PWD"` makes the repository root explicit. The root `gradle.properties` enables `kotlin.mpp.enableCInteropCommonization=true` for the custom CommonCrypto cinterop used by the iOS targets.

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
- The Firestore letter adapter encodes a drawing into the `drawingJson` string field under `chats/{chatId}/letters/{letterId}`; it must not upload images or use Firebase Storage.
- Letter creation/expiry timestamps and invite validation must be controlled by trusted backend logic, not only by the client clock.
- The target retention period for letters is seven days, with Firestore TTL configured on the expiry field.
- Client-side validation is not a substitute for Firestore Security Rules and server-side authorization.
