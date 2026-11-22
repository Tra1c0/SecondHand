// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.keyboard.emoji

import android.app.Application
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Stage 93a: the emoji search by name and keyword (acceptance rows 1-3, 6, 7 of docs/EMOJI-KONZEPT.md). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class EmojiSearchIndexTest {
    private val app: Application get() = RuntimeEnvironment.getApplication()
    private fun search(q: String, langs: List<String> = listOf("de", "en", "es")) = EmojiSearchIndex.search(app, q, langs)
    private fun base(e: String) = e.replace("️", "")

    @Test fun theRedHeartIsAmongTheFirstThreeAndThereAreMoreThanSixteenHits() {
        val hits = search("herz")
        assertTrue(hits.take(3).toString(), base("❤️") in hits.take(3).map(::base))
        assertTrue("hits: ${hits.size}", hits.size > 16)
    }

    @Test fun obviousGermanWordsComeFirst() {
        assertEquals("👍", search("daumen").first())
        assertTrue(search("feuer").take(3).toString(), "🔥" in search("feuer").take(3))
        assertEquals("🇩🇪", search("flagge deutschland").first())
        val laugh = search("lachen").take(5)
        assertTrue(laugh.toString(), laugh.any { it == "😂" || it == "😆" || it == "😀" })
    }

    @Test fun caseUmlautsAndAccentsDoNotMatter() {
        assertEquals(search("glücklich"), search("gluecklich"))
        assertEquals(search("Herz"), search("herz"))
        assertTrue(base("❤️") in search("corazon", listOf("es")).take(3).map(::base))
    }

    @Test fun otherLanguagesFindTheirWords() {
        assertTrue(base("❤️") in search("heart", listOf("en")).take(3).map(::base))
        assertTrue(base("❤️") in search("corazón", listOf("es")).take(3).map(::base))
    }

    @Test fun nonsenseFindsNothingAndUnsupportedEmojisAreLeftOut() {
        assertTrue(search("xyzq").isEmpty())
        assertTrue(search("").isEmpty())
        val all = EmojiSearchIndex.search(app, "herz")
        val without = EmojiSearchIndex.search(app, "herz", supported = { it != all.first() })
        assertTrue(all.first() !in without)
    }
}
