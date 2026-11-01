# Soundboard NG – Projektdokumentation und Runbook

**Stand:** 01.11.2026  
**Repository:** https://github.com/lattemacchiato/soundboard  
**Basis:** Fork von `508-dev/soundboard` (GPL-3.0)  
**Status:** v1.1.1 veröffentlicht; nächstes Feature: **Play Folder**

> Zusammenfassung der gemeinsam erarbeiteten, bestätigten Abläufe und Entscheidungen. Bei künftigen Änderungen sind der aktuelle Quellcode und die tatsächlichen CI-Ergebnisse maßgeblich.

## 1. Projekt und Entwicklungsumgebung

| Eigenschaft | Wert |
|---|---|
| Lokales Projekt | `~/AndroidStudioProjects/soundboard` |
| System | Ubuntu 24.04, Android Studio |
| Technologie | Kotlin, Jetpack Compose, Gradle |
| Testgerät | Samsung Galaxy Tab A (2016), Android 8.1 / API 27 |
| Release Application ID | `de.lattemacchiato.soundboardng` |
| Debug Application ID | `de.lattemacchiato.soundboardng.debug` |
| Stabiles Release | `v1.1.1` |
| Installierter Debug-Stand | `1.1.1-dev` |

Die App ist kostenlos, werbefrei, trackingfrei, Open Source und vollständig offline nutzbar. Die ursprüngliche Application ID `dev.co508.soundboard` wurde für Soundboard NG ersetzt. Release und Debug können wegen verschiedener Application IDs parallel auf dem Tablet installiert werden.

## 2. Arbeitsweise und Git-Strategie

**Zusammenarbeit:** Immer nur einen überschaubaren Schritt bearbeiten, die Ausgabe abwarten und erst dann fortfahren. Kleine Änderungen direkt im Editor; größere Änderungen als anwendbare Unified-Diff-Patches. Vor Umbauten zuerst Datenmodell, Audio-Engine und Abhängigkeiten untersuchen. Regelmäßig bauen, testen und committen. Git-Merges, Pushes und Releases bewusst und kontrolliert ausführen.

**Branches:** `main` = stabil/veröffentlicht; `develop` = Integration; größere Features auf eigenen Feature-Branches. Nach Release `v1.1.1` wurde `origin/main` in `develop` integriert (Merge-Commit `6176f27`), `develop` gepusht und ein sauberer Arbeitsstand bestätigt.

Nützliche Befehle:

```bash
git branch --show-current
git status -sb
git fetch origin
git log --oneline --max-count=5
git diff --check
./gradlew assembleDebug
./scripts/sync-version.sh --check
```

## 3. Vorhandene App-Funktionen und Entscheidungen

- Mehrere Soundboards; Wechsel über Hamburger-Menü.
- Pro Sound Loop oder One-Shot.
- Boardwechsel stoppt alle laufenden Sounds.
- Fade-out stoppt und setzt die Wiedergabeposition zurück.
- Persistenter Lock-Modus, der das Hamburger-Menü deaktiviert.
- Board-Löschen nur nach Bestätigung; letztes Board nicht löschbar.
- Titelzeile zeigt den aktuellen Boardnamen.
- Sortier-/Rearrange-Modus und persistente Soundboard-Daten.
- Lock-Modus wurde auf Android 8.1 erfolgreich getestet.

Konkrete Klassennamen und Implementierungsdetails vor einer Änderung erneut im Repository prüfen.

## 4. Versionierung und abgeleitete Dateien

`version.txt` ist die maßgebliche Versionsquelle; Release Please aktualisiert sie. `scripts/sync-version.sh` erzeugt daraus:

- `versionName` in `app/build.gradle.kts`
- `versionCode` in `app/build.gradle.kts`
- `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt`

Formel:

```text
versionCode = MAJOR * 1000000 + MINOR * 1000 + PATCH
```

Für `1.1.1`: `versionCode = 1001001`, `versionName = "1.1.1"`.

```bash
./scripts/sync-version.sh          # generierte Dateien aktualisieren
./scripts/sync-version.sh --check  # Konsistenz prüfen (auch CI)
./scripts/sync-version.sh --print  # berechnete Werte anzeigen
```

**F-Droid-Anforderung:** `versionCode` und `versionName` müssen als einfache Literale auf eigenen Zeilen in `app/build.gradle.kts` stehen. Eine dynamische Gradle-Berechnung würde die bisherige F-Droid-Versionserkennung gefährden.

Debug verwendet das Suffix `-dev`. Änderungen am Quellcode aktualisieren die bereits installierte Debug-App nicht automatisch.

## 5. CI, Branch Protection und Release Please

### CI

`.github/workflows/ci.yml` prüft unter anderem Versionskonsistenz, ktlint, Unit-Tests, Android Lint und Debug-Build. Der erforderliche Job-Status heißt `build`; ein weiterer Pflichtcheck heißt `conventional-commit`.

Für `main` wurde Branch Protection eingerichtet:

- Pull Request erforderlich; **0** notwendige Review-Freigaben.
- Erforderliche Statuschecks: `build`, `conventional-commit`.
- Strict Checks aktiviert (Branch muss aktuell sein).

GitHub Actions sind aktiviert, externe Actions zugelassen; Workflow-Default-Berechtigung `write`. `.github/workflows/release-please.yml` fordert `contents: write` und `pull-requests: write`.

**Fallstrick:** Mit dem üblichen `GITHUB_TOKEN` erstellte PRs/Pushes lösen reguläre `pull_request`-/`push`-CI nicht immer erneut aus. Ein separat gestarteter `workflow_dispatch` ersetzt keinen erforderlichen PR-Statuscheck. Vollautomatische PR-Synchronisierung wurde daher zurückgestellt.

### Release-Please-Konfiguration

- `release-please-config.json`: `release-type: simple`, `separate-pull-requests: true`, Tags `vX.Y.Z`.
- `.release-please-manifest.json`: zuletzt `{".": "1.1.1"}`.
- `CHANGELOG.md` wird von Release Please gepflegt.
- `version.txt` wird von Release Please aktualisiert.

`v1.1.0` wurde zur Wiederherstellung einer konsistenten Release-Basis manuell veröffentlicht. Danach erstellte Release Please PR **#5** für `v1.1.1`. Nach manueller Synchronisierung der abgeleiteten Dateien und erfolgreicher CI wurde PR #5 per Squash gemergt. Release Please erzeugte daraufhin Tag und Release `v1.1.1`.

### Halbautomatisches Release-Runbook

1. Änderungen über Feature-Branches und `develop` integrieren.
2. Release Please erstellt Release-PR.
3. Release-PR-Branch lokal auschecken.
4. `./scripts/sync-version.sh` ausführen.
5. `./scripts/sync-version.sh --check` ausführen; abgeleitete Dateien committen und pushen.
6. Auf grüne Pflichtchecks `build` und `conventional-commit` warten.
7. Release-PR mergen.
8. Release-Please-Workflow und neues Tag/Release kontrollieren.
9. **Signierte APK bauen, verifizieren und als Release-Asset hochladen** (Abschnitt 6).
10. Veröffentlichten `main`-Stand in `develop` integrieren und pushen.

Kontrollbefehle:

```bash
gh pr view <NUMMER> --json state,mergeable,reviewDecision,statusCheckRollup
gh run list --workflow=release-please.yml --limit 5
gh release view vX.Y.Z --json tagName,name,isDraft,isPrerelease,publishedAt,url
```

## 6. Signierte Release-APK: Bau, Prüfung, Installation, Veröffentlichung

### Signierung

Die Release-Signierung in `app/build.gradle.kts` verwendet lokal `keystore.properties`, alternativ `SOUNDBOARD_KEYSTORE_*`-Umgebungsvariablen in CI. Ohne Signierungsdaten wird eine unsignierte Release-APK gebaut (für F-Droid vorgesehen).

Der lokale Keystore und die vier Signierungsparameter wurden als vorhanden geprüft. Historischer Keystore-Pfad: `~/AndroidSigning/soundboard-ng-release.jks`, Alias `soundboard-ng`.

**Unbedingt denselben Keystore für spätere Updates behalten.** Keystore, Schlüssel und Passwörter niemals committen oder veröffentlichen.

### SDK-Umgebung

In einer neuen Shell waren diese Exports erforderlich:

```bash
export ANDROID_HOME="$HOME/Android/Sdk"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
```

Die vorhandene Build-Tools-Version war `36.0.0`.

### APK bauen und verifizieren

Den Build vom passenden veröffentlichten Quellstand ausführen:

```bash
./gradlew assembleRelease

APK=app/build/outputs/apk/release/app-release.apk
BT="$ANDROID_HOME/build-tools/36.0.0"
"$BT/aapt" dump badging "$APK" | head -1
"$BT/apksigner" verify --verbose "$APK"
```

Für `v1.1.1` wurden bestätigt:

| Prüfung | Ergebnis |
|---|---|
| Paket | `de.lattemacchiato.soundboardng` |
| Version | `1.1.1` |
| VersionCode | `1001001` |
| APK-Größe | 2.338.541 Bytes |
| APK-Signaturschema v2 | `true` |
| Signierer | 1 |

Android 8.1 unterstützt v2-Signaturen. Die beim Prüfen erschienenen Java-Warnungen zur nativen Bibliotheksladung waren nicht kritisch.

### Auf dem Tablet installieren

```bash
adb devices
adb install -r app/build/outputs/apk/release/app-release.apk
```

`-r` aktualisiert eine bestehende App mit gleicher Application ID und kompatibler Signatur und erhält normalerweise die Daten. **Bei `INSTALL_FAILED_UPDATE_INCOMPATIBLE` nicht deinstallieren**, sondern zuerst Signaturursache prüfen.

Für den separaten Debug-Build:

```bash
./gradlew installDebug
```

Auf dem Tablet wurden `1.1.1` (Release) und `1.1.1-dev` (Debug) erfolgreich angezeigt.

### APK auf GitHub veröffentlichen

Die APK gehört als Asset zum GitHub-Release, **nicht als Binärdatei ins Git-Repository**.

```bash
VERSION=1.1.1
gh release upload "v$VERSION"   app/build/outputs/apk/release/app-release.apk

gh release view "v$VERSION" --json assets   --jq '.assets[] | {name, size, downloadCount}'
```

Beim ersten Upload hieß das Asset noch `app-release.apk`. Daher wurde die Asset-ID abgefragt:

```bash
gh api "repos/lattemacchiato/soundboard/releases/tags/v$VERSION"   --jq '.assets[] | {id, name}'
```

Und mit der tatsächlich zurückgegebenen ID umbenannt:

```bash
gh api --method PATCH   repos/lattemacchiato/soundboard/releases/assets/<ASSET-ID>   -f name="Soundboard-NG-v${VERSION}.apk"   --jq '{id, name, size, state}'
```

Das Umbenennen ändert APK-Inhalt und Signatur nicht. **Zielname für jedes künftige Release:** `Soundboard-NG-vX.Y.Z.apk`.

Bestätigtes Release: https://github.com/lattemacchiato/soundboard/releases/tag/v1.1.1

**Entscheidung:** Die Veröffentlichung einer signierten APK ist ab jetzt ein fester, zunächst manueller Bestandteil jedes Releases.

## 7. Nächstes Feature: „Play Folder“

**Grundidee:** Zusätzlich zu Einzeldateien kann eine Sound-Kachel einen Ordner mit Audiodateien repräsentieren und dessen Dateien automatisch nacheinander abspielen.

| Aspekt | Spezifikation |
|---|---|
| Ordnerauswahl | Android Storage Access Framework (SAF) |
| Berechtigungen | Persistenter Zugriff auf ausgewählten Ordner |
| Datenhaltung | Ordnerreferenz und Einstellungen dauerhaft speichern |
| Alphabetisch | Sortierung nach Dateinamen |
| Shuffle | Zufällige Reihenfolge ohne Wiederholung im Durchlauf |
| Loop aus | Ende nach letzter Datei |
| Loop + alphabetisch | Gleiche Reihenfolge erneut |
| Loop + Shuffle | Neue Mischung pro Durchlauf |
| Kachelbedienung | Start/Stopp analog zur Sound-Kachel |
| Anzeige | Reihenfolge und Loop-Status erkennbar |
| Navigation | Zunächst keine Next-/Previous-Schaltflächen |
| Boardwechsel | Bestehende Stopp-Regeln gelten |
| Fehlerfälle | Leere Ordner, fehlende/unlesbare Dateien, Berechtigungsverlust abfangen |

**Loop gilt für den gesamten Ordnerdurchlauf, nicht für eine einzelne Datei.**

Vor Implementierung untersuchen: Datenmodell, Audio-Engine und Completion-Callbacks, Ordneraktualisierung, Persistenz, UI, Fehlerbehandlung, Tests. Im neuen Chat mit einer kleinen Bestandsaufnahme der relevanten Quelldateien beginnen.

## 8. Backlog

| Thema | Stand |
|---|---|
| **Play Folder** | **Nächstes Feature** |
| Automatische Synchronisierung der Release-PR-Versionsdateien | Zurückgestellt |
| CI-Trigger nach automatischem Bot-Commit | Teil der zurückgestellten Release-Automatisierung |
| Signierte APK automatisch an GitHub-Release anhängen | Zurückgestellt; manueller Upload Pflicht |
| F-Droid-Veröffentlichung | Weiterhin Projektziel |
| Erneute Upstream-Contribution / Merge-Angebot an `508-dev` | Ungefähr drei Monate nach Ende Oktober 2026 erneut prüfen |
| Weitere Features | Nach jeder Implementierung gemeinsam priorisieren |

Für eine spätere Vollautomatisierung wurden Fine-Grained PAT oder GitHub-App-Token diskutiert. **Keine dieser Lösungen wurde umgesetzt.** Der derzeitige halbautomatische Prozess ist bewusst beibehalten worden.

## 9. Wichtigste Erkenntnisse

1. Ein erfolgreicher Release-Please-Lauf bedeutet **nicht**, dass eine installierbare APK als Asset vorliegt.
2. Die Android-Versionsdateien werden bislang durch `sync-version.sh` synchronisiert; Release Please allein genügt nicht.
3. `GITHUB_TOKEN`-Folge-Workflows und verpflichtende PR-Checks erfordern besondere Sorgfalt.
4. F-Droid benötigt die Versionsangaben als Literale in Gradle.
5. Release- und Debug-App sind getrennte Installationen.
6. Für Updates muss der ursprüngliche Release-Signaturschlüssel erhalten bleiben.
7. Die SDK-Umgebungsvariablen müssen in neuen Shell-Sitzungen ggf. erneut gesetzt werden.
8. Der Name des hochgeladenen GitHub-Assets muss ausdrücklich kontrolliert werden.
9. `docs/deployment.md` enthält teilweise ältere bzw. geplante Verfahren; im Zweifel aktuelle Workflows und GitHub-Ausgaben prüfen.
10. Nach jedem abgeschlossenen Feature gemeinsam den nächsten Backlog-Punkt bestimmen, statt Infrastrukturthemen unbegrenzt auszubauen.

## 10. Release-Checkliste

- [ ] Release-PR erstellt
- [ ] `sync-version.sh` auf Release-PR-Branch ausgeführt
- [ ] Versionskonsistenz geprüft
- [ ] Generierte Dateien committet und gepusht
- [ ] Pflichtchecks `build` und `conventional-commit` grün
- [ ] Release-PR gemergt
- [ ] Tag und GitHub-Release vorhanden
- [ ] Signierte Release-APK gebaut
- [ ] APK-Version und Signatur geprüft
- [ ] APK als `Soundboard-NG-vX.Y.Z.apk` hochgeladen
- [ ] Release-Asset kontrolliert
- [ ] Optional: Update auf Testgerät geprüft
- [ ] `main` in `develop` integriert und gepusht
- [ ] Entwicklungsstand und Versionskonsistenz geprüft

## 11. Wichtige Dateien

- `docs/deployment.md` – ausführlichere bestehende Deployment-Dokumentation
- `.github/workflows/ci.yml` – CI
- `.github/workflows/release-please.yml` – Release Please
- `release-please-config.json` – Release-Konfiguration
- `.release-please-manifest.json` – Release-Manifest
- `version.txt` – Versionsquelle
- `scripts/sync-version.sh` – abgeleitete Versionen
- `app/build.gradle.kts` – Android-Konfiguration und Signierung
- `CHANGELOG.md` – Release-Änderungen
