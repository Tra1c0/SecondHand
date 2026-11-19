# GenieX-Companion-App: Machbarkeitsstudie (Stand 2026-11-19, Fassung 2 mit Primärquellen)

Methode: curl/javap/strings; Ablage unter `.../scratchpad/geniex-dl/` (nur gelesen, nie ausgeführt). Kürzel: AAR = `geniex-dl/aar/x/`, DOCS = `geniex-dl/docs/llms-full.txt` (https://geniex.aihub.qualcomm.com/llms-full.txt), HF = `geniex-dl/hf/` (huggingface.co/qualcomm/Qwen3-4B), RAW = raw.githubusercontent.com/qualcomm/GenieX/main/, DEMO = raw.githubusercontent.com/qualcomm/ai-hub-apps/main/apps/geniex_chat_android/.
"nicht verifiziert" = weiterhin ohne Primärbeleg. Keine Modellgewichte geladen; vom Modell-Zip nur das Zip-Inhaltsverzeichnis per Range-Request (letzte 128 KB) gelesen.

## 0. Kernbefund der zweiten Runde (Blocker)

Für das Xiaomi 15 (SM8750, generischer "Snapdragon 8 Elite") ist **kein** vorkompiliertes Qwen3-4B-Bundle veröffentlicht. HF `release_assets.json` (v0.67.0) listet für w4a16 nur: qcs8275, qcs9075, sa8775p, **snapdragon-8-elite-for-galaxy**, **snapdragon-8-elite-gen5-for-galaxy**, x-elite, x2-elite. Die AI-Hub-Seite sagt: "This model is currently not supported on any All Models chipset" und nennt als Geräte u. a. Samsung Galaxy S25/S26 (aihub.qualcomm.com/models/qwen3_4b). Dasselbe gilt für Qwen3-4B-Instruct-2507 (HF release_assets.json, gleiche Chipsetliste). Das S3-Objekt für `..._qualcomm_snapdragon_8_elite.zip` antwortet 403 (nicht vorhanden), das `..._for_galaxy.zip` antwortet 200.
Die GenieX-Doku sagt dazu: ein Galaxy S25 meldet `ro.soc.model = SM8750`, nicht `SM8750-AC` (= Alias von `qualcomm-snapdragon-8-elite-for-galaxy`); "pass the chipset explicitly if you need the variant asset" (DOCS, Abschnitt Platforms, Hinweis "Chipset vs. SoC id"). Ob das Galaxy-Bundle auf einem Nicht-Galaxy-SM8750 (Xiaomi 15) läuft: **nicht verifiziert** (gleiche Hexagon-Architektur v79 plausibel; kompilierte Kontext-Binaries sind aber SoC-spezifisch). Die Demo trägt für Qwen3-4B `chipset: "SM8750"` ein (DEMO assets/model_list.json); ob diese Anfrage für qwen3_4b ein Asset findet, ist nicht belegt (Fehlerstring "Requested chipset not available for this model" in libgeniex.so).

## 1. Zugriff (aktuell)

Erreichbar: raw.githubusercontent.com, repo1.maven.org, huggingface.co (API + raw), geniex.aihub.qualcomm.com, aihub.qualcomm.com, www.qualcomm.com (JS-Seiten, Inhalt über `.model.json`), S3 qaihub-public-assets (genaue Objekt-URLs; Listing 403). Weiterhin nicht erreichbar: github.com Web/API, HF-CDN (nicht angefragt), github-MCP für qualcomm/*.

## 2. Android-SDK (Maven, AAR geprüft)

- `com.qualcomm.qti:geniex-android`, neueste auf Maven Central **0.10.0** (2026-10-28); 0.11.0 existiert als CLI-Release (S3 index.json), Maven 404 (maven-metadata.xml; HEAD 0.11.0.pom).
- POM: Lizenzen "BSD 3-Clause" + "Qualcomm Terms of Use", **keine Abhängigkeiten deklariert**; Code nutzt kotlinx-coroutines-Flow und kotlin.Result, also selbst einbinden (javap). Maven .sha1 stimmt mit dem geladenen AAR überein.
- AAR-Manifest: package `com.geniex.sdk`, minSdk 27, keine Permissions, nur `uses-native-library required=false` für libadsprpc.so, libcdsprpc.so, libOpenCL.so. Die App muss `INTERNET` selbst deklarieren (DOCS Android Install, Schritt 3).
- Native Libs **nur arm64-v8a**, **QAIRT/QNN-Runtime gebündelt** (libQnnHtp.so, libQnnHtpV79.so/-Skel/-Stub für SM8750, V81 für Gen 5, libQnnSystem, libQnnHtpPrepare 85 MB, libgeniex*.so, llama.cpp/ggml). Gebündelte QAIRT-Version: 2.50.40.260831 (strings libQnnHtp.so; notes/build.md Z. 203).
- Demo-Manifest/Gradle: `INTERNET`, `WAKE_LOCK` (Download), `packaging { jniLibs.useLegacyPackaging = true }`, `android:extractNativeLibs="true"` (DEMO build.gradle/AndroidManifest). Begründung dafür steht in der Doku nicht (nicht verifiziert). `uses-native-library` für libcdsprpc.so kommt bereits aus dem AAR-Manifest.
- Doku-Hinweis: Init liefert `NOT_SUPPORTED`, wenn dem Gerät die CPU-Features fehlen (armv8.7-a fp16/dotprod/i8mm); beim Xiaomi 15 nicht relevant (DOCS Troubleshooting Android).
- Netz/Telemetrie: kein Java-Netzwerkcode; Downloader nativ (Rust) in libgeniex.so, nur bei `pullFlow`. Kein Server, keine Telemetrie-Strings gefunden (negative Suche). Update-Prüfung nur im CLI (RAW notes/run.md). Logs: logcat-Tag `GenieXSdk` (RAW bindings/android/README.md).

### Dokumentierte Aufrufsequenz für 0.10.0 (DOCS "Android Quickstart"/"API reference", Qualcomm-AI-Hub-Abschnitt)

```kotlin
GenieXSdk.getInstance().init(context)                      // idempotent; init$default existiert in 0.10.0 (javap)
ModelManagerWrapper.pullFlow(ModelPullInput(model_name="ai-hub-models/Qwen3-4B",
    hub=HubSource.AUTO, chipset="SM8750" /* oder "SM8750-AC" fuer das Galaxy-Asset */))
  .collect { ev -> when(ev){ is PullEvent.Progress -> ...; PullEvent.Completed -> ...; is PullEvent.Error -> ev.code, ev.message } }
val paths = ModelManagerWrapper.getPaths("ai-hub-models/Qwen3-4B") ?: error(...)
val llm = LlmWrapper.builder().llmCreateInput(LlmCreateInput(model_path=paths.model_path,
    tokenizer_path=paths.tokenizer_path, config=ModelConfig(), runtime_id="qairt", compute_unit=null /* NPU */))
    .build().getOrThrow()
val t = llm.applyChatTemplate(arrayOf(ChatMessage("user", text)), null /*tools*/, false /*enableThinking*/).getOrThrow()
llm.generateStreamFlow(t.formattedText, GenerationConfig(maxTokens = 256)).collect { r -> when(r){
    is LlmStreamResult.Token -> r.text; is LlmStreamResult.Completed -> r.profile (ttftMs, decodingSpeed, ...)
    is LlmStreamResult.Error -> r.throwable } }
llm.stopStream(); llm.destroy()  // bzw. close()
```
- Pflicht laut Doku: **den chat-templated Prompt** (`formattedText`) übergeben, nie Rohtext (sonst "degenerate output"). `maxTokens` Default ist 32. "Always pass templated.formattedText".
- **Thinking abschalten**: dritter Parameter von `applyChatTemplate(messages, tools, enableThinking, ...)`. 0.10.0 javap/Strings: Parameter `tools`, `enableThinking`, `addGenerationPrompt` (+ ein weiterer boolean, Name nicht ermittelt). In `ModelConfig` gibt es **kein** `enable_thinking` mehr (die Demo-main nutzt es noch; Demo-Stand ist also neuer/anders als 0.10.0). Python-Doku: `enable_thinking=False` "asks a thinking-capable model to skip its thinking turn" (DOCS Python API). Ob Qwen3-4B (das nicht-Instruct "base"/Hybrid-Modell) dadurch wirklich ohne Denkblock antwortet: nicht verifiziert; das Instruct-2507 ist laut Doku das einzige als "Supported model" für Android gelistete LLM (DOCS Android API reference, "Supported models": `ai-hub-models/Qwen3-4B-Instruct-2507`, VLM `Qwen2.5-VL-7B-Instruct`).
- **Falle `nCtx`**: Doku: bei qairt werden `nCtx != 0` und `nGpuLayers != 0` mit `PARAM_NOT_SUPPORTED` abgelehnt, "Leave both at defaults". Aber in 0.10.0 sind die Defaults `nCtx=2048`, `nGpuLayers=-1` (javap-Bytecode des ModelConfig-Konstruktors; Doku-Tabelle nennt ebenfalls 2048). Die Demo setzt für qairt ausdrücklich `ModelConfig(nCtx = 0, nGpuLayers = 0)` (DEMO MainActivity.kt) – das ist die sichere Variante; die Doku-Beispiele mit `ModelConfig()` sind widersprüchlich.
- Fehlertypen: `Result.failure` (build, applyChatTemplate), `LlmStreamResult.Error(throwable)`, `PullEvent.Error(code, message)`, `GenieXModelError(code)`. Zeiten: `ProfilingData` ttftMs, promptTimeMs, decodeTimeMs, prefillSpeed, decodingSpeed, promptTokens, generatedTokens, stopReason. Speicher: nicht im SDK (App misst selbst). Kontextlänge: im Bundle fest (4096 laut HF-Tabelle).
- Weitere Details: `ModelConfig.power_mode` (Default `burst`, RAW notes/run.md "Power mode"); `HubSource.LOCALFS` + `local_path` importiert ein entpacktes AI-Hub-Verzeichnis (metadata.json + *.bin) oder die AI-Hub-.zip ohne Netz und **ohne chipset** (DOCS Models, "Run a local ... bundle → Android"); Modelle liegen im SDK-Cache (Rust-Manager, Demo-Kommentar: `filesDir/geniex/models/{org}/{repo}`), die Doku verbietet `adb push` in Zielordner ("use ModelManagerWrapper").
- Ein Pull braucht laut Doku **kein Login**; HF-Token nur für gated HF-Modelle (DOCS Models "Set up a Hugging Face token"). Für AI-Hub-Bundles liegt der Download auf öffentlichem S3 (HEAD ohne Auth: 200).

## 3. Modell Qwen3-4B (HF-API/Karte, S3)

- HF-Repo `qualcomm/Qwen3-4B`: **nicht gated** (`gated:false`), enthält nur .gitattributes, LICENSE (114 Byte, verweist auf Qwen3-4B-Lizenz), README.md, release_assets.json (insgesamt 20 KB, **keine Gewichte im HF-Repo**). Lizenz laut Karte/Tags: apache-2.0.
- Gewichte: Zip auf S3 `.../qai-hub-models/models/qwen3_4b/releases/v0.67.0/qwen3_4b-geniex_qairt-w4a16-qualcomm_snapdragon_8_elite_for_galaxy.zip`, **2 666 196 166 Bytes** Zip (HEAD, Content-Length), entpackt ca. **3,17 GB**: part1_of_4.bin 778 MB, part2 660 MB, part3 660 MB, part4 1,06 GB, tokenizer.json 11 MB, vocab.json, merges.txt, genie_config.json, htp_backend_ext_config.json, metadata.json (58 KB), config.json, sample_prompt.txt (Range-Read des Zip-Inhaltsverzeichnisses). Es gibt zusätzlich eine `genie`-Variante gleicher Größe (2 666 195 401 Byte) für die ältere Genie-Runtime ("Genie support will be deprecated soon", HF README).
- Quantisierung w4a16, Kontext 4096 (HF-Performance-Tabelle), QAIRT-Version des Bundles **2.51.0.260929181845** (release_assets.json `tool_versions`). Prüfsummen: die HF-API liefert keine sha256 (keine LFS-Objekte); S3-ETag nur Multipart-MD5 (`782fbe12…-318`). Ob GenieX beim Pull eine `digest` prüft: Feldname in libgeniex.so, Verhalten nicht verifiziert.
- Runtime vs. Bundle: Bundle 2.51 > gebündelte Runtime 2.50.40. Doku: Plugin lädt QNN über C-Interface mit Untergrenze C-API 2.27, "older and newer QAIRT runtimes load without ABI breaks", aber wörtlich: "A runtime the plugin accepts can still produce **wrong output at full speed**, so confirm the override resolved where you meant." (RAW notes/run.md, Abschnitt "Using a custom QNN library"; gesagt im Kontext der Override-Funktion). Eine Matrix "welche Bundle-Versionen akzeptiert Runtime 2.50": nicht dokumentiert. Zudem: Demo-Katalog/Docs rechnen mit AAR-Version und Bundle, die zusammen veröffentlicht wurden (Bundle-Stand 2026-09/10 vs. AAR 2026-10-28), also vermutlich getestete Kombination; nicht verifiziert.
- Sprachen: HF-Karte "Supported languages: 100+ languages and dialects", "multilingual"; Deutsch nicht einzeln genannt.
- **Qualcomm "Generative AI usage and limitations"**: Text unter https://qaihub-public-assets.s3.us-west-2.amazonaws.com/qai-hub-models/GenerativeAIUsageTerms.txt (gelesen). Kernsatz: "Model may not be used for or in connection with any of the following applications ('Unacceptable Risk Applications'): Unfair, manipulative or deceptive acts; Exploit vulnerabilities of age, disability or social or economic situation; Predictive policing; Social scoring; Emotion recognition in workplace and education; Real-time remote biometric identification in publicly accessible spaces for law enforcement; Biometric categorization system (sensitive characteristics); and/or Generating facial recognition databases by scraping images ..." Zusätzlich "High-Risk Applications" (u. a. biometrische Identifikation, kritische Infrastruktur, Bildung, Beschäftigung, wesentliche Dienste, Strafverfolgung, ...), von denen Qualcomm "advises against" (Text, gelesen). Die HF-Karte führt eine ähnliche Liste unter "Usage and Limitations" (Bildung, Beschäftigung, Strafverfolgung, u. a.).

## 4. Lizenzen/Weitergabe (nur Textlage)

- GenieX-Code: BSD-3-Clause, (c) 2024-2026 Qualcomm Technologies (RAW LICENSE). README: "Use of this project is also subject to Qualcomm's Terms of Use". POM: BSD-3 + Terms of Use, `distribution: repo`.
- Qualcomm Terms of Use (https://www.qualcomm.com/site/terms-of-use, Text aus `.model.json`): es ist eine Website/Plattform-Nutzungsbedingung. Zitate: "Your access to and use of the materials available on the Site ... may be subject to the terms and conditions of a corresponding agreement(s) in place between you and Qualcomm Technologies, Inc. ..." und "Except as otherwise provided in a written Agreement between you and Qualcomm, you may not modify, copy, reproduce, republish, upload, post, transmit, transfer, or distribute in any way any of the contents of this Site. ... You may, however, download content from this Site solely for your personal, non-commercial use ...". Ein ausdrücklicher Satz zur Weitergabe der QNN-Binaries in einer App steht dort nicht (nicht gefunden). Eine eigene LICENSE zu libQnn*/Skel im AAR fehlt; RAW NOTICE listet nur Go-Abhängigkeiten. Das Demo-README sagt, die SDK-Abhängigkeit "is released under its own license". Das Verbreiten einer APK mit gebündelten Libs ist damit aus den Texten nicht beantwortet.
- Modell: Apache-2.0 laut HF/AI-Hub; plus die Qualcomm-Nutzungsbeschränkungen oben.

## 5. Whisper

- GenieX unterstützt **keine ASR**: Audio-Eingabe nur über llama.cpp-VLMs mit Conformer-Encoder (z. B. gemma-4-E2B GGUF); "QAIRT bundles do not support audio" (DOCS Models, "Input modalities"; Local server: "QAIRT models report audio: false"). Das Whisper-Vorhaben läuft also nicht über GenieX.
- Veröffentlichte Whisper-Bundles (HF `release_assets.json`, v0.67.0): Whisper-Tiny/Base/Small/Medium/Large-V3-Turbo (float) sowie Small-Quantized und Large-V3-Turbo-Quantized (w8a16), jeweils mit Formaten `precompiled_qnn_onnx`, `qnn_context_binary`, `voice_ai`, Tool-Versionen QAIRT 2.51.0 + **onnx_runtime 1.30.0**. Für Snapdragon 8 Elite gibt es auch hier nur `qualcomm-snapdragon-8-elite-for-galaxy` (plus gen5-for-galaxy, gen6, extreme-gen6, 8gen1/8gen3), kein generisches `snapdragon-8-elite`. Laufzeit: ONNX Runtime mit QNN-Provider bzw. QNN direkt, also ein eigener Stack neben GenieX. (Whisper-Base-Quantized: API 401, nicht öffentlich.)

## 6. Leistungszahlen (primär: HF-Karte Qwen3-4B, aihub-Seite)

- HF-Tabelle "Response Rate (tokens per second)", w4a16, "Snapdragon 8 Elite For Galaxy Mobile": GENIE 26,13 tok/s bei Ctx 4096. GENIEX_QAIRT (Prompt-Kontext-Länge variiert): 20,85 (512), 20,24 (1024), 18,84 (2048), 18,67 (3072), 16,27 tok/s (4096). Gen 5 For Galaxy: 23,44 (512) bis 16,20 (4096). "Measured on a short prompt with a long response (with thinking); may slow down when using longer context lengths."
- Time to first token und Speicher: auf der HF-Karte und der AI-Hub-Seite **nicht** angegeben (aihub-Seite: "not supported on any All Models chipset", keine Metrik). Energie/Wärme: nichts gefunden. Speicher- und Thermalhinweise in der GenieX-Doku: für Android keine gefunden (grep nach memory/thermal in llms-full.txt; nur Linux-Board-OOM-Beispiel mit anderem Modell).
- Die früheren Schnipsel-Zahlen (26,8 tok/s, TTFT 0,06-1,9 s) stammten von einem Mirror/anderer Version und sind durch die obige Primärtabelle ersetzt.

## 7. Praktisches für den Blindbau

- Kompilieren gegen das AAR ist möglich (Maven Central, kein NDK). AAR nur arm64-v8a: auf x86_64-Emulator scheitert `System.loadLibrary("npu_jni")` (Folge aus Lib-Liste; nicht getestet). Doku: "There's no x86 or non-Snapdragon ARM build" (DOCS FAQ Chipsets & devices). Sauberer Fehler beim Init auf Nicht-Snapdragon: `InitCallback.onFailure(String)` ist vorgesehen; nicht getestet.
- Demo-Dateien: `build.gradle` (pinnt `geniex-android:0.3.5`, Kotlin 2.2.0, AGP 8.13.0), `src/main/AndroidManifest.xml`, `src/main/assets/model_list.json`, `src/main/java/com/geniex/demo/MyApplication.kt`, `MainActivity.kt` (Logik in einer Datei). Der Demo-Code passt nicht 1:1 zu 0.10.0 (`enable_thinking` in ModelConfig).
- Foreground-Service: Doku verlangt keinen; Demo lädt in Activity-Coroutine mit PARTIAL_WAKE_LOCK. Für einen gebundenen Service im Hintergrund: nicht verifiziert.
- Speicherbedarf zur Laufzeit: nicht dokumentiert; 3,17 GB Gewichte sind mmap-/Ladegröße-Anhalt, kein Beleg.

## 8. Urteil (aktualisiert)

1. Die API-Seite ist jetzt gut belegt: Sequenz init, pullFlow, getPaths, builder.build, applyChatTemplate (enableThinking=false), generateStreamFlow, stopStream, destroy; die App-Logik wäre ca. 150 Zeilen und blind schreibbar.
2. **Hauptrisiko hat sich verschoben**: nicht die API, sondern dass es für das Xiaomi 15 kein offiziell ausgewiesenes Bundle gibt (nur "for Galaxy", Qwen3-4B auf AI-Hub-Seite "not supported" für 8 Elite generisch). Ob das Galaxy-Bundle (SM8750-AC) auf SM8750 des Xiaomi läuft, ist offen und nur auf dem Gerät klärbar. Es gibt keinen Blindbau-Weg, das vorab zu entscheiden.
3. Wahrscheinlichste Ausfälle: (a) `pullFlow` mit chipset "SM8750" findet kein Asset oder lädt eine unpassende Variante; mit "SM8750-AC" bzw. direktem S3-Download plus `LOCALFS` (kein chipset nötig) wäre der Umweg möglich; (b) Bundle-QAIRT 2.51 gegen Runtime 2.50.40 oder Galaxy-Bundle auf Nicht-Galaxy-SoC: Laden scheitert oder liefert Müll ("wrong output at full speed"); (c) `nCtx`-Default 2048 statt 0 → `PARAM_NOT_SUPPORTED`; Thinking-Ausgabe (`<think>`) bei Qwen3-4B trotz `enableThinking=false`.
4. Download: ein einzelnes 2,67-GB-Zip (HTTPS, ohne Login, öffentlich), entpackt 3,17 GB; Prüfsumme nur über SDK-Intern. Für "Download erst auf Tipp" passt `pullFlow` (resumable laut Doku); WLAN, mind. 6-7 GB frei (Zip plus entpackt, Annahme).
5. Rechtliches (Textlage): Weitergabe einer APK mit Qualcomm-Binaries nicht beantwortet; Nutzungsverbote des Modells (Gen-AI-Usage-Terms) berühren eine Satzkorrektur-Tastatur nicht erkennbar, die Entscheidung bleibt beim Owner.
6. Whisper: eigenes Projekt (ONNX Runtime + QNN oder QNN-Context-Binary, ebenfalls nur "for-galaxy" für 8 Elite). Nicht mit GenieX.
7. Empfehlung: kleiner Spike mit Fehleranzeige (Init, Chipset-Auswahl SM8750 vs SM8750-AC, Pull/LOCALFS, Laden, ein Satz) und logcat `GenieXSdk`; erst danach Service-Anbindung an die Tastatur. Minimum für den Owner: APK sideloaden, WLAN, ~7 GB frei, Akku-Optimierung aus, ggf. Logcat/Screenshot der Fehlermeldung.
