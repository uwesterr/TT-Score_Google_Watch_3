---
title: TT Score Pro Play Store Release Guide
---

# TT Score Pro Play Store Release Guide

This file is the repo-side checklist for preparing TT Score Pro for a public Play Store release.

## 1) Release signing

Use Play App Signing and an upload key.

Release signing values can be provided through:

- environment variables, or
- `local.properties`

Supported keys:

- `TT_SCORE_UPLOAD_STORE_FILE`
- `TT_SCORE_UPLOAD_STORE_PASSWORD`
- `TT_SCORE_UPLOAD_KEY_ALIAS`
- `TT_SCORE_UPLOAD_KEY_PASSWORD`

A sample file is included at:

- `release-signing.sample.properties`

Recommended local workflow:

```bash
cd wear-table-tennis
cp release-signing.sample.properties /tmp/tt-score-release.properties
# fill in real values locally, or copy them into local.properties / env vars
./gradlew test
./gradlew assembleDebug
./gradlew bundleRelease
```

If release signing values are not configured, the Gradle files fall back to debug signing so local release builds still work. Use a real upload key before shipping publicly.

## 2) Privacy policy

Repo copy:

- `docs/privacy-policy.md`

Before public release:

- publish this policy on a public, non-editable URL
- add that URL in Play Console
- keep the in-app privacy policy screens on watch and phone
- replace the placeholder contact section with a real support or privacy contact

## 3) Play Console app content

### Ads

- Declare: **No ads**

### App access

- Declare: **No login required**
- If review ever needs a paired watch + phone flow, provide simple reviewer instructions in Play Console

### Data safety working draft

Likely declaration baseline for the current app:

- account creation: **No**
- data collected: **Yes, limited on-device app data**
- developer backend sharing: **No**
- data types involved:
  - personal info: **Player names entered by the user**
  - app activity / app interactions: **Match state and score stored locally**
- data handling purpose:
  - app functionality
- encryption in transit:
  - **Device-to-device sync only via Google Play Services / Wear OS Data Layer**
- deletion:
  - users can clear data by uninstalling or clearing app storage

Recheck these answers against the final shipping build before submission.

## 4) Listing copy draft

### App title

TT Score Pro

### Short description

Standalone Wear OS table-tennis scorer with a live phone scoreboard companion.

### Full description

TT Score Pro is a fast, touch-first table-tennis scoring app for Wear OS, with an optional Android phone companion that mirrors the live match.

Features:

- singles and doubles match support
- best of 3 or best of 5
- serve rotation, deuce, set point, and match point cues
- undo
- player name editing on watch
- live phone scoreboard with large landscape mode
- spoken score and match announcements on phone

TT Score Pro is designed to stay usable directly on the watch, with the phone acting as an optional companion display.

## 5) Screenshots and assets

Use Play-compatible screenshots for the Wear listing:

- no device frame
- only the app UI
- 1:1 aspect ratio

Recommended screenshot set:

- watch singles in-progress score screen
- watch doubles in-progress score screen
- watch settings screen
- phone companion landscape scoreboard mode

Current repo screenshots that may be useful as a starting point:

- `docs/emulator-screenshot.png`
- `docs/emulator-screenshot-nine-nine.png`
- `docs/emulator-screenshot-settings.png`
- `docs/phone-landscape-screenshot.png`

Verify that the final uploaded assets match current app behavior exactly.

## 6) Final pre-submit checklist

- bump version if needed
- run:
  - `./gradlew test`
  - `./gradlew assembleDebug`
  - `./gradlew bundleRelease`
- validate singles on watch
- validate doubles on watch
- validate phone scoreboard mode
- validate privacy policy screens on watch and phone
- validate round-watch layouts on small and large round devices
- upload to **internal testing** first
- then promote to **closed testing**
- then publish to production
