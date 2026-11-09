# Accounts and sync: Firebase setup

The app signs in with Google and keeps each account's data in Firestore. The Firebase project is not part of the
repository; the app builds without it and then simply hides the Account section in Settings.

1. Firebase console -> Add project (Analytics is not needed).
2. Build -> Authentication -> Get started -> Sign-in method -> Google -> Enable, pick a support email, Save.
3. Project settings -> Your apps -> Add app -> Android. Package name `com.mutqan.quran`.
4. Add the SHA-1 of every key you install with: the debug key (`keytool -list -v -keystore ~/.android/debug.keystore
   -alias androiddebugkey -storepass android`) and the release key (`keytool -list -v -keystore release.keystore`).
5. Download `google-services.json` and put it in `app/` (it is git-ignored). Download it again after adding a
   SHA-1 or enabling Google sign-in, so it contains the web client.
6. Build -> Firestore Database -> Create database (production mode), then Rules -> paste `firestore.rules` -> Publish.
7. GitHub releases: add a repository secret `GOOGLE_SERVICES_JSON` with the file's whole content.
