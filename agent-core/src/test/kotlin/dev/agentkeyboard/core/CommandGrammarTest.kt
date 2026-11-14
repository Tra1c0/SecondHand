// SPDX-License-Identifier: GPL-3.0-only
package dev.agentkeyboard.core

import dev.agentkeyboard.core.CommandGrammar.Action
import dev.agentkeyboard.core.CommandGrammar.AppName
import dev.agentkeyboard.core.CommandGrammar.Area
import dev.agentkeyboard.core.CommandGrammar.Role
import dev.agentkeyboard.core.CommandGrammar.Target
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Stage 52: the unified command language, from typed words to readings. */
class CommandGrammarTest {
    private val whatsapp = AppName("com.whatsapp", "WhatsApp")
    private val business = AppName("com.whatsapp.w4b", "WhatsApp Business")
    private val duolingo = AppName("com.duolingo", "Duolingo")
    private val signal = AppName("org.thoughtcrime.securesms", "Signal")
    private val maps = AppName("com.google.android.apps.maps", "Google Maps")
    private val telegram = AppName("org.telegram.messenger", "Telegram")
    private val english = AppName("com.example.english", "English Grammar")

    private val matcher = LanguageMatcher(listOf("de", "en", "es").map { LanguageMatcher.Language(it, LanguageNames.classifiedAliases(it)) },
        listOf("lang", "snip", "clip", "guard", "mem", "group", "math"))

    private var groups = mapOf("messenger" to listOf(whatsapp.packageName, signal.packageName))
    private val vocabulary = object : CommandGrammar.Vocabulary {
        override val apps = listOf(whatsapp, business, duolingo, signal, maps, telegram, english)
        override val groups get() = this@CommandGrammarTest.groups
        override val snippets = listOf("adresse", "gruss")
        override fun languages(word: String, exactOnly: Boolean) = matcher.match(word, exactOnly)
    }

    /** Area names and the German second names, as the keyboard passes them. */
    private val areas = Area.entries.associateBy { it.id } + mapOf("sprache" to Area.LANG, "baustein" to Area.SNIP,
        "ablage" to Area.CLIP, "wachter" to Area.GUARD, "waechter" to Area.GUARD, "gedachtnis" to Area.MEM,
        "gedaechtnis" to Area.MEM, "gruppe" to Area.GROUP)

    private fun parse(text: String) = CommandGrammar.parse(text, '@', areas, vocabulary,
        claim = { word -> if (matcher.match(word).isNotEmpty()) Area.LANG else null })
    private fun single(text: String) = parse(text).also { assertEquals(1, it.size, "$text: $it") }.single()
    private fun step(text: String) = single(text).steps.single()
    private fun apps(step: CommandGrammar.Step) = step.targets.map { (it as Target.App).app }

    @Test fun ownerExamplesNeedNoMarks() {
        val set = step("@lang duolingo spanish")
        assertEquals(Area.LANG, set.area)
        assertNull(set.action) // no action word: with a value it sets
        assertEquals(listOf(duolingo), apps(set))
        assertEquals("es", set.language)
        assertFalse(set.legacy)
        assertEquals(listOf(Target.All), step("@lang apps").targets)
        val replace = step("@lang whatsapp german") // replaces "del …; set …"
        assertEquals(listOf(whatsapp), apps(replace))
        assertEquals("de", replace.language)
        val chain = single("@lang whatsapp german; duolingo spanish; @guard banking off")
        assertEquals(listOf(Area.LANG, Area.LANG, Area.GUARD), chain.steps.map { it.area })
        assertEquals(listOf("de", "es", null), chain.steps.map { it.language })
        assertEquals(Action.OFF, chain.steps[2].action)
        assertEquals(listOf("banking"), chain.steps[2].unknown) // no app of that name: shown as unknown
    }

    @Test fun wordsFindTheirSlotInAnyOrderAndWithTolerance() {
        for (text in listOf("@lang whatsapp german", "@lang german whatsapp", "@lang WhatsApp Deutsch", "@lang whtasapp germna",
                "@lang whatsapp deutsh", "@lang deutsch whatsap")) {
            val readings = parse(text)
            assertTrue(readings.isNotEmpty(), text)
            assertTrue(readings.all { it.steps.single().language == "de" }, text)
            assertTrue(readings.any { apps(it.steps.single()) == listOf(whatsapp) }, text)
        }
        // a started word needs three letters; two letters only match exactly
        assertEquals(listOf(duolingo), apps(step("@lang duo es")))
        assertEquals(listOf("du"), step("@lang du es").unknown)
        // a whole word of a longer name, and a name of several words
        assertEquals(listOf(maps), apps(step("@guard maps off")))
        val joined = step("@lang google maps english")
        assertEquals(listOf(maps), apps(joined))
        assertEquals("en", joined.language)
        // the same tolerance for German action words: from four letters a typo still counts
        assertEquals(Action.LIST, step("@lang zeieg").action)
        assertEquals(Action.DELETE, step("@lang whatsapp loeschen").action)
        assertEquals(Action.DELETE, step("@lang whatsapp lösch").action)
    }

    @Test fun anAmbiguousWordOffersBothReadings() {
        val readings = parse("@lang whatsap german")
        assertEquals(setOf(whatsapp, business), readings.map { apps(it.steps.single()).single() }.toSet())
        // an exact name wins over a started one
        assertEquals(listOf(whatsapp), apps(step("@lang whatsapp german")))
        // a language typed exactly wins over the start of an app ("English Grammar")
        assertEquals("en", step("@lang telegram english").language)
        // app: forces an app
        val forced = step("@lang app:english german")
        assertEquals(listOf(english), apps(forced))
        assertEquals("de", forced.language)
        assertEquals(listOf("english"), step("@lang app:english english").let { listOf(apps(it).single().label.substringBefore(' ').lowercase()) })
    }

    @Test fun chainsWithEverySeparatorKeepTheArea() {
        for (separator in listOf(";", " ;", " then", " dann", " →", "→", " ->")) {
            val reading = single("@lang whatsapp german$separator duolingo spanish")
            assertEquals(listOf("de", "es"), reading.steps.map { it.language }, separator)
            assertEquals(listOf(listOf(whatsapp), listOf(duolingo)), reading.steps.map { apps(it) }, separator)
            assertTrue(reading.words.any { it.role == Role.SEPARATOR }, separator)
        }
        // a new area word ends the area without a separator; a separator before it starts nothing
        val two = single("@lang whatsapp german @guard signal off")
        assertEquals(listOf(Area.LANG, Area.GUARD), two.steps.map { it.area })
        assertEquals(2, single("@lang whatsapp german; @guard signal off").steps.size)
        // an empty step after a separator is none
        assertEquals(1, single("@lang whatsapp german;").steps.size)
    }

    @Test fun germanAreaNamesAndActionsWorkLikeTheEnglishOnes() {
        val guard = step("@wächter signal aus")
        assertEquals(Area.GUARD, guard.area)
        assertEquals(Action.OFF, guard.action)
        assertEquals(listOf(signal), apps(guard))
        assertEquals(Area.GUARD, step("@waechter signal an").area)
        assertEquals(Action.ON, step("@waechter signal an").action)
        assertEquals(Area.MEM, step("@gedächtnis whatsapp aus").area)
        assertEquals(Action.LIST, step("@sprache zeig").action)
        assertEquals(Action.LIST, step("@sprache ?").action)
        assertEquals(Action.DELETE, step("@sprache whatsapp weg").action)
        assertEquals(Action.DELETE, step("@lang whatsapp -").action)
        assertEquals(listOf(Target.All), step("@sprache alle weg").targets)
        assertEquals(Action.ADD, step("@gruppe messenger neu telegram").action)
        assertEquals(Action.ADD, step("@group messenger + telegram").action)
    }

    @Test fun groupsAreTargetsAndHaveTheirOwnArea() {
        val lang = step("@lang messenger german")
        assertEquals(listOf(Target.Group("messenger", listOf(whatsapp.packageName, signal.packageName))), lang.targets)
        val set = step("@group messenger whatsapp signal telegram")
        assertNull(set.action)
        assertEquals("messenger", set.name)
        assertTrue(set.nameExists)
        assertEquals(listOf(whatsapp, signal, telegram), set.members)
        val fresh = step("@group social telegram signal")
        assertEquals("social", fresh.name)
        assertFalse(fresh.nameExists)
        assertEquals(listOf(telegram, signal), fresh.members)
        val remove = step("@group messenger del signal")
        assertEquals(Action.DELETE, remove.action)
        assertEquals(listOf(signal), remove.members)
        assertEquals(Action.DELETE, step("@group del messenger").action)
        assertTrue(step("@group del messenger").members.isEmpty())
        // a typo of an own group is that group; a new name has nothing to show
        assertEquals("messenger", step("@group mesenger").name)
        assertEquals(Action.LIST, step("@group list").action)
        assertNull(step("@group").name)
        // "all" is no member of a group, and no name either
        assertEquals(listOf("all"), step("@group messenger all").unknown)
        assertFalse(CommandGrammar.isValidGroupName("all"))
        assertFalse(CommandGrammar.isValidGroupName("lang"))
        assertFalse(CommandGrammar.isValidGroupName("x"))
        assertTrue(CommandGrammar.isValidGroupName("arbeit"))
        // all apps for the other areas
        assertEquals(listOf(Target.All), step("@guard all off").targets)
        assertTrue(step("@guard apps off").all)
    }

    @Test fun oldSyntaxStaysOld() {
        // the old language command for the current field
        val switch = step("@lang spanisch")
        assertTrue(switch.legacy)
        assertEquals("es", switch.language)
        assertTrue(single("@lang").legacy)
        assertTrue(single("@lang xyz").legacy) // unknown words alone: the old command decides (nothing)
        assertTrue(single("@lang spa").legacy)
        // the short forms are "@lang <language>"
        val claimed = step("@deutsch")
        assertTrue(claimed.legacy)
        assertEquals("de", claimed.language)
        assertEquals(Role.VALUE, single("@deutsch").words.single().role)
        // snippets and clips: only an action word as the first word is new
        assertTrue(step("@snip adresse").legacy)
        assertTrue(step("@snip adr").legacy)
        assertTrue(step("@snip einladung Name: Lena an der Tür").legacy)
        assertTrue(step("@clip termin").legacy)
        assertTrue(step("@clip").legacy)
        assertTrue(step("@clip on the way").legacy)
        assertEquals(Action.ON, step("@clip on").action)
        assertEquals(Action.OFF, step("@ablage aus").action)
        // a snippet called like an action word is still inserted
        val named = CommandGrammar.parse("@snip neu", '@', areas, object : CommandGrammar.Vocabulary {
            override val apps = emptyList<AppName>()
            override val snippets = listOf("Neu")
        }).single().steps.single()
        assertTrue(named.legacy)
    }

    @Test fun snippetsAreAddedAndDeleted() {
        val add = step("@snip add gruss Viele Grüße; bis dann")
        assertEquals(Action.ADD, add.action)
        assertEquals("gruss", add.name)
        assertTrue(add.nameExists) // replaces the snippet of that name
        assertEquals("Viele Grüße; bis dann", add.text) // separators belong to the text
        val fresh = step("@baustein neu tschuess Tschüss!")
        assertEquals("tschuess", fresh.name)
        assertFalse(fresh.nameExists)
        assertEquals("Tschüss!", fresh.text)
        val delete = step("@snip del adrese")
        assertEquals(Action.DELETE, delete.action)
        assertEquals("adresse", delete.name)
        assertTrue(delete.nameExists)
        assertEquals(listOf("nichts"), step("@snip del nichts").unknown)
        assertEquals(Action.LIST, step("@snip ?").action)
        // a new area word ends the text
        val chain = single("@snip add gruss Hallo; @lang whatsapp german")
        assertEquals("Hallo", chain.steps[0].text)
        assertEquals(Area.LANG, chain.steps[1].area)
    }

    @Test fun everyWordHasARoleForTheColours() {
        val text = "@lang whatsapp german; xyz @guard signal off"
        val reading = single(text)
        val roles = reading.words.map { text.substring(it.start, it.end) to it.role }
        assertEquals(listOf("@lang" to Role.AREA, "whatsapp" to Role.TARGET, "german" to Role.VALUE, ";" to Role.SEPARATOR,
            "xyz" to Role.UNKNOWN, "@guard" to Role.AREA, "signal" to Role.TARGET, "off" to Role.ACTION), roles)
        // a language makes no sense to delete: shown as unknown
        val delete = step("@lang whatsapp del german")
        assertNull(delete.language)
        assertEquals(listOf("german"), delete.unknown)
        // a second action word is unknown
        assertEquals(listOf("on"), step("@guard signal off on").unknown)
    }

    @Test fun theTokenizerSplitsSeparatorsThatStickToWords() {
        assertEquals(listOf("@lang", "a", ";", "b", "→", "c", "->", "d"),
            CommandGrammar.tokenize("@lang a;b→c->d").map { it.text })
        assertEquals(listOf(0 to 5, 6 to 7), CommandGrammar.tokenize("@lang a").map { it.start to it.end })
        assertTrue(parse("lang whatsapp").isEmpty()) // no area word at the start
    }

    @Test fun nameMatchUsesTheLanguageTolerance() {
        assertEquals(0, NameMatch.score("whatsapp", "WhatsApp"))
        assertEquals(1, NameMatch.score("whats", "WhatsApp"))
        assertEquals(1, NameMatch.score("maps", "Google Maps"))
        assertEquals(2, NameMatch.score("whtasapp", "WhatsApp"))
        assertNull(NameMatch.score("wh", "WhatsApp")) // too short to start a name
        assertNull(NameMatch.score("signl", "Telegram"))
        assertEquals(listOf("WhatsApp", "WhatsApp Business"), NameMatch.started("wh", listOf("WhatsApp Business", "WhatsApp", "Signal")))
    }
}
