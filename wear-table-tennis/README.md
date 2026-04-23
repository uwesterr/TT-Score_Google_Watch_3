# TT Score for Wear OS

Native Wear OS table-tennis scorer for Me vs Opponent.

## Match Rules

- Best of 3 sets.
- Each set goes to 11 points.
- A set must be won by 2 points.
- Service changes every 2 points before 10-10.
- From 10-10 onward, service changes every point.
- The app asks who serves first at the start of every set.

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

Then select a Wear OS emulator or connected Pixel/Google Watch and run the `app` configuration.

## Test

From this folder, once Gradle/Android tooling is available:

```sh
gradle test
gradle connectedAndroidTest
```

If Android Studio generates a Gradle wrapper for the project, use `./gradlew` instead of `gradle`.

This environment did not have Gradle or ADB installed on PATH, so the project is scaffolded for Android Studio import rather than verified locally with a full Android build.
