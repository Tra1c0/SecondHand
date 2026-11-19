# Runde 3: Bestandsaufnahme (nur lesend, kein Code geändert)

Kennzeichnung: [G] = im Code/Docs gelesen, [V] = Vermutung oder Framework-Wissen, nicht im Repo geprüft.
Pfade kurz: K = app/src/main/java/helium314/keyboard, A = app/src/main/assets/layouts, T = app/src/test/java/helium314/keyboard.

## Schlüsselbefund für 1 und 2
Die Gboard-Optik gibt es schon, aber nur hinter der Vorlage „Flacher Look“ (FlatPreset, Etappe 23a/23b). [G]
Die Beschreibung des Owners (5 Reihen, Vierpfeil-Taste, Pfeil + scrollbare Leiste) passt exakt zu den
Upstream-Standardlayouts, also ist die Vorlage bei ihm nicht angewendet (oder Stil ≠ Flat). [G: Layouts] [V: seine Einstellung]
`FlatStyleRenderTest.shiftedKeyboardAndUrlFieldRender` (T/agent/FlatStyleRenderTest.kt:122-155) prüft die Gboard-Seiten
schon Zeichen für Zeichen, aber nur mit Stil Flat und gesetzten Layout-Einstellungen.
Billigste Sofortmaßnahme: Vorlage anwenden. Der Auftrag sagt aber „Standard soll Gboard sein“, also Entkopplung vom Stil (siehe 1, 2).

## 1. Symbolseiten wie Gboard

Wo definiert [G]:
- Standard: A/symbols/symbols.txt (Seite 1), A/more_symbols/symbols_shifted.txt (Seite 2). Gboard-Fassung: symbols_flat.txt,
  symbols_shifted_flat.txt. Zuordnung über Pref `layout_<TYPE>` (Settings.PREF_LAYOUT_PREFIX + LayoutType.SYMBOLS/MORE_SYMBOLS),
  Standardwerte `Defaults.kt:30-33` (`LayoutType.default`: "symbols", "symbols_shifted"), gelesen in `Settings.readDefaultLayoutName` (Settings.java:574)
  und `LayoutParser.parseLayout` (keyboard_parser/LayoutParser.kt:~37, Pro-Subtyp-Layout `subtype.layouts[type]` hat Vorrang).
- Boden-/Funktionstasten: A/functional/functional_keys.json (Standard) vs functional_keys_flat.json. Pref `layout_FUNCTIONAL`; FlatPreset setzt
  alle drei (FlatPreset.kt:50-51, FUNCTIONAL_LAYOUT Zeile 134-136).
- Der Owner-Look „Tastenanordnung“ (KeyboardProfileStore) ist KEIN Layout-Speicher: Gruppe LAYOUT enthält nur Zahlenreihe, Emoji-Taste,
  Sprachtaste (KeyboardProfileStore.kt:40-44). Profile überschreiben Symbollayouts also nicht. [G]

Ursache der Abweichungen (Standard-Dateien) [G]:
- Extra Reihe Seite 1: symbols.txt beginnt mit `% \ | = [ ] < > { }` (Reihe 1), danach `@ # $$$ _ & - + ( ) /`, `* " ' : ; ! ?`. Dazu kommt die
  Zahlenreihe aus `KeyboardParser.createRows` (Zeile 99-117) -> 5 Reihen. Diese erste Reihe liefert zugleich die Hinweisbuchstaben für die
  Buchstabentasten (`addSymbolPopupKeys`, KeyboardParser.kt:303-313). Entfernen der Reihe aus symbols.txt würde die Hinweise verschieben;
  die Flat-Variante umgeht das mit `LayoutParser.parseLayout(SYMBOLS, …, "symbols")` nur wenn Stil Flat UND Layoutname "symbols_flat" (Zeile 306-309).
- Seite 2 Standard: Zahlenreihe bleibt (Flat blendet sie aus: `flatMoreSymbols`, KeyboardParser.kt:114-115, hängt an `THEME_ID_FLAT`);
  `¶ §` (Hauptzeichen ¶, § nur im Popup) statt `§ ¶`; Reihe 2 hat nur 9 Tasten (`$$$1..4 ^ ° = { }`, `\` wandert nach Reihe 3);
  `% ℅` statt `✓ ✔ ✗` (kein ✓); `Δ ∆`: Hauptzeichen ist griechisch Δ (U+0394), Gboard nutzt vermutlich ∆ (U+2206) [V, trivial].
  Flat-Datei: `§ ¶`, `£ ¥ $ ¢ ^ ° = { } \`, `% © ® ™ ✓ [ ]` = Gboard-Soll. Test erwartet Δ (FlatStyleRenderTest:147).
- Vierpfeil-Taste = KeyCode.DPAD, in functional_keys.json nur auf SYMBOLS mit `dpadKeyEnabled` (Pref `show_dpad_key`, Default true,
  Defaults.kt:82, SettingsValues.java:230, KeyboardId.kt:34, KeyData.kt:225). Schalter: Tasten-Seite (AgentAreaKeysScreen.kt:228, PreferencesScreen.kt:163).
  Die 12/34-Taste (`numpad`) gibt es in der Standarddatei nur auf Seite 1 (Selector `symbols`), auf Seite 2 fehlt sie; `<`/`>` auf Seite 2 kommen aus der
  letzten Basisreihe (`adjustBottomFunctionalRowAndBaseKeys` ersetzt Komma/Punkt, KeyboardParser.kt:~222-240), Gboard-konform wäre `<`/`>` nur dort.
- Währungstaste `$$$` = Währung der Locale (€ im Deutschen). Flat-Seite 2 hat feste `£ ¥ $ ¢`. Passt zu Gboard-DE.
- Zahlenreihe aus + „Zahlenreihe in Symbolen“ an (Default, Defaults.kt:149): `addNumberRowOrPopupKeys` (KeyboardParser.kt:281-285) ERSETZT die erste
  Symbolreihe durch Ziffern. Mit symbols_flat ginge dann die `@ # € …`-Reihe verloren (nur noch Popups). Fallstrick beim Umstellen. [G]

Kleinste korrekte Änderung (Vorschlag):
1. Die Inhalte von symbols_flat/symbols_shifted_flat zu den Standarddateien machen (oder Standardnamen in Defaults.kt:30-33 auf die Flat-Namen setzen).
2. Theme-Bedingungen in KeyboardParser (Zeile 114 und 306) von `THEME_ID_FLAT` auf den Layoutnamen umstellen (Seite 2 ohne Zahlenreihe; Hinweisquelle "symbols").
   Seite 1 braucht dann die Hinweisquelle: eigene Datei oder die alte Reihe 1 nur dafür behalten.
3. Funktionstasten: Standard-functional_keys.json um Seite-2-Selector (`moreSymbols`: `<`/`>`, numpad) erweitern, Dpad-Taste im Standard ausblenden
   (Default `show_dpad_key` false, ohne Migration für Setzende; Default ändert nur ungesetzte). Emoji/numpad `"type":"character"` bleibt Stilsache der Flat-Datei.
4. Sonderfall Zahlenreihe aus: `numberRowInSymbols` muss für die neuen Dateien die Reihe NICHT ersetzen (Gboard hat die Ziffern immer).
Datenmigration [G/V]: Ungesetzte Prefs folgen dem neuen Standard automatisch. Nur gesetzte Werte bleiben: FlatPreset-Nutzer haben "symbols_flat" schon; eigene
Layoutkopien (`custom.*`, LayoutUtilsCustom) oder Pro-Sprache-Layouts (Subtyp-Extra `layouts`) bleiben unberührt. Ob der Owner welche hat, ist im Repo nicht
sichtbar [V]. Keine Migration nötig, außer er hat Standardnamen "symbols" explizit gespeichert (AppUpgrade.kt:306-310 schreibt `MORE_SYMBOLS` bei alten Installationen) [V].
Tests: FlatStyleRenderTest (Gboard-Seiten), KeyboardParserTest (Zeile ~516 SYMBOL_ALPHA-Popup), SubtypeTest (symbols_arabic), FieldCorrectionMemoryTest und
TouchZonesDynamicTest (bauen SYMBOLS nur als Kulisse), SymbolInkTest. Ein neuer Test: Seiten für Theme Material/Rounded + Zahlenreihe an/aus.

## 2. Leiste wie Gboard

Aufbau heute [G]:
- Layout R.layout.suggestions_strip (res/layout/suggestions_strip.xml): LinearLayout `suggestions_strip_wrapper` = [ImageButton `suggestions_strip_toolbar_key`
  (Pfeil/Raster) | HorizontalScrollView `toolbar_container` > LinearLayout `toolbar` (gone) | `suggestions_strip` (Gewicht 1) | `pinned_keys`].
- SuggestionStripView.kt (613 Zeilen): Erzeugt Tasten aus Prefs (Zeile ~170-195), `setToolbarVisibility` (259-277) schaltet Toolbar vs. Vorschläge,
  Klick auf Pfeil (380-395); Langdruck auf Toolbar-Taste heftet an (`addKeyToPinnedKeys`, 582), Wischen nach unten blendet aus.
- ToolbarKey-Enum (latin/utils/ToolbarUtils.kt:~120): 38 Werte, u.a. VOICE, CLIPBOARD, UNDO … EMOJI, Cursortasten, AGENT, SKILL_1-3, DICTATE.
  Prefs `toolbar_keys` (Reihenfolge+an/aus, Format `NAME,true;...`), `pinned_toolbar_keys`, `clipboard_toolbar_keys`; Codes in `getCodeForToolbarKey`.
- Modi `ToolbarMode` (EXPANDABLE Default/TOOLBAR_KEYS/SUGGESTION_STRIP/HIDDEN); `auto_show_toolbar` Default false, `auto_hide_toolbar` Default false (Defaults.kt:175-182).
  Auto-Zeigen nur bei Auswahl, Zeilenanfang oder nach Enter (LatinIME.setNeutralSuggestionStrip, ~1850); Auto-Ausblenden bei Wortvorschlägen (LatinIME:1745).
- Einstellungen zum Sortieren: ToolbarScreen.kt (ReorderSwitchPreference/ReorderDialog mit Drag&Drop), Fork-Seite „Leiste & Befehle“ (agent_bar_keys
  „Knöpfe wählen und sortieren“, AgentAreaBarScreen-Familie).
- Flat-Zweig (FlatStrip.kt, 215 Zeilen) macht schon: linke Taste = Raster -> FlatMenuView (6 Pillen, Design/Undo/Einhand/Emoji/Inkognito/Floating,
  agent/FlatMenuView.kt:49-54), Toolbar-Tasten über Space-Gewichte gleichmäßig verteilt (`arrange`, Zeile 40-85), letzte angeheftete Taste im Kreis (Mikro/Diktat),
  externe Vorschläge verdrängen die Toolbar immer (SuggestionStripView.kt:~325). FlatPreset: TOOLBAR_KEYS = CLIPBOARD, EMOJI, AGENT; PINNED = DICTATE;
  Auto-Zeigen und Auto-Ausblenden an (FlatPreset.kt:42-48, 144-145). Das ist die Gboard-Leiste mit 3 Slots.
- Wer die Leiste belegt (Priorität, [G] LatinIME.java): setSuggestedWords (1725-1750): Befehls-/Format-/Feld-/Gedächtnis-Chips (KeyboardCommands.createChipsView,
  :694-698) > Symbol-Chips (SymbolDrawing) > Screenshot-Chip > Wortvorschläge. setNeutralSuggestionStrip (~1825): Chips > Symbol-Chips > Zwischenablage-Pille
  (ClipboardHistoryManager, SmartPaste-Chips) > Update-Chip > Interpunktion/leer, danach Toolbar-Auto-Zeigen. Inline-Autofill: `showInlineAutofill` (LatinIME:1511-1530)
  mit Schließen-Kreuz. Aktionspanel/Rad sind Overlays (AgentKeyboardFrame), konkurrieren nicht um die Leiste; ihre Einstiegstasten sind ToolbarKeys (AGENT, SKILL_*, DICTATE).
- Tests: FlatStyleRenderTest.stripShowsFiveEvenlySpacedButtons… (Slots auf 2,5 px), FlatMenuTest, FlatStyleTest, FlatClipboardTest, RgbLightingRenderTest,
  AgentPanelViewTest, DictationUiTest, SettingsBarAreaTest, KeyboardParserTest (ToolbarKey). devicetest: STRIP_FIXED filtert „Mehr Tasten“/„Textaktionen“ (devicetest.py:66).

Lücken zu Gboard [G]: (a) Spreizung/Raster nur im Flat-Stil (`flatStyle`-Guards überall in SuggestionStripView); (b) Scrollfallback bei zu vielen Tasten (`isFillViewport`,
Spacer kollabieren, FlatStrip.kt:62-72); (c) Raster-Taste öffnet 6 Pillen, nicht „alle Werkzeuge“; (d) kein Ziehen im Tastaturfenster; (e) mit Vorschlägen kein Rückweg-Pfeil zu den Tools
(im Flat-Stil: nur über das Menü); (f) Auto-Zeigen nur am Anfang/Enter/Auswahl, Gboard zeigt Tools bei leerer Vorschlagsleiste auch nach Leerzeichen.

Kleinste Architektur (Vorschlag):
- Stufe a (S): `flatStyle`-Verhalten von der Theme-Prüfung lösen (Pref/Layout-Parameter „feste Slots“), Max. 8 Slots erzwingen (ReorderDialog begrenzt „an“ auf 8, Scrollfallback entfällt), Preset-Werte
  (auto show/hide) als Standard. Reicht für „kein Scrollen, kein Pfeil“.
- Stufe b (M): FlatMenuView/neues `ToolGridView` zeigt ALLE ToolbarKeys als Raster (Icon + Name, bereits `createToolbarKey`, `getCodeForToolbarKey`), Tippen führt aus, markiert „in Leiste“. Kein neues Datenformat.
- Stufe c (M-L): Halten + Ziehen im Raster zwischen „Leiste“ (Slotzeile oben) und „Alle“; schreibt `toolbar_keys`/`pinned_toolbar_keys` über bestehende Helfer
  (`addPinnedKey`/`removePinnedKey`-Muster, Prefstring `NAME,true`). Billigere Zwischenstufe: Button „Leiste anpassen“ im Raster öffnet die vorhandene Reorder-Seite (S).
- Vorschläge vs. Tools: bestehender Mechanismus genügt (auto_show/auto_hide). Optional kleiner „<“-Pfeil links bei Vorschlägen = `toolbarExpandKey` im Nicht-Flat-Modus, schon vorhanden; im Flat-Stil müsste
  er neben dem Raster als zweites Element ergänzt werden (S-M, nur SuggestionStripView/FlatStrip).

## 3. Wischen auf Symbolen gibt anderes Symbol

Mechanismus (wahrscheinlichster) [G]:
- „Tastenwahl durch Ziehen“: `config_key_selection_by_dragging_finger` = true (res/values/config-per-form-factor.xml:12, themes-common.xml:56; false erst ab sw600dp). PointerTracker
  `mIsAllowedDraggingFinger = sParams.mKeySelectionByDraggingFinger || modifier || …` (PointerTracker.java:836). Rutscht der Finger auf eine Nachbartaste, wird die
  alte nicht getippt (`dragFingerFromOldKeyToNewKey` 1020-1060, `callListenerOnRelease(..., withSliding=true)`) und die Taste unter dem Finger beim Loslassen getippt (`onUpEventInternal` -> `detectAndSendKey`, 1285).
  Gestenerkennung (Wischtippen) läuft nur auf Alphabet-Seiten (`mIsDetectingGesture`, Zeile 704), auf Symbolseiten also nie; Symbole-Malen (SymbolInk) startet nur aus der Gestenerkennung, also ebenfalls nicht.
- Langdruck-Popups: Symbole mit Popup (Seite 1: `%`/`‰` unter `_`, `-`: – ⁻ — ·, `+`: ± ⁺, `(`/`)` Klammervarianten, `*`: † ‡ ★, `'`/`"` Gänsefüßchen aus de.txt; Seite 2: `% ℅` ...).
  Tabelle kommt aus A/symbols/*.txt (zweite bis n-te Spalte je Zeile) plus A/locale_key_texts/<sprache>.txt [popup_keys]. Popup-Auswahl durch Gleiten gilt auch beim Langdruck (`mIsInSlidingKeyInput`).
- Dass `/` -> `%` rauskommt, ist im Code nicht ableitbar: `/` hat in keiner Datei ein Popup, `%` ist Popup von `_` (Seite 1 Standard). Entweder Ziehen zur Nachbartaste oder `/` und `_` verwechselt. [V]
  Die Fork-Gestenprofile (SPACE/DELETE) und TouchModel (nur Buchstaben, TouchModel.kt CODES=0x250) greifen auf Symbolseiten nicht. [G]
Vernünftige Regel [V]: Auf SYMBOLS/SYMBOLS_SHIFTED/NUMBER-Seiten kein Ziehen auf Nachbartasten (nur Modifikatoren wie Shift/?123 behalten es), d.h. in `onDownEventInternal` Zeile 836
`&& mKeyboard.mId.getElement().isAlphabet()`; Popups nur per Langdruck wie Gboard (sichtbare Paare: `-`: – —, `(`: ⟨…). Das ist eine Ein-Zeilen-Änderung in PointerTracker.java (sensibel, Prüfer!).
Alternativ nur ohne PointerTracker-Änderung: Pref `keySelectionByDraggingFinger` über Theme-Attribut per Stil (themes-flat.xml) abschalten (kein Java), wirkt aber für Buchstaben mit.
Test: PointerTracker-Test mit Down auf `/`, Move auf Nachbartaste, Up -> erwartet `/` oder nichts. Dafür gibt es bisher keine Robolectric-Harness (nur AgentSpaceGestureTest/SymbolInkTest,
die PointerTracker über Events fahren; dort Muster nachlesen).

## 4. Latenz auf Symboltasten

Pfad [G]: Down -> `onDownEventInternal` (830): `onPressKey` (Haptik/Ton, `SymbolDrawing.beforeOtherInput`, `invalidateAgentEditor`), Long-Press-Timer, Key-Preview. Tippen geschieht erst bei
UP (`detectAndSendKey`, 1500-1510) wie bei Buchstaben. Keine Timer vor dem Commit außer: Long-Press (kein Einfluss auf Tap), Space-Doppeltipp (nur Space), `sTypingTimeRecorder` (nur Gesten).
Kein „Auto-Zurück zu Buchstaben“ nach Symbol im Code gefunden. `TouchModel.adjust` nur Buchstaben.
Wahrscheinlichste Ursache [V, Belege G]: Symbole sind Trennzeichen. `InputLogic.handleSeparatorEvent` (InputLogic.java:1388-1450) läuft synchron auf dem UI-Thread:
(1) `commitCurrentAutoCorrection` (2678-2720) holt bei anstehender Aktualisierung die Vorschläge synchron (`performUpdateSuggestionStripSync`, Zeile 2681-2691), dann `commitChosenWord`,
plus Fork-Hooks (`mTypedWordFirst`, `mFieldCorrections`, Gedächtnis WORD_COMMIT); (2) danach `setNeutralSuggestionStrip` sofort (InputLogic ~1448 / LatinIME ~1820): Chips-Quellen `tryShowCommandChips`
(Text vor Cursor lesen, KeyboardCommands.createChipsView mit FORMAT/FIELD/MEMORY-Chips), Zwischenablage-Pille, Update-Chip. Buchstaben lösen das nicht aus (nur verzögert, 100 ms,
`config_delay_in_milliseconds_to_update_suggestions`, res/values/config-common.xml:73). Zusätzlich `"` ruft `isInsideDoubleQuoteOrAfterDigit` (IPC an die App, 1420).
Zweite Kandidatin: Wechsel auf die Symbolseite selbst (Tastaturaufbau, `KeyboardSwitcher`), nur beim ersten Mal nach Konfigurationsänderung ohne Cache [V].
Messen:
- Robolectric (T/latin/InputLogicTest.kt-Muster): Wort tippen, dann `@`/`,`/`1` per `latinIME.onEvent`; mit `TypingPerf` zählen: Anfragen an die App (`TypingPerf.app()` in
  RichInputConnection:289,549,665,734,896,1292) und Fork-Hook-Zeiten (`TypingPerf.snapshot()`, Hook STRIP_CHIPS/WORD_COMMIT) pro Taste; Vergleich Buchstabe vs. Symbol. Zeit nur mit Fake-Uhr (TypingPerfTest), nicht real.
- Gerät (devicetest.py-Szenario): Test-App-TextWatcher schreibt `uptimeMillis` beim Textwechsel (nur Zeitstempel, keine Inhalte); Fingerereignis-Zeit aus `adb shell getevent -t`;
  Differenz Up-Ereignis -> Text sichtbar. Alternativ Diagnose-Karte „Tippen“ (Median/p95/Max, Diagnose.kt:578-600) nach 50 Symboltipps ablesen; neuen Hook `SEPARATOR_COMMIT` um `handleSeparatorEvent` legen (Zahlen, nie Text; TypingPerf-Regeln). Zur Abgrenzung dieselbe Messung mit Chips/Autokorrektur aus.

## 5. Cursorsteuerung

Heute [G]:
- Leertasten-Wisch: PointerTracker.onKeySwipe (1068-1133). `sPointerStep` = fest 10 dp (Zeile 108). Horizontal: `stepsX = dX / sPointerStep` -> `onSpaceSwipe(gesture, steps)` ->
  `KeyboardActionListenerImpl.onMoveCursorHorizontally` (KeyboardActionListenerImpl.kt:~499-552): Grapheme-genau per `moveStepsToCharCount` (StringUtils.kt:299), `connection.setSelection(new,new)` (RichInputConnection:925-945),
  Fallback ARROW_LEFT/RIGHT-Tasten, `inputLogic.finishInput()`/`restartSuggestionsOnWordTouchedByCursor`, Haptik GESTURE_MOVE je Aufruf. Keine Beschleunigung, linear 1 Zeichen je 10 dp.
  Vertikal: `onMoveCursorVertically` (476-488) sendet ARROW_UP/DOWN je 10 dp (DPAD-Keyevent, die App entscheidet).
- Prefs: `horizontal_space_swipe` (Default MOVE_CURSOR), `vertical_space_swipe` (Default NONE), Fork: `agent_space_swipe_left/right/up/down/hold/double_tap` (SpaceGestures.kt, Auflösung Pro-Geste vor altem Pref).
  Vertikal-Auswahl enthält TOUCHPAD_MODE (SpaceGestures.choices: nur vertikal). Einstellungen: AgentSpaceScreen.kt:39-40, AgentAreaGesturesScreen.kt:334-338.
- Es gibt schon einen 2D-Touchpad-Modus (Upstream HeliBoard): TouchpadHandler.java (keyboard/): aktiviert nach >=8 Schritten vertikalem Wisch (KeyboardActionListenerImpl.onSpaceSwipe, `requiredSteps = 8`)
  plus Long-Press-Debounce, Tastaturansicht Alpha 0,5, Beschleunigung `1 + |d|/50`, Schwelle 70-0,6*Empfindlichkeit px (`touchpad_sensitivity` Default 50 -> 40 px), Randscrollen (`touchpad_edge_scroll`, Rand 10 %).
  Bewegt nur über ARROW_*-Codes (also DPAD-Events; nie `setSelection`), entweder X oder Y je Schritt (kein echter 2D-Diagonalzug). Im Owner-Profil ist er nicht an die horizontale Geste gebunden (Auswahl `horizontal` enthält TOUCHPAD_MODE nicht).
- Löschwisch: PointerTracker.onKeySwipe Zweig DELETE (1121-1131), `onMoveDeletePointer(steps)` (KeyboardActionListenerImpl:~392-401) verschiebt den Selektionsanfang per `setSelection(start,end)`, beim Loslassen `onUpWithDeletePointerActive`
  löscht die Auswahl (Pref `delete_swipe`, Default true). 10 dp je Zeichen, linear, kein Wortsprung.
- Fork-Gesten Leertaste: `SpaceDetents` (Einmal-Aktionen mit Rastpunkten, Haptik `HapticChain.Feel.DETENT/STRETCH`), Tests SpaceDetentsTest, SpaceGesturesTest, AgentSpaceGestureTest, InputLogicTest:1150, 1289-1299, 3104.
- CursorAnchorInfo: Die Tastatur ruft beim Feldstart `requestCursorUpdates(MONITOR|IMMEDIATE)` (InputLogic.java:359, RichInputConnection:1371) und nutzt nur `selectionStart/End` in
  `LatinIME.onUpdateCursorAnchorInfo` (LatinIME.java:881). Pixel/Zeilen-Daten nutzt sie nirgends.

Was Android erlaubt [V, Framework-Wissen]:
- `InputConnection.setSelection(start,end)`: zeichengenau per Index, der Editor zeichnet/animiert den Cursor selbst. Zeilenwechsel nur über DPAD_UP/DOWN-Keyevents (`sendDownUpKeyEvents`, App-Layout entscheidet) oder
  Ctrl+Pfeil-Meta (Wortsprung, wie WORD_LEFT/RIGHT in InputLogic.java:~1056-1068). Der IME kennt das Textlayout der App nicht, kann keinen Pixel auf einen Index abbilden.
- `requestCursorUpdates` / `CursorAnchorInfo` (ab API 21): Einfügemarke (horizontal/top/baseline/bottom), Matrix, Komposit-Text, Zeichenboxen nur fürs Kompositionsgebiet. Ab API 33 mit Filtern
  (EDITOR_BOUNDS, CHARACTER_BOUNDS, INSERTION_MARKER) und ab API 34 `VISIBLE_LINE_BOUNDS` (sichtbare Zeilenränder). Gilt nur in Apps, die es liefern (TextView/EditText ja; WebView, Compose, Flutter, Spiele oft nicht oder spät). minSdk der App ist 21 (build.gradle.kts:28): API-Zweige nötig.
- Kein „Cursor-Handle“, kein Zugriff aufs Layout, die App entscheidet über Scrollen und Animation. „Flüssig“ heißt ehrlicherweise: schnelle, gleichmäßige Indexsprünge bei hoher Eingabefrequenz mit Haptik; die App malt den Cursor.
Bestmögliches Design (Vorschlag):
- Horizontal: Weg pro Pixel nichtlinear (dx^1,3 oder Stufen: langsam = 1 Zeichen je ~8 dp, schnell bis ~1 Wort je Ruck), alles aus `moveStepsToCharCount`.
- „Magnetismus“ = Hysterese an Wortgrenzen: Nähert sich der Zielindex einer Wortgrenze (Leerzeichen/Satzzeichen), wird der Index ±1 Zeichen „klebrig“ gehalten, bis der Finger eine Schwelle (z.B. 6 dp) überschreitet; ein leichter Tick (`HapticChain.Feel.DETENT`) an der Grenze. Reine Rechnung über Text +-20 Zeichen -> als kleiner Kern in `agent-core` testbar (ohne Android).
- Vertikal: erstes Ziel DPAD_UP/DOWN je Zeilenschritt (so schon da; Schrittgröße aus Zeilenhöhe schätzen wenn CursorAnchorInfo `bottom-top` vorliegt, sonst feste 24 dp). Sauberer ab API 34: aus `VISIBLE_LINE_BOUNDS` Ziel-Index schätzen [V, unsicher].
- 2D-Zug: pro Frame dominierende Achse mit Totzone; beide Achsen gleichzeitig nur wenn Diagonale klar. Auto-Scroll am Rand = vorhandene Randlogik des TouchpadHandlers (Beschleunigung 0,95 pro Tick) weiterverwenden.
- Löschwisch: gleiche Kurve und Magnetismus; Selektion wortweise über Hysterese, Haptik an Wortgrenzen; Loslassen löscht wie heute (bleibt Klick der Nutzerin, Regel „Editortext nie automatisch ersetzen“ gilt hier nicht, ist Löschen durch Geste).
Dateien: neu `agent/CursorTrack.kt` (reine Kurve/Hysterese, testbar), Aufruf in `KeyboardActionListenerImpl.onMoveCursorHorizontally/onMoveDeletePointer` (Kotlin, mittleres Risiko); TouchpadHandler.java nur Kurve auswechseln;
PointerTracker.onKeySwipe: nur `sPointerStep` als Wert/Kurve (Zeile 1092-1130) -> eng, Prüfer Pflicht (CLAUDE.md). InputLogic bleibt unberührt.

## 6. Alles auswählen per Langdruck auf Leertaste

Ablauf [G]: Pref `agent_space_hold` = SELECT_ALL -> PointerTracker.onLongPressed (1318-1330) -> `sListener.onCustomRequest(SPACE_HOLD)` -> `runSpaceAction` (KeyboardActionListenerImpl:~336-374)
-> `code(CLIPBOARD_SELECT_ALL)` -> InputLogic.java:1030 `mConnection.selectAll()` -> RichInputConnection.java:950-955: wenn schon alles markiert: Cursor ans Ende; sonst `mIC.performContextMenuAction(android.R.id.selectAll)`.
Warum kein Kontextmenü [V, Framework-Wissen]: `TextView.onTextContextMenuItem(ID_SELECT_ALL)` markiert nur (`selectAllText`) und ruft `invalidateActionModeAsync` nur, wenn zuvor schon eine Markierung/ActionMode da war.
Ohne Vorauswahl startet es keinen ActionMode (kein Ausschneiden/Kopieren/Einfügen-Leiste). Mit Handmarkierung läuft der ActionMode schon -> wird aktualisiert, deshalb sieht der Owner das Menü dann. Der Code setzt nichts anderes ab (kein Ctrl+A, kein setSelection). Ctrl+A per Keyevent geht in TextView auch über denselben Menüpunkt, hilft also nicht [V].
Optionen:
a) (S, ohne Android-Trick) Eigene Aktionsleiste: nach SELECT_ALL (und Auswahl-Änderungen) zeigt die Leiste Chips „Kopieren / Ausschneiden / Einfügen / (Teilen)“ – die Bausteine gibt es: ToolbarKeys COPY/CUT/PASTE/SELECT_*
   (ToolbarUtils.kt, Codes CLIPBOARD_COPY/CUT/PASTE, InputLogic.java:1030-1050), der Leistenmechanismus `setExternalSuggestionView` und Auto-Zeigen der Toolbar bei Auswahl (LatinIME.setNeutralSuggestionStrip: `hasSelection()` -> Toolbar sichtbar, aber nur wenn `auto_show_toolbar` an ist; Standard aus).
   Kleinste Änderung: bei Auswahl `setToolbarVisibility(true)` und Toolbar-Vorbelegung COPY/CUT/PASTE; der Owner hat dann dasselbe Menü in jeder App.
b) (S) Nach `performContextMenuAction(selectAll)` ohne Vorauswahl zusätzlich `performContextMenuAction(android.R.id.startSelectingText)` (API 21+, in TextView/Editor an; nicht in allen Apps) – [V], Wirkung je App prüfen, kein Fehler wenn ignoriert.
c) (M) Auswahl über `setSelection(0, length)` + `requestCursorUpdates` – ändert am ActionMode nichts [V].
Fork-Textaktionen (AgentPanel, „Textaktionen“-Taste) setzen eine Auswahl voraus (agent_panel_select_text) und sind KI-/Textwerkzeuge, kein Ersatz für Kopieren/Einfügen. Empfehlung a) + optional b).
Tests: InputLogicTest:3221/3366 prüfen nur das Mapping SpaceAction->KeyCode; ein Test für `selectAll()` (Fake-InputConnection: erwartete `performContextMenuAction`-Aufrufe) fehlt.

## Betroffene Tests und Szenarien bei Umbau von Symbolseiten und Leiste
- Robolectric: FlatStyleRenderTest (Symbolseiten-Zeilen 122-155, Leistenslots, Pixelfarben), FlatMenuTest, FlatStyleTest, FlatClipboardTest, RgbLightingRenderTest, DesignTilesTest, SettingsSystemAreaTest, ScreenshotChipTest, FlatPanelTest,
  KeyboardParserTest, SubtypeTest, SettingsBarAreaTest, SettingsKeysScreenTest, SymbolInkTest, TouchZonesDynamicTest, FieldCorrectionMemoryTest, InputLogicTest (Space-Gesten, Löschwisch).
- devicetest.py (tools/devicetest/devicetest.py): `Driver.type` tippt Nicht-Buchstaben über die Taste „Symbole“/„Symbols“ (KEY_NAMES, Zeile 60) und liest Tasten per Beschreibung (`self.keymap.get(char)`, Zeile 390-396):
  Szenarien `typing_email` (@, ., -), `typing_number`, `typing_text`, `prompt_folders` (Ordnername mit Ziffern „Arbeit73“, FOLDER_NAME Zeile 1062), `clipboard_pill`, `prompt_store`, `autofill_inline`: bleiben stabil, solange die
  Zeichen und die Taste „Symbole“ existieren; `?`/`!`/`:` liegen auf Seite 1 schon im Gboard-Layout (€-Zeichen: Locale!). Falls die Symbolseite Ziffern in der Zahlenreihe hat und die Buchstabenseite auch: keymap findet Ziffern auf der Buchstabenseite zuerst, kein Wechsel nötig.
  Leiste: `STRIP_FIXED` (Zeile 66, „Mehr Tasten“, „Textaktionen“) und `strip_nodes/strip_texts` (Zeile 236-246) hängen an den Beschreibungen der Leistentasten; ein neues Raster mit eigener Beschreibung muss dort ergänzt werden, sonst gelten
  Raster-Tasten als „Wortvorschlag“ (`wait_for_suggestions`, Zeile 417/530/842).
- Docs-Zeilen: FEATURES.md Zeile 23 (Flacher Look), 25 (Symbolseiten wie Gboard), 26 (Emojis auf den Symbolseiten), 27 (Tastaturmenü), 68 (Leertasten-Gesten), 174 (Bereich Leiste & Befehle), 173 (Tasten & Sprachen); ANLEITUNG.md Abschnitt „Aussehen: flacher Look“
  (Zeile 1273-1320), Leistenmodus (Zeile 211-212), Gesten Leertaste (Zeile 169-184, 332); ARCHITECTURE.md „Flacher Stil“ (Zeile 2405-2470, KeyboardParser/FlatStrip/FlatMenuView), „Geänderte Upstream-Dateien“ (PointerTracker, KeyboardParser, SuggestionStripView),
  Fallstricke P100-P102, P289; ROADMAP Etappe 23; VALIDATION (Etappen 23, 33, 64).

## Vorschlag: Etappenschnitt (nach Nutzen/Aufwand)
1. **Symbolseiten = Gboard, stilunabhängig** (M): Standarddateien/Namen auf Flat-Inhalt, Theme-Bedingungen in KeyboardParser auf Layoutnamen, Bodenzeile (numpad auf Seite 2, Dpad aus), Zahlenreihe-aus-Sonderfall. Test: Seiten pro Stil. Risiko: KeyboardParser (Upstream, nicht Sperrliste); Hinweisbuchstaben der Buchstabentasten verschieben sich, wenn die alte Reihe 1 fehlt.
2. **Alles auswählen zeigt Kopieren/Ausschneiden/Einfügen** (S): Option a) (+b). Risiko gering (LatinIME/Leiste, nicht InputLogic).
3. **Wischen auf Symbolseiten tippt nie die Nachbartaste** (S): eine Bedingung in PointerTracker.onDownEventInternal. Risiko: PointerTracker -> enge Änderung + Prüfer + Test (Down/Move/Up). Erst reproduzieren (welcher Weg liefert `/`->`%`).
4. **Leiste wie Gboard Stufe a+b** (M): feste Slots/Raster unabhängig vom Stil, Max. 8, Raster mit allen Werkzeugen; devicetest STRIP_FIXED anpassen. Risiko: SuggestionStripView/FlatStrip (Fork-nah), Konkurrenz der Chips bleibt unverändert.
5. **Latenz messen** (S, nur Messung): Hook SEPARATOR_COMMIT + Szenario; danach gezielter Fix (z.B. Strip-Update nach dem Commit statt davor, `setNeutralSuggestionStrip` entkoppeln). Fix selbst (M) berührt InputLogic -> Prüfer Pflicht.
6. **Leiste Stufe c, Ziehen im Fenster** (L): eigenes Raster mit Drag&Drop; Zwischenstufe „Leiste anpassen“ -> vorhandene Reorder-Seite (S).
7. **Cursor Trackpad** (L): reine Kurve/Magnetismus im Kern (S, testbar), Anbindung in KeyboardActionListenerImpl (M), 2D und Randscrollen über TouchpadHandler (M), PointerTracker nur Schrittgröße/Kurve (Risiko hoch: Sperrliste, Prüfer; Gerätetest nötig, Robolectric kann „Gefühl“ nicht prüfen).
Reihenfolge nach Wert/Aufwand: 2, 3, 1, 5(Messung), 4, 7a(Kurve), 6, 7b.
