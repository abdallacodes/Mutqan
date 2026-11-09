# Google Play Console — answers to give

Written 2026-11-09 from what the code does. Where a choice is the owner's, it says so. Re-check against the code before each submission.

## Create app (the form already open)
- App name: Mutqan (the store name per language is in `listing-ar.md` / `listing-en.md`)
- Default language: **Arabic** (the form defaults to English (United States); change it)
- App or game: **App**. Free or paid: **Free** (a free app can never become paid)
- Package name: `com.mutqan.quran`

## Store settings
- Category: Books & Reference (alternative: Education)
- Contact email: support.mutqan@gmail.com. Website: https://abdallacodes.github.io/Mutqan/
- Privacy policy URL: https://abdallacodes.github.io/Mutqan/privacy.html

## App content
- **App access:** all functionality is available without signing in (the account is optional).
- **Ads:** no ads.
- **Advertising ID:** not used (the merged manifest has no `AD_ID` permission).
- **Content rating (IARC questionnaire):** reference/educational app; no violence, sexual content, drugs, gambling or user-to-user sharing; no location; no purchases. Expect "Everyone / 3+".
- **Target audience:** owner's choice. Recommended: 13 and over (not child-directed), which avoids the Families policy. Choosing an age group under 13 brings the Families requirements.
- **Government / financial / health / news app:** none apply.
- **Data safety:** see below.
- **Account deletion:** in the app, Settings → Account → Delete my account. Web page: https://abdallacodes.github.io/Mutqan/delete-account.html

## Data safety
- Collects or shares data? **Yes** (only if the user signs in, which is optional).
- All data encrypted in transit? **Yes.** Users can request deletion? **Yes** (in-app and the web page above).
- Shared with third parties: **No.** (Google's Firebase processes it for the app; that is not sharing under Play's definition.)

| Data type | Collected | Optional | Purpose |
|---|---|---|---|
| Personal info → Email address | Yes | Yes | Account management |
| Personal info → Name | Yes | Yes | Account management |
| Personal info → User IDs | Yes | Yes | Account management, App functionality |
| App activity → App interactions (revision logs, coverage, plans, exercise results) | Yes | Yes | App functionality |
| App activity → Other user-generated content (saved passages, subject titles, group notes) | Yes | Yes | App functionality |
| Device or other IDs (a random per-install id used for sync, and Firebase's installation id) | Yes | Yes | App functionality |

Not collected: location, financial info, health, messages, photos/videos, audio recordings, files, contacts, calendar, web browsing, diagnostics/crash logs.

Owner to decide: recitation audio streams from `audio-cdn.tarteel.ai` and fonts from GitHub, so those servers see the user's IP address and the file asked for. Play's form is about data the *developer* collects; these are requests made to third parties, and the privacy policy says so. Declare "Device or other IDs / IP" too only if you want the most conservative answer.

## Foreground services (App content → Foreground service permissions)
- `mediaPlayback`: "Plays Quran recitation the user starts, with notification controls, so it continues when the screen is off or the app is in the background."
- `dataSync`: "Downloads recitation audio files the user chose to save for offline listening, with a progress notification, so the download continues when the app is closed." (Android 15 limits this type to about 6 hours a day; downloads are short.)

## Assets to prepare
- App icon 512×512 PNG (no transparency, under 1 MB).
- Feature graphic 1024×500.
- Phone screenshots: 2 to 8, PNG or JPEG, **16:9 or 9:16 only**, each side 320 to 3840 px. The owner's Pixel screenshots are 1080x2424 (about 9:20) and would be rejected, so they are framed onto 1080x1920. For promotion eligibility give at least 4, each side at least 1080 px. Tablet (7" and 10"), desktop and Android XR sections are optional.
- Ready in `store/assets/`: `icon-512.png`, `feature-graphic-1024x500.png`, and six phone screenshots `screens/1-today.png` to `6-surahs.png` (1080x1920, upload in this order). Retake or reword with `make_screenshots.py`.
- Short and full descriptions: `listing-ar.md`, `listing-en.md`.

## Release steps
1. Fill the forms above. Create the app. In Setup → App signing choose Play App Signing, and add its SHA-1 to Firebase (project `mutqan---quran`, app `com.mutqan.quran`).
2. Upload the bundle `Mutqan-<version>.aab` (CI artifact *Mutqan-Play-Bundle* on a tag build).
3. A personal developer account created after November 2023 needs a **closed test with at least 12 testers opted in for 14 days** before production access can be requested. An organization account is exempt.
4. Production release, staged rollout recommended.
