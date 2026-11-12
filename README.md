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

## CI and releases

- Every pull request to `master` and every push to `master` runs lint and unit tests, and builds a debug APK.
- Pushing a version tag such as `v1.2.3` builds a signed release APK and publishes it as a GitHub release.
  The version name is taken from the tag (`1.2.3`). The version code is `major * 10000 + minor * 100 + patch`.

The release workflow needs two repository secrets:

| Secret | Value |
| --- | --- |
| `RELEASE_KEYSTORE_BASE64` | The release keystore (`.jks`), base64 encoded |
| `RELEASE_KEYSTORE_PASSWORD` | The keystore password, which is also the key password |

To build a signed release APK locally, set `RELEASE_KEYSTORE_FILE`, `RELEASE_KEYSTORE_PASSWORD` and
`RELEASE_KEY_ALIAS`, then run `./gradlew assembleRelease -PappVersion=1.2.3`.
