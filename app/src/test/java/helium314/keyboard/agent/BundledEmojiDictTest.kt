// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent

import android.app.Application
import helium314.keyboard.latin.dictionary.Dictionary
import helium314.keyboard.latin.dictionary.DictionaryFactory
import helium314.keyboard.latin.utils.DictionaryInfoUtils
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import java.util.Locale
import kotlin.test.*

/** Emoji dictionaries for German, English and Spanish ship in assets, so no download is needed. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class BundledEmojiDictTest {
    private val app: Application get() = RuntimeEnvironment.getApplication()

    @Before fun clean() {
        File(DictionaryInfoUtils.getWordListCacheDirectory(app)).deleteRecursively()
    }

    @Test fun theOwnersLanguagesAreBundled() {
        val assets = DictionaryInfoUtils.getAssetsDictionaryList(app).orEmpty().toSet()
        assertTrue(setOf("emoji_de.dict", "emoji_en.dict", "emoji_es.dict").all { it in assets })
    }

    @Test fun anEmojiLookupExtractsTheBundledDictionaryForTheBestLocale() {
        val german = DictionaryInfoUtils.getCachedDictForLocaleAndType(Locale.GERMANY, Dictionary.TYPE_EMOJI, app)
        assertNotNull(german)
        assertEquals("emoji.dict", german.name)
        assertEquals(app.assets.open("dicts/emoji_de.dict").use { it.readBytes() }.size.toLong(), german.length())
        val us = DictionaryInfoUtils.getCachedDictForLocaleAndType(Locale.US, Dictionary.TYPE_EMOJI, app)
        assertEquals(app.assets.open("dicts/emoji_en.dict").use { it.readBytes() }.size.toLong(), us!!.length())
        // a language without a bundled emoji dictionary still has none
        assertNull(DictionaryInfoUtils.getCachedDictForLocaleAndType(Locale.JAPANESE, Dictionary.TYPE_EMOJI, app))
        // other types are not extracted by this path
        File(DictionaryInfoUtils.getWordListCacheDirectory(app)).deleteRecursively()
        assertNull(DictionaryInfoUtils.getCachedDictForLocaleAndType(Locale.GERMANY, Dictionary.TYPE_MAIN, app))
    }

    @Test fun anExtractedEmojiDictionaryIsReusedNotRewritten() {
        val first = DictionaryInfoUtils.getCachedDictForLocaleAndType(Locale.GERMANY, Dictionary.TYPE_EMOJI, app)!!
        first.setLastModified(1_000_000L)
        repeat(3) {
            val again = DictionaryInfoUtils.getCachedDictForLocaleAndType(Locale.GERMANY, Dictionary.TYPE_EMOJI, app)
            assertEquals(first, again)
        }
        assertEquals(1_000_000L, first.lastModified(), "the mapped file must not be written again")
        assertTrue(first.parentFile!!.listFiles()!!.none { it.name.endsWith(".tmp") })
    }

    @Test fun bundledEmojiDictionariesAddNoLanguages() {
        DictionaryInfoUtils.getCachedDictForLocaleAndType(Locale.GERMANY, Dictionary.TYPE_EMOJI, app)
        val locales = helium314.keyboard.latin.utils.getDictionaryLocales(app)
        // only main dictionaries count: the cache folder holding just the extracted emoji.dict adds nothing
        assertFalse(locales.any { it.toLanguageTag() == "de-DE" })
        assertTrue(locales.any { it.language == "de" })
    }

    @Test fun bundledEmojiDictionariesFollowTheEmojiSetting() {
        val (_, withoutEmoji) = DictionaryFactory.getAvailableDictsForLocale(Locale.GERMANY, app, false)
        assertTrue(withoutEmoji.none { it.startsWith("emoji_") })
        assertTrue(withoutEmoji.any { it.startsWith("main_") })
        val (_, withEmoji) = DictionaryFactory.getAvailableDictsForLocale(Locale.GERMANY, app, true)
        assertTrue("emoji_de.dict" in withEmoji)
    }
}
