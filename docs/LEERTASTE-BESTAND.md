# Bestand: RGB-Beleuchtung und Leertaste (nur gelesen, nichts geändert)

Pfade: APP = app/src/main/java/helium314/keyboard, CORE = agent-core/src/main/kotlin/dev/agentkeyboard/core.
Jede Aussage trägt "gelesen" (Code/Doku gesehen) oder "Vermutung".

## 1. Rendering des Lichts
- Kein eigenes View, kein Overlay, keine Drawable: Das Licht wird mitten im normalen Zeichnen von `KeyboardView.onDrawKey`
  über sieben Hooks eines `RgbKeyPainter` gemalt (APP/keyboard/KeyboardView.java:309,320,361,398,464,490,577; gelesen).
  Wichtigster Hook: `drawOverBackground` direkt nach `background.draw` (KeyboardView.java:398; gelesen).
- Dateien: APP/agent/RgbLighting.kt (Singleton, Uhr, Modus, Frame-Schleife, 430 Z.), RgbKeyPainter.kt (381), RgbTexture.kt (508:
  `RgbTexture`, `RgbWork`, `RgbTiles`), RgbStrip.kt (Leiste); Rechnen in CORE/RgbLighting.kt (1053 Z., `RgbEngine`),
  RgbPatterns.kt (`RgbPatterns.field`, `RgbOverlay`), RgbOwnPattern.kt (eigene Muster) (alles gelesen).
- Threads (gelesen): UI-Thread: Uhr vorrücken (`RgbLighting.onFrame` :257), `beginDraw`, Zeichnen. Thread `rgb-light`
  (RgbTexture.kt ~RgbWork): rechnet das Farbraster 48 x max. 32 (`computeGrid`, `engine.base`+`effects`), doppelt gepuffert, ein Raster
  hinter der Uhr. Thread `rgb-tiles` (MIN_PRIORITY): Detail-Kacheln einmal je Muster.
- Bitmaps/Shader/Paint (gelesen, RgbTexture.kt / RgbKeyPainter.kt):
  * Feld-Bitmap 48 x <=32 ARGB_8888, je Bild `bitmap.setPixels(...)` (RgbTexture.kt `show`), `BitmapShader` CLAMP, bilinear
    (ab API 33 `filterMode=LINEAR`, :346), Matrix streckt es über die View (`setArea`).
  * Kachel-Bitmap (nur Muster mit Detail) als `BitmapShader` REPEAT; je Bild nur `setLocalMatrix` (`updateTile`).
  * Je Taste eigene Shader-Objekte (SparseArray, max. 256) und eine ALPHA_8-Maske der Tastenform (`RgbKeyMasks`, max. 32);
    Zeichnen = `canvas.drawBitmap(mask, 0, 0, paint-mit-Shader)` (`drawMask`). Doku P90 erklärt warum (Display-Liste hält Shader per Referenz).
  * ColorFilter: `PorterDuffColorFilter(SRC_IN)` je Farbe gecacht (`RgbFilterCache`, max. 64) nur für "Jede Taste einfarbig" und Icons/Beschriftung.
- "Höhenlinien" = `RgbPattern.TOPO` (CORE/RgbPatterns.kt:159-166 und :309-333; gelesen):
  * Farbfeld (weiche Hügel, Fbm-Rauschen, `RgbNoise`) wird je Bild im 48x32-Raster gerechnet (Hintergrund-Thread), Zeit über `flow`.
  * Die Linien sind eine STATISCHE 384x384-Kachel (`contourTile`, 16 Stufen, Linien per Höhe/Gradient, jede 4. gestrichelt, weiß, Alpha
    <=0,6), periodisch, einmal gebaut (20-40 ms Desktop-JVM, laut Doku), danach Kachel-Shader.
  * Bewegung = NUR VERSCHIEBEN: `RgbOverlay.transform` (RgbPatterns.kt:258-282) setzt Skala `w*0,8/384` (w = View-Breite) und Drift
    `0,035` bzw. `0,025` Kachelbreiten je Phasen-Durchlauf in x bzw. y. Die Linien verformen sich NIE; nur das Farbfeld darunter ändert sich.
  * Gegenstück "eigene Muster": Generator `CONTOUR` (CORE/RgbOwnPattern.kt:488-495) rechnet Linien aus fbm je Rasterzelle
    (7 Stufen, Breite 0,14) - nur 48x32 aufgelöst, daher weich; Muster TOPO als eigenes Muster nutzt ihn (:265-266). Ob der Owner
    das eingebaute (Kachel) oder ein eigenes Muster nutzt: Vermutung = eingebautes TOPO (Kachel), da er "Textur auf der Leertaste" sieht.
- Maskierung je Taste (gelesen): `areaOf` (RgbKeyPainter.kt:~300) ordnet Leertaste `RgbArea.SPACE` zu (Code SPACE oder
  BACKGROUND_TYPE_SPACEBAR); Bereiche in `settings.areas` entscheiden; die Maske ist die Form der Taste (Drawable
  `background.current`), das Licht wird durch sie gemalt. Leertaste und Buchstaben laufen durch denselben Pfad, nur der Bereich unterscheidet.
- Hardware-Beschleunigung (gelesen): `KeyboardView.setHardwareAcceleratedDrawingEnabled` -> `setLayerType(LAYER_TYPE_HARDWARE, null)`
  (KeyboardView.java:180-184), aufgerufen aus KeyboardSwitcher.java:559 mit `LatinIME.mIsHardwareAcceleratedDrawingEnabled`
  (= `enableHardwareAcceleration()` des InputMethodService, LatinIME.java:636). `onDraw` nimmt bei `canvas.isHardwareAccelerated()` den
  direkten Pfad `onDrawKeyboard(canvas)` (:257-259), der Offscreen-Bitmap-Puffer (`mOffscreenBuffer`) gilt nur bei Software. In
  `onDrawKeyboard` werden bei Hardware IMMER alle Tasten gezeichnet (:314 `drawAllKeys || isHardwareAccelerated`). Kein Manifest-Flag
  `hardwareAccelerated` gefunden (gelesen). Ob die Ebene auf dem Gerät wirklich Hardware ist: Vermutung ja.

## 2. Frame-Planung
- Treiber (gelesen, APP/agent/RgbLighting.kt): `Choreographer.FrameCallback` (`onFrame` :257), nur im Modus `RUNNING`.
  `scheduleFrame` (:245): `postFrameCallbackDelayed(frameCallback, delayMs)` mit `delayMs = pacer.nanosUntilDue/1e6`, sonst
  `postFrameCallback`. Kein Handler, kein ValueAnimator, kein postInvalidateOnAnimation.
- 30/60 Hz (CORE/RgbLighting.kt:1030-1053, gelesen): `RgbFramePacer(fps)`: Intervall 1e9/30 oder 1e9/60, `shouldRender` mit 4 ms
  Spiel, `nanosUntilDue` plant den nächsten Aufruf erst wenn fällig. Nur diese zwei Werte (FRAME_RATES in AgentRgbFrameRate.kt:73).
  Keine Abfrage der Display-Rate; auf 90/120-Hz-Displays kommt ein Callback je Vsync nach dem Delay (Vermutung zur Praxis).
- Je erlaubtem Bild: `engines.advance(now)` (Phase += dt/Periode, dt max 100 ms), dann `redraw` = `view.invalidateAllKeys()`
  (+ `RgbStrip.paint` wenn Leiste leuchtet) (:272-276; gelesen). `invalidateAllKeys` = `invalidate()` der GANZEN View
  (KeyboardView.java:~633-641). Keine Teilrechtecke fürs Licht. (`invalidateKey` mit Teilrechteck existiert für Tastendruck/Sprachkürzel.)
- Pause/Stopp (gelesen, CORE `RgbRun.mode` :1021 und RgbLighting.kt `update`/`isVisible`): OFF (aus); STATIC (Android-Animationen aus
  oder Energiesparmodus + `pauseInPowerSave`, einmal zeichnen, Taps ignoriert); PAUSED (nicht sichtbar: nicht angehängt/`isShown`/
  Fenster/Breite 0; Overlay offen [Panel, Rad, Rechner, Zeichen-Kunst]; Flat-Menü ohne leuchtende Leiste; kein bewegtes Element);
  RUNNING nur wenn `settings.moves()` (ein leuchtendes Element bewegt sich, `reactiveOnly` zählt nicht) oder Tipp-Effekt verblasst.
  `onHideWindow` und `onDetached` stoppen Frames. Energiesparmodus per Broadcast, Animationsskala alle <=2 s (SYSTEM_CHECK_MS).
  "Nur beim Tippen" (`reactiveOnly`): Bilder nur solange ein Effekt verblasst (`glowMs`).
- Offscreen-Puffer: bei Hardware-Pfad nicht aktiv (siehe 1). Software-Pfad (nur wenn `isHardwareAccelerated()` false): Offscreen-Bitmap, volle
  Neuzeichnung bei `invalidateAllKeys` (KeyboardView.java:257-272; gelesen).
- Messungen im Repo (gelesen): `RgbFrameStats` (CORE/RgbLighting.kt:915; Fenster 5 s): UI-Anteil Licht je Bild, ganzes Zeichnen der
  Tastatur (`beginDraw`..`endDraw`), Zahl/Dauer der Hintergrundraster; `RgbLighting.frameWork()`/`lastFrameWork()` :~125; angezeigt in
  der Diagnose-Karte "RGB-Beleuchtung" (APP/agent/Diagnose.kt:516-535: Bilder, UI avg/max, Zeichnen avg/max, Hintergrund) plus Zeilen je Muster
  (`lightPatterns`: Kosten eines 48x32-Rasters, gemessen auf dem Telefon). Kein GPU-Zähler, kein Energiewert. Keine TypingPerf-Verbindung gefunden
  (nicht gesucht, Vermutung). Doku: "GPU-Kosten und Akku auf einem Telefon sind nicht gemessen" (ARCHITECTURE.md ~2688; gelesen).

## 3. Tipp-Reaktionen
- Weg (gelesen): `PointerTracker.setPressedKeyGraphics` (PointerTracker.java:476-489, aus `onDownEventInternal` :860) ->
  `sDrawingProxy.onKeyPressed(key, withPreview)` -> `MainKeyboardView.onKeyPressed` (:362-364) -> `RgbLighting.onKeyPressed(view, key)`
  (APP/agent/RgbLighting.kt:~160-178). UI-Thread. Übergeben: NUR `Key` (und `withPreview`, nicht weitergereicht). Es werden Taste-Mitte
  `(key.x + key.width/2 + paddingLeft)/width` und `(key.y + key.height/2 + paddingTop)/height`, `keyId` (identityHashCode) und `uptimeMillis` an
  `engines.tap(x, y, keyId, now)` gegeben (gelesen). Der echte Touch-x/y ist an diesem Hook NICHT verfügbar.
- Wo x/y verfügbar ist (gelesen, ohne Änderungsvorschlag): `PointerTracker.onDownEvent(x, y, ...)` :664 / `onDownEventInternal(x,y,eventTime)` :830 /
  `onMoveEvent(x, y, ...)` :935 (Pixel in View-Koordinaten) und `MainKeyboardView.onTouchEvent(MotionEvent)` :607 bzw. `processMotionEvent` :623
  (ruft `tracker.processMotionEvent`). Auf der View-Ebene ist das volle MotionEvent mit Verlauf vorhanden, VOR der Weitergabe an den Tracker.
- Modell (gelesen, CORE/RgbLighting.kt:602-682, 825-860): Ringpuffer `MAX_TAPS = 8` (tapX, tapY, tapKey, tapTime), `nextTap` überschreibt
  den ältesten. Zustandslose Funktionen der Zeit seit Tipp: `fade(age)` = smoothstep über `glowMs` (Vorgabe 800 ms); FLASH = `keyFade`
  (nur diese Taste, weiß bis 0,7); RIPPLE = `ring` (Ring: Radius `progress * 1,2` Tastaturbreiten, Breite 0,12, Abnahme `1-progress`, Abstand mit
  `aspect` korrigiert); SPEED_UP = `phase`-Schritt mal `1 + 3*globalFade`; `reactiveOnly` skaliert Stärke. Eigene Muster: Ebenen-Reaktionen
  PULSE/RIPPLE/KEY_GLOW/SPOTLIGHT (RgbOwnPattern.kt:49-55, 421-435). Alles wirkt NUR auf das Farbfeld bzw. die Stärke, die Linien-Kachel wird
  nicht verformt/angezogen. Keine Dämpfung/Federung, keine Physik.
- Datenschutz (gelesen): kein Tap-Effekt in Passwortfeldern/Inkognito (`privateField`), Taps werden bei neuem Feld/Ausblenden verworfen.
- Effektbild hängt am 48x32-Raster: Ring/Impuls ist damit auf Rasterauflösung (<=~25 px je Zelle bei 1200 px) begrenzt (Vermutung, aus 48 Spalten).

## 4. Leertaste
- Zeichnung (gelesen): Tastenhintergrund `mSpacebarBackground` (`ColorType.SPACE_BAR_BACKGROUND`, KeyboardView.java:123, :600), dann
  Licht-Hook (Maske der Tastenform), Icon/Beschriftung, in `MainKeyboardView.onDrawKeyTopVisuals` (:729-748) `drawLanguageOnSpacebar`
  (:839ff; Text zentriert, Sprachname/`mSpaceBarText`, Alpha aus `mLanguageOnSpacebarAnimAlpha`, Fade-Animator) wenn
  `mLanguageOnSpacebarFormatType != NONE`, und `drawKeyPopupHint` ("..." bei Sprachwechsel-per-Leertaste). Das Sprachkürzel nimmt `labelColor` (gelesen).
  Dass das Label beim Owner "versteckt" ist: Vermutung (Einstellung/Format NONE oder Licht überdeckt es; nicht geprüft).
- Größe auf 1200-px-Telefon (Vermutung, aus Layoutwerten): Layout `functional_keys*.json`: Unterste Reihe symbol_alpha 0,15, Komma,
  (Sprache), Emoji, Numpad, SPACE ohne Breite (= Rest), dann Punkt, action 0,15; Standardtastenbreite 0,1 (`DEFAULT_KEYBOARD_COLUMNS`=10,
  KeyboardParams.java:236). Leertaste ca. 0,35-0,5 Breite = ~420-600 px (gelesen: Gewichte; Summe ungeprüft). Höhe: `config_default_keyboard_height` 205,6 dp
  (res/values/config.xml:19), x Dichte (~2,75-3) = ~565-620 px, vier Reihen -> Reihe ~130-150 px; Leertaste ~120-140 px hoch (Vermutung;
  abhängig von Höhenskala, Zahlenreihe x1,33, Abstände). Fläche ~0,06-0,08 Mio px = ~4 % der Tastaturfläche (Vermutung).
- Sonst auf der Taste (gelesen): Wisch-Cursorbewegung (`mSpaceGestures`, PointerTracker.java:891, 1073ff) - Zeichnen eines Indikators auf der
  Leertaste dafür nicht gefunden; Sprachwechsel-Hinweis "..." (siehe oben). Kein Slide-Indikator im Zeichencode gefunden (Vermutung: keiner).

## 5. Moderne Grafik-APIs
- gelesen (grep über app/src, ohne Tests): KEIN `RuntimeShader`/AGSL, `RenderEffect`, `RenderNode`, OpenGL/EGL, `TextureView`. Einzige Treffer:
  `SurfaceView` in InlineAutofillUtils.java:162 (Autofill, unrelated), `setLayerType(LAYER_TYPE_HARDWARE)` an KeyboardView, Emoji-/Clipboard-View,
  DrawingPreviewPlacerView. Doku ARCHITECTURE.md:~2603 sagt ausdrücklich: "Kein `RuntimeShader`: ein Weg für alle Android-Versionen,
  alles auf der JVM prüfbar".
- Shader-Unterklassen: nur `BitmapShader` (RgbTexture.kt). API-Guard-Muster: `Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU`
  inline (RgbTexture.kt:346); 66 Dateien nutzen SDK_INT inline; Paket `compat` (APP/compat: AppWorkarounds, ClipboardManagerCompat, ImeCompat,
  IsLockedCompat ...) für Kapselungen (gelesen). minSdk 21 laut Auftrag: RuntimeShader (API 33) bräuchte Guard plus Rückfall (Vermutung).

## 6. Tests
- agent-core `RgbOwnPatternTest.ownPatternsStayWithinTheBudget` (CORE-Test :513-553, gelesen): misst Kosten eines "Bildes" (1634 `light`-Werte =
  32x48-Raster + 45 Tasten x2 + 8 Leiste) mit acht Taps und RIPPLE, 400 Aufwärm-Bilder, Bestwert aus 9 Läufen x 60 Bilder (nanoTime, JVM des
  Testrechners); Aussage: jedes schwere eigene Muster (4xNoise, 4xCONTOUR, Plasma+Funkeln+Ring+Spot, 1xLinear) `ms <= max(2*ink, 0.5)` und
  `< 4.0 ms`; relativ zum Muster INK, daher laufzeit-/lastabhängig (bekannt flaky auf CI).
- `RgbPatternsTest.oneFrameOfEachPatternFitsTheBudget` (:372-398; gelesen): jedes eingebaute Muster `< 15 000 µs` je Bild, gleicher Aufbau,
  druckt Kachelzeit. `theFlowRepeatsExactlyAtItsPeriod` (Fluss-Periode). RgbLightingTest, RgbCurveTest, RgbFrameStatsTest, RgbPatternJsonTest (nur Namen gesehen).
- App/Robolectric (RgbLightingRenderTest, gelesen Namen): `uiThreadLightingWorkPerFrame` (:632; misst UI-Zeit, schreibt build/reports/rgb-lighting,
  asserts nur `frames>0`, keine Zeitgrenze), `flashAndReactiveLightFollowKeyPresses`, `privateFieldsFeedNoTapsAndANewFieldDropsThem`,
  `aHiddenKeyboardNeverRequestsFrames`, `animationsOffGiveAStillPictureAndIgnoreTaps`, `onlyTheMainKeyboardViewIsLit`,
  `texturePatternsDrawOnKeysAndBackground`, `stillPatternsComputeNothingPerFrame`, `smoothKeysRunTheLightAcrossEachKey`, u. a.; RgbTilesTest, RgbFrameRateCardTest.
  Robolectric rastert in Software, sagt nichts über GPU.

## 7. Analyse: wo die Energie geht (aus dem Code, Vermutung/Analyse)
- Je Bild (30 oder 60 Hz) wird die GANZE Tastatur-View invalidiert (`invalidateAllKeys`); im Hardware-Pfad werden alle ~45 Tasten neu in die
  Display-Liste aufgenommen (Hintergrund, Maske+Shader, Beschriftung, Icons) und die Hardware-Ebene (`LAYER_TYPE_HARDWARE`) komplett neu
  gerendert und zusammengesetzt, obwohl die Leertaste nur ~4 % der Fläche hat (Analyse). Wäre nur die Leertaste lebendig, wäre das Teilrechteck
  `invalidate(x,y,x2,y2)` plausibel; heute nicht genutzt (Analyse).
- CPU/UI-Thread je Bild: `engines.advance`, `beginDraw`, je Taste `light()` (Palettenwerte), Matrix-Setzen, Shader-Wechsel, ~2-4 Zeichenaufrufe
  mit Shader je Taste (Doku: bis zu vier). Doku-Zahlen: Vorbereitung 0,03-0,2 ms; Zeichnen des Lichts 2,5-6 ms bzw. ganze Tastatur 7-45 ms
  in Robolectric-Software (nicht aussagekräftig für das Gerät).
- Hintergrund-Thread `rgb-light`: pro bewegtem Bild ein Raster 48x32 (1536 Werte, TOPO ~0,67 ms in der Test-JVM für den ganzen Rahmen; Doku). Auf
  dem Telefon vermutlich mehrfach langsamer (Vermutung). Kommt dazu, Kern wird dafür jedes Bild aufgeweckt.
- GPU/Upload: Das Feld-Bitmap (48x32x4 = ~6 KB) wird je Bild mit `setPixels` verändert; ein veränderter Software-`Bitmap` wird im
  Hardware-Renderer beim nächsten Zeichnen als Textur neu hochgeladen (Vermutung, Android-Verhalten, nicht im Repo belegt). Das ist
  klein; teurer ist vermutlich das Neuaufzeichnen/Rendern der ganzen Ebene. Die 384x384-Kachel (~590 KB) wird nur einmal hochgeladen, danach nur Matrix (gelesen).
- Overdraw (Analyse): je Taste Basis-Hintergrund, Feld-Licht per Maske, Kachel per Maske, evtl. Blitz/Druck, dazu Tastaturhintergrund-Licht
  und Kachel über die ganze View (`drawKeyboardBackground`) - bis zu 4-6 Schichten pro Pixel.
- Frame-Takt: Choreographer-Aufruf nur wenn fällig (kein Dauer-Vsync), aber bei 30 Hz bleibt trotzdem alle 33 ms CPU+GPU aktiv, solange die
  Tastatur sichtbar ist und etwas "moves()". Keine Anpassung an die Bildschirm-Rate, kein adaptives Absenken im Leerlauf, kein Stopp wenn die
  Bewegung unmerklich ist (gelesen: nur die oben genannten Pausenregeln).
- Für ein neues Konzept relevant (Analyse): (1) Tipp-x/y fehlt am Licht-Hook (View-`onTouchEvent` oder Tracker hätten es). (2) Linien sind
  eine starre, nur verschobene Kachel: verformende Wellen/Anziehung bräuchten Neuberechnung (Raster 48x32 reicht nicht für scharfe Linien,
  siehe eigenes CONTOUR) oder einen Shader (RuntimeShader erst API 33, minSdk 21). (3) 8-Tipp-Ringpuffer und `fade`-Kurve sind ein vorhandenes
  Gerüst für Impulse, ohne Dämpfung/Federphysik. (4) Messhilfe `RgbFrameStats` + Diagnose-Karte lässt sich wiederverwenden.
