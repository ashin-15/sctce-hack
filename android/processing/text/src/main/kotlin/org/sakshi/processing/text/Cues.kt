package org.sakshi.processing.text

/** Label vocabulary of the benchmark rules; [wire] is the benchmark spelling. */
public enum class ProducerLabel(public val wire: String) {
    INSULT("insult"),
    THREAT("threat"),
    SEXUAL_HARASSMENT("sexual_harassment"),
    CASTE_RELIGIOUS_SLUR("caste_religious_slur"),
    DOXXING("doxxing"),
    COERCIVE_CONTROL("coercive_control"),
}

/** One phrase that suggests [label] for human review; [language] names the list the phrase belongs to. */
public data class Cue(val label: ProducerLabel, val phrase: String, val language: LanguageHint) {
    init {
        require(phrase.isNotBlank()) { "Cue phrase must not be blank" }
    }
}

/** Versioned cue phrases; [reviewed] holds the languages whose cues a native speaker has signed off. */
public class CueList(public val version: String, public val cues: List<Cue>, public val reviewed: Set<LanguageHint>)

/** The phrases of the benchmark rules baseline. Synthetic-fixture derived and not native-speaker reviewed. */
public object BenchCueList {
    /** Exactly the benchmark `RULES` phrases, in benchmark order, with no language marked reviewed. */
    public val V1: CueList = CueList(
        version = "bench-rules-v1",
        cues = buildList {
            fun add(label: ProducerLabel, language: LanguageHint, vararg phrases: String) {
                phrases.forEach { add(Cue(label, it, language)) }
            }
            add(ProducerLabel.INSULT, LanguageHint.ENGLISH, "worthless", "idiot")
            add(ProducerLabel.INSULT, LanguageHint.HINDI, "बेकार", "मूर्ख")
            add(ProducerLabel.INSULT, LanguageHint.HINGLISH, "bekaar", "bewakoof")
            add(ProducerLabel.INSULT, LanguageHint.MALAYALAM, "കൊള്ളില്ല", "വിഡ്ഢി")
            add(ProducerLabel.INSULT, LanguageHint.MANGLISH, "kollilla", "viddhi")
            add(ProducerLabel.THREAT, LanguageHint.ENGLISH, "hurt you", "not be safe")
            add(ProducerLabel.THREAT, LanguageHint.HINDI, "चोट", "सुरक्षित नहीं")
            add(ProducerLabel.THREAT, LanguageHint.HINGLISH, "chot", "safe nahi")
            add(ProducerLabel.THREAT, LanguageHint.MALAYALAM, "ഉപദ്രവിക്കും", "സുരക്ഷിതമാകില്ല")
            add(ProducerLabel.THREAT, LanguageHint.MANGLISH, "upadravikkum", "safe aakilla")
            add(ProducerLabel.SEXUAL_HARASSMENT, LanguageHint.ENGLISH, "nude", "sexual messages")
            add(ProducerLabel.SEXUAL_HARASSMENT, LanguageHint.HINDI, "नग्न", "अश्लील")
            add(ProducerLabel.SEXUAL_HARASSMENT, LanguageHint.HINGLISH, "ashleel")
            add(ProducerLabel.SEXUAL_HARASSMENT, LanguageHint.MALAYALAM, "നഗ്ന", "അശ്ലീല")
            add(ProducerLabel.SEXUAL_HARASSMENT, LanguageHint.MANGLISH, "ashleela")
            add(ProducerLabel.CASTE_RELIGIOUS_SLUR, LanguageHint.ENGLISH, "caste are dirty", "religion makes you inferior")
            add(ProducerLabel.CASTE_RELIGIOUS_SLUR, LanguageHint.HINDI, "जाति", "धर्म")
            add(ProducerLabel.CASTE_RELIGIOUS_SLUR, LanguageHint.HINGLISH, "jaati", "dharm")
            add(ProducerLabel.CASTE_RELIGIOUS_SLUR, LanguageHint.MALAYALAM, "ജാതി", "മതം")
            add(ProducerLabel.CASTE_RELIGIOUS_SLUR, LanguageHint.MANGLISH, "jaathi", "matham")
            add(ProducerLabel.DOXXING, LanguageHint.ENGLISH, "publish")
            add(ProducerLabel.DOXXING, LanguageHint.HINDI, "प्रकाशित")
            add(ProducerLabel.DOXXING, LanguageHint.MALAYALAM, "പരസ്യമാക്കും")
            add(ProducerLabel.COERCIVE_CONTROL, LanguageHint.ENGLISH, "password")
            add(ProducerLabel.COERCIVE_CONTROL, LanguageHint.HINDI, "पासवर्ड")
            add(ProducerLabel.COERCIVE_CONTROL, LanguageHint.MALAYALAM, "പാസ്‌വേഡ്")
        },
        reviewed = emptySet(),
    )
}
