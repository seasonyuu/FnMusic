# Contribution Guidelines

## Commit Standards

- Write commit subjects and bodies in English.
- Use the Conventional Commits format: `<type>(<scope>): <imperative description>`.
- Keep each commit focused on one coherent change. Separate implementation, UI changes, and tests when they can be reviewed independently.
- Add a `Signed-off-by` trailer to every commit. Generate it from the Git identity used for that commit (for example, with `git commit -s`); do not copy a fixed name or email from this document.
- Add a `Co-authored-by` trailer identifying the code agent that actually produced the change. Resolve the agent's current name and email from the active runtime or task context, and do not hardcode a particular agent identity in this document.
- Preserve both trailers when amending or rebasing commits, and verify that their names and emails match the identities used for the current commit.
- Before committing, review the staged diff, run `git diff --check`, and run the most relevant Gradle tests. Report any test that cannot run and why.
- Do not commit credentials, cookies, tokens, local connection settings, generated reports, databases, keystores, or other machine-specific files. Keep those files ignored and use `.env.example` for empty configuration placeholders.

## GitHub Synchronization

- The canonical repository is `https://github.com/seasonyuu/FnMusic.git`.
- Use `main` for the synchronized branch unless a task explicitly requests another branch.
- Inspect the remote state before pushing. Use `--force-with-lease` for an explicitly requested history rewrite; never use an unconditional force push.
- When using GitHub CLI in this repository, specify the repository explicitly where supported: `seasonyuu/FnMusic`.
