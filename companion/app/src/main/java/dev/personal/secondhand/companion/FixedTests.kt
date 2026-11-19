package dev.personal.secondhand.companion

/** One of the two built-in tests. The texts are fixed and German; no user text ever goes to the model in stage 80. */
data class FixedTest(
    val id: Int,
    val title: String,
    val systemInstruction: String,
    val userText: String,
    val maxTokens: Int,
    /** The exact expected answer, or null when only the speed is of interest. */
    val expected: String?,
)

object FixedTests {
    // two spelling mistakes: "Spatziergang" and "dannach"
    val SENTENCE = FixedTest(
        id = 2,
        title = "Test 2: festen Satz korrigieren",
        systemInstruction = "Korrigiere Rechtschreibung und Grammatik. Gib nur den korrigierten Satz aus.",
        userText = "Wir haben gestern einen langen Spatziergang gemacht und dannach Kaffee getrunken.",
        maxTokens = 96,
        expected = "Wir haben gestern einen langen Spaziergang gemacht und danach Kaffee getrunken.",
    )

    // about 80 words, curt on purpose
    val PARAGRAPH = FixedTest(
        id = 3,
        title = "Test 3: längerer Absatz",
        systemInstruction = "Schreibe den Absatz kürzer und freundlicher um. Gib nur den neuen Text aus.",
        userText = "Sehr geehrte Frau Berger, ich muss Ihnen leider mitteilen, dass Ihre Bestellung vom letzten " +
            "Montag nicht rechtzeitig geliefert werden konnte, weil unser Lager derzeit überlastet ist und " +
            "mehrere Lieferanten ihre Waren verspätet geschickt haben. Wir können noch nicht sagen, wann " +
            "genau die Ware eintrifft, rechnen aber mit etwa zwei Wochen. Falls Sie nicht so lange warten " +
            "wollen, müssen Sie die Bestellung selbst stornieren. Das Formular dafür finden Sie auf unserer " +
            "Webseite. Mit freundlichen Grüßen, Ihr Kundendienst",
        maxTokens = 192,
        expected = null,
    )

    /** Fragments of the fixed texts: log lines containing one are dropped from the report. */
    fun forbiddenLogFragments(): List<String> =
        listOf("Spatziergang", "Spaziergang", "dannach", "Berger", "Kundendienst", "Lieferanten", "Bestellung vom", "Rechtschreibung und Grammatik")
}
