# Build und Prüfungen

## Festgeschriebene Werkzeuge

Der unveränderte Upstream-Snapshot verwendet JDK 17, Gradle 9.7.1 (inklusive
Wrapper-Prüfsumme), Android Gradle Plugin 9.1.1, Kotlin 2.4.0,
compile/target SDK 37 und NDK 28.0.13004108. Diese Versionen werden zunächst
nicht aktualisiert.

Die Android-36-Robolectric-Tests benötigen eine separate Java-21-Laufzeit.
JDK 17 bleibt die Kompilier- und Gradle-Laufzeit; Java 21 startet ausschließlich
die Testprozesse. Gradle erkennt übliche lokale Installationen automatisch;
zusätzliche Installationen können über `org.gradle.java.installations.paths`
in der eigenen Gradle-Konfiguration angegeben werden.

Mit den aktuellen Android-Kommandozeilenwerkzeugen heißt das Plattformpaket
`platforms;android-37.0`. AGP verwendet zusätzlich Build Tools 36.0.0.

```bash
sdkmanager 'platform-tools' 'platforms;android-37.0' \
  'build-tools;36.0.0' 'ndk;28.0.13004108'
```

`JAVA_HOME` muss auf JDK 17 und `ANDROID_HOME` auf das SDK zeigen. Lokale
Proxy-/Zertifikatseinstellungen gehören in die eigene Gradle-Konfiguration,
nicht ins Repository. Zugangsdaten werden für den lokalen Meilenstein nicht
benötigt.

## Befehle

```bash
python3 tools/dev.py doctor
python3 tools/dev.py build
python3 tools/dev.py upstream-tests
```

Der Build-Helper erkennt alternativ eine benutzereigene `.toolchains` neben
dem Projekt. Mit dem festgeschriebenen Plugin enthält die APK alle vier ABIs, unabhängig
von `--abi`; sie läuft also auf Telefon und x86_64-Emulator. Die APK und ihre SHA-256-Prüfsumme werden unter
`artifacts/` abgelegt; der Dateiname nennt die tatsächlich enthaltenen ABIs (derzeit `universal`).
Die unveränderten Upstream-ABI-Definitionen bleiben im Projekt erhalten.

Nach Einführung der entsprechenden Module/Tests:

```bash
python3 tools/dev.py core-tests
python3 tools/dev.py agent-tests
python3 tools/dev.py lint
```

## Befunde beim Ausgangsbuild

- Die Upstream-CI nennt `testRunTestsUnitTest`. Dieser Task existiert mit dem
  festgeschriebenen Plugin nicht; `testDebugUnitTest` ist der verfügbare Task.
- Upstream dokumentiert bekannte Testfehler und verwendet dafür ursprünglich
  den `runTests`-Buildtyp. Die tatsächlichen Baseline-Ergebnisse werden in
  `VALIDATION.md` festgehalten; Fehler werden nicht durch stumme Ausschlüsse
  als bestanden dargestellt.
- Die aktuelle Cloud-Umgebung hat knapp 8 GB Dateisystemplatz und kein
  `/dev/kvm`. Eine parallele Emulatorinstallation füllte den Datenträger;
  der unvollständige Download und seine Caches wurden entfernt. Ein solcher
  Infrastrukturabbruch ist kein App-Testbefund.

Vor einem Geräteversuch muss der fertige Build samt Prüfungen vorliegen.
SDK-Downloadarchive können nach abgeschlossener Installation bereinigt werden.

## Begrenzter temporärer Testspeicher

App-Unit-Tests verwenden seit13e `forkEvery=1`: eigener Java-21-Worker je Testklasse.
Robolectric hält sonst App-/Wörterbuchkopien bis zum Prozessende, was im begrenzten
Cloud-Dateisystem trotz freiem Platz vor dem Lauf zu temporärem ENOSPC führte.
Der Wiederholungslauf prüft alle332Fälle ohne diese Infrastrukturfehler. Assertions
und Testauswahl bleiben unverändert; Prozessstarts erhöhen nur die Testdauer.

## Installierbare APK (GitHub Actions)

Android installiert eine neue Version nur dann als Update (mit allen Daten), wenn sie mit
demselben Schlüssel signiert ist und eine höhere Versionsnummer hat. Lokale und Cloud-Builds
nutzen einen Debug-Schlüssel, der in jeder neuen Umgebung anders ist; sie sind nur zum Prüfen da.

- Workflow `.github/workflows/agent-apk.yml`, nur von Hand gestartet (Actions → „Agent Keyboard
  APK“ → „Run workflow“, Branch wählen). Baut `:app:assembleDebugNoMinify` (App-ID
  `dev.personal.agentkeyboard.debug`, Name „Agent Keyboard Lab“) und veröffentlicht die APK samt
  SHA-256 als Vorab-Release `build-<Laufnummer>` mit `release-notes/next.md` als Text.
- Signatur: Repository-Secrets `AGENT_KEYSTORE_BASE64` (PKCS12-Datei, Base64) und
  `AGENT_KEYSTORE_PASSWORD` (Passwort von Datei und Schlüssel), Alias `agentkeyboard`. Der
  Workflow schreibt die Datei nach `$RUNNER_TEMP` und löscht sie nach dem Build. `app/build.gradle.kts`
  nimmt die Signatur nur, wenn `AGENT_KEYSTORE_FILE` gesetzt ist; sonst bleibt es beim
  Debug-Schlüssel.
- Versionsnummer: `AGENT_VERSION_CODE` = Laufnummer des Workflows, also steigend.
- Zertifikat des Owners (SHA-256):
  `D5:6E:8F:00:11:D7:80:63:37:90:8C:FA:A7:36:E5:D4:F8:FE:25:49:14:5A:5B:37:5D:FB:BB:1D:18:63:60:2A`.
  Der Workflow gibt das Zertifikat der APK im Log aus.
- Öffentliches Release-Repository `Tra1c0/AgenticKeyboard` (Name in `UpdateReleases.REPOSITORY`
  und in `RELEASE_REPOSITORY` des Workflows): Der Workflow legt dort zusätzlich dasselbe Release
  `build-<Laufnummer>` mit APK und `.sha256` an (kein Vorab-Release, Text aus
  `release-notes/next.md`). Die In-App-Prüfung und Freunde lesen nur dort, ohne Token. Fehlt das
  Secret `RELEASES_TOKEN`, überspringt der Workflow diesen Schritt mit einer Warnung; der Build
  schlägt deshalb nicht fehl.
- Einmalige Einrichtung durch den Owner: Das öffentliche Repository braucht mindestens einen
  Commit (z. B. eine README), sonst kann `gh release create` dort kein Tag anlegen. Dann auf
  GitHub → Settings → Developer settings → Personal access tokens → Fine-grained tokens →
  „Generate new token“: Resource owner `Tra1c0`, Repository access „Only select repositories“ →
  nur `Tra1c0/AgenticKeyboard`, Repository permissions → Contents: „Read and write“, sonst nichts.
  Den Token im privaten Repository unter Settings → Secrets and variables → Actions als Repository
  secret `RELEASES_TOKEN` speichern. Läuft er ab, neuen Token anlegen und das Secret ersetzen.
- Geht der Schlüssel verloren, lässt sich keine Version mehr als Update installieren: einmal
  deinstallieren (alle Daten weg), neuen Schlüssel als Secret anlegen. Der Owner hebt eine Kopie
  im Passwortmanager auf; Keystores werden nie committet.

## Fremdbibliotheken der Agent-Funktionen

- `com.google.zxing:core` 3.5.4 (Apache-2.0, `LICENSE-Apache-2.0` im Wurzelverzeichnis): nur der
  QR-Encoder für `@qr`, kein Scanner, keine Kamera.

## Mitgelieferte Emoji-Wörterbücher

Aus `https://codeberg.org/Helium314/aosp-dictionaries`, Ordner `emoji_cldr_signal_dictionaries`
(Stand 2026-09-22), unverändert nach `app/src/main/assets/dicts/` kopiert. Lizenz laut README des
Repositorys: Unicode License V3 (CLDR) und AGPL-3.0 (Signal); mit der GPL-3.0 des Forks
vereinbar (GPL-3.0 Abschnitt 13). Zum Aktualisieren die Dateien neu laden und die Summen hier
ersetzen.

| Datei | SHA-256 |
| --- | --- |
| `emoji_de.dict` | `d9a6c4b557c66ecc47e5cec965bd32976b69098fbfab32680a90d59c50e9c153` |
| `emoji_en.dict` | `4102dd0cbed74fd1f81f69ce99ec07e6c474f257501f8273338cfb2725fd85c4` |
| `emoji_es.dict` | `7f00955494a6f75047c4186dfd58584e61c0303ef1373ed3715a2d531864db04` |

## Emoji-Mix-Tabelle

Quelle der Paarliste (25a): `https://raw.githubusercontent.com/xsalazar/emoji-kitchen-backend/main/app/metadata.json`,
abgerufen am 2026-11-13, 98 873 872 Byte, SHA-256
`73a7a2a6fbeb620f13ed6622b344cc0dfd21dfe07aca8ce46c564afe54e21804` (am selben Tag zweimal
geladen, gleiche Summe). Die Datei ist nicht im Repository.

Lizenzstand: Das Repository des Autors hat keine Lizenzdatei. Erlaubnis des Autors laut Owner
mündlich am 2026-11-13, schriftliche Lizenz ausstehend. Der Owner hat entschieden, dass wir die
Liste verwenden. Offen: Lizenzdatei im Repository des Autors nachprüfen (`docs/HANDOFF.md`).
Die Bilder selbst lädt die App erst auf dem Telefon von Google (www.gstatic.com), sie sind nicht
in der APK.

Erzeugt: `app/src/main/assets/emoji_mix_pairs.bin`, 48 690 Byte, SHA-256
`28a06b840cca7f5203b83a8c8d6c02041615a44096676a6baf73e37bbb63a556` (619 Emojis, 147 000 Paare
nur mit dem neuesten Bild je Paar, 43 Datumswerte). Neu erzeugen:

```bash
mkdir -p /tmp/emojimix && curl -sSo /tmp/emojimix/metadata.json \
  https://raw.githubusercontent.com/xsalazar/emoji-kitchen-backend/main/app/metadata.json
python3 -I tools/emoji_mix_table.py /tmp/emojimix/metadata.json
```

Der Generator prüft, dass jede `gStaticUrl` dem Muster der App entspricht, und bricht sonst ab;
die Ausgabe ist bei gleicher Eingabe Byte für Byte gleich (gzip ohne Zeitstempel). Danach
Summen und Zahlen hier und `CT/EmojiMixTest` (619 Emojis, 147 000 Paare) anpassen. Das Asset hat
bewusst keine Endung `.gz` (Fallstrick P70).

