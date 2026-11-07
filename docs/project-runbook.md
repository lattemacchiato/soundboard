# Soundboard NG – Projektdokumentation und Runbook

**Stand:** 07.11.2026
**Repository:** https://github.com/lattemacchiato/soundboard
**Basis:** Fork von `508-dev/soundboard` (GPL-3.0)
**Status:** v1.2.0 veröffentlicht und getestet; `main` und `develop` synchron

> Zusammenfassung der gemeinsam erarbeiteten, bestätigten Abläufe und Entscheidungen. Bei künftigen Änderungen sind der aktuelle Quellcode und die tatsächlichen CI-Ergebnisse maßgeblich.

## 1. Projekt und Entwicklungsumgebung

| Eigenschaft | Wert |
|---|---|
| Lokales Projekt | `~/AndroidStudioProjects/soundboard` |
| System | Ubuntu 24.04, Android Studio |
| Technologie | Kotlin, Jetpack Compose, Gradle |
| Referenzgerät | Samsung Galaxy Tab A (2016), Android 8.1 / API 27 |
| Weiteres Testgerät | Samsung Galaxy S24 |
| Release Application ID | `de.lattemacchiato.soundboardng` |
| Debug Application ID | `de.lattemacchiato.soundboardng.debug` |
| Stabiles Release | `v1.2.0` |
| Installierter Debug-Stand | `1.2.0-dev` |

Die App ist kostenlos, werbefrei, trackingfrei, Open Source und vollständig offline nutzbar. Die ursprüngliche Application ID `dev.co508.soundboard` wurde ersetzt. Release und Debug sind eigenständige Installationen mit separaten Daten und können parallel betrieben werden.

## 2. Arbeitsweise und Git-Strategie

**Zusammenarbeit:** In kleinen, kontrollierten Schritten arbeiten. Konkrete Bash-Befehle bereitstellen, Ausgabe abwarten und erst danach fortfahren. Neue Dateien bevorzugt mit Bash-Heredocs erstellen. Vor Umbauten Datenmodell, Audio-Engine und Abhängigkeiten prüfen.

**Branches:** `main` ist stabil und veröffentlicht; `develop` dient der Integration. Features auf `feature/*`, Fehlerbehebungen auf `fix/*`, Dokumentation auf `docs/*` entwickeln. Release Please erzeugt eigene temporäre Branches.

**Verbindlich:** `./gradlew check` vor jedem Commit, Merge und Release. Zusätzliche Gerätetests nach Bedarf. Beim PR von `develop` nach `main` einen **normalen Merge-Commit** verwenden, nicht Squash: Conventional Commits sollen für Release Please erhalten bleiben.

```bash
git branch --show-current
git status -sb
git fetch origin
git log --oneline --max-count=5
git diff --check
./gradlew check
./scripts/sync-version.sh --check
```

Nach v1.2.0 sind `main`, `develop` und `origin/develop` auf `e6590267e15b799634155f044f85509ef9f4fda8` synchronisiert. Die alten Release-Please-Branches wurden entfernt.

## 3. Vorhandene App-Funktionen und Entscheidungen

- Mehrere Soundboards; Anlegen, Umbenennen, Löschen und Wechseln.
- Einzelne Sounds mit Loop oder One-Shot.
- Ordnerwiedergabe mit alphabetischer Reihenfolge oder Shuffle ohne Wiederholung innerhalb eines Durchlaufs; bei Loop + Shuffle neue Mischung pro Durchlauf.
- Boardwechsel stoppt alle laufenden Sounds; Fade-out stoppt und setzt die Wiedergabeposition zurück.
- Persistenter Lock-Modus; Hamburger-Menü deaktiviert, vereinfachte Bedienelemente, unmittelbares Auslösen und Schutz gegen unbeabsichtigte Mehrfachauslösung.
- Board-Löschen mit Bestätigung; letztes Board nicht löschbar.
- Aktiver Boardname in der Titelzeile; Sortier-/Rearrange-Modus.
- Backup/Restore und persistente Einstellungen.
- Screenshots im README und unter `docs/screenshots/`.

Konkrete Klassen und Implementierungsdetails vor Änderungen erneut im Repository prüfen.

## 4. Versionierung und abgeleitete Dateien

`version.txt` ist die maßgebliche Versionsquelle; Release Please aktualisiert sie. `scripts/sync-version.sh` synchronisiert daraus:

- `versionName` und `versionCode` in `app/build.gradle.kts`.
- `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt`.

```text
versionCode = MAJOR * 1000000 + MINOR * 1000 + PATCH
```

Für v1.2.0: `versionCode = 1002000`, `versionName = "1.2.0"`; Debug verwendet `1.2.0-dev`.

```bash
./scripts/sync-version.sh
./scripts/sync-version.sh --check
./scripts/sync-version.sh --print
```

**F-Droid:** `versionCode` und `versionName` müssen als einfache Literale auf eigenen Zeilen in `app/build.gradle.kts` stehen. Ein Build aktualisiert keine bereits installierte App: Die APK muss danach installiert werden.

## 5. CI, Branch Protection und Release Please

### CI und Branch Protection

`.github/workflows/ci.yml` prüft unter anderem Versionskonsistenz, ktlint, Unit-Tests, Android Lint und Debug-Build. Erforderliche Statuschecks für `main`: `build` und `conventional-commit`. Pull Request erforderlich, derzeit 0 verpflichtende Reviews, Strict Checks aktiviert.

**Fallstrick:** Mit `GITHUB_TOKEN` erzeugte Bot-Pushes lösen reguläre PR-/Push-Checks nicht zwingend erneut aus. Ein `workflow_dispatch` ersetzt keinen verpflichtenden PR-Statuscheck. Die vollautomatische Release-PR-Synchronisierung ist daher zurückgestellt.

### Release-Please-Konfiguration

- `release-please-config.json`: `release-type: simple`, `separate-pull-requests: true`, Tags `vX.Y.Z`.
- `.release-please-manifest.json`: nach v1.2.0 auf `1.2.0`.
- `CHANGELOG.md` und `version.txt` werden durch Release Please aktualisiert.

### Verbindlicher halbautomatischer Release-Ablauf

**A – `develop` nach `main`**

1. Feature-Branches nach Tests in `develop` integrieren.
2. Auf `develop` `./gradlew check` ausführen.
3. PR von `develop` nach `main` erstellen; CI und Conventional-Commit-Check abwarten.
4. PR mit **Create a merge commit** mergen, nicht squashen.

**B – Release-Please-PR**

5. Den von Release Please erzeugten Release-PR und dessen **tatsächlichen Branch-Namen** prüfen (bei v1.2.0: `release-please--branches--main--components--soundboard-ng`).
6. Branch lokal auschecken; `./scripts/sync-version.sh` und `./scripts/sync-version.sh --check` ausführen.
7. `./gradlew check` ausführen, abgeleitete Versionsdateien committen und pushen.
8. Pflichtchecks `build` und `conventional-commit` abwarten.
9. Release-PR mit normalem Merge-Commit mergen. Release Please erstellt Tag, Changelog und GitHub-Release.

**C – Release-Stand lokal synchronisieren**

```bash
git switch main
git pull --ff-only origin main
git fetch origin --tags
git log -1 --oneline
git show -s --oneline vX.Y.Z
git status -sb
```

Vor dem APK-Build sicherstellen, dass HEAD auf dem veröffentlichten Tag steht; bei inzwischen weiteren Commits ausdrücklich vom Release-Tag bauen. Danach signierte APK bauen, prüfen und auf dem Referenzgerät testen (Abschnitt 6).

**D – Veröffentlichung und Nachbereitung**

10. Getestete APK unter `Soundboard-NG-vX.Y.Z.apk` als Asset zum bestehenden Release hochladen.
11. GitHub-Asset und Release-Status kontrollieren.
12. `develop` per geprüftem Fast-Forward auf `main` bringen, `./gradlew check`, dann pushen.
13. Vollständig gemergte, nicht mehr benötigte Release-Please-Branches prüfen und löschen.
14. Debug-APK neu bauen und auf dem S24 installieren; beide Versionen kontrollieren.

```bash
gh pr view <NUMMER> --json state,mergeable,reviewDecision,statusCheckRollup
gh run list --workflow=release-please.yml --limit 5
gh release view vX.Y.Z --json tagName,isDraft,isPrerelease,assets
```

**Hinweis:** `isLatest` ist in der verwendeten `gh release view --json`-Version kein unterstütztes Feld.

## 6. Signierte Release-APK: Bau, Prüfung, Installation, Veröffentlichung

### Signierung

Die Release-Signierung in `app/build.gradle.kts` verwendet lokal `keystore.properties`, alternativ `SOUNDBOARD_KEYSTORE_*`-Umgebungsvariablen in CI. Historischer Keystore-Pfad: `~/AndroidSigning/soundboard-ng-release.jks`, Alias `soundboard-ng`.

**Unbedingt denselben Keystore für Updates verwenden.** Keystore, private Schlüssel und Passwörter niemals committen oder veröffentlichen. Ohne Signierdaten kann ein unsignierter Release-Build entstehen; den Signaturstatus daher immer explizit prüfen.

### APK bauen und Signatur prüfen

```bash
export ANDROID_HOME="$HOME/Android/Sdk"
export ANDROID_SDK_ROOT="$ANDROID_HOME"

./gradlew check
./gradlew assembleRelease

APK=app/build/outputs/apk/release/app-release.apk
cat app/build/outputs/apk/release/output-metadata.json

APKSIGNER="$(find "$HOME/Android/Sdk/build-tools" \
  -name apksigner -type f | sort -V | tail -n 1)"
"$APKSIGNER" verify --verbose --print-certs "$APK"
```

Für v1.2.0 bestätigt: `Verifies`, v2-Signatur `true`, ein Signierer, RSA 4096 Bit, Zertifikats-SHA-256 `9d9c85cd80039d1528a5cfeb4824bc9bc52cd0c0d93a57ad4f07ad371479a5b0`. v1-Signatur ist nicht vorhanden, für Android 8.1 jedoch nicht erforderlich. Die Java-Warnungen zur nativen Bibliotheksladung waren nicht kritisch.

### Update auf Galaxy Tab A und Regressionstest

```bash
adb devices
adb shell dumpsys package de.lattemacchiato.soundboardng \
  | grep -E 'versionCode=|versionName='
adb install -r app/build/outputs/apk/release/app-release.apk
adb shell dumpsys package de.lattemacchiato.soundboardng \
  | grep -E 'versionCode=|versionName='
```

`-r` aktualisiert die bestehende App bei kompatibler Application ID und Signatur unter Erhalt der App-Daten. **Bei `INSTALL_FAILED_UPDATE_INCOMPATIBLE` keinesfalls deinstallieren**, sondern zuerst die Signaturursache untersuchen.

**Verbindliche sechs Regressionstests auf dem Galaxy Tab A:**

1. Vorhandene Soundboards, Sounds und Einstellungen erhalten.
2. Einzelne Sounds abspielen und stoppen.
3. Ordnerwiedergabe einschließlich Shuffle ohne Wiederholung.
4. Boardwechsel und Stop-All.
5. Lock-Modus mit unmittelbarer Auslösung, ohne unbeabsichtigte Mehrfachauslösung.
6. Backup-/Restore-Bildschirm öffnen; Einstellungen kontrollieren (kein Restore notwendig).

Für v1.2.0 wurden alle sechs Prüfungen erfolgreich durchgeführt.

### Getestete APK als GitHub-Asset veröffentlichen

```bash
VERSION=1.2.0
cp app/build/outputs/apk/release/app-release.apk \
  "app/build/outputs/apk/release/Soundboard-NG-v${VERSION}.apk"

sha256sum app/build/outputs/apk/release/app-release.apk \
  "app/build/outputs/apk/release/Soundboard-NG-v${VERSION}.apk"
```

Die Prüfsummen müssen identisch sein. Die APK gehört als **Asset zum bestehenden GitHub-Release**, nicht als Binärdatei ins Repository. Upload per **Edit release → Asset hinzufügen → Update release** oder:

```bash
gh release upload "v$VERSION" \
  "app/build/outputs/apk/release/Soundboard-NG-v${VERSION}.apk"

gh release view "v$VERSION" \
  --json tagName,isDraft,isPrerelease,assets \
  --jq '{tag: .tagName, draft: .isDraft, prerelease: .isPrerelease, assets: [.assets[] | {name: .name, size: .size}]}'
```

Für v1.2.0 wurde `Soundboard-NG-v1.2.0.apk` (2.338.545 Bytes) veröffentlicht: https://github.com/lattemacchiato/soundboard/releases/tag/v1.2.0

### `develop` nach dem Release synchronisieren

```bash
git fetch origin
git switch develop
git status -sb
git merge-base --is-ancestor develop origin/main
echo "Fast-Forward möglich (0=ja, 1=nein): $?"
```

Nur bei Rückgabewert 0:

```bash
git merge --ff-only origin/main
./gradlew check
git status -sb
git push origin develop

git rev-parse main
git rev-parse develop
git rev-parse origin/develop
```

Vollständig gemergte Release-Please-Branches identifizieren und **erst nach Prüfung** löschen:

```bash
git fetch origin --prune
git branch -a | grep release-please
git branch -r --merged origin/main | grep release-please
```

### Release und Debug parallel auf Samsung S24

```bash
# Release (v1.2.0)
adb install -r app/build/outputs/apk/release/app-release.apk

# Debug neu bauen UND installieren
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk

# Installierte Versionen prüfen
adb shell dumpsys package de.lattemacchiato.soundboardng \
  | grep -E 'versionCode=|versionName='
adb shell dumpsys package de.lattemacchiato.soundboardng.debug \
  | grep -E 'versionCode=|versionName='
```

Erwartet: Release `1.2.0` (`1002000`), Debug `1.2.0-dev` (`1002000`). Ein Build allein aktualisiert **nicht** die installierte App. Der APK-Dateiname ist für Updates unerheblich; entscheidend sind Application ID, Versionscode und Signatur. Bei mehreren verbundenen Geräten `adb -s <SERIENNUMMER>` verwenden.

## 7. Abgeschlossenes Feature: „Play Folder“

Mit v1.2.0 veröffentlicht: Ordnerauswahl über das Android Storage Access Framework, persistenter Ordnerzugriff, alphabetische Reihenfolge und Shuffle ohne Wiederholung je Durchlauf. Loop gilt für den gesamten Ordnerdurchlauf, bei Loop + Shuffle wird für jeden Durchlauf neu gemischt. Die Ordnerwiedergabe folgt den bestehenden Stopp-Regeln bei Boardwechsel. Tests für Shuffle und Ordnerdatenmodell sind vorhanden.

Für Erweiterungen die aktuelle Implementierung in Datenmodell, Audio-Engine und UI prüfen.

## 8. Backlog

| Thema | Stand |
|---|---|
| Play Folder | Mit v1.2.0 veröffentlicht |
| Performance Lock Mode | Mit v1.2.0 veröffentlicht |
| Automatische Synchronisierung der Release-PR-Versionsdateien | Zurückgestellt |
| CI-Trigger nach automatischem Bot-Commit | Teil der zurückgestellten Release-Automatisierung |
| Signierte APK automatisch an GitHub-Release anhängen | Zurückgestellt; manueller Upload Pflicht |
| Kotlin-Deprecation `Modifier.menuAnchor()` | Technische Wartung, offen |
| F-Droid-Veröffentlichung | Weiterhin Projektziel |
| Erneute Upstream-Contribution / Merge-Angebot an `508-dev` | Später erneut prüfen |
| Weitere Features | Nach jeder Implementierung gemeinsam priorisieren |

Für eine spätere Vollautomatisierung wurden Fine-Grained PAT oder GitHub-App-Token diskutiert. Noch nicht umgesetzt.

## 9. Wichtigste Erkenntnisse

1. Ein erfolgreicher Release-Please-Lauf bedeutet **nicht**, dass eine installierbare APK als Asset vorliegt.
2. Release Please aktualisiert `version.txt`; abgeleitete Dateien werden weiterhin mit `sync-version.sh` synchronisiert.
3. `GITHUB_TOKEN`-Folge-Workflows und verpflichtende PR-Checks erfordern besondere Sorgfalt.
4. Conventional Commits durch normale Merge-Commits für Release Please erhalten.
5. `./gradlew check` vor jedem Commit, Merge und Release ausführen.
6. F-Droid benötigt Versionsangaben als Literale in Gradle.
7. Release und Debug sind getrennte Installationen.
8. Für Updates muss der ursprüngliche Release-Signierschlüssel erhalten bleiben.
9. Signaturen explizit mit `apksigner` prüfen; Dateiname allein ist kein Signaturnachweis.
10. Build und Installation sind getrennte Schritte; Debug nach dem Release neu bauen **und** installieren.
11. Nach Release `develop` synchronisieren und alte Release-Branches prüfen.
12. `docs/deployment.md` enthält teilweise ältere Verfahren; aktuelle Workflows und GitHub-Ausgaben haben Vorrang.

## 10. Release-Checkliste

### Vorbereitung

- [ ] Features getestet und in `develop` integriert
- [ ] `./gradlew check` erfolgreich
- [ ] PR `develop` → `main`, Pflichtchecks grün
- [ ] Normaler Merge-Commit, kein Squash

### Release Please

- [ ] Release-PR erstellt und korrekter Branch ausgecheckt
- [ ] `./scripts/sync-version.sh` ausgeführt
- [ ] `./scripts/sync-version.sh --check` erfolgreich
- [ ] `./gradlew check` erfolgreich
- [ ] Abgeleitete Dateien committet und gepusht
- [ ] Pflichtchecks `build` und `conventional-commit` grün
- [ ] Release-PR gemergt; Tag und GitHub-Release vorhanden

### APK und Gerätetests

- [ ] Lokaler `main` entspricht Release-Tag
- [ ] `./gradlew check` erfolgreich
- [ ] Signierte Release-APK gebaut
- [ ] APK-Version und v2-Signatur geprüft
- [ ] Update auf Galaxy Tab A erfolgreich; App-Daten erhalten
- [ ] Alle sechs Regressionstests erfolgreich
- [ ] Getestete APK als `Soundboard-NG-vX.Y.Z.apk` kopiert
- [ ] SHA-256-Prüfsummen identisch
- [ ] APK als GitHub-Release-Asset hochgeladen und kontrolliert

### Abschluss

- [ ] `develop` per geprüftem Fast-Forward synchronisiert
- [ ] `./gradlew check` auf `develop` erfolgreich
- [ ] `develop` gepusht
- [ ] Alte Release-Please-Branches geprüft und bereinigt
- [ ] Debug-APK neu gebaut und auf S24 installiert
- [ ] Release- und Debug-Versionsnummern geprüft
- [ ] Runbook und Backlog aktualisiert

## 11. Wichtige Dateien

- `docs/deployment.md` – ergänzende Deployment-Dokumentation (teils älter)
- `.github/workflows/ci.yml` – CI
- `.github/workflows/release-please.yml` – Release Please
- `.github/workflows/sync-release-version.yml` – Release-Version-Synchronisierung (aktuellen Einsatz prüfen)
- `release-please-config.json` – Release-Konfiguration
- `.release-please-manifest.json` – Release-Manifest
- `version.txt` – Versionsquelle
- `scripts/sync-version.sh` – abgeleitete Versionsdateien
- `app/build.gradle.kts` – Android-Konfiguration und Signierung
- `CHANGELOG.md` – Release-Änderungen
- `docs/screenshots/` – App-Screenshots
