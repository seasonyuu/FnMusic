# Choosing verification

Use checks that exercise the changed behavior. The commands below are alternatives,
not a checklist to run for every edit. Build prerequisites are in [README.md](../README.md).

## Local checks

| Change | Starting point |
| --- | --- |
| Documentation only | Review links and command examples; `git diff --check` |
| Android module logic | `./gradlew :data:testDebugUnitTest` (replace `:data` with the affected Android module) |
| One regression | Add `--tests '*RelevantTestClass'` to that module's unit-test task |
| App wiring or resources | `./gradlew :app:assembleDebug` plus tests for affected behavior |
| Shared changes spanning Android modules | `./gradlew testDebugUnitTest` |
| Python verification tools | `python3 -m unittest discover -s scripts -p 'test_verify_*.py' -v` |

`:core:model` is a Kotlin/JVM module, so its test task is `:core:model:test`,
not `testDebugUnitTest`. An APK build verifies compilation and packaging, not
runtime behavior. Python discovery includes test classes declared after a file's
`unittest.main()` block.

Normal lyric unit tests use fixtures; leave `FNMUSIC_LYRICS_SMOKE` unset for local
verification. Dependency resolution may still require network access. Live NAS
verifiers and opt-in lyric smoke checks are separate integration checks, not part
of the local fixture suite.

## Device checks

Follow the [app and data preservation rules](../AGENTS.md#emulator-app-and-data-preservation).
Use an explicitly designated test emulator for any test that may uninstall apps
or clear data, including when cleanup behavior is unknown. Existing connected
emulators are not automatically disposable.

Select and verify the dedicated emulator's serial before running a device task:

```sh
adb devices -l
# Set TEST_EMULATOR_SERIAL to the verified dedicated emulator's serial first.
ANDROID_SERIAL="${TEST_EMULATOR_SERIAL:?Set a dedicated test emulator serial}" \
  ./gradlew :feature:music:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.seasonyuu.fnmusic.feature.music.LocalOutputSheetTest
```

Gradle device-test cleanup can remove installed packages. Reinstalling afterward
does not restore deleted data. Keep instrumentation class filters scoped to the
module that contains them.

For feature-specific verification:

- [Local audio output](local-audio-output.md): routing tests and physical-output acceptance.
- [Liquid menu](liquid-menu.md#repeatable-verification): rendering and interaction matrix.
  Its script currently selects the first connected device for each API level and
  does not honor `ANDROID_SERIAL`; ensure all eligible connected devices are
  dedicated test emulators before running it.
- [AirPlay](airplay.md#build-and-offline-checks): native/offline checks and separately
  enabled receiver verification. Advancing playback counters alone do not prove audible output.

If a safe test device is unavailable, complete relevant local checks and report
the device-validation gap. If an in-place installation fails, report the error
and known cause before corrective action; do not resolve it by uninstalling or
clearing the daily-use app.

## Completion evidence

Report the checks actually run and their outcome, plus any skipped checks and
remaining manual acceptance. Rerun affected checks after a fix; a passing run need
not be repeated unless a later change invalidates it.
