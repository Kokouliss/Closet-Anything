# Closet Anything

Where everything is. An Android app for a household (one person, a family or roommates) that keeps track of clothes, shoes, jewellery and anything else: which room, which space in it, which box, with a photo of each thing so it is easy to recognise. Available in Greek and English.

## What it does today

- **Households.** A household is a home and the people in it. Create one for yourself, or share it: every household has an invite code to type, a QR code to scan from the app, and a link to send. A Google account can also be invited by email address and sees the invitation when it signs in. The owner can remove members and make a new code.
- **Rooms and spaces.** Each household has rooms (My room, Hall, Storage), and each room has the spaces where things are kept (Wardrobe, Top shelf, Under the bed).
- **Things and boxes.** A space holds things: a name, a photo taken with the camera or picked from the gallery, a note, how many, and tags. A thing can be a box, which holds more things. Things move between spaces and in and out of boxes; a box takes its contents along.
- **Search.** Finds things by name, note or tag across the household, whatever the accents and capitals, and shows where each one is: room › space › box.
- **Sign-in** with Google, or with an email and password (with a new-password link and an email to confirm the address). The data follows the account, so a new phone shows the same things. Everything works offline and syncs later.
- **Settings:** your name, light or dark, English or Greek (following the phone until one is chosen), sign out, and **Delete account**, which the Play Store requires of apps with accounts.

## Set up Firebase (once, about ten minutes)

The app stores everything in a Firebase project that you own. The free Spark plan is enough and no payment card is needed.

1. Open the [Firebase console](https://console.firebase.google.com) and create a project, for example "Closet Anything". Google Analytics can stay off.
2. Go to **Build > Authentication > Sign-in method** and enable **Google**. Pick a support email when asked. Then press **Add new provider** and enable **Email/Password** (leave "Email link" off). The emails Firebase sends sign with the project's public-facing name, which you set under **Project settings > General**.
3. Go to **Build > Firestore Database** and create a database in **production mode**. Choose a location close to you, for example `eur3 (Europe)`. The location cannot be changed later.
4. In Firestore, open the **Rules** tab, replace the text with the contents of `firebase/firestore.rules`, and press **Publish**. Without this step the app cannot read or write anything. Repeat this step whenever `firebase/firestore.rules` changes.
5. Go to **Project settings > Your apps**, add an **Android app**, and enter:
   - Package name: `com.kokouliss.closetanything`
   - SHA-1: `5E:BF:9C:15:BD:EF:A2:D2:88:F0:B2:39:01:8C:A5:5A:48:5E:2A:E8`. The console calls this field optional, but Google sign-in does not work without it. To add it later, open **Project settings > Your apps**, pick the Android app and press **Add fingerprint**.
6. Download `google-services.json` and put it in the `app` folder, so the file is `app/google-services.json`. Do this after step 2, because the file must contain the Google sign-in client id. The file is ignored by git on purpose.
7. For the GitHub builds, add the same file as a repository secret named `GOOGLE_SERVICES_JSON` (its whole text) under **Settings → Secrets and variables → Actions**.

The SHA-1 belongs to the debug key in `app/debug.keystore`, which is committed so that every machine builds an app Google sign-in accepts. It is the same key as in List Anything and Shelf Anything. A release build signed with another key needs that key's SHA-1 added in step 5 as well.

If you choose a Google account and land back on the sign-in screen, the SHA-1 is missing or belongs to another key. Add it as described in step 5 and try again after a few minutes. The app does not need to be rebuilt.

### Invite links

The invite link and the QR code point at `https://closet-anything.web.app/join/<code>`. Scanned from inside the app, the QR code joins right away. For the link to open the app when tapped in a chat or scanned with the phone's camera, a site at that host has to vouch for the app, as List Anything's `firebase/hosting` does: a Firebase Hosting site with `.well-known/assetlinks.json` for the package `com.kokouliss.closetanything` and the signing key's SHA-256. If your Firebase project gets another hosting domain, change the host in `InviteCode` (core/domain) and in `app/src/main/AndroidManifest.xml` together.

## Download

Every pull request and every merge to `main` builds the app and puts it on the [Releases](https://github.com/Kokouliss/Closet-Anything/releases) page. Open the page on an Android phone (Android 9 or newer), download the `.apk` and open it to install, allowing installs from the browser when Android asks.

- While a pull request is open it has a pre-release named **PR #number**, replaced each time the pull request changes.
- When it is merged, that pre-release goes away and a numbered release (`v0.1.<build>`) takes its place. The [latest release](https://github.com/Kokouliss/Closet-Anything/releases/latest) is always the newest merged build.

Every build has a higher build number than the one before and the same signing key, so it installs over the one you have. The build number is also the app's version, so a crash report in the Firebase console (Crashlytics) names the build it came from.

Until a real signing key is set up (as in List Anything's README, under "Signing key"), downloads are signed with the shared debug key, which is fine for trying the app out but not for the Play Store.

## Build and run

Open the folder in a recent Android Studio and press **Run**, or build from a terminal with JDK 17 or newer:

```
./gradlew :app:assembleDebug
```

The app is then at `app/build/outputs/apk/debug/app-debug.apk`. It needs Android 9 or newer.

A build without `app/google-services.json` still works, but only shows a "Setup needed" screen.

## Tests

```
./gradlew test
```

The unit tests cover search and locations, the palette's contrast, the photo sizing and the sign-in error handling.

The Firestore security rules have their own tests, which run against the local Firestore emulator (Node 20 and Java 21 or newer):

```
cd firebase && npm install && npm test
```

## How it is built

Kotlin and Jetpack Compose, with Hilt, Navigation 3 and Firebase (Auth and Firestore), laid out like List Anything and Shelf Anything:

| Module | What it holds |
| --- | --- |
| `app` | Start-up, sign-in gate, navigation |
| `feature/auth` | Sign-in screen (Google, email and password) and Settings |
| `feature/household` | Households and invites, rooms and spaces, things and boxes, photos, search |
| `core/domain` | Models and repository interfaces, plain Kotlin |
| `core/data` | Firebase implementations of the repositories, photo compression |
| `core/ui` | Theme, icons and shared components |
| `build-logic` | Gradle convention plugins |
| `firebase` | Firestore security rules and their tests |

The look: the AnyKind family's ink and paper neutrals, with the teal of the logo's triangle as the lead colour. The colours live in `core/ui/.../theme/Color.kt`, and a unit test checks every text and background pair for contrast (WCAG AA).

## Known limits

- Photos are stored inside Firestore, shrunk to at most 1280 pixels, so the project needs no billing account. Move them to Firebase Storage if full-size photos are wanted.
- Deleting a room, a space, a box or a household deletes what is inside it from the phone that asks, so a phone that is offline only deletes what it has in its cache. A Cloud Function (which needs a billing account) would finish the job on the server.
- A box cannot be put inside another box.
- Invite links open in the browser until the hosting site described above is deployed; the code and the in-app QR scan work without it.
