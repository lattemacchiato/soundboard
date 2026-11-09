# Deployment — Soundboard NG

This document distinguishes the **current, tested release procedure** from optional or planned distribution channels. The procedure below was exercised for **v1.3.1 on 2026-11-09**.

## Release policy

- Develop each change on a separate feature or fix branch.
- Run `./gradlew check` before commits, merges, and releases.
- Merge completed changes into `develop`; prepare releases by merging `develop` into `main`.
- Use **normal merge commits** for release PRs (do not squash or rebase them).
- A push to `main` starts Release Please. The release is created only after its release PR is merged.
- Do not publish an APK until a **signed release build** has been installed and smoke-tested on a physical device.
- Never commit keystores, passwords, or signing configuration containing secrets.

## 1. Finish a feature or fix

On its feature/fix branch, inspect the changes and run the full check:

```bash
./gradlew check
git diff --check
git status -sb
```

Commit and push the feature branch, then merge it into `develop` using the agreed PR workflow. Before merging, confirm the relevant checks pass. Keep `develop` as the integration branch; do not release directly from a feature branch.

## 2. Promote `develop` to `main`

After verifying the changes on `develop`, update `main` and merge:

```bash
git switch main
git pull --ff-only origin main
git fetch origin
git merge --no-ff origin/develop
./gradlew check
git status -sb
git push origin main
```

Resolve any conflicts and rerun the checks before pushing. **The push to `main` triggers Release Please**; merely merging to `develop` does not.

## 3. Prepare the Release Please PR

Release Please opens a PR similar to `chore(main): release X.Y.Z`. It updates the release manifest, `version.txt`, and `CHANGELOG.md` according to the Conventional Commits merged since the last release.

**Current manual step:** the bot-created release PR may show required checks such as `build` and `conventional-commit` as *Expected / Waiting for status to be reported*. In the v1.3.1 release, a human-authored push to the release branch triggered these checks.

Fetch and check out the actual branch shown in the PR (example for v1.3.1):

```bash
git fetch origin
git switch --track origin/release-please--branches--main--components--soundboard-ng
```

If the branch already exists locally, use `git switch <branch-name>` and `git pull --ff-only` instead. Do not assume the branch name will always remain identical.

Synchronize Android and Fastlane versions:

```bash
bash scripts/sync-version.sh
git status --short
git diff --stat
git diff
```

Check the newly generated Fastlane changelog explicitly, because an untracked file does not appear in `git diff`:

```bash
cat fastlane/metadata/android/en-US/changelogs/<versionCode>.txt
```

Replace `<versionCode>` with the actual generated number (for v1.3.1: `1003001`). Inspect the changelog and ensure the versionName/versionCode correspond to `version.txt`.

Run formatting and quality checks:

```bash
./gradlew :app:ktlintTestSourceSetFormat
./gradlew :app:ktlintMainSourceSetFormat
git diff --check
./gradlew check
git status --short
```

Review any unexpected formatting changes. Stage **only the intended version-related files** (typically `app/build.gradle.kts` and the new Fastlane changelog), then commit and push:

```bash
git add app/build.gradle.kts fastlane/metadata/android/en-US/changelogs/<versionCode>.txt
git diff --cached --check
git commit -m "chore(release): sync Android version for X.Y.Z"
git push origin HEAD
```

Use a `chore(release)` subject rather than `feat` or `fix`: this commit is not a new product change. Wait for **all required PR checks** to pass. Do not bypass checks or merge while they are pending.

## 4. Merge the release PR

On GitHub, use **Merge pull request** (normal merge, not squash/rebase) after checks pass. Release Please then creates the GitHub Release and tag `vX.Y.Z`.

Update the local checkout and verify the tag:

```bash
git switch main
git pull --ff-only origin main
git fetch origin --tags
git status -sb
git tag --list 'vX.Y.Z'
git show -s --format='%h %s' vX.Y.Z
grep -E 'versionCode =|versionName =' app/build.gradle.kts
```

For v1.3.1, the tag resolved to merge commit `62e0db1`, and the Android version was `versionCode = 1003001`, `versionName = "1.3.1"`.

## 5. Build and verify the signed APK locally

A local `keystore.properties` supplies the release signing credentials; see `keystore.properties.example`. Keep the real file and keystore out of Git.

```bash
./gradlew clean assembleRelease
ls -lh app/build/outputs/apk/release/
```

Verify the APK rather than assuming that `assembleRelease` produced a signed artifact:

```bash
APKSIGNER="$(find "$HOME/Android/Sdk/build-tools" -name apksigner -type f | sort -V | tail -n 1)"
"$APKSIGNER" verify --verbose --print-certs \
  app/build/outputs/apk/release/app-release.apk
```

Check for `Verifies`, the expected signer certificate, and an appropriate signing scheme. **APK Signature Scheme v2 is sufficient for Android 8.1 (API 27)**; v1 is not mandatory for that device. A build intended to support Android versions below 7.0 must also meet their signing requirements. Java native-access warnings from the verification tool are not by themselves APK signature failures.

## 6. Install and smoke-test on the physical device

```bash
adb devices
adb install -r app/build/outputs/apk/release/app-release.apk
```

Expect `Success`. `-r` updates the installed app while preserving its data. If `INSTALL_FAILED_UPDATE_INCOMPATIBLE` appears, **do not uninstall**; investigate certificate compatibility first.

Minimum smoke test:

1. Confirm the displayed version is correct.
2. Confirm existing boards and sounds remain present.
3. Play FILE and FOLDER sounds.
4. Create a Full ZIP Backup containing folder sounds.
5. Save the ZIP outside the app before restoring it; then restore and verify playback.

For changes in other areas, add targeted regression tests. In v1.3.1, ZIP-v1 backward compatibility was separately tested during development.

## 7. Publish the tested APK to the existing GitHub Release

Prepare a clearly named copy and verify it is byte-for-byte identical to the tested APK:

```bash
mkdir -p dist
cp app/build/outputs/apk/release/app-release.apk dist/Soundboard-NG-vX.Y.Z.apk
sha256sum app/build/outputs/apk/release/app-release.apk dist/Soundboard-NG-vX.Y.Z.apk
git status --short
```

`dist/` is a local distribution directory, not a source-code commit. The two SHA-256 values must match.

Using an authenticated GitHub CLI (`gh auth status`):

```bash
gh release upload vX.Y.Z dist/Soundboard-NG-vX.Y.Z.apk \
  --repo lattemacchiato/soundboard

gh release view vX.Y.Z \
  --repo lattemacchiato/soundboard \
  --json assets \
  --jq '.assets[] | "\(.name) (\(.size) bytes)"'
```

Do not use `--clobber` by default; an existing asset should not be silently overwritten. The v1.3.1 APK was published as `Soundboard-NG-v1.3.1.apk` (2,358,593 bytes; SHA-256 `e20232ccd1c79be0ccbb5edd0fff89cbe0d6eb999425e093c469790e67cca640`).

## Versioning and Conventional Commits

`version.txt` is the release version source of truth; Release Please updates it. `scripts/sync-version.sh` derives:

```text
version.txt
  -> app/build.gradle.kts: versionName and versionCode
  -> fastlane/metadata/android/<locale>/changelogs/<versionCode>.txt
```

`versionCode = major * 1000000 + minor * 1000 + patch`. Keep `versionName` and `versionCode` as literal values in Gradle if downstream F-Droid tooling parses them. `scripts/sync-version.sh --check` can detect drift in CI.

| Commit subject | Release effect |
| --- | --- |
| `feat: ...` | Minor version |
| `fix: ...`, `perf: ...` | Patch version |
| Breaking-change marker | Depends on Release Please configuration |
| `docs:`, `chore:`, `ci:`, `refactor:`, `test:` | No release by themselves |

Consult `.release-please-config.json` for the actual versioning policy; older pre-1.0 guidance is not applicable to current 1.x releases.

## Signing and distribution compatibility

Android update compatibility depends on the installed package ID and signing certificate. APKs signed by a different key generally cannot update each other in place. Preserve the release keystore and credentials securely; never upload them to the repository.

- **GitHub Release APK:** signed locally with the project release key in the current procedure.
- **Google Play:** if adopted, Play App Signing key management and compatibility with locally signed APKs must be established before publication.
- **f-droid.org:** normally builds and signs with its own key; its distribution is not automatically interchangeable with GitHub APKs. Reproducible-build workflows may offer a route to matching signatures.

For local signing configuration, see `keystore.properties.example`; for CI secrets, see `docs/secrets.md` if that setup is enabled.

## Other distribution paths — not part of the verified v1.3.1 procedure

Earlier versions of this document described automated GitHub Actions publication of a signed APK/AAB, Google Play internal-track delivery, and a self-hosted F-Droid repository. **Do not treat those descriptions as operational instructions without checking the current workflow files and credentials.** The v1.3.1 release used the manual local build, device test, and `gh release upload` process above.

### Google Play (optional)

If Google Play distribution is introduced, verify package identity, Play App Signing enrollment, Play Console access, service-account permissions, store metadata, internal-track upload, and manual production promotion. Do not assume an AAB upload is currently automated.

### F-Droid (optional / separate project)

The repository contains F-Droid-related metadata/scripts, including `scripts/fdroid-publish.sh` and `fdroid/` files. Confirm their current paths and repository URLs before using them; historical documentation referenced `508-dev` and `dev.co508.soundboard`, which may not match Soundboard NG's current identity (`de.lattemacchiato.soundboardng`).

For an official f-droid.org submission, consult its current submission guide and validate the metadata, package ID, build environment, and signing/reproducibility requirements. Publishing a GitHub Release alone does not establish that f-droid.org has accepted or built the app.

### Further automation (backlog)

Automating the Release Please PR version-sync commit and reliably triggering its CI checks is a separate improvement. The present, tested workflow intentionally retains the manual `scripts/sync-version.sh` step and subsequent push.

## Troubleshooting and safeguards

- **Release PR checks stay “Expected”:** ensure the version-sync commit was pushed to the release branch; then inspect GitHub Actions and branch protection before merging.
- **No local tag after release:** `git fetch origin --tags`.
- **Unsigned APK:** inspect `keystore.properties` and verify with `apksigner` before installing.
- **Signature mismatch on update:** do not uninstall until app data has been backed up and signing identities compared.
- **APK upload conflicts:** inspect existing release assets; do not overwrite them without an explicit decision.
- **Never manually create a release tag during the normal Release Please flow.** Manual release recovery requires its own coordinated procedure to avoid desynchronizing the release manifest.
