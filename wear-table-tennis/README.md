# TT Score Pro for Wear OS

Native Wear OS table-tennis scorer for Me vs Opponent.

## Match Rules

- Best of 3 sets.
- Each set goes to 11 points.
- A set must be won by 2 points.
- Service changes every 2 points before 10-10.
- From 10-10 onward, service changes every point.
- The app asks who serves first in set 1.
- Later sets automatically alternate the starting server.
- The Pro version adds editable player names, speech input for names, best-of-3 or best-of-5 match format, haptics/display settings, and live cue banners.
- Swipe left to open point-history charts:
  - during a set: current-set chart
  - after a set: completed-set chart
  - after a match: final-set chart

## Project

- Package: `com.uwe.tabletennisscore`
- UI: Kotlin + Jetpack Compose for Wear OS + Wear Material 3
- Minimum SDK: 30
- Target SDK: 35

## Run

Open this folder in Android Studio:

```text
wear-table-tennis/
```

Then select a Wear OS emulator or connected Pixel Watch / Samsung Galaxy Watch and run the `app` configuration.

On the watch:

- Use `Set` to open settings.
- Tap `Mic` beside a player name to dictate it.
- Swipe left from the score screen to inspect the current set chart.
- Swipe left from the set-complete or match-over screen to inspect the finished set chart.

## Test

From this folder, once Gradle/Android tooling is available:

```sh
gradle test
gradle connectedAndroidTest
```

If Android Studio generates a Gradle wrapper for the project, use `./gradlew` instead of `gradle`.

This environment did not have Gradle or ADB installed on PATH, so the project is scaffolded for Android Studio import rather than verified locally with a full Android build.
