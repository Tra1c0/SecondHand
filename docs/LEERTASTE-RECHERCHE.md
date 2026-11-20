# Leertaste-Animation: Faktenprüfung (Stand 2026-11-20)

Labels: **[B]** belegt (Primärquelle) · **[P]** plausibel (Sekundärquelle/Schluss) · **[U]** unbelegt.
Quellenkürzel (alles lokal gespeichert, Seiten als Text unter `.../scratchpad/txt/`, Code unter `src/ hwui/ fw/ sf/ kgsl/`):
- DAD = developer.android.com (erreichbar). RAW = raw.githubusercontent.com (erreichbar):
  `aosp-mirror/platform_frameworks_base@main` (View, ViewRootImpl, RuntimeShader, hwui), `google/skia@main`,
  `google/perfetto@main/docs`, `LineageOS/android_frameworks_native@lineage-23.0` (SurfaceFlinger; Android-16-Basis, Annahme),
  `LineageOS/android_kernel_xiaomi_sm8450@lineage-22.2` (kgsl-Treiber, Snapdragon 8 Gen 1, NICHT 8 Elite).
- Achtung: `@main` ist der heutige AOSP-Stand, kann neuer sein als Android 16/HyperOS. Feature-Flags (aconfig) sind im Quelltext
  sichtbar, ihr Wert auf dem Xiaomi 15 nicht.
- **Blockiert** (Proxy 403 / DNS): source.android.com, android.googlesource.com, iquilezles.org, displaymate.com, arxiv.org, perfetto.dev,
  skia.org, registry.khronos.org, developer.qualcomm.com, docs.qualcomm.com, dragonwingdocs.qualcomm.com, developer.huawei.com, oled-a.org,
  android-developers.googleblog.com, github.com (HTML/API). www.qualcomm.com lieferte nur eine JS-Hülle ohne Inhalt.
  Aussagen aus diesen Hosts sind unten als **"nur Suchauszug"** markiert.

---
## A. AGSL / RuntimeShader

**A1 Mindest-API.** RuntimeShader "Added in API level 33" (DAD `reference/android/graphics/RuntimeShader`); AGSL "is used by Android 13 and above"
(DAD `develop/ui/views/graphics/agsl`). `setInputBuffer`/`setInputShader` ebenfalls API 33. Folge: bei minSdk 21 ist ein eigener Fallback-Pfad
(Canvas) für API < 33 zwingend. **[B]**

**A2 Ausführungsort.** "When Android issues a GPU accelerated drawing operation, it assembles a single GPU fragment shader … Your AGSL effect
contributes a function … to the GPU's fragment shader." (DAD agsl). Ein RuntimeShader ist ein Skia-`SkRuntimeShaderBuilder`
(`hwui/jni/Shader.cpp`: `SkRuntimeEffect::MakeForShader(SkString(...), SkRuntimeEffect::Options{})`), gezeichnet von HWUI auf der RenderThread. **[B]**
Backend: `Properties.cpp`: `useVulkan = use_vulkan().value_or(false); … getProperty(PROPERTY_RENDERER, useVulkan ? "skiavk" : "skiagl")`
→ GL oder Vulkan, per Gerät/Property gewählt. Welches Backend das Xiaomi 15 nutzt, steht in keiner Quelle. **[B] für Mechanismus, [U] für Xiaomi 15**
(am Gerät: `adb shell getprop debug.hwui.renderer`, `dumpsys gfxinfo <pkg>` → "Pipeline=").

**A3 Ableitungen dFdx/dFdy/fwidth.** NICHT verfügbar. Skia `sksl_shared.sksl:247-252`: `$pure $es3 $genType dFdx(...)`, `... dFdy`, `... fwidth`
(als `$es3` markiert). `SkSLFunctionCall.cpp:1057`: "Strict-ES2 programs can never call an `$es3` function."
`SkRuntimeEffect.h`: `Options::maxVersionAllowed = SkSL::Version::k100` (Default; Hwui übergibt `Options{}`), und `strictES2Mode()` ist true bei
k100. DAD agsl-vs-glsl: "AGSL fixes its GLSL feature set at GLSL ES 1.0". **[B]**
Folge: konstante Linienbreite braucht |∇h| selbst (analytisch, 2–3 Zusatz-Auswertungen/Differenzen, oder Gradient-Textur), s. G1.

**A4 Schleifen.** Strict ES2: `ForStatement.cpp:183`: "In strict-ES2, loops must be unrollable or it's an error"; `ConvertWhile`: "while loops are not supported";
DAD agsl: "for loops with limitations. Some control structures require constant expressions". Konstante Obergrenze, keine while/do, keine Rekursion
("without support for recursion"). Indexierung von Arrays ebenfalls nach ES2-Anhang A beschränkt (`Compiler.cpp:424`). **[B]**
Folge: fBm-Oktaven/Warp-Stufen als Schleife mit konstanter Zahl oder ausrollen; Kosten steigen linear mit Oktaven × 70k Pixel.

**A5 `half`.** DAD agsl-vs-glsl: "AGSL introduces half and short types which also represent medium precision."; Skia `src/sksl/README.md`:
"'float', 'int', and 'uint' are always high precision. New types 'half', 'short', and 'ushort' are medium precision (we do not use low precision)."
**[B]** Zahlenwerte von mediump: GLSL ES 1.00 §4.5.1 = mindestens Bereich ±2^14, relative Genauigkeit 2^-10 (≈ fp16); Implementierung darf mehr
bieten (Spezifikation selbst blockiert; **nur Suchauszug**, mehrere Sekundärtexte einig). **[P]**
Folgen (Schluss aus 2^-10): (a) `half` für eine wachsende Zeit-Uniform ist ungeeignet: ab t≈1024 s Auflösung 1 s, ab 2048 s Sprünge von 2 s,
darüber ab 16384 Überlauf-Risiko; Zeit auf der CPU modulo Periode halten oder als `float`. (b) Pixelkoordinaten bis 1200 in `half`:
Schrittweite bei 512–1024 = 0,5 px, bei ≥1024 = 1 px → sichtbares Stufen von Linien/Warp; Koordinaten und Höhenwert `float`, nur Farbe/Alpha `half`.
**[P]** (rechnerischer Schluss, gerätabhängig).
Nutzen: Qualcomm: "scalar architecture can be twice as power-efficient … for … mediump" (Adreno-Guide, **nur Suchauszug**); Android "Optimize with reduced precision"
(DAD `games/optimize/vulkan-reduced-precision`): "Half precision does not always result in a performance improvement, especially for complicated calculations."
**[B] für die Einschränkung, [U] für Gewinn bei dieser Shader-Größe**

**A6 Uniform-Limits.** In `SkRuntimeEffect.h` und DAD ist kein Limit dokumentiert; `uniformSize()` = Summe aller Uniforms; GPU-Backends haben eigene
Grenzen (UBO-Größe). Für einen Shader mit <50 floats irrelevant. **[U] (kein Limit genannt), [P] praktisch irrelevant**

**A7 Kind-Shader / BitmapShader.** `RuntimeShader.setInputShader(String, Shader)` und `setInputBuffer(String, BitmapShader)` (API 33). DAD:
"setInputBuffer … returns samples directly from the bitmap's buffer … no transformation … such as colorspace conversion or alpha premultiplication";
Hinweis auf "heightmaps" ausdrücklich; Koordinaten nicht normalisiert ("(0, 0) in the upper-left … (width, height) in the bottom-right").
Filter: `Shader.cpp`: `SkSamplingOptions(filter ? SkFilterMode::kLinear : SkFilterMode::kNearest, SkMipmapMode::kNone)` bzw. `Aniso(maxAniso)`;
BitmapShader hat `FILTER_MODE_LINEAR/NEAREST` und `setMaxAnisotropy`. → bilinear oder nearest, **keine Mipmaps**. **[B]**
Wichtig: 8-bit-Höhenkarte quantisiert (256 Stufen) → Isolinien aus einer 8-bit-Textur zeigen Banding; Höhe auf 2 Kanäle packen oder analytisch rechnen. **[P]**

**A8 Kosten eines Uniform-Updates pro Frame.** `RuntimeShader.java`: `setFloatUniform(...)` → `nativeUpdateUniforms(mNativeInstanceRuntimeShaderBuilder, …); discardNativeInstance();`
und `createNativeInstance` → `nativeCreateShader(builder, matrix)` → `builder->makeShader(matrix)`. Der kompilierte `SkRuntimeEffect` bleibt im
Builder; pro Update wird nur die Uniform-Daten-Kopie geändert und beim nächsten Zeichnen ein neues `SkShader`-Objekt (leichtgewichtig) erzeugt. **[B]**
Dass Skia das GPU-Programm daraufhin wiederverwendet (Programm-Cache nach Effekt-Hash), ist erwartetes Verhalten, in diesem Lauf nicht aus Code verifiziert. **[P]**
Der Shader wird erst bei Verwendung kompiliert; erster Frame hat Jank (einmal vorwärmen). **[P]**
Zeichnen: UI-Thread muss pro Frame `invalidate()` → Display-List der View neu aufzeichnen (B1), weil die Uniform nur über ein neu gesetztes Paint/Shader
in die DisplayList kommt. Eine RenderThread-only-Animation gibt es für AGSL-Uniforms nicht (AnimatedVectorDrawable/RenderNode-Properties sind etwas anderes, H5). **[P]**

---
## B. Kosten einer Per-Frame-Invalidierung einer kleinen View

**B1 Nur diese View wird neu aufgezeichnet.** `View.updateDisplayListIfDirty()` (fw/View.java:23841): erzeugt die DisplayList nur neu, wenn
`PFLAG_DRAWING_CACHE_VALID == 0 || !hasDisplayList || mRecreateDisplayList`; sonst "no work needed", Kinder werden nur re-issued.
DAD hardware-accel: "Views that have not been invalidated can be redrawn simply by re-issuing the previously recorded display list." **[B]**
(Der IME-Fensterbaum wird dennoch traversiert; ein `performTraversals`/Choreographer-Frame läuft pro Invalidate; Kosten nicht beziffert, **[U]**.)

**B2 Dirty-Region/Partial Update in HWUI.** `CanvasContext.cpp`: `mDamageAccumulator.finish(&dirty)`, `computeDirtyRect(frame, &dirty)` (bei
`bufferAge > 1` Vereinigung der letzten Schaden-Rechtecke, bei age 0 Vollbild). `SkiaPipeline.cpp:393-399`: "clip drawing to dirty region only when not
recording SKP files" → `canvas->androidFramework_setDeviceClipRestriction(clipRestriction)`. GL: `EglManager.cpp`: `enablePartialUpdates = true`,
`useBufferAge = true`, `EGL_KHR_swap_buffers_with_damage` ist Pflicht ("Missing required extension"), `eglSetDamageRegionKHR` wenn `EGL_KHR_partial_update`,
sonst Preserved-Swap (Kommentar: Adreno-Treiberbug b/31957043 verhinderte Buffer-Age). Vulkan: `VulkanSurface::presentCurrentBuffer` →
`native_window_set_surface_damage`; "VkManager will always operate in a mode equivalent to … kBufferAge". Ergebnis: GPU rastert nur das Schadens-Rechteck
(Scissor/Clip), nicht das ganze Fenster. **[B]** (Ob Adreno 830 mit EGL_KHR_partial_update läuft: **[U]**, am Gerät `dumpsys SurfaceFlinger`/EGL-Extensions.)
DAD: `invalidate(Rect)` seit API 28 deprecated: "In API 21 the given rectangle is ignored entirely in favor of an internally-calculated area."
→ Schadensfläche = Bounds der invalidierten View (Leertaste), nicht frei wählbar. **[B]**

**B3 SurfaceFlinger/HWC.** SF übergibt Surface-Damage an HWC: `OutputLayer.cpp:754`: `hwcLayer->setSurfaceDamage(surfaceDamage)`.
`Output.cpp:1138-1160`: `dirty = !getDirtyRegion().isEmpty(); mMustRecompose = dirty && …` – bei keinem Dirty wird nicht neu komponiert.
Die IME ist ein eigenes Fenster = eigene Layer; jeder Frame mit Damage macht diesen Layer "aktualisiert" → Display-Pipeline läuft. Ob HWC die
Damage-Info zu echtem Teil-Scan-out/Teil-Update nutzt (Command-Mode-DSI, DPU), ist Hersteller-HAL: **[U]**.
Allgemein (source.android.com, **nur Suchauszug**): "The HWC chooses the most efficient path … Layers marked for client composition are rendered by SurfaceFlinger";
Overlays begrenzt (typ. 4), darüber GLES-Komposition; "Overlay planes can be less efficient than GPU composition when the screen isn't changing". **[P]**
Ob Xiaomi-HWC die IME-Schicht Device- oder Client-komponiert: **[U]**, am Gerät `dumpsys SurfaceFlinger` (Composition Type pro Layer).

**B4 Gesamtbild.** Skalierung nach Fläche gilt nur für den Fragment-Teil; feste Pro-Frame-Kosten (Choreographer, Traversal, DisplayList, RenderThread-Sync,
Buffer-Dequeue/Queue, SF-Commit, HWC-Validate/Present, ggf. Display-Takt) hängen von der Frame-ZAHL, nicht von der Pixel-Zahl ab (s. E).

---
## C. Frame-Rate-Steuerung

**C1 Basics.** DAD Choreographer: "receives timing pulses (such as vertical synchronization) … and then schedules work … as part of rendering the next display frame";
`View.postInvalidateOnAnimation()` für vsync-ausgerichtete Invalidierung. Es gibt keine "20-fps-Choreographer"-API: Drosseln = Frames auslassen
(Zeitstempel-Differenz) oder Callback verzögern. **[B]**

**C2 Surface.setFrameRate.** API 30 (`setFrameRate(float,int)`), API 31 (`…, changeFrameRateStrategy`). Javadoc (fw/Surface.java): "Usage of this API won't
introduce frame rate throttling … the system may change the display refresh rate"; 0 = "accept the system's choice"; "it's fine to pass 30fps to a device that can only run
the display at 60fps". `FrameRateParams`-Variante hinter Flag `ARR_SETFRAMERATE_API`. SurfaceControl-Gegenstück: `SurfaceControl.Transaction.setFrameRate/setFrameRateCategory`
(ViewRootImpl nutzt `mFrameRateTransaction.setFrameRateCategory(mSurfaceControl, …)`). **[B]**
Eine IME hat keinen direkten Surface-Handle auf das eigene Fenster; sie kann nur über View-APIs (C3) oder Window-LayoutParams abstimmen. **[P]**

**C3 ARR / View-Votes (API 35).** `View.setRequestedFrameRate(float)`, `REQUESTED_FRAME_RATE_CATEGORY_{DEFAULT,NO_PREFERENCE,LOW,NORMAL,HIGH}`,
`setFrameContentVelocity`, alle "Added in API level 35" (DAD View-Referenz). DAD (develop/ui/views/animations/adaptive-refresh-rate): "Caution: Adaptive refresh rate is supported on
devices that implement specific HAL APIs and run on Android 15-QPR1 and above. To check device support, use the hasArrSupport() API.";
"Views typically vote when drawn or updated"; "A View votes only if it requires redrawing. The final frame rate is determined by the highest vote.";
"Currently, most Views default to a 'Normal' frame rate, which is often set to 60 Hz."; "If any of the votes is greater than 60 Hz, it counts as a 'High' vote."
NO_PREFERENCE: "even if the View is active, the framework will not consider it". Javadoc: "the preference is valid as long as the View is invalidated"; auf ViewGroup
"will not propagate to child views". **[B]**
Defaults im Code (fw/View.java): kleine Views (`FRAME_RATE_SIZE_PERCENTAGE_THRESHOLD = 0.07f`, d. h. ≤ 7 % der Display-Pixel; Leertaste ≈ 2,2 %) bekommen
`FRAME_RATE_CATEGORY_LOW | REASON_SMALL`, wenn Flag `toolkitFrameRateBySize` aktiv, sonst NORMAL; große Views NORMAL/HIGH. Bei Positions-/Größenänderung
"boost" HIGH_HINT, "&& !isInputMethodWindowType" (IME-Fenster ausgenommen, `calculateFrameRateCategory`). **[B]** (Flag-Wert auf Xiaomi 15 **[U]**)
Konsequenz: Mit `setRequestedFrameRate(REQUESTED_FRAME_RATE_CATEGORY_LOW)` oder `…NO_PREFERENCE` auf der Leertaste kann die IME verhindern, dass die Animation
selbst eine höhere Rate verlangt. Ob Xiaomi ARR unterstützt (`Display.hasArrSupport()`): **[U]**, Laufzeit-Check.

**C4 Touch-Boost.** DAD: "Touch boost: When a touch event (MotionEvent.ACTION_DOWN) is detected, the refresh rate is boosted to 'High' for some time after the touch has been released";
`Window.setFrameRateBoostOnTouchEnabled(boolean)` (API 35): "Disabling touch boost behavior is not recommended". Code (ViewRootImpl): `FRAME_RATE_TOUCH_BOOST_TIME = 3000` ms nach ACTION_UP,
Kategorie `FRAME_RATE_CATEGORY_HIGH_HINT` ("this vote may be ignored in favor of more explicit votes", Surface.java). `shouldTouchBoost`: Kommentar "don't suppress touch boost
for TYPE_INPUT_METHOD in ViewRootImpl" (Flag `toolkitFrameRateTouchBoost25q1`), davor `undesiredType = TYPE_INPUT_METHOD && toolkitFrameRateTyping` (IME ausgenommen). Nur
bei `handled` Touch-Events. **[B]** (welcher Zweig auf HyperOS gilt: **[U]**). Folge: Tippen hebt die Panel-Rate evtl. unabhängig von unserer Animation für ~3 s; unsere Touch-Phase (60 fps)
kostet dann kaum Zusatz-Display-Energie, die Idle-Phase schon. **[P]**
SurfaceFlinger zusätzlich: eigener Touch-Timer (`Scheduler.cpp`: `defaultTouchTimerValue … ? 200 : 0` ms) und `RefreshRateSelector.cpp`:
`if (signals.touch && !hasExplicitVoteLayers) { … "Touch Boost" … Descending }` → bei expliziten Votes irgendeiner Layer entfällt SF-Touch-Boost. **[B]** (lineage-23.0)

**C5 Wer entscheidet.** SF `RefreshRateSelector` bewertet Votes ALLER sichtbaren Layer: `LayerVoteType {NoVote, Min, Max, Heuristic, ExplicitDefault, ExplicitExactOrMultiple,
ExplicitExact, ExplicitGte, ExplicitCategory}`; `LayerHistory.cpp`: unsichtbare Layer → `NoVote`; `LayerInfo.h`: `kMinFpsForFrequentLayer = 10_Hz` – "If a layer is infrequent, its
average refresh rate is disregarded in favor of a low refresh rate." Idle-Policy: `if (!signals.touch && signals.idle …) → Ascending (niedrigste Rate)`. Die IME-Layer konkurriert
mit App-Layer; der höchste/kompatibelste Wunsch gewinnt (DAD: "the system generally selecting the highest frame rate"; "other hints … from System UI components"). **[B]**
Ohne explizite Votes (also auch, wenn wir nichts setzen) schätzt SF die Layer-Rate heuristisch aus Präsentationszeiten; Updates ≥ 10 Hz zählen als "frequent" → der 15–20-fps-
Idle-Plan erzeugt einen Heuristic-Vote, der das Panel über die Idle-Rate halten kann; <10 fps nicht. **[B] Code, [P] Auswirkung**

**C6 Was passiert bei 20–30 fps auf 120-Hz-LTPO.** ARR (source.android.com, **nur Suchauszug**): "lets the display refresh rate adapt to the content frame rate using discrete VSync steps";
"ARR lets devices operate at rates lower than their maximum refresh rates, transitioning to higher rates only when essential"; HAL-Hinweis `notifyExpectedPresent` "so that the display can adapt its
self-refresh period"; `minFrameIntervalNs` = "max refresh rate". Android 11 Multiple-Refresh-Rate (Suchauszug): "SurfaceFlinger tries to set the refresh rate to something that is a multiplier"
(24 & 60 → 120 Hz). Panel-seitige Wiederholung/Taktabsenkung ist Treiber/Panel-spezifisch. **[P]** Ob das Xiaomi-15-Panel bei 20 fps auf 20/40/60 Hz oder per Frame-Repetition läuft: **[U]**.

**C7 OEM (Xiaomi/HyperOS).** Nur Sekundärquellen (Suchauszug): Xiaomi-FAQ: "screen lowers its refresh rate when content isn't changing"; GSMArena: Auto-Modus kann im Leerlauf auf 10 Hz;
Neowin: "Benutzerdefinierte Apps" erlaubt pro-App-120-Hz nur im festen-120-Hz-Modus. Kein Beleg zu Whitelists, die IME-Fenster betreffen. **[P]/[U]**

---
## D. Display-Energie

**D1 Refresh-Rate ↔ Energie.** Android (DAD `games/optimize/power`): "Higher refresh rates result in smoother user experiences … but increase the power consumption of the display panel."; ARR-Seite:
"For small animations such as progress bars … this high refresh rate is unnecessary, and results in high power consumption." **[B]** (qualitativ, keine Zahlen)
Zahlen, alle nur Suchauszug: OLED-Association-Review (LTPO-Galaxy-Klasse): 640 mW @120 Hz vs 465 mW @60 Hz (ca. 5 Jahre alt); eine Quelle: Backplane-Strom 35 mA @60 Hz → <3 mA @1 Hz
(nur Backplane); ein Erklärtext "1/120 der Refresh-Leistung" (Modell, keine Messung); OnePlus "etwa halbiert" (Herstellerangabe). **[P]** Keine DisplayMate-/Panel-Hersteller-Messung für 1 Hz
vs 10 Hz auf einem 2024/25-Panel gefunden. **[U] für das Xiaomi-15-Panel**

**D2 Statisches Bild vs. Animation.** Qualitativ plausibel (LTPO senkt bei unveränderter Szene; fortlaufende Updates verhindern das, C5/C6); eine zitierfähige Primärquelle mit Messwerten für "kleine
Animation hält LTPO auf hoher Rate" fand ich nicht. Die Xiaomi-FAQ (Suchauszug) bestätigt nur das Prinzip. **[P]**
Wichtig: Die Display-Rate gilt global für das ganze Panel (auch App-Bereich), nicht nur für 2 % der Fläche. Daher ist nicht die Animationsfläche, sondern die verlangte Rate der Hauptstellhebel. **[P]**

**D3 OLED-Leistung vs. Pixelhelligkeit.** Dong & Zhong, MobiSys 2011 (Chameleon, arXiv 1101.1240) und Dong/Choi/Zhong USENIX 2009: Leistung pro Pixel hängt von Farbe/Intensität ab (R/G/B
verschieden effizient); Modell nahe linear in (linearer) RGB-Intensität, plus konstanter Basisterm. Beide **nur Suchauszug** (arXiv blockiert). Dünne Linien auf Schwarz: Beitrag ∝ Fläche × Leuchtdichte, bei
~10 % Linienanteil von 70k px verschwindend gegenüber dem Rest des Panels. **[P]** (Schluss, nicht gemessen)

---
## E. CPU vs. GPU bei winzigen Lasten

**E1 Adreno-Idle/DVFS.** kgsl-Treiber (SM8450-Kernel, Snapdragon 8 Gen 1, RAW): `Kconfig`: `config QCOM_KGSL_IDLE_TIMEOUT … default 80` mit "This value decides after how long the GPU will go into
slumber. A higher value will mean that the GPU is powered ON for a longer duration which will have power costs."; `kgsl_timer()` → `KGSL_STATE_SLUMBER`; sysfs `idle_timer` (RW), `gpubusy`,
`gpu_busy_percentage`, `gpuclk`, `min_pwrlevel`, `max_pwrlevel`, `num_pwrlevels`, `force_clk_on` usw. (`kgsl_pwrctrl.c`). **[B]** für diesen Kernel; 8-Elite-Kernel/Kconfig kann abweichen **[U]**.
Schluss: bei Frame-Abstand < Idle-Timeout (80 ms ⇒ >12,5 fps) kommt die GPU nie in Slumber, sie bleibt (mindestens auf niedrigster Stufe) powered; ein 15–20-fps-Idle-Plan hält die GPU also dauerhaft wach,
während 5–10 fps sie zwischen Frames schlafen legen könnte. **[P]** (Gerät entscheidet; zusätzlich hält SF-Komposition die GPU nicht zwingend wach.)

**E2 Fixe Pro-Frame-Kosten.** Pipeline-Stationen: Choreographer-Wakeup → UI-Thread (Traversal, Recording) → RenderThread (Sync, GPU-Submit) → Buffer queue → SF (Commit/Latch) → HWC (Validate/Present) → Panel.
Quellen zeigen die Stationen (B1–B3), aber keine Mikrojoule pro Frame. **Keine direkte Quelle**, dass bei 70k Pixeln feste Kosten die Shaderkosten dominieren. **[U]** Der Schluss ist plausibel (ein Fragment-
Shader mit ~100 ALU-Ops × 70k px × 20 fps ist ≈ 0,14 GFLOP/s, winzig gegenüber GPU-Spitze), aber Aufwachen/Kontextwechsel lassen sich nicht aus Quellen beziffern. **[P]**

**E3 Google-Leitlinien.** Zitat ARR-Seite: "For small animations such as progress bars and audio visualizers, this high refresh rate is unnecessary, and results in high power consumption."
Games-Power-Seite: "If the display refresh rate is higher than the target frame rate … no benefit … only increased power consumption." Beides stützt "niedrige Rate", nicht "weniger Pixel". Keine
"vermeide Dauer-Animationen"-Zeile in der geprüften Android-Doku gefunden (DAD `topic/performance/power` ist dafür nur eine Einstiegsseite). **[B]** für Zitate, **[U]** für das Verbot.

**E4 CPU-Alternative.** Keine Quelle, die CPU-Rendering (z. B. 70k px Canvas-Software) energetisch gegen GPU vergleicht. **[U]**; Hinweis: eine Software-Bitmap müsste ohnehin als Textur hochgeladen werden (H2).

---
## F. Messwerkzeuge (nicht gerootetes Xiaomi 15)

**F1 Perfetto-Batteriezähler.** `docs/data-sources/battery-counters.md`: Zähler `batt.charge_uah`, `batt.capacity_pct`, `batt.current_ua` ("typically the average over a small window"), via IHealth-HAL; "The presence
and the resolution of these counters depends on the device manufacturer."; beim USB-Laden "you will likely observe a positive instantaneous current … This can make measurements in lab settings problematic". **[B]**
**F2 ODPM-Power-Rails.** Perfetto: "requires the dedicated hardware on the device. This hardware is not yet available on most production phones." (älterer Text); DAD Power Profiler (Stand 2026-03-06):
"ODPM … only available on Pixel 6 and subsequent Pixel devices running Android 10"; "Devices that don't support ODPM can offer … Coulomb counters and the battery gauge." Macrobenchmark `PowerMetric`:
"limited to physical Google Pixel 6, Pixel 6 Pro, and newer physical devices." Eine IPowerStats-HAL kann auch ein OEM implementieren (source.android.com, Suchauszug), für Xiaomi 15 kein Beleg. **[B] Pixel-only laut Google; [U] für Xiaomi 15**
**F3 BatteryManager.** `BATTERY_PROPERTY_CURRENT_NOW` (API 21): "Instantaneous battery current in microamperes … Positive values indicate net current entering the battery …, negative values … discharging." Einheiten/Vorzeichen-Abweichungen
einiger OEMs (mA statt µA, invertiertes Vorzeichen) sind aus Foren bekannt, keine Primärquelle gefunden. **[B] Definition, [U] OEM-Abweichung**
**F4 batterystats.** DAD: "Battery Historian is no longer actively maintained; if possible, … Power Profiler"; Batterystats ist ein Framework-Sammler (Wakelocks, Zustände, Schätzungen), keine Messung der Leistung einzelner Funktionen;
für Detailvergleiche zu grob (Schluss; Quelle: DAD `topic/performance/power/setup-battery-historian`; "less precise than external power monitors", Suchauszug einer Messstudie). **[P]**
**F5 Frame-Messung.** Perfetto FrameTimeline (`docs/data-sources/frametimeline.md`): "requires Android 12(S) or higher"; Module in SurfaceFlinger, zeigt Jank/Präsentationszeiten; Expected/Actual-Tracks je App mit mindestens einem Frame. `dumpsys gfxinfo <pkg> framestats`:
Dokumentseite in diesem Lauf nicht abgerufen. **[B] FrameTimeline, [U] gfxinfo-Details**
**F6 GPU-Zähler.** AGI (DAD `agi/supported-devices`, Stand 2026-02-26): Liste endet bei Pixel 7/Galaxy S21/OPPO/OnePlus 9R; **kein Xiaomi, kein Snapdragon 8 Elite**; "If the device is not listed, its GPU driver is most likely incompatible with AGI."
AGI-Seite (2026-09-18): "Android Performance Analyzer is the new recommended tool … public beta"; APA-Blog (2026-05): GPU-Zähler "across hardware from Qualcomm, Arm, Imagination, and Samsung", "Devices running Android 12+ will provide the best experience", System-Profiler auf Perfetto-Basis, "track battery and power consumption".
Geräteliste von APA nicht gefunden → Xiaomi 15 **[U]**. **[B] für AGI-Liste**
**F7 Snapdragon Profiler.** Qualcomm-Seiten sind JS-Apps (leer per curl), docs.qualcomm.com/developer.qualcomm.com blockiert. Nur Suchauszug: FAQ nennt ADB ≥1.0.40; fehlende Vulkan-Metriken "may be fixed by having ADB root access";
"Qualcomm Profiler" ist ein separates, neueres Werkzeug; Release Notes bis 2024.1. Ob auf nicht gerootetem Xiaomi 15 Echtzeit-GPU-/Energiemetriken laufen: **[U]**, Produktstatus 2026 **[U]**.
**F8 kgsl-sysfs ohne Root.** Knoten existieren (E1), ob `adb shell` (SELinux-Domäne shell) auf Xiaomi 15/Android 16 lesen darf: kein Beleg; Google verschärft GPU-Zugriffe (Suchauszug, Mali-bezogen). **[U]**, `adb shell cat /sys/class/kgsl/kgsl-3d0/gpubusy` einfach probieren; Alternative Perfetto `power/gpu_frequency` ftrace (Perfetto-Doku `gpu.md`, [B] als Datenquelle, Rechte unbekannt).
**F9 A/B-Methode.** Direkte Primärquelle für UI-Differenzen: keine. Verwertbar: Macrobenchmark-Doku: "lock screen brightness to a fixed value, maintain a stable device temperature, and close competing background processes";
Android-Doku "Measure component power" (Suchauszug): Flugmodus, konstante CPU-Last, Thermal-Throttling beachten, Farbe/Inhalt des Displays beeinflusst die Leistung; Studien (Suchauszug) mitteln ≥3 Wiederholungen,
externe Messgeräte sind Goldstandard, Software-Zähler ungenauer. Konkrete Minuten/Wiederholungszahlen: **[U]** (eigene Wahl, z. B. ABAB-Interleaving, ≥5 Paare, Median+IQR, Netz/Ladung aus, Helligkeit fest, Temperatur protokollieren = Schluss). **[P]**

---
## G. Technikreferenzen (je eine gute Quelle)

- **G1 Isolinien mit konstanter Breite.** Mit Ableitungen: Evan Wallace "Anti-Aliased Grid Shader" (madebyevan.com/shaders/grid/) und rreusser "adaptive contouring in fragment shaders" (rreusser.github.io/notebooks/…):
  Linienkoordinate d = |h − k·Δ| / |∇h|, Alpha = 1 − smoothstep(w, w+1px, d). Ohne Ableitungen (AGSL!): |∇h| analytisch aus der Höhenfunktion (Noise-Ableitungen) oder per 2 Zusatz-Auswertungen h(x+ε), h(y+ε), oder Gradient in 2 Kanälen vorberechnen. Beide **nur Suchauszug**. **[P]**
- **G2 Domain Warping.** Inigo Quilez, "Domain Warping" (iquilezles.org/articles/warp): "warp … distort the domain with another function g(p) before we evaluate f", z. B. fbm(p + 4·q). Nur Suchauszug (Host blockiert). **[P]**
- **G3 Analytische Wellen/Impulse.** Gedämpfte Radialwelle h(r,t)=A·e^(−γt)·e^(−r/ρ)·sin(k·r−ω·t) ist Standardformel; lineare Überlagerung mehrerer Impulse gilt (lineare Wellengleichung, Schluss). Primärquelle für genau diese Form nicht gefunden. **[U]/[P]**
  Zeit-unabhängige Feder: Ryan Juckett "Damped Springs" (ryanjuckett.com/damped-springs/): geschlossene Lösung für unter-/kritisch-/überdämpft, Dt-unabhängig (Suchauszug). **[P]**
- **G4 Marching Squares (CPU, Vektor-Isolinien).** Klassischer Algorithmus (Lorensen/Cline 1987 für 3D, 2D-Variante); in dieser Sitzung keine Primärquelle abgerufen. **[U]** (gut bekannt, hier ohne Zitat)
- **G5 Flow Maps.** Alex Vlachos (Valve), "Water Flow in Portal 2", SIGGRAPH 2010 Advances in Real-Time Rendering (advances.realtimerendering.com/s2010/…): zwei Phasen, zeitlich überblendet gegen Verzerrungsaufbau. Suchauszug. **[P]**

---
## H. Alternativen innerhalb von Android

**H1 Canvas/Paths auf Hardware-Canvas.** HWUI zeichnet über Skia GPU (A2). Eine Primärquelle zum internen Pfad-Rasterisierer (Tessellation vs. CPU-Maske) in dieser Skia-Version nicht gelesen. **[U]** Pro Frame ~20–40 Linienzüge neu aufzuzeichnen kostet UI-Thread-Zeit (Recording) und RT-Zeit; Anzahl Pfad-Segmente begrenzt das. **[P]**
**H2 Bitmap pro Frame.** Kein Zitat zu Upload-Kosten gefunden; 70k px × 4 B = 280 kB/Frame (Schluss), aber Texturupload = Treiber-/Speicherkopie plus Invalidate. **[U]/[P]**
**H3 SurfaceView.** DAD: "Provides a dedicated drawing surface … punches a hole in its window … it can have an impact on performance since a full alpha-blended composite will be performed each time the Surface changes." Eigene Layer
→ eigene Komposition in SF (B3). In einer IME: keine Doku-Aussage gefunden; Hardware-Beschleunigung: DAD IMS "Starting in API 21, hardware acceleration is always enabled on capable devices"; "system may automatically enable hardware
accelerated drawing for your IME … Make sure that your IME is able to handle either case." Ob SurfaceView im TYPE_INPUT_METHOD-Fenster zuverlässig funktioniert: **[U]** (Gerätetest). Eine eigene Surface darf außerdem selbst `Surface.setFrameRate` setzen (C2). **[B] Zitate, [U] IME-Eignung**
**H4 TextureView.** DAD: "TextureView can only be used in a hardware accelerated window. When rendered in software, TextureView will draw nothing."; Inhalt wird als externe Textur in den View-Baum gerendert (zusätzlicher Pass, bekannt höhere Latenz, kein Zitat gefunden). `setRequestedFrameRate` wird für TextureView/SurfaceView respektiert (ARR-Seite: "Frame rates explicitly set for TextureView and SurfaceView are respected"). **[B]/[P]**
**H5 RenderNode/RenderEffect/AVD.** DAD AGSL: RenderEffect "is more expensive than drawing a custom View" (setRenderEffect auf View + Kinder, RuntimeShader-Effect, API 31+/33). RenderNode (API 29): DAD "RenderNode is used to build hardware accelerated rendering hierarchies"; Eigenschaften (Translation, Alpha) ändern sich ohne Neuaufzeichnung. AnimatedVectorDrawable: "Starting from API 25 … runs on RenderThread" (Referenz, Suchauszug), sinnvoll für wenige animierbare Pfad-Eigenschaften, nicht für topografische Rauschfelder. **[B]/[P]**

---
## Kritische Bewertung des fremden Vorschlags (Befunde, keine Meinung)

1. "AGSL RuntimeShader" erfordert API 33; minSdk 21 braucht Fallback (A1). **[B]**
2. "analytic domain warping + fwidth-artige Linien": Ableitungen gibt es in AGSL nicht (A3); |∇h| muss selbst berechnet werden. **[B]**
3. "half-Precision für alles": für Zeit und Koordinaten ungeeignet (A5); Gewinn auf Adreno nicht garantiert. **[B]/[P]**
4. "Precomputed height map": 8-bit nearest/bilinear ohne Mipmaps (A7), Banding möglich; liefert aber keinen Gradient ohne weitere Taps. **[B]/[P]**
5. Adaptive Rate 15–20 fps idle: bei ≥10 fps zählt die Layer als "frequent" (C5), hält das Panel/GPU über Idle-Niveau (E1); die Fläche (2 %) senkt nur den Fragment-Teil. Energie-Stellhebel ist die Frame-Zahl und die vom Panel verlangte Rate, nicht die Pixelzahl. **[B] Code, [P] Auswirkung**
6. Rate explizit deckeln: `setRequestedFrameRate(LOW/NO_PREFERENCE)` auf der View und/oder `Window.setFrameRateBoostOnTouchEnabled(false)` (API 35, laut Google "not recommended") sind die dokumentierten Hebel. **[B]**

---
## Nur am Gerät entscheidbar
1. Läuft HWUI auf dem Xiaomi 15 mit skiagl oder skiavk; EGL_KHR_partial_update vorhanden (Teil-Damage real genutzt)?
2. Zeigt `Display.hasArrSupport()` true; welche Flag-Werte (`toolkitFrameRateBySize`, `…TouchBoost25q1`, `…Typing`) gelten auf HyperOS 3/Android 16?
3. Welche Panel-Rate stellt sich bei 15/20/30 fps Leertasten-Updates bei sonst statischer App ein (1/10/20/30/60/120 Hz)? Folgt HyperOS-eigene Logik anderen Regeln als AOSP?
4. Hält eine 2-%-Fläche mit 15–20 fps das Panel über der Idle-Rate (Perfetto: SF `TouchState`, Refresh-Rate-Track, Layer-Votes)?
5. Wirkt sich der IME-Touch-Boost (3 s) real aus; wie ändert `setFrameRateBoostOnTouchEnabled(false)` Rate und Tipp-Latenz?
6. Kompositionstyp der IME-Layer (Device vs. Client) und ob HWC Teil-Updates (Surface-Damage) wirklich für Power nutzt.
7. GPU-Slumber-/Idle-Timeout und Stromstufen des Adreno 830 (nicht der SM8450-Wert 80 ms); ob `/sys/class/kgsl/...` ohne Root lesbar ist.
8. Energiedifferenz in mW/µAh zwischen: kein Effekt, AGSL 20 fps, AGSL 60 fps, Canvas-Linien, Bitmap-Upload (nur A/B-Messung; ODPM auf Xiaomi 15 unbekannt, Batteriezähler ±OEM).
9. Anzahl fBm-Oktaven/Warp-Stufen, die ohne Jank in <2 ms RenderThread-Zeit passen; Shader-Kompilierzeit beim ersten Frame.
10. Ob `half` auf Adreno 830 echten fp16-Pfad nimmt und ob Banding/Wrap sichtbar wird (Zeit/Koordinaten).
11. Ob SurfaceView/TextureView im IME-Fenster (TYPE_INPUT_METHOD) stabil funktioniert und welche zusätzlichen Kompositionskosten entstehen.
12. Tippen-Latenz (Down→Pixel) mit/ohne Animation (Perfetto FrameTimeline + hochfrequente Kamera/Touch-Latenz-Messung).
13. Gleiche Messungen im thermischen Gleichgewicht (Panel- und SoC-Temperatur beeinflussen Rate/Takt).
14. Ob Xiaomi für die Tastatur-App oder die IME-Fenster eine Whitelist/Rate-Override (HyperOS "Benutzerdefinierte Apps") anwendet.
