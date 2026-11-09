# Development on Multiple Workstations

This document describes the workflow for developing Soundboard NG on
Ubuntu and Windows using GitHub as the synchronization point.

For general development commands, see [development.md](development.md).
For branching, CI, releases, and device testing, see
[project-runbook.md](project-runbook.md).

## 1. Workstations

| Property | Ubuntu | Windows |
|---|---|---|
| OS | Ubuntu 24.04 | Windows 11 |
| Project | `~/AndroidStudioProjects/soundboard` | `C:\dev\soundboard` |
| IDE | Android Studio | Android Studio |
| JDK | Android Studio JBR, Java 25 | Android Studio JBR, Java 25 |
| Gradle | `./gradlew` | `.\gradlew.bat` |
| Git remote | `origin` | `origin` |

Repository: https://github.com/lattemacchiato/soundboard

Both workstations use the same GitHub repository and branches.
GitHub is the synchronization point, not a shared working directory.

### Git and line endings

The repository contains `.gitattributes` to normalize line endings
across platforms. In particular, `gradlew` uses LF and `gradlew.bat`
uses CRLF.

On Windows, the repository uses:

```powershell
git config --local core.autocrlf false
```

Do not change line-ending settings or run bulk normalization without
reviewing the resulting Git diff.

## 2. Start of Every Development Session

Always synchronize before editing files.

### Ubuntu (Bash)

```bash
cd ~/AndroidStudioProjects/soundboard

git status -sb
git branch --show-current
git fetch origin --prune
git pull --ff-only
git status -sb
```

### Windows (PowerShell)

```powershell
cd C:\dev\soundboard

git status -sb
git branch --show-current
git fetch origin --prune
git pull --ff-only
git status -sb
```

These commands assume that the current branch tracks the correct
remote branch.

If `git status` reports uncommitted changes, inspect them before
pulling. Do not discard changes automatically.

If `git pull --ff-only` fails, stop and investigate. Never resolve
divergence with a blind force push or automatic reset.

### Starting a New Feature

Start from an up-to-date `develop` branch:

```bash
git switch develop
git pull --ff-only origin develop
git switch -c feature/SHORT-DESCRIPTION
```

Use `fix/*` for bug fixes and `docs/*` for documentation changes.

## 3. End of Every Development Session

Synchronize completed or unfinished work before switching machines.

### Ubuntu (Bash)

```bash
git status -sb
git diff --check
./gradlew check
git add <specific-files>
git diff --cached --check
git diff --cached --stat
git commit -m "wip: describe unfinished work"
git push -u origin HEAD
git status -sb
```

### Windows (PowerShell)

```powershell
git status -sb
git diff --check
.\gradlew.bat check
git add <specific-files>
git diff --cached --check
git diff --cached --stat
git commit -m "wip: describe unfinished work"
git push -u origin HEAD
git status -sb
```

Replace `<specific-files>` with the actual changed file paths.
Review staged changes before committing.

Only create a commit if there are changes to commit. If everything
is already committed, a push may be sufficient.

The full Gradle `check` must succeed before every commit, including
WIP commits. Never commit secrets or signing material.

A WIP commit is allowed on a feature branch. It does not mean the
feature is complete or ready to merge.

## 4. Switching Between Workstations

### On the Source Workstation

1. Run the full Gradle check.
2. Commit all changes needed to continue, including unfinished work.
3. Push the current feature branch to GitHub.
4. Verify that the working tree is clean.

### On the Destination Workstation

First fetch remote changes:

```bash
git fetch origin --prune
```

If the feature branch already exists locally:

```bash
git switch feature/SHORT-DESCRIPTION
git pull --ff-only
```

If the feature branch does not yet exist locally:

```bash
git switch --track origin/feature/SHORT-DESCRIPTION
```

These Git commands work in both Bash and PowerShell.

Check the status before continuing:

```bash
git status -sb
```

Do not edit the same feature branch independently on two machines
without synchronizing the changes first.

Git synchronizes committed files, not uncommitted changes, IDE
settings, local configuration, keystores, or Android device data.

## 5. Tests, Pull Requests, and Releases

Before each commit, merge, or release:

- Review and supplement relevant unit tests.
- Run the complete Gradle `check`.
- Perform device testing where required.
- Review the Git diff and confirm no secrets are staged.

Feature branches are integrated into `develop` through pull requests.
Use regular merge commits where required by the established workflow.

Follow [project-runbook.md](project-runbook.md) for release-specific
steps, version synchronization, Release Please, and APK publication.

Do not treat a successful Gradle build as proof that audio playback
works correctly. Audio behavior requires appropriate device tests.

## 6. Release Signing

Soundboard NG uses the same existing release signing key on both
workstations. Do not generate a replacement key for published updates.

### Ubuntu

Keystore:

`~/AndroidSigning/soundboard-ng-release.jks`

Alias: `soundboard-ng`

### Windows

Keystore:

`C:\Users\admin\AndroidSigning\soundboard-ng-release.jks`

Alias: `soundboard-ng`

Local signing configuration is stored in the gitignored
`keystore.properties` file in the repository root.

Use the appropriate absolute `storeFile` path for each workstation.
Keep `storePassword` and `keyPassword` local and confidential.

Never commit or publish keystores, passwords, signing configuration,
or other private credentials.

### Build a Release APK

Ubuntu:

```bash
./gradlew check
./gradlew assembleRelease
```

Windows:

```powershell
.\gradlew.bat check
.\gradlew.bat assembleRelease
```

Output:

`app/build/outputs/apk/release/app-release.apk`

Verify the resulting APK with Android SDK `apksigner` before
installation or publication.

Expected signing certificate SHA-256:

`9d9c85cd80039d1528a5cfeb4824bc9bc52cd0c0d93a57ad4f07ad371479a5b0`

Both workstations have been verified to use this certificate.

For the complete release procedure, see
[project-runbook.md](project-runbook.md).

## 7. Troubleshooting

### Uncommitted Local Changes

Run:

```bash
git status -sb
git diff
```

Inspect changes before pulling or switching branches.
Commit tested work on the feature branch when appropriate.
Do not use `git reset --hard` to discard unknown changes.

### Branch Has No Upstream

For a new local feature branch, after the required tests and commit:

```bash
git push -u origin HEAD
```

For an existing remote branch not yet checked out locally:

```bash
git switch --track origin/feature/SHORT-DESCRIPTION
```

### Fast-Forward Pull Fails

Stop and inspect:

```bash
git status -sb
git log --oneline --graph --decorate -12
git branch -vv
```

Do not automatically rebase, merge, reset, or force-push.
Resolve the situation deliberately.

### Gradle Fails

Check the active Java version, Android SDK configuration, and
Gradle error output. The project uses Java 25 through Android
Studio's bundled JBR.

Do not upgrade Gradle, Android Gradle Plugin, or dependencies merely
because Android Studio suggests an update.

### Signing Fails

Check that `keystore.properties` points to the correct local
keystore and uses the correct alias and passwords.

Do not print passwords into terminal logs or commit them.
Never generate a new key to work around a signing failure.

## 8. Collaboration Rule

At the beginning and end of every development session, explicitly
check Git synchronization and provide copy-and-paste commands for
the current workstation and branch.

Work incrementally: one step, inspect the result, then continue.
