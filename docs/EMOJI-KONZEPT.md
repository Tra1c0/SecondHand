# Emoji-Ansicht und Emoji-Suche: Konzept (Etappe 93)

Auftrag des Owners (2026-11-22): „Die Emoji-Page und Suche sind komplett kaputt. Nicht nur braucht ein Emoji ewig, um eingefügt zu werden,
sondern das Feld ist nur auf 16 Felder begrenzt und blendet teils die naheliegendsten Ergebnisse aus. Das normale Emoji-Feld funktioniert nicht
als ein durchgängiges, sondern wie abgetrennte Bereiche. Bist du bei einem fertig mit Runterscrollen, musst du seitlich wischen. Das killt den Flow.“
Vorbild: Gboard. Nichts hier wurde auf einem Telefon gesehen.

## 1. Was er will, und was dem widerspricht

Will: ein Emoji in einem Zug finden und einsetzen. Suche: tippen, das naheliegendste Emoji steht vorn, alle passenden sind erreichbar, ein Tipp
setzt sofort ein. Ansicht: eine durchgehende Liste, Kategorien nur als Sprungmarken.

Widerspricht dem: ein Treffer, der fehlt, obwohl sein Name das Suchwort enthält („Herz“ ohne ❤️); eine feste Obergrenze; ein sichtbares Warten
nach dem Tipp; ein seitlicher Wechsel, um weiterzuscrollen; ein Sprung der Liste, wenn sich die Leiste der Kategorien bewegt.

## 2. Befund (aus dem Code, belegt)

| Symptom | Ursache | Stelle |
| --- | --- | --- |
| 16 Felder | Ergebnisfläche der Suche hat fest 2 Reihen (Hochformat; Querformat 1) à 8 Tasten | `EmojiSearchActivity.init`: `DynamicGridKeyboard.ofRowCount(…, 2, …)` |
| naheliegende Treffer fehlen | Suche über die unscharfe Tipp-Suche des Emoji-Wörterbuchs: höchstens 18 Kandidaten (`SuggestedWords.MAX_SUGGESTIONS`), Nicht-Emojis darunter werden verworfen, Rangfolge nach Tipp-Ähnlichkeit, nicht nach Namen | `EmojiSearchActivity.search`, `SingleDictionaryFacilitator.getSuggestions` |
| Einfügen dauert | das Emoji wird erst in `onStop` per Broadcast gesendet, also nach Schließ-Animation und Anhalten des Suchfensters | `EmojiSearchActivity.onStop`, `LatinIME.onEmojiSearchDone` |
| seitlich wischen | jede Kategorie ist eine eigene Seite eines `ViewPager2`, jede Seite scrollt nur in sich | `EmojiPalettesView.PagerAdapter` |

## 3. Plan

**93a Suche (zuerst, kleiner):**
1. Eigener Suchindex aus den Unicode-CLDR-Annotationen (Namen und Stichwörter, `annotations` und `annotationsDerived` für Flaggen und
   Zusammensetzungen) für de, en, es, als kompakte Asset-Datei je Sprache (Emoji, Name, Stichwörter). Lizenz: Unicode License V3, frei,
   Hinweis in `docs/BUILD.md`. Nur Emojis, die das Gerät darstellt (`SupportedEmojis`), Grundform ohne Hautfarbe (Varianten wie bisher per Langdruck).
2. Rangfolge: Name gleich Suchwort > ein Wort des Namens oder ein Stichwort gleich Suchwort > beginnt mit dem Suchwort > enthält es; bei
   Gleichstand zuerst zuletzt benutzte Emojis, dann die Reihenfolge von Unicode. Mehrere Suchwörter: alle müssen passen (UND). Groß-/Kleinschreibung
   und Umlaute egal („herz“ = „Herz“, „gluecklich“ = „glücklich“). Sprachen: Tastatursprache zuerst, dann die übrigen aktiven Sprachen.
   Ohne Index (fehlende Datei): die alte Wörterbuchsuche bleibt der Rückweg.
3. Keine Obergrenze: die Ergebnisfläche scrollt senkrecht, sichtbar wie bisher 2 Reihen (Hochformat).
4. Sofort einfügen: der Tipp sendet das Emoji sofort (vor `finish()`), `onStop` sendet nur noch, wenn nichts gesendet wurde; keine
   Schließ-Animation.

**93b durchgehende Ansicht (danach, größer):** alle Kategorien in einer senkrechten Liste mit Überschriften; die Kategorie-Leiste springt an die
Stelle und markiert beim Scrollen die sichtbare Kategorie; „Zuletzt benutzt“ oben. Eigenes Konzept-Kapitel vor dem Bau (Speicher, Tempo bei
~3.700 Emojis, Langdruck-Varianten, Löschen-Taste, Emoji-Mixer).

## 4. Abnahme-Zeilen 93a

| # | Ausgangslage beim Owner | Aktion | Erwartung | Gegenfall | Nachweis |
| --- | --- | --- | --- | --- | --- |
| 1 | deutsche Tastatur, Suche offen | „herz“ | ❤️ unter den ersten drei, mehr als 16 Treffer erreichbar per Scrollen | ❤️ fehlt, Ende bei 16 | Unit-Test Index, G |
| 2 | dto. | „lachen“, „daumen“, „feuer“, „flagge deutschland“ | 😂/😆, 👍, 🔥, 🇩🇪 vorn | nur unscharfe Treffer | Unit-Test |
| 3 | englische oder spanische Tastatur | „heart“, „corazón“ | ❤️ vorn | nur deutscher Index | Unit-Test |
| 4 | Treffer sichtbar | Tipp auf ein Emoji | steht im Feld, bevor das Suchfenster zu ist | Warten auf die Animation | G (Zeit bis Text im Feld), O |
| 5 | Treffer sichtbar | Zurück/Abbrechen | nichts eingefügt | Emoji trotzdem eingefügt | G |
| 6 | Suchwort ohne Treffer | „xyzq“ | leere Fläche, kein Absturz | Absturz | Unit-Test |
| 7 | Gerät stellt ein neues Emoji nicht dar | Suche | erscheint nicht als Kästchen | Tofu-Kästchen | Unit-Test (Filter) |
| 8 | Passwortfeld/Inkognito | Suche | wie bisher (kein Lernen, Zuletzt-benutzt-Regel wie bisher) | neue Speicherung | Codeprüfung |

G = Gerätetest im Emulator mit echten Tasten, O = nur der Owner am Telefon.
