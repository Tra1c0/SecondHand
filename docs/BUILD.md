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
dem Projekt. Er baut standardmäßig ARM64; `--abi x86_64` erstellt separat eine
APK für einen x86_64-Emulator. Die APK und ihre SHA-256-Prüfsumme werden unter
`artifacts/` mit ABI-Suffix abgelegt. Der AGP-Entwicklungsparameter wählt nur
eine native Zielarchitektur; eine Liste bedeutet keine universelle APK.
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
