// SPDX-License-Identifier: GPL-3.0-only
package dev.agentkeyboard.core

import java.util.Locale

/**
 * Stage 108 (owner, 2026-11-26: "gibt's, wird's, dreht's … werden nur vorgeschlagen"): a German verb form with "es" shortened
 * and typed without the apostrophe ("gibts", "wirds", "drehts", "gehts", "wars", "hats") gets it ("gibt's"). Productive, not a
 * list: the typed word ends in "s", and the part before it is a finite German verb form that the main dictionary knows, either
 * one of the common irregular forms ([irregular]) or a regular form ending in "t" ([GermanVerbForms]). Whether the typed word
 * itself is a word (nichts, rechts, stets, Amts, Guts: then nothing happens) is the caller's question. Pure, keeps nothing.
 */
object GermanShortForms {
    const val MIN_LETTERS = 3

    /** Common irregular finite forms (present and past, first and third person) that take "'s". */
    private val irregular = setOf(
        "ist", "war", "wär", "hat", "hab", "hatt", "wird", "gibt", "gab", "tut", "tat", "kann", "konnt", "soll", "will", "darf",
        "mag", "muss", "ging", "kam", "sah", "lag", "sieht", "nimmt", "läuft", "lief", "hilft", "weiß", "hält", "hielt",
        "fällt", "fiel", "steht", "stand", "nahm", "kriegt",
    )

    /**
     * The form with the apostrophe ("gibt's", lower case; the caller restores a capital), or null. [typed] in lower case
     * letters. [isGermanWord] answers whether the German main dictionary knows a lower case word; [isInfinitive] answers from
     * the bundled verb list.
     */
    @JvmStatic
    fun target(typed: String, isGermanWord: (String) -> Boolean, isInfinitive: (String) -> Boolean): String? {
        if (typed.length < MIN_LETTERS + 1 || !typed.endsWith("s") || !typed.all { it.isLetter() && it.isLowerCase() }) return null
        val stem = typed.dropLast(1).lowercase(Locale.GERMAN)
        val finite = stem in irregular || (stem.endsWith("t") && GermanVerbForms.isRegularForm(stem, isInfinitive))
        if (!finite || !isGermanWord(stem)) return null
        return "$stem's"
    }
}
