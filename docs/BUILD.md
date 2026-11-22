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

- Workflow `.github/workflows/agent-apk.yml`, nur von Hand gestartet (Actions → „SecondHand
  APK“ → „Run workflow“, Branch wählen). Baut `:app:assembleDebugNoMinify` (App-ID
  `dev.personal.agentkeyboard.debug`, Name „SecondHand“, in allen Varianten ohne Zusatz) und veröffentlicht die APK samt
  SHA-256 als Vorab-Release `build-<Laufnummer>` mit `release-notes/next.md` als Text.
- Bauart (62): Die APK ist die Variante `debugNoMinify`, aber **nicht debuggable** und ohne R8 (nur
  die Variante, die der Owner installiert; Gründe: ein debuggable-Programm läuft ohne die vollen
  Optimierungen von Android, und `BuildConfig.DEBUG` schaltete den Debug-Modus ein, der jedes
  getippte Wort ins Logcat schrieb). Name `debugNoMinify`, App-ID mit `.debug`, Signatur und
  Quellordner `app/src/debugNoMinify` bleiben, sonst wäre es keine Update-Installation mehr. Was sich
  für den Owner ändert: Debug-Modus standardmäßig aus (ein gespeichertes „an“ wird beim ersten Update
  einmal zurückgesetzt, danach lässt es sich wieder einschalten), die Debug-Einstellungen erscheinen
  erst nach fünf Tipps auf die Versionszeile unter Über, Vorschlags-Zeitlimit 200 statt 500 ms, die
  Symbolleisten-Tasten werden nur noch beim Update neu eingelesen, die fünf Ausnahmen bei
  Auffälligkeiten sind aus. Dazu wird die native Bibliothek `libjni_latinime.so` (Wörterbuchsuche,
  Wischtippen) jetzt optimiert übersetzt (`NDK_DEBUG=0` statt `1`, das richtet sich nach `isDebuggable`,
  nicht nach `isJniDebuggable`; P259). Die Diagnose zeigt „Debugfähige App“ und „Debug-Modus“. Wer die Debug-Bauart
  braucht, nimmt die Variante `debug` (`assembleDebug`). Weil die Variante nicht mehr debuggable ist,
  hätte `assemble` zusätzlich `lintVital` ausgeführt; `lint { checkReleaseBuilds = false }` schaltet das
  ab (das volle Lint ist `python3 tools/dev.py lint`).
- Prüfungen des Workflows (63). Jeder Lauf prüft die gebaute APK und scheitert bei einem Fehler,
  bevor etwas veröffentlicht wird. Was tun, wenn …
  - … „Package and version code … expected …“: Der Versionscode muss die Laufnummer sein
    (`AGENT_VERSION_CODE`), das Paket `dev.personal.agentkeyboard.debug`. Nicht die Datei umbenennen
    (Laufnummern, P240); Ursache in `app/build.gradle.kts` suchen. Mit dem Schlüssel des Owners und
    ohne gültige `AGENT_VERSION_CODE` bricht Gradle selbst ab.
  - … „not signed with the owner's certificate“: Das Secret `AGENT_KEYSTORE_BASE64` ist ein anderer
    Schlüssel als der des Owners (unten die Zahl). Secret prüfen; nur wenn der Owner bewusst einen
    neuen Schlüssel einführt (dann ist jede Installation einmal Neuinstallation), `OWNER_CERT_SHA256`
    im Workflow und hier ändern.
  - … „The APK is debuggable“: `isDebuggable` der Variante `debugNoMinify` steht wieder auf `true`.
  - … `apksigner verify` bricht ab: die Signatur ist beschädigt; neu bauen.
  - … „No successful run of agent-tests.yml covers commit …“ (nur `main`): Keiner der letzten
    grünen Läufe des Test-Workflows hat denselben Stand der getesteten Dateien wie dieser Commit.
    Als Nachweis zählt jeder grüne Lauf, dessen Commit in den Pfaden von `agent-tests.yml` gleich
    ist (ein späterer reiner Docs-Commit oder ein Merge-Commit obendrauf behält den Nachweis; der Lauf
    darf vom Arbeitsbranch stammen). Unter Actions → „SecondHand Tests“ → „Run workflow“ auf `main`
    starten, bei Rot die Fehler beheben, dann den APK-Workflow neu starten. Test-Builds anderer Branches
    prüfen das nicht.
  - … „RELEASES_TOKEN is missing“ (nur `main`): Secret anlegen (Einrichtung unten). Der Lauf scheitert
    bewusst, damit ein Build, den die App nie findet, nicht als fertig gilt. Das private Vorab-Release
    ist da schon veröffentlicht.
  - … der Schritt „Read the public release back“ scheitert: im öffentlichen Repository fehlt das
    Release oder ein Asset, eine Download-URL weicht ab, oder eine der beiden APK-Dateien lässt sich
    nicht ohne Token laden bzw. passt nicht zu ihrer Prüfsumme (die Meldung sagt, was). Release dort
    ansehen; ein erneuter Lauf derselben Nummer (Actions → „Re-run“) lädt die Dateien mit `--clobber`
    neu hoch. Die Liste fragt der Schritt zuerst ohne Token ab, wie die App; antwortet GitHub mit dem
    Ratenlimit für anonyme Anfragen (403/429, die Runner teilen sich Adressen), fragt er mit dem Token
    des Laufs nach, das im öffentlichen Repository keine Rechte hat und dieselbe Liste sieht.
  - … „tag build-N already exists … points to …“: Die Nummer gehört einem anderen Commit (z. B. weil
    der Workflow umbenannt wurde). Nichts löschen, bevor der Owner entschieden hat; ein erneuter Lauf
    gleicher Nummer und gleichen Commits lädt dagegen neu hoch.
  - Reihenfolge auf `main`: Prüfschritt, privates Vorab-Release, dann Test-Nachweis, öffentliches
    Release, Zurücklesen. Scheitert der Lauf am Test-Nachweis, am Token oder beim Zurücklesen, gibt es
    das private Vorab-Release dieser Nummer schon; ein Update für die App ist es erst im öffentlichen
    Repository.
  - „Re-run“ und neuer Start: Ein erneuter Lauf (Actions → „Re-run“) behält die Nummer und benutzt die
    Workflow-Datei des ursprünglichen Commits. Er hilft nach einem Ausfall von außen (Netz, GitHub,
    fehlendes Secret, Tests inzwischen grün). Steckt der Fehler in `agent-apk.yml` selbst oder im Code,
    braucht es nach der Korrektur einen neuen Start („Run workflow“) mit neuer Nummer. Blieb das
    Release durch einen abgebrochenen Upload als Entwurf stehen, veröffentlicht der erneute Lauf es
    (`gh release edit --draft=false`).
  - „agent-tests.yml: path line not understood“ oder „The path … matches no file“: Der Test-Nachweis
    liest die Pfadliste von `agent-tests.yml`. Dort darf hinter einem Pfad kein Kommentar stehen, kein
    Ausschluss (`!`) vorkommen und jeder Pfad muss auf mindestens eine Datei passen; sonst würde der
    Nachweis diesen Pfad still übergehen.
  - Der Gradle-Schritt bekommt `-Xmx3g`; `gradle.properties` hat nur 1024m. Die Schlüsseldatei wird
    auch nach einem gescheiterten Build gelöscht (`trap`).
- Slang-Liste neu erzeugen (71): `python3 -I tools/build_informal_words.py <entpacktes Datenpaket>` schreibt
  `app/src/main/assets/agent/informal_words.txt`, `agent-core/src/test/resources/informal_cases.tsv` und
  `docs/referenzen/datensaetze/AUSWAHL.md`. Es liest die Wörterbücher der App (`app/src/main/assets/dicts/main_{de,en-US,en-GB}.dict`):
  ändert sich eines davon, die Liste neu erzeugen und prüfen, was herausfällt; danach `InformalWordsTest` und `SuggestTest`.
- `build-test-auto.yml` (Upstream-Rest): lief bei jedem Pull Request und war immer rot (er ruft
  `testRunTestsUnitTest` auf, nicht der Weg dieses Forks). Seit 63 nur noch von Hand
  (`workflow_dispatch`); die Tests laufen in `agent-tests.yml`.
- Die Workflow-Dateien dürfen nie umbenannt oder verschoben werden (nur die `name:`-Zeile ändert sich): GitHub zählt die Laufnummer je Workflow-Datei, eine neue Datei finge bei 1 an, und alle installierten Builds sähen eine niedrigere Versionsnummer (P240).
- Zwei Dateinamen (57): Jeder Build liegt unter `SecondHand-build-<N>.apk` und
  `AgentKeyboard-build-<N>.apk` (gleiche Bytes), je mit einer eigenen `.sha256`, die genau diese
  Datei nennt (vier Dateien je Release, im Vorab-Release und im öffentlichen Repository). Grund:
  Installierte Builds bis 27 suchen nur nach dem alten Namen (`UpdateReleases.apkName` in
  `agent-core`); ohne die alte Datei bekämen sie nie wieder ein Update. Die App ab diesem Build
  nimmt den neuen Namen zuerst und fällt auf den alten zurück. Der alte Name darf entfallen, sobald es
  keine Installation unter diesem Build mehr gibt (alle Geräte des Owners sind auf dem neuen
  Stand); dann in `agent-apk.yml` die `LEGACY_APK_NAME`-Zeilen und `UpdateReleases.legacy*` samt Tests
  entfernen. Die lokale Datei aus `tools/dev.py build` heißt `SecondHand_<Version>-debugNoMinify-<ABI>.apk`
  (Gradle-Ausgabename in `app/build.gradle.kts`).
- Signatur: Repository-Secrets `AGENT_KEYSTORE_BASE64` (PKCS12-Datei, Base64) und
  `AGENT_KEYSTORE_PASSWORD` (Passwort von Datei und Schlüssel), Alias `agentkeyboard`. Der
  Workflow schreibt die Datei nach `$RUNNER_TEMP` und löscht sie nach dem Build. `app/build.gradle.kts`
  nimmt die Signatur nur, wenn `AGENT_KEYSTORE_FILE` gesetzt ist; sonst bleibt es beim
  Debug-Schlüssel.
- Versionsnummer: `AGENT_VERSION_CODE` = Laufnummer des Workflows, also steigend. Mit dem Schlüssel
  des Owners und ohne gültige Zahl bricht der Build ab (63), statt still 1 zu nehmen.
- Notizen in der APK (54): Der Gradle-Task `embed<Variante>ReleaseNotes` (`EmbedReleaseNotes` in
  `app/build.gradle.kts`) kopiert `release-notes/next.md` bei jedem Build als Asset
  `agent/release_notes.md` in die APK, auch lokal und für die Unit-Tests (`mergeDebugAssets`). Die
  App zeigt sie nach einem Update einmal als „Neu in Build N“, offline. Dieselbe Datei ist der Text
  des Releases (`--notes-file`); die In-App-Prüfung speichert davon höchstens 8 KiB für „Build N
  ist da“. Darum gehört in `next.md` das Wichtigste nach oben.
- Zertifikat des Owners (SHA-256):
  `D5:6E:8F:00:11:D7:80:63:37:90:8C:FA:A7:36:E5:D4:F8:FE:25:49:14:5A:5B:37:5D:FB:BB:1D:18:63:60:2A`.
  Der Workflow gibt das Zertifikat der APK im Log aus (Schritt „Verify the built APK“) und bricht ab, wenn es nicht dieses ist
  (`OWNER_CERT_SHA256` im Kopf von `agent-apk.yml`; `WorkflowContractTest` prüft, dass beide gleich sind).
- Öffentliches Release-Repository `Tra1c0/SecondHandAgenticKeyboard` (seit 2026-11-14, vorher
  `Tra1c0/AgenticKeyboard`; Name in `UpdateReleases.REPOSITORY`
  und in `RELEASE_REPOSITORY` des Workflows): Der Workflow legt dort zusätzlich dasselbe Release
  `build-<Laufnummer>` mit APK und `.sha256` an (kein Vorab-Release, Text aus
  `release-notes/next.md`). Die In-App-Prüfung und Freunde lesen nur dort, ohne Token. Nur Builds
  von `main` (geprüft) gehen dorthin und werden damit zum Update; Test-Builds vom Arbeitsbranch
  bleiben Vorab-Releases hier (Owner, 2026-11-14). Fehlt das Secret `RELEASES_TOKEN`, scheitert der Lauf (seit 63; vorher nur eine Warnung). Danach
  liest der Workflow die öffentliche Liste wie die App ohne Token zurück, verlangt vier Assets mit den
  erwarteten Download-URLs, lädt beide APK-Dateien samt `.sha256` ohne Token und vergleicht sie mit dem
  gebauten Stand. Vor dem Veröffentlichen verlangt er einen grünen Lauf von `agent-tests.yml` für
  einen Commit mit demselben Stand der getesteten Dateien.
- Wann die App fragt (seit 56): auf „Jetzt prüfen“, mit der täglichen Suche (Schalter, anfangs aus)
  und mit „Neue Builds sofort melden“ (Schalter, anfangs an) beim Öffnen der Tastatur, höchstens alle
  15 Minuten seit dem letzten Versuch, nach Fehlschlägen in Folge seltener (bis 4 Stunden). Gesendet
  wird jedes Mal nur ein `GET` der Release-Liste an `api.github.com`, ohne Token und ohne Getipptes,
  mit `If-None-Match` (ETag der letzten Antwort, nicht direkt nach einem Update); unverändert
  antwortet GitHub mit 304. Die Prüfung beim Öffnen läuft nicht bei gesperrtem Gerät, Inkognito, in
  Passwortfeldern, mit Datensparmodus oder ohne Netz; „Jetzt prüfen“ und die tägliche Suche achten
  nicht auf den Datensparmodus. Einen Push (FCM
  oder eigener Server) gibt es nicht (Owner, 2026-11-15): Ein neuer Build erscheint also erst, wenn
  die Tastatur das nächste Mal nach Ablauf der 15 Minuten geöffnet wird. Download und Installation
  bleiben beim Tipp des Owners.
- Einmalige Einrichtung durch den Owner: Das öffentliche Repository braucht mindestens einen
  Commit (z. B. eine README), sonst kann `gh release create` dort kein Tag anlegen. Dann auf
  GitHub → Settings → Developer settings → Personal access tokens → Fine-grained tokens →
  „Generate new token“: Resource owner `Tra1c0`, Repository access „Only select repositories“ →
  nur `Tra1c0/SecondHandAgenticKeyboard`, Repository permissions → Contents: „Read and write“, sonst nichts.
  Den Token im privaten Repository unter Settings → Secrets and variables → Actions als Repository
  secret `RELEASES_TOKEN` speichern. Läuft er ab, neuen Token anlegen und das Secret ersetzen.
- Geht der Schlüssel verloren, lässt sich keine Version mehr als Update installieren: einmal
  deinstallieren (alle Daten weg), neuen Schlüssel als Secret anlegen. Der Owner hebt eine Kopie
  im Passwortmanager auf; Keystores werden nie committet.

## Gerätetests im Emulator (73)

- Workflow `.github/workflows/device-tests.yml` („SecondHand Device Tests“): startet bei Pushes auf
  `claude/**` und `main`, die `app/**`, `agent-core/**`, `tools/devicetest/**`, Gradle-Dateien oder den
  Workflow ändern, und von Hand (auch auf einem Arbeitsbranch). Eingaben von Hand: `repeats` (1, 3 oder 5
  Emulatoren nebeneinander, jeder frisch gestartet) und `control` (Kontrolllauf: grün genau dann, wenn jedes Szenario an einem Vergleich scheitert). Ein neuer Push auf
  denselben Branch bricht den laufenden Lauf ab; zwei von Hand gestartete Läufe desselben Branches auch.
- Job „Build the APKs“: `python3 tools/dev.py build --abi x86_64` (die ausgelieferte Variante; mit einer
  vorgegebenen ABI baut Gradle `libjni_latinime.so` nur für diese, der Emulator ist x86_64) und
  `./gradlew -p tools/devicetest :app:assembleDebug` (die Test-App, ein eigenes Gradle-Projekt mit
  demselben Wrapper). Er bricht ab, wenn der Tastatur-APK die x86_64-Bibliothek fehlt oder sie Testcode
  enthält (Suche nach dem Paketnamen der Test-App in `classes*.dex`). Keine Geheimnisse, Wegwerf-Schlüssel
  des Runners, kein Release; die beiden APKs liegen drei Tage als Artefakt `apks` am Lauf.
- Job „Emulator <n>“: KVM freischalten (udev-Regel), `reactivecircus/android-emulator-runner` v2.38.0
  (per Commit festgelegt) mit API 34, `google_apis`, `x86_64`, Profil `pixel_6`, 4 Kernen, 4096 MB,
  dann `python3 -I tools/devicetest/devicetest.py --keyboard-apk … --test-apk … --out device-test-out`.
  Der Emulator-Schritt hat 25 Minuten Zeit. Der letzte Schritt „Results“ schreibt die Tabelle in die Job-Zusammenfassung und ins Log und scheitert,
  wenn `results.json` fehlt oder nicht `ok` ist.
- Ergebnis lesen: im Job-Log die Zeilen `INFO …`, `RESULT PASS|FAIL|ERROR <szenario> …`, `PROBLEM …`,
  `CONTROL …`, `SUMMARY OK|FAILED passed= failed= total= expected= videos=`. `FAIL` ist eine nicht erfüllte
  Erwartung an die Tastatur, `ERROR` ein Problem des Geräts oder des Skripts (zählt auch als rot). Artefakt
  `device-test-<n>` (14 Tage): `video-NN.mp4`, `<szenario>-end.png`, bei Rot `<szenario>-end.json`
  (Knotenliste), `results.json`, `summary.md`. Aus der Cloud-Umgebung lassen sich Artefakte und ganze Logs
  nicht laden (403): mit `get_job_logs` und kleinem `tail_lines` arbeiten oder die Zeilen von einem
  Hilfs-Agenten herausfiltern lassen.
- Ein neues Szenario ist eine Funktion in `devicetest.py`, eingetragen in `SCENARIOS`; es beginnt mit
  `d.start(name)`, tippt mit `d.type`/`d.press` und prüft mit `d.expect_field`/`d.expect_equal`. Ein
  Szenario ohne Vergleich gilt als `ERROR`. Danach einen Kontrolllauf (`control`) starten: Jedes Szenario
  muss dort FAIL zeigen, sonst steht es unter „CONTROL BROKEN“ und der Kontrolllauf ist rot. Der Kontrolllauf
  belegt je Szenario nur den ersten Vergleich, der vom Getippten abhängt.
- Lokal gibt es keinen Emulator (kein `/dev/kvm`); das Skript lässt sich nur gegen ein nachgebautes `adb`
  trocken prüfen. Jede Annahme über das Verhalten im Emulator gilt erst nach einem GitHub-Lauf.

## Begleit-App `companion/` (80)

- Ein eigenes Gradle-Projekt neben `tools/devicetest`: `companion/settings.gradle.kts`, `companion/build.gradle.kts`,
  `companion/gradle.properties`, Modul `companion/app`. Das Haupt-`settings.gradle` kennt es nicht; es nutzt den Wrapper des
  Hauptprojekts, dieselben Versionen von AGP (9.1.1) und Kotlin (2.4.0, über das Plugin `kotlin.plugin.serialization`
  mit `apply false`) und `compileSdk = 37`.
- Bauen und prüfen (der Lauf braucht Internet für `com.qualcomm.qti:geniex-android:0.10.0` und `kotlinx-coroutines`, die
  Cloud-Umgebung nutzt ihren Maven-Spiegel):
  `./gradlew -p companion :app:testDebugUnitTest :app:assembleDebug`. Die APK liegt danach unter
  `companion/app/build/outputs/apk/debug/app-debug.apk` (etwa 97 MB, enthält Qualcomms Laufzeit, nur `arm64-v8a`; nicht versioniert).
  Es gibt nur diese Debug-Variante, keine Signatur des Owners, keinen Workflow.
- **Wird nie veröffentlicht:** nicht in `agent-apk.yml`, in kein Release und nicht ins Repository. Grund: Die APK bündelt die
  Qualcomm-Laufzeit (QAIRT/QNN, `llama.cpp`), und aus den Texten (BSD-3 des GenieX-Codes plus Qualcomms Nutzungsbedingungen, keine
  eigene Lizenz der Bibliotheken im Paket) ist nicht ablesbar, ob sie so weitergegeben werden darf (`docs/W4-GENIEX-MACHBARKEIT.md` §4).
  Die Builds dieses Projekts sind öffentlich herunterladbar, darum geht diese APK nur als Datei an den Owner. Nach jedem Bau Dateigröße
  und SHA-256 notieren und mitschicken.
- Kein Emulator-Test: Das AAR hat nur `arm64-v8a`, und die Laufzeit braucht die NPU eines Snapdragon 8 Elite. Auf einem x86-Emulator
  zeigt die App „Auf diesem Gerät nicht unterstützt“ (nicht ausprobiert, aus dem AAR gelesen).

## Fremdbibliotheken der Agent-Funktionen

- `com.google.zxing:core` 3.5.4 (Apache-2.0, `LICENSE-Apache-2.0` im Wurzelverzeichnis): nur der
  QR-Encoder für `@qr`, kein Scanner, keine Kamera.
- `com.googlecode.libphonenumber:libphonenumber` 9.0.40 (Apache-2.0, `LICENSE-Apache-2.0`), in
  `agent-core`: Telefonnummern des Formatwächters (Etappe 41), nur Prüfen und Formatieren mit den
  mitgelieferten Metadaten, ohne Geocoder und Netzbetreiber. Größe gemessen am 2026-11-14: Jar
  360 KB (Klassen 257 KB, Metadaten 331 KB entpackt, 126 KB gepackt), keine weiteren
  Abhängigkeiten; in der unminifizierten Debug-APK geschätzt etwa 0,35 MB mehr (Debug-APK vorher
  38 660 284 Byte). Grenze laut Auftrag 1,5 MB, deshalb kein eigener Formatierer.

## Deutscher Verbwortschatz

`app/src/main/assets/agent/german_verb_lemmas.txt` enthält 6.584 Infinitive. Quelle sind die
Beiträge zu [UniMorph Deutsch](https://github.com/unimorph/deu/tree/d226d2112d3490d8f04ece10d4538123d4297a39)
auf Grundlage der Beiträge im englischen Wiktionary unter [CC BY-SA 3.0](https://creativecommons.org/licenses/by-sa/3.0/).
Die abgeleitete Liste enthält nur kleingeschriebene Infinitive, ergänzt um häufige Verben. Sie
steht unter derselben Lizenz. Nach dem Download der festgeschriebenen Datei `deu` erzeugt
`python3 -I tools/build_german_verb_lemmas.py deu` die Liste. Das Skript prüft die SHA-256-Summe.

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

## Emoji-Stichwörter

Quelle der Stichwort-Tabelle für tolerante Emoji-Vorschläge (Etappe 35): CLDR 49.0.0 als
npm-Pakete von `registry.npmjs.org`, Lizenz Unicode License V3 (dieselbe Datenquelle und Lizenz
wie die CLDR-Teile der Emoji-Wörterbücher oben), abgerufen am 2026-11-13. Die Pakete sind nicht im
Repository.

| Paket | SHA-256 des `.tgz` |
| --- | --- |
| `cldr-annotations-full-49.0.0.tgz` | `ce415a4898da7a4ca755437de2593863c05045fc6b1649d8da5ee05fa3bdc33b` |
| `cldr-annotations-derived-full-49.0.0.tgz` | `e83ab0348ec09f4b5bb27f658b9c424c9c8fbf512484b728903515b9f01c3c28` |

Erzeugt: `app/src/main/assets/emoji_keywords.bin`, 118 669 Byte, SHA-256
`3b97cc360530209322526e5f75b1d5ad976df2dafaf2a728dfb9311e1cd71b30` (Deutsch, Englisch, Spanisch,
je 1914 der 1940 Emojis der Emoji-Ansicht; ohne Hauttöne). Neu erzeugen:

```bash
mkdir -p /tmp/cldr && cd /tmp/cldr
for p in cldr-annotations-full cldr-annotations-derived-full; do
  curl -sSfLO https://registry.npmjs.org/$p/-/$p-49.0.0.tgz && mkdir -p $p && tar xzf $p-49.0.0.tgz -C $p
done
cd - && python3 -I tools/emoji_keywords.py /tmp/cldr/cldr-annotations-full /tmp/cldr/cldr-annotations-derived-full
```

Die Ausgabe ist bei gleicher Eingabe und gleicher Emoji-Ansicht (`assets/emoji/*.txt`) Byte für
Byte gleich (am 2026-11-13 zweimal erzeugt). Nach einer neuen CLDR-Version oder geänderten
Emoji-Listen Summen und Zahlen hier anpassen und `CT/EmojiKeywordsTest` laufen lassen. Das Asset
hat bewusst keine Endung `.gz` (Fallstrick P70).

## Emoji-Mix-Tabelle

Quelle der Paarliste (25a): `https://raw.githubusercontent.com/xsalazar/emoji-kitchen-backend/main/app/metadata.json`,
abgerufen am 2026-11-13, 98 873 872 Byte, SHA-256
`73a7a2a6fbeb620f13ed6622b344cc0dfd21dfe07aca8ce46c564afe54e21804` (am selben Tag zweimal
geladen, gleiche Summe). Die Datei ist nicht im Repository.

Lizenzstand (**später zu prüfen**): Das Repository des Autors (`xsalazar/emoji-kitchen-backend`)
hat keine Lizenzdatei. Der Autor hat laut Owner am 2026-11-13 mündlich zugestimmt; eine
Lizenzdatei oder schriftliche Erlaubnis fehlt noch. Der Owner hat entschieden, dass wir die Liste
verwenden. Offen: später nachprüfen, ob eine Lizenzdatei im Repository des Autors liegt, und
das Ergebnis hier eintragen (`docs/HANDOFF.md`, `docs/ROADMAP.md` → U7). Am 2026-11-13 erneut
erzeugt: gleiche Eingabe-Summe, gleiche Ausgabe Byte für Byte.
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



## Emoji-Suchindex (Etappe 93a)

`app/src/main/assets/emoji_search/search_<de|en|es>.tsv` stammt aus den Unicode-CLDR-Annotationen (`cldr-json`, Pakete `cldr-annotations-full` und
`cldr-annotations-derived-full`, Stand 2026-11-22), Lizenz Unicode License V3 (frei, mit Hinweis; der Hinweis steht in der ersten Zeile jeder Datei).
Neu erzeugen: die sechs `annotations.json` als `<lang>.json` und `d_<lang>.json` in einen Ordner legen, dann
`python3 tools/emoji/build_search_index.py <ordner> app/src/main/assets/emoji_search`.
