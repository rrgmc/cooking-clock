# CLAUDE.md

Guidance for Claude Code in this repository. The product requirements are in
[docs/PROMPT.md](docs/PROMPT.md); build, CI and release steps for people are in [README.md](README.md).

## Project

Cooking Clock is a single-module Android app (`app/`) in Kotlin with Jetpack Compose and Material 3.
The package is `dev.cookingclock`. minSdk 26, compile and target SDK 36. The theme is always light.

## Commands

```bash
./gradlew assembleDebug                        # debug APK
./gradlew testDebugUnitTest                    # JVM unit tests (app/src/test)
./gradlew lintDebug                            # Android lint, must stay free of errors
./gradlew lintDebug testDebugUnitTest assembleDebug   # what CI runs
```

`JAVA_HOME` points to JDK 21, which Gradle uses. The `java` on `PATH` is Java 8; do not rely on it.
The Android SDK is in `%LOCALAPPDATA%\Android\Sdk`; `local.properties` (not committed) points to it.

## Architecture

- `data/Repository` is the single in-memory store (`StateFlow<AppState>`), saved as JSON in
  shared preferences. There is no database.
- `data/ClockController` is the only place that changes state. Every change ends in `sync()`,
  which reschedules alarms, starts the foreground service when needed and refreshes the widget.
  New features that change state must go through it.
- A clock stores `endAt` as wall-clock epoch millis while running, and `remainingMs` otherwise.
  `CookingClock.remainingAt(now)` gives the displayed value.
- `alarm/AlarmScheduler` sets one `AlarmManager.setAlarmClock` alarm per running clock.
  `AlarmReceiver` and `BootReceiver` call `ClockController.fireExpired`.
- `service/TimerService` is a `specialUse` foreground service. It runs while a clock is running
  or ringing, owns the ongoing and alarm notifications, plays the alarm through `Ringer`, and
  checks once a second for expired clocks as a backup to the exact alarms.
- `service/ClockViews` builds the RemoteViews rows shared by the notification and the widget.
  Running clocks use a count-down `Chronometer`, so the system updates them without app wake-ups.
- `widget/ClockWidgetProvider` is a plain RemoteViews widget with no collection adapter.
- `ui/` holds the Compose screens. Navigation between the main and settings screens is a
  boolean in `MainActivity`; there is no navigation library.

Only `material-icons-core` is a dependency. Icons it lacks (pause, stop, light bulb) are vector
drawables in `res/drawable`.

## Workflow

- `master` is protected: every change goes through a pull request, and the `build` check must
  pass. Direct pushes to `master` are rejected, also for admins. Branches are deleted on merge.
- Releases: pushing a tag `vX.Y.Z` runs `.github/workflows/release.yml`, which builds an APK
  signed with the `rrgmc` key from repository secrets and publishes a GitHub release. The version
  name and code come from the tag (`-PappVersion`); there is no version number in the source.
- Release signing reads `RELEASE_KEYSTORE_FILE`, `RELEASE_KEYSTORE_PASSWORD` and
  `RELEASE_KEY_ALIAS` from the environment. Never commit a keystore or a password.

## Windows pitfalls

- The user's terminal is Windows PowerShell 5.1: no `&&`, no `base64`. Give PowerShell commands
  with `;` or on separate lines, or say explicitly that a command is for Git Bash.
- `gradlew` must keep its executable bit in git (`git update-index --chmod=+x gradlew`), or CI on
  Linux fails with "Permission denied".
- Text files the user creates on Windows can start with a UTF-8 byte-order mark. Strip it before
  using a file's content as a password.
- In Git Bash, `adb shell` paths such as `/sdcard/...` are rewritten by MSYS. Set
  `MSYS_NO_PATHCONV=1` first.

## Testing on the emulator

AVD `Medium_Phone_API_36.1` exists. Start it with `emulator -avd Medium_Phone_API_36.1`, install
the debug APK with `adb install -r`, and grant `POST_NOTIFICATIONS` with `adb shell pm grant`.
`uiautomator dump` finds most Compose nodes by text or content description, but not the text of
extended floating action buttons; tap those by coordinates. Alarm sound cannot be heard on the
emulator.
