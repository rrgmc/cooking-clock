# cooking-clock

Cooking Clock is an Android app for running several kitchen countdown timers at once.
The requirements are in [docs/PROMPT.md](docs/PROMPT.md).

## Features

- Several countdown clocks, one per row: name, time, start or pause, reset, delete.
- Tap the time to add or remove 1 or 5 minutes from the current value.
- Tap the name to type a new name or pick a saved one. Saved names can be deleted.
- The menu on each row sets a background color and an alarm profile.
- Alarm profiles (settings screen) choose a sound and whether to vibrate. A default profile always exists.
- Exact alarms fire even when the app is closed or the phone is idle.
- An ongoing notification lists every active clock. Rows get smaller as clocks are added.
- A home screen widget lists the active clocks.
- "Clear all" and every delete ask for confirmation. The light bulb button keeps the screen on.

## Build

Requirements: JDK 17 or later and the Android SDK (platform 36).

```bash
./gradlew assembleDebug
```

The APK is written to `app/build/outputs/apk/debug/app-debug.apk`.
