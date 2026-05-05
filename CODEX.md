# Codex Instructions

Project: TT Score Pro, a table-tennis scorer for Wear OS / Pixel Watch with a companion phone/tablet display.

Keep this file current when project facts, workflows, device commands, or important implementation details change.

## Workspace

- Main Android project: `wear-table-tennis/`
- Watch module: `wear-table-tennis/app`
- Phone/tablet companion module: `wear-table-tennis/phone`
- Watch package/application id: `com.uwe.tabletennisscore`
- UI stack: Kotlin, Jetpack Compose, Wear Compose Material 3
- Minimum SDK: 30
- Target/compile SDK: 35

There are also root-level web/docs/pitch artifacts. Do not mix those with the Wear OS app unless the user specifically asks.

## Key Files

- Watch UI and app orchestration: `wear-table-tennis/app/src/main/java/com/uwe/tabletennisscore/MainActivity.kt`
- Match rules: `wear-table-tennis/app/src/main/java/com/uwe/tabletennisscore/MatchLogic.kt`
- Watch settings persistence: `wear-table-tennis/app/src/main/java/com/uwe/tabletennisscore/AppSettings.kt`
- Match state saver: `wear-table-tennis/app/src/main/java/com/uwe/tabletennisscore/MatchStateSaver.kt`
- Watch-phone sync: `wear-table-tennis/app/src/main/java/com/uwe/tabletennisscore/CompanionSync.kt`
- Phone/tablet companion UI: `wear-table-tennis/phone/src/main/java/com/uwe/tabletennisscore/phone/MainActivity.kt`
- Watch instrumentation tests: `wear-table-tennis/app/src/androidTest/java/com/uwe/tabletennisscore/TableTennisAppTest.kt`
- Watch unit tests: `wear-table-tennis/app/src/test/java/com/uwe/tabletennisscore/MatchLogicTest.kt`

## Working Rules

- Make minimal, scoped diffs and follow existing Compose/Kotlin patterns.
- Do not rewrite project structure unless explicitly requested.
- Prefer Kotlin/Gradle-native solutions.
- Do not change package names unless required.
- Be careful with the dirty worktree. Many files may already be modified or untracked.
- If a task concerns Play Store release, change only build/signing/release files unless the user asks for app behavior changes.
- If a task concerns Garmin, do not modify Wear OS code; create a separate `/garmin` folder or a porting plan.

## Build And Test

The system `java` may be Java 8, but Gradle requires Java 17+. Use Android Studio's bundled JDK:

```sh
cd wear-table-tennis
/usr/bin/env JAVA_HOME=/Applications/Android\ Studio.app/Contents/jbr/Contents/Home ./gradlew test
/usr/bin/env JAVA_HOME=/Applications/Android\ Studio.app/Contents/jbr/Contents/Home ./gradlew app:compileDebugAndroidTestKotlin
/usr/bin/env JAVA_HOME=/Applications/Android\ Studio.app/Contents/jbr/Contents/Home ./gradlew app:assembleDebug
```

For release work, also run:

```sh
/usr/bin/env JAVA_HOME=/Applications/Android\ Studio.app/Contents/jbr/Contents/Home ./gradlew app:bundleRelease
```

Connected Android tests require a connected watch/emulator:

```sh
/usr/bin/env JAVA_HOME=/Applications/Android\ Studio.app/Contents/jbr/Contents/Home ./gradlew app:connectedDebugAndroidTest
```

## Installing To Watch

`adb` may not be on PATH. Use:

```sh
/Users/uwesterr/Library/Android/sdk/platform-tools/adb devices -l
```

When multiple devices are attached, target the physical watch by transport id:

```sh
/Users/uwesterr/Library/Android/sdk/platform-tools/adb -t <transport_id> install -r app/build/outputs/apk/debug/app-debug.apk
/Users/uwesterr/Library/Android/sdk/platform-tools/adb -t <transport_id> shell am start -n com.uwe.tabletennisscore/.MainActivity
```

Recent known physical device: Pixel Watch 3, model `Pixel_Watch_3`, device `sol`. Transport ids can change, so always check `adb devices -l`.

## App Behavior To Preserve

- Table-tennis scoring.
- Singles and doubles match modes.
- Editable player/team names with speech input.
- Best-of-3 and best-of-5 match formats.
- Correct serve rotation, deuce behavior, set wins, match wins, and doubles opening-order prompts.
- Undo.
- Haptics, sounds, set/match cue effects, and keep-screen-on setting.
- Score/history screens with swipeable point-history charts.
- Phone/tablet companion sync and display.
- Privacy screen and local-only data model.

## Watch UI Notes

- Design for tiny round screens first. Text must not overlap or get clipped.
- Settings controls should remain readable on a Pixel Watch round viewport.
- Toggle rows use an obvious selected/unselected treatment and full `ON` / `OFF` labels.
- Hardware scoring setting must be easy to identify as on or off and have a reliable full-row tap target.

## Hardware Scoring

- The setting is `hardwareScoringEnabled` in `AppSettings`.
- Detection uses Wear OS stem buttons via `WearableButtons`.
- Two detected side buttons: first scores Me/Home, second scores Opponent/Away.
- One detected side button: single press scores Me/Home, double press scores Opponent/Away.
- No detected side buttons: rotary crown fallback scores Me/Home backward and Opponent/Away forward.
- Hardware scoring only applies on a live score screen, not in settings/prompts/completed screens.

## Output Format

For substantial changes, report:

- Summary
- Files changed
- Commands run
- Remaining manual steps, if any
