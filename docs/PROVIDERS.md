# Provider- und Laufzeitverträge

Dokumentationsprüfung: 2026-11-09. Keine echten Modellanfragen im Buildsystem.
Schlüssel werden ausschließlich vom Nutzer in der App eingerichtet.

## Transport

Nur feste HTTPS-Ursprünge, keine Weiterleitungen oder automatischen POST-Retries.
OkHttp 5.3.2 wird ausdrücklich als Android-AAR eingebunden. RequestBody ist
one-shot, weil die Retry-Einstellung allein 503 + Retry-After:0 nicht verhindert.
Der AndroidX-Startup-HTTP-Initializer ist entfernt; Initialisierung erfolgt erst
bei einer ausdrücklich gestarteten Cloud-Aktion.
Bewusst freigegebener Auswahltext ist der gesamte Editor-Kontext. Keine Kontakte,
Zwischenablage, Tippverläufe oder Umgebungstexte werden ergänzt. Anfragen laufen
außerhalb des Hauptthreads, mit Abbruch, Zeit- und Antwortgrößenlimit. Fehlertexte
stammen aus der App; fremde Fehlerkörper werden weder angezeigt noch geloggt.

## OpenAI

Voreinstellung für kurze Textaufgaben: `gpt-6-luna`, frei änderbar. Modellkennung
am 2026-11-09 im offiziellen Modellkatalog geprüft:
https://developers.openai.com/api/docs/models

`POST https://api.openai.com/v1/responses`, Bearer-Schlüssel.
`model`, `instructions`, `input` (user), `max_output_tokens`, `store:false`,
`stream:false`. `store:false` betrifft Antwortobjekte und ist keine Zusicherung
über sämtliche serverseitigen Aufbewahrungsvorgänge.
Nur abgeschlossene Antworten akzeptieren. Alle Assistant-Message-Blöcke und deren
`output_text`-Inhalte durchlaufen; Refusal/Incomplete sind kein Ersetzungsergebnis.

- https://developers.openai.com/api/docs/guides/text
- https://developers.openai.com/api/reference/resources/responses/methods/create
- https://developers.openai.com/api/docs/guides/error-codes

## Anthropic

Am 2026-11-11 erneut gegen die offizielle Referenz geprüft (Messages „create“ und „API versions“):
Bearer-Schlüssel, `anthropic-version: 2026-10-21`, `temperature`/`top_p`/`top_k` entfernt,
`stop_reason` ∈ end_turn, max_tokens, stop_sequence, tool_use, pause_turn, refusal,
model_context_window_exceeded. Implementiert in `AnthropicProvider` (Etappe 16a). Voreinstellung
`claude-haiku-5-5` (im Modellkatalog der Referenz als schnellstes Modell geführt), frei änderbar.
`max_tokens` 8192: Bei Modellen mit standardmäßig aktivem Thinking zählen die Denk-Token mit; ein
abgeschnittenes Ergebnis (`stop_reason:max_tokens`) wird verworfen und als „zu lang“ gemeldet.
Keine echte Anfrage gesendet.

`POST https://api.anthropic.com/v1/messages`, Bearer-Schlüssel,
`anthropic-version: 2026-10-21`. `model`, `max_tokens`, `system`, `messages`,
`stream:false`; keine `temperature`/`top_p`/`top_k` in dieser API-Version.
Nur Textblöcke mit `stop_reason:end_turn`; Thinking und Tool-Blöcke sind kein Text.

- https://platform.claude.com/docs/en/api/messages/create
- https://platform.claude.com/docs/en/api/versioning
- https://platform.claude.com/docs/en/api/migration-2026-10-21
- https://platform.claude.com/docs/en/api/errors

## Gemini

Implementiert in `GeminiProvider` (Etappe 16b) nach dem unten stehenden Vertrag vom 2026-11-09.
Am 2026-11-11 NICHT erneut prüfbar: die Doku-Hosts waren aus der Entwicklungsumgebung nicht
erreichbar. Deshalb gibt es keine voreingestellte Modellkennung; der Nutzer trägt sie ein. Vor der
ersten echten Nutzung Vertrag und Modellnamen prüfen. Keine echte Anfrage gesendet.

`POST https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent`.
Schlüssel im `x-goog-api-key`-Header, niemals in der URL. Modellkennung strikt
validieren. `systemInstruction`, `contents`, `generationConfig` mit einem Kandidaten.
Nur `finishReason:STOP` und Text ohne `thought:true` akzeptieren. Sicherheitssperren,
fehlende Kandidaten und abgeschnittene Antworten sind Fehler. generateContent ist
weiter unterstützt; der Adapter kapselt die als Nachfolger empfohlene Interactions-API.

- https://aistudio.google.com/api-reference
- https://aistudio.google.com/api-reference/generate-content/generate-content

## Sprache und lokale Modelle

Ab Android 12 ausschließlich expliziten On-device-SpeechRecognizer benutzen,
Verfügbarkeit prüfen, Mikrofonrecht in der Einstellungs-Activity anfordern.
Kein stiller Rückfall auf einen möglicherweise cloudbasierten Erkenner.
Ergebnisvorschau und bewusste Übernahme bleiben erforderlich.

- https://developer.android.com/reference/android/speech/SpeechRecognizer

Umgesetzt in Etappe 18a2 (`OnDeviceSpeechEngine`). Am 2026-11-11 gegen die Referenz geprüft:
`createOnDeviceSpeechRecognizer` und `isOnDeviceRecognitionAvailable` gibt es ab API 31; die
Erzeugung wirft `UnsupportedOperationException`, wenn keine On-device-Erkennung verfügbar ist;
alle Aufrufe gehören auf den Main-Thread; `RECORD_AUDIO` ist nötig; Fehlercodes 12
(`ERROR_LANGUAGE_NOT_SUPPORTED`) und 13 (`ERROR_LANGUAGE_UNAVAILABLE`). Die Referenz nennt den
Erkenner „on-device“, sichert aber nicht wörtlich zu, dass der Systemdienst nie das Netz nutzt;
die App-Texte behaupten deshalb nur, dass die Tastatur ausschließlich diesen Erkenner verwendet.
Der allgemeine Erkenner ist dort als „likely to stream audio to remote servers“ beschrieben und
wird nie erzeugt.

Lokale Inferenz benötigt eine echte, gepinnte Laufzeit und ein separat importiertes
kompatibles Modell, mit begrenztem Speicher, Kontext und Ausgabetokens. Keine
automatischen Downloads. Mögliche Laufzeiten: llama.cpp JNI (.gguf) oder LiteRT-LM
(.litertlm). Keine Inferenzprüfung ohne tatsächliches Modell und passendes Gerät behaupten.

- https://github.com/ggml-org/llama.cpp/blob/master/docs/android.md
- https://github.com/ggml-org/llama.cpp/blob/master/docs/models.md
- https://developers.google.com/edge/litert-lm/android

## Act Builder — strukturierte Entwürfe (14a)

Vertrag geprüft am2026-11-09, Implementierung2026-11-11:
https://developers.openai.com/api/docs/guides/structured-outputs

Responses nutzt `text.format` mit `type:json_schema`, festem Namen, `strict:true`
und app-eigenem Schema. Alle Objektfelder sind erforderlich; unbekannte Felder
sind ausgeschlossen. Optionale Bedingungen werden als null/Objekt-Union dargestellt,
Schritte als geschlossene Local-/AI-Varianten. Kein frei übergebbares Schema.

Builder-Anfragen erhalten4096 statt2048 Ausgabetokens; Antwortgrenze16384 UTF-16-
Einheiten und Transportgrenzen bleiben bestehen. Die App validiert zusätzlich
Aktionen, Bedingungen, Unicode, Referenzen und8/3-Schrittgrenzen. Das Modell schlägt
nur name/steps vor; Provider und Modell der Schritte stammen aus der Freigabe,
UUID/Revision1 aus der App. Entwürfe werden weder gespeichert noch ausgeführt.
Modelle ohne Structured-Output-Unterstützung können den Auftrag ablehnen; kein
stiller Rückfall auf schwächer validierte Ausgabe. Live-API-Prüfung steht aus.
