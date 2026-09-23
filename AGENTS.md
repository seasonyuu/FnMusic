# Contribution Guidelines

## Task Scope and Context

- Complete the requested behavior and relevant verification; fix failures caused by the change before handing it back. Report unrelated failures and unavailable checks without expanding into unrelated refactors.
- Load documentation when it applies: [docs/development.md](docs/development.md) for setup and module boundaries; [api.md](api.md) for fnOS protocol changes; [third_party/README.md](third_party/README.md) for pinned dependencies and adapters.
- For UI contracts, use [liquid-menu.md](docs/liquid-menu.md), [appearance-theme-colors.md](docs/appearance-theme-colors.md), or [cover-placeholders.md](docs/cover-placeholders.md) as relevant. For output routing, use [local-audio-output.md](docs/local-audio-output.md) or [airplay.md](docs/airplay.md).

## Validation

- Choose checks for the changed behavior; see [docs/testing.md](docs/testing.md) for commands and device boundaries. Documentation-only changes need link/command review and `git diff --check`, not Gradle tests.
- Local fixture tests and builds may run without repeated approval. Fix change-related failures and rerun affected checks; broaden validation when shared behavior or unresolved failures justify it.
- Treat live NAS, online-provider, and receiver verification separately from fixture tests. Use them when the task authorizes that external interaction; a local-test request does not authorize live writes or playback.

## Commit Standards

- Write commit subjects and bodies in English.
- Use the Conventional Commits format: `<type>(<scope>): <imperative description>`.
- Keep each commit focused on one coherent change. Separate implementation, UI changes, and tests when they can be reviewed independently.
- Add a `Signed-off-by` trailer to every commit. Generate it from the Git identity used for that commit (for example, with `git commit -s`); do not copy a fixed name or email from this document.
- Add a `Co-authored-by` trailer identifying the code agent that actually produced the change. Resolve the agent's current name and email from the active runtime or task context, and do not hardcode a particular agent identity in this document.
- Preserve both trailers when amending or rebasing commits, and verify that their names and emails match the identities used for the current commit.
- Before committing, review the staged diff, run `git diff --check`, and complete the relevant validation above. Report skipped or unavailable checks and why.
- Do not commit credentials, cookies, tokens, local connection settings, generated reports, databases, keystores, or other machine-specific files. Keep those files ignored and use `.env.example` for empty configuration placeholders.

## GitHub Synchronization

- The canonical repository is `https://github.com/seasonyuu/FnMusic.git`.
- Use `main` for the synchronized branch unless a task explicitly requests another branch.
- Inspect the remote state before pushing. Use `--force-with-lease` for an explicitly requested history rewrite; never use an unconditional force push.
- When using GitHub CLI in this repository, specify the repository explicitly where supported: `seasonyuu/FnMusic`.

## Emulator App and Data Preservation

- Never uninstall apps or clear app data on an emulator used for daily development or manual testing. This includes direct commands such as `adb uninstall`, `pm clear`, and Gradle uninstall tasks, as well as indirect cleanup by test runners, scripts, or emulator resets/wipes.
- Run device tests that may uninstall apps or clear data only on a dedicated test emulator. Verify the target device and the test runner's cleanup behavior before running them; do not treat an existing emulator as disposable unless it is explicitly designated for testing. If cleanup behavior is unknown, use a dedicated test emulator.
- Prefer in-place APK updates that preserve app data. If an in-place installation fails, report the error and its cause (or state that the cause is not yet known) before taking further corrective action. Do not automatically uninstall the existing app or clear its data to resolve installation failures.
