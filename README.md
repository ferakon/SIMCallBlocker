# SIM Call Blocker

A tiny Android app (Android 10+) that rejects every incoming call on one chosen SIM or eSIM,
while calls on your other SIM ring normally. No internet permission, no ads, no tracking.

## How it works
Android lets one app hold the **call screening** role. For each incoming call, the system asks
that app's `CallScreeningService` what to do and tells it which phone account (SIM) the call
arrived on. This app compares that SIM with the one you picked and rejects or silences the call.

## Get the APK

**Option A, no computer needed (GitHub Actions)**
1. Create a new GitHub repository and upload the contents of this folder (keep the
   `.github/workflows/build.yml` file).
2. Open the repo's **Actions** tab. The "Build APK" workflow runs on every push (or press
   "Run workflow").
3. When it finishes, download the `SimCallBlocker-apk` artifact, unzip it, and open the APK
   on your phone.

**Option B, Android Studio**
Open this folder in Android Studio, let it sync, then *Build → Build APK(s)*.

## Set up on the phone
1. Install the APK (allow "install unknown apps" for your browser/files app when asked).
2. Open **SIM Call Blocker** and:
   - tap **Set as call screening app** and choose it,
   - grant the **Phone** permission,
   - pick your Spanish eSIM,
   - turn on **Block calls on the chosen SIM**.
3. Test by calling the Spanish number from another phone (one not saved in your contacts).

## Options
- **Reject**: the call is ended immediately (the caller usually hears it go to voicemail or
  get cut off). No missed-call notification.
- **Silence**: the call still comes in and can be answered, but never rings or vibrates.
- **Keep in call log**: untick to hide rejected calls from your call log as well.

## Things to know
- Only one app can be the call screening app. If you use Truecaller, Hiya or similar, this
  replaces it in that role.
- Android generally doesn't send calls from your saved contacts through a third-party
  screening app, so contacts on the Spanish SIM may still ring. For spam that is usually fine.
- SMS and data on that SIM are unaffected. This only touches voice calls.
- If calls stop being blocked after a phone update, open the app and check step 1 is still ticked.
