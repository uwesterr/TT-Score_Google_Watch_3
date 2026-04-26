# TT Score Pro for Wear OS Watches

Wear OS table-tennis scoring app for fast point tracking during real matches.

![TT Score Wear OS Emulator Screenshot](wear-table-tennis/docs/emulator-screenshot.png)
![TT Score Wear OS Emulator Screenshot (Current State)](wear-table-tennis/docs/emulator-screenshot-current.png)
![TT Score Wear OS Emulator Screenshot (Settings)](wear-table-tennis/docs/emulator-screenshot-settings.png)

## Functionality

- Match format: best of 3 sets.
- Set rules: first to 11 points, win by 2 points.
- Serve rules:
  - service changes every 2 points before deuce
  - at 10-10 and above, service changes every point
- At the start of the match, the app asks who serves first in set 1.
- In later sets, first serve switches automatically to the player who did not start the previous set.
- Main scoring screen shows:
  - current points (`Me` vs `Opponent`)
  - current server
  - set score (`0-0`, `1-0`, etc.)
- Quick controls:
  - `+ Me`
  - `+ Opp`
  - `Undo`
  - `New` (reset match during play)
- Pro features:
  - dedicated settings screen
  - editable player names
  - speech input for `Me` and `Opponent` names in settings
  - best-of-3 or best-of-5 match format
  - rule-aware cue banners for serve change, deuce, set point, match point, and change ends
  - haptics toggle
  - display always on toggle
  - swipeable point-history charts
- Swipe left on the watch to open chart screens:
  - during a set: current-set point progression
  - after a set: completed-set point progression before `Next set`
  - after a match: final-set point progression before `New match`
- Keeps the watch display on while the app is in the foreground.
- Shared large-round layout tuned for both Pixel Watch 3 and Samsung Galaxy Watch7 44mm (`SM-L310`).

## Requirements

- Android Studio (latest stable recommended).
- Android SDK with:
  - `platform-tools` (ADB)
  - Wear OS emulator image (if testing in emulator)
- Java 17 runtime (Android Studio bundled JBR works).
- For physical watch install:
  - Wear OS watch
  - Tested target sizes:
    - Google Pixel Watch 3
    - Samsung Galaxy Watch7 44mm (`SM-L310`)
  - Developer options enabled on watch
  - ADB debugging enabled
  - Wireless debugging enabled (`Debug over Wi-Fi` on some Samsung One UI builds)
  - Watch and computer on the same Wi-Fi network

## Install And Run (Step By Step)

### 1) Get the project

```bash
git clone https://github.com/uwesterr/TT-Score_Google_Watch_3.git
cd TT-Score_Google_Watch_3/wear-table-tennis
```

### 2) Open in Android Studio

1. Open Android Studio.
2. Select `Open`.
3. Choose the `wear-table-tennis` folder.
4. Wait for Gradle sync to finish.

### 3) Run on Wear emulator

1. Android Studio -> `Tools` -> `Device Manager`.
2. Create/start a `Wear OS Large Round` virtual device.
3. Select the emulator in the run target dropdown.
4. Run the `app` configuration.

### 4) Run on a real Wear OS watch (wireless ADB)

#### Option A: Samsung Galaxy Watch7 44mm (`SM-L310`)

On the watch:

1. Wake the watch and open `Settings`.
2. Tap `Connections`.
3. Tap `Wi-Fi`.
4. Turn Wi-Fi on if needed.
5. Choose the same Wi-Fi network the computer is using.
6. Enter the Wi-Fi password if prompted.
7. Go back to the main `Settings` screen.
8. Tap `About watch`.
9. Tap `Software`.
10. Tap `Software version` 5 times.
11. Wait for the toast confirming Developer options are enabled.
12. Go back to `Settings`.
13. Tap `Developer options`.
14. Turn on `ADB debugging`.
15. Confirm the warning prompt if one appears.
16. Turn on `Wireless debugging` or `Debug over Wi-Fi` (Samsung wording varies by One UI version).
17. Tap the wireless debugging entry itself.
18. Tap `Pair new device` or `Pair device with pairing code`.
19. Keep that screen open.
20. Note the following from the watch:
    - the pairing IP address and port
    - the 6-digit pairing code
21. After pairing is complete, go back one screen and note the normal connection IP address and port shown for wireless debugging.

#### Option B: Google Pixel Watch 3

On watch:

1. `Settings` -> `System` -> `About` -> `Versions`.
2. Tap `Build number` 7 times (enable Developer options).
3. Go to `Settings` -> `Developer options`.
4. Enable `ADB debugging`.
5. Enable `Wireless debugging`.
6. Open `Pair new device` and note pairing code.

#### Pair and install from the computer

On the computer:

1. Open `Terminal`.
2. Change into the Wear project folder:

```bash
cd /Users/uwesterr/Documents/New\ project/wear-table-tennis
```

3. Define the local ADB path:

```bash
ADB="/Users/uwesterr/Library/Android/sdk/platform-tools/adb"
```

4. Ask ADB to discover the watch:

```bash
$ADB mdns services
```

5. In the output, find the current `_adb-tls-pairing._tcp` entry for the watch and use that IP:port for pairing:

```bash
$ADB pair <PAIRING_IP:PAIRING_PORT>
```

6. When Terminal asks for `Enter pairing code:`, type the 6-digit code from the watch and press Enter.
7. Wait for the `Successfully paired` message.
8. Back in the watch's wireless debugging screen, find the regular connection IP:port.
9. Connect to the watch:

```bash
$ADB connect <CONNECT_IP:CONNECT_PORT>
$ADB devices
```

10. Confirm the watch appears in the `adb devices` list as `device`.
11. Install the app:

```bash
env JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" \
ANDROID_SERIAL="<CONNECT_IP:CONNECT_PORT>" \
./gradlew :app:installDebug
```

12. Launch the app:

```bash
$ADB -s <CONNECT_IP:CONNECT_PORT> shell am start -n com.uwe.tabletennisscore/.MainActivity
```

13. The app should open on the watch on the `Serve first?` screen.

If the watch does not connect the first time:

1. Make sure the watch and computer are still on the same Wi-Fi.
2. Turn `Wireless debugging` off and back on on the watch.
3. Open `Pair new device` again.
4. Use the fresh pairing port and fresh 6-digit code.
5. Ignore stale ports shown in old Android Studio popups and use the current values from the watch or `adb mdns services`.

### 5) Manual smoke test on the watch

1. Launch the app.
2. Choose who serves first in set 1.
3. Tap both scoring buttons and confirm points update immediately.
4. Confirm service changes every 2 points before `10-10`.
5. Get to `10-10` and confirm service changes every point.
6. Finish set 1, tap `Next set`, and confirm set 2 starts immediately with the opposite starting server.
7. Test `Undo`.
8. Swipe left during the set and confirm the chart screen appears.
9. On the set-finished screen, swipe left and confirm the last-set chart appears.
10. On the match-over screen, swipe left and confirm the final-set chart appears.
11. Open `Settings`, tap the `Mic` button next to a player name, and confirm speech input fills the field.
12. Test `New`.
13. Confirm the screen stays on while the app is active in the foreground.

## Build And Test

From `wear-table-tennis`:

```bash
env JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew test
env JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :app:assembleDebug
```
