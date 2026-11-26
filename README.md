# SIM Call Blocker

A small Android app for dual-SIM phones. It rejects every incoming call on one SIM or
eSIM you choose, and calls on your other SIM ring as normal.

Android can block individual numbers, or silence all calls with Do Not Disturb, but it
can't block all calls to just one SIM. This app fills that gap. It's useful for a
secondary line that only gets spam, such as a travel eSIM or a data SIM with a number.

## Requirements

- Android 12 or later
- A dual-SIM phone (two physical SIMs, or SIM + eSIM)

## Install

1. Download the latest `SimCallBlocker-vX.Y.apk` from
   [Releases](../../releases/latest).
2. Open it on your phone. If asked, allow your browser or file manager to install
   unknown apps.

Updates install over the previous version, and your settings are kept.

## Setup

Open **SIM Call Blocker** and follow the three steps:

1. **Allow call screening.** Tap *Set as call screening app* and choose SIM Call Blocker.
2. **Choose the SIM to block.** Grant the Phone permission, then pick the SIM whose calls
   you want blocked.
3. **Turn on blocking.**

Options:

- **Reject**: the call is ended at once. The caller is cut off or sent to voicemail, and
  you get no missed-call notification.
- **Silence**: the call still arrives and can be answered, but it never rings or vibrates.
- **Keep rejected calls in the call log**: untick this to hide rejected calls from your
  call log too.

The app shows how many calls it has blocked and lists the most recent ones.

## How it works

The app holds Android's *call screening* role, so Android asks it about each incoming
call. Android doesn't tell ordinary apps which SIM a call arrived on. So the app checks
which SIM is in the ringing state at that moment. If it's the SIM you chose, and no other
SIM is ringing, the call is rejected or silenced.

If the app can't tell for sure (nothing shows as ringing within a couple of seconds,
another SIM is ringing, or something goes wrong), it lets the call through. It is built
never to block calls on your other SIM.

## Limitations

- **Only one app can hold the call-screening role.** If you use Truecaller, Hiya or a
  similar app for call screening, this app replaces it in that role.
- **Calls from your saved contacts are not screened.** Android never passes them to
  third-party screening apps, so contacts calling the blocked SIM will still ring.
- **Voice calls only.** SMS and mobile data on the blocked SIM are unaffected.
- **Android's own blocked-numbers list still applies** to both SIMs, as before.

## Privacy

The app has no internet permission. Everything stays on your phone: no analytics, no
ads, no accounts.

## Troubleshooting

The **Last call checked** section shows the time, number and decision for the most
recent incoming call:

| What you see | Meaning |
| --- | --- |
| Empty after a call rang through | Android didn't ask the app. Either the caller is in your contacts, or step 1 isn't done (re-check it after system updates). |
| `blocked: chosen SIM was ringing` | Working as intended. |
| `allowed: another SIM was ringing` | The call was on your other SIM. |
| `allowed: no SIM showed as ringing…` | The app couldn't detect which SIM was ringing on this phone. Please open an issue with your phone model and Android version. |
| `allowed: chosen SIM not found` / `not active` | The SIM was removed or changed. Pick it again in step 2. |
| `allowed: blocking is switched off` | Turn on step 3. |

## Building from source

The app is plain Java with no dependencies beyond the Android SDK.

- **Android Studio:** open the project folder, let Gradle sync, then choose
  *Build → Build APK(s)*.
- **GitHub Actions:** fork the repo and open the **Actions** tab. Each push builds an APK,
  which you can download as the `SimCallBlocker-apk` artifact. Without signing secrets
  the APK is signed with a temporary debug key, so each new build has to replace the
  previous one by uninstalling first. To sign your own builds, add these repository
  secrets:
  - `KEYSTORE_BASE64`: a base64-encoded keystore
  - `KEYSTORE_PASSWORD`: its password
  - `KEY_ALIAS`: the key alias

  Pushing a tag such as `v1.1` that matches `versionName` in `app/build.gradle` also
  publishes a GitHub Release.

## Licence

MIT. See [LICENSE](LICENSE).
