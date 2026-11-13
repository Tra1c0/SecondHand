// SPDX-License-Identifier: GPL-3.0-only
package dev.agentkeyboard.core

/**
 * Hand-picked words the CLDR keywords miss (stage 35): colloquial German, chat abbreviations and a
 * few English words, each with up to two emojis, best first. Fixed in code, never loaded from files.
 * Keys are matched folded ([EmojiKeywords.fold]) and by word form ([EmojiKeywords.stem]).
 */
object EmojiSynonyms {
    private val table: List<Pair<List<String>, List<String>>> = listOf(
        listOf("hdl", "hdgdl", "hdal", "ily", "lieb", "liebdich", "ilu", "xoxo") to listOf("❤️", "🥰"),
        listOf("geil", "krass", "lit", "fire", "hammer", "endgeil") to listOf("🔥", "🤩"),
        listOf("nice", "nais", "passt", "gebongt", "alles klar", "alright") to listOf("👌", "👍"),
        listOf("yay", "juhu", "jippie", "yippie", "woohoo", "endlich") to listOf("🥳", "🎉"),
        listOf("glückwunsch", "glückwünsche", "gratuliere", "gratulation", "congrats", "congratulations") to listOf("🎉", "🥳"),
        listOf("geburtstag", "bday", "birthday", "geburi") to listOf("🎂", "🎉"),
        listOf("ups", "hoppla", "oops", "sorry", "sry", "entschuldigung") to listOf("😅", "🙈"),
        listOf("danke", "dankeschön", "merci", "thx", "thanks", "dankbar") to listOf("🙏", "😊"),
        listOf("bussi", "küsschen", "kussi", "knutsch") to listOf("😘", "💋"),
        listOf("hmm", "hm", "grübel", "überleg", "überlegen") to listOf("🤔"),
        listOf("schade", "leider", "menno", "mist") to listOf("😕", "😞"),
        listOf("omg", "krasse", "wtf", "alter") to listOf("😱", "🤯"),
        listOf("lol", "xd", "haha", "hahaha", "lmao", "rofl") to listOf("😂", "🤣"),
        listOf("vegan", "veganer", "pflanzlich") to listOf("🌱", "🥦"),
        listOf("vegetarisch", "veggie") to listOf("🥗", "🥕"),
        listOf("metal", "headbangen", "moshpit", "mosh") to listOf("🤘", "🎸"),
        listOf("rock", "rocken", "konzert", "gig") to listOf("🎸", "🤘"),
        listOf("zocken", "gaming", "gamer", "konsole", "daddeln") to listOf("🎮", "🕹️"),
        listOf("kampfsport", "karate", "kickboxen", "mma", "judo", "kampfkunst") to listOf("🥋", "🥊"),
        listOf("chillen", "chill", "entspannt", "gechillt", "relaxen", "relax") to listOf("😌", "🛋️"),
        listOf("feierabend", "wochenende", "weekend") to listOf("🍻", "🎉"),
        listOf("urlaub", "ferien", "vacation") to listOf("🏖️", "✈️"),
        listOf("kotzen", "kotz", "würg", "eklig", "ekelhaft", "bäh") to listOf("🤮", "🤢"),
        listOf("lecker", "mjam", "yummy", "köstlich") to listOf("😋", "🤤"),
        listOf("tschüss", "tschüs", "ciao", "bye", "servus", "moin", "hallo", "hey", "hi", "huhu") to listOf("👋", "😊"),
        listOf("gn8", "gutenacht", "nachti", "nacht") to listOf("🌙", "😴"),
        listOf("müde", "gähn", "penne", "pennen", "schlafen") to listOf("😴", "🥱"),
        listOf("stark", "pumpen", "gym", "training", "trainieren") to listOf("💪", "🏋️"),
        listOf("prost", "cheers", "anstoßen") to listOf("🍻", "🥂"),
        listOf("antifa", "antifaschistisch") to listOf("✊"),
        listOf("solidarität", "solidarisch", "streik", "protest", "demo") to listOf("✊", "📢"),
        listOf("pride", "queer", "lgbt", "lgbtq") to listOf("🏳️‍🌈", "🏳️‍⚧️"),
        listOf("trans") to listOf("🏳️‍⚧️"),
        listOf("musik", "song", "lied") to listOf("🎵", "🎶"),
        listOf("singen", "gesang", "sänger", "sängerin") to listOf("🎤", "🎶"),
        listOf("ok", "okay", "oki", "okidoki", "jo", "jep", "yes") to listOf("👍", "👌"),
        listOf("nö", "nope", "nein") to listOf("🙅", "👎"),
        listOf("schnell", "eilig", "asap", "sofort") to listOf("⚡", "🏃"),
        listOf("idee", "einfall", "geistesblitz") to listOf("💡"),
        listOf("arbeit", "büro", "job", "arbeiten") to listOf("💼", "💻"),
        listOf("lernen", "lerne", "uni", "schule", "studium") to listOf("📚", "✏️"),
        listOf("spanisch", "español") to listOf("🇪🇸"),
        listOf("deutsch") to listOf("🇩🇪"),
        listOf("englisch", "english") to listOf("🇬🇧"),
        listOf("traurig", "heul", "snif", "schnief") to listOf("😢", "😭"),
        listOf("wütend", "sauer", "angepisst", "grr", "grrr") to listOf("😡", "😤"),
        listOf("glücklich", "froh", "freu", "freue", "happy") to listOf("😊", "😄"),
    )

    private val byWord: Map<String, List<String>>
    private val byStem: Map<String, List<String>>

    init {
        val words = HashMap<String, List<String>>()
        val stems = HashMap<String, List<String>>()
        for ((keys, emojis) in table) for (key in keys) {
            val folded = EmojiKeywords.fold(key)
            words.putIfAbsent(folded, emojis)
            val stem = EmojiKeywords.stem(folded)
            if (stem.length >= EmojiKeywords.MIN_STEM) stems.putIfAbsent(stem, emojis)
        }
        byWord = words
        byStem = stems
    }

    /** Every emoji the synonyms lead to. */
    val emojis: Set<String> get() = table.flatMapTo(HashSet()) { it.second }

    fun lookup(folded: String): List<String> = byWord[folded].orEmpty()

    fun lookupStem(stem: String): List<String> = byStem[stem].orEmpty()
}
