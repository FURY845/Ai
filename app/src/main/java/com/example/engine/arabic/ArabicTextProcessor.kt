package com.example.engine.arabic

import kotlin.math.max
import kotlin.math.min

object ArabicTextProcessor {

    // Common Arabic Diacritics (Harakat)
    private val DIACRITICS_REGEX = Regex("[\\u064B-\\u065F\\u0670\\u06D6-\\u06DC\\u06DF-\\u06E8\\u06EA-\\u06ED]")
    private val TATWEEL_REGEX = Regex("\\u0640")
    private val PUNCTUATION_REGEX = Regex("[\\p{Punct}،؛؟«»\"'()\\[\\]{}<>ـ]+")
    private val EXTRA_SPACES_REGEX = Regex("\\s+")

    // Egyptian Curriculum Stopwords (filtered for TF-IDF / Search indexing)
    val STOPWORDS: Set<String> = setOf(
        "في", "من", "على", "إلى", "الى", "عن", "مع", "هذا", "هذه", "ذلك", "تلك", "هؤلاء",
        "الذي", "التي", "الذين", "اللاتي", "اللواتي", "كان", "كانت", "يكون", "تكون", "كانوا",
        "أن", "ان", "إن", "إنما", "انما", "هو", "هي", "هم", "هن", "نحن", "أنا", "انت", "أنت",
        "لقد", "قد", "ثم", "بل", "لكن", "لكنها", "لكنه", "أو", "او", "أم", "ام", "حتى",
        "إذا", "اذا", "إذ", "اذ", "لو", "لما", "ما", "ماذا", "منذ", "حيث", "بين", "بينما",
        "كل", "بعض", "غير", "سوى", "مثل", "نحو", "دون", "أمام", "خلف", "فوق", "تحت",
        "عند", "لدى", "فقط", "جدا", "أيضا", "ايضا", "كذلك", "هناك", "هنا", "له", "لها",
        "لهم", "به", "بها", "بهم", "فيه", "فيها", "فيهم", "منه", "منها", "منهم", "عليه",
        "عليها", "عليهم", "إليه", "إليها", "إليهم"
    )

    /**
     * Normalizes Arabic text for high-accuracy search, comparison, and embedding:
     * - Strips diacritics / tashkeel
     * - Normalizes all forms of Hamza (أ, إ, آ, ٱ -> ا)
     * - Normalizes Taa Marbuta (ة -> ه)
     * - Normalizes Alef Maksura (ى -> ي)
     * - Normalizes Waw Hamza and Yaa Hamza (ؤ -> و, ئ -> ي)
     * - Strips Tatweel (Kashida)
     * - Replaces Eastern Arabic numerals (٠-٩) with standard digits (0-9)
     * - Normalizes whitespace and lowers any Latin characters
     */
    fun normalize(text: String): String {
        if (text.isBlank()) return ""

        var result = text

        // 1. Remove Tashkeel / Harakat
        result = DIACRITICS_REGEX.replace(result, "")

        // 2. Remove Tatweel (ـ)
        result = TATWEEL_REGEX.replace(result, "")

        // 3. Convert Eastern Arabic numerals to standard 0-9
        result = convertArabicNumeralsToStandard(result)

        // 4. Character substitutions
        val sb = StringBuilder(result.length)
        for (ch in result) {
            when (ch) {
                'أ', 'إ', 'آ', 'ٱ', 'ٵ', 'ٴ' -> sb.append('ا')
                'ة' -> sb.append('ه')
                'ى' -> sb.append('ي')
                'ؤ' -> sb.append('و')
                'ئ' -> sb.append('ي')
                'ء' -> sb.append("") // remove isolated hamza for search matching
                '\u0671' -> sb.append('ا') // Alef Wasla
                '\u0670' -> sb.append('ا') // Dagger Alef
                else -> sb.append(ch.lowercaseChar())
            }
        }

        // 5. Clean punctuation and multiple spaces
        val cleaned = PUNCTUATION_REGEX.replace(sb.toString(), " ")
        return EXTRA_SPACES_REGEX.replace(cleaned, " ").trim()
    }

    /**
     * Light Stemmer: removes common Arabic affixes (prefixes & suffixes)
     */
    fun lightStem(token: String): String {
        var word = normalize(token)
        if (word.length <= 3) return word

        // Remove prefixes
        if (word.startsWith("وال") && word.length > 5) word = word.substring(3)
        else if (word.startsWith("فال") && word.length > 5) word = word.substring(3)
        else if (word.startsWith("كال") && word.length > 5) word = word.substring(3)
        else if (word.startsWith("بال") && word.length > 5) word = word.substring(3)
        else if (word.startsWith("لل") && word.length > 4) word = word.substring(2)
        else if (word.startsWith("ال") && word.length > 4) word = word.substring(2)
        else if ((word.startsWith("و") || word.startsWith("ف") || word.startsWith("ب") || word.startsWith("ك") || word.startsWith("ل") || word.startsWith("س")) && word.length > 4) {
            word = word.substring(1)
        }

        // Remove suffixes
        if (word.endsWith("هما") && word.length > 5) word = word.substring(0, word.length - 3)
        else if (word.endsWith("هم") && word.length > 4) word = word.substring(0, word.length - 2)
        else if (word.endsWith("هن") && word.length > 4) word = word.substring(0, word.length - 2)
        else if (word.endsWith("ها") && word.length > 4) word = word.substring(0, word.length - 2)
        else if (word.endsWith("كم") && word.length > 4) word = word.substring(0, word.length - 2)
        else if (word.endsWith("نا") && word.length > 4) word = word.substring(0, word.length - 2)
        else if (word.endsWith("ين") && word.length > 4) word = word.substring(0, word.length - 2)
        else if (word.endsWith("ون") && word.length > 4) word = word.substring(0, word.length - 2)
        else if (word.endsWith("ات") && word.length > 4) word = word.substring(0, word.length - 2)
        else if (word.endsWith("يه") && word.length > 4) word = word.substring(0, word.length - 2)
        else if (word.endsWith("ي") && word.length > 3) word = word.substring(0, word.length - 1)
        else if (word.endsWith("ه") && word.length > 3) word = word.substring(0, word.length - 1)

        return word
    }

    /**
     * Extracts indexed tokens from Arabic text: normalized and stemmed, non-stopwords
     */
    fun tokenize(text: String, stem: Boolean = true): List<String> {
        val normalized = normalize(text)
        if (normalized.isBlank()) return emptyList()

        return normalized.split(" ")
            .map { it.trim() }
            .filter { it.length >= 2 && !STOPWORDS.contains(it) }
            .map { if (stem) lightStem(it) else it }
            .filter { it.isNotBlank() }
    }

    /**
     * Splits text into meaningful semantic sentence chunks
     */
    fun splitIntoPassages(text: String, maxWordsPerPassage: Int = 120, overlapWords: Int = 25): List<String> {
        val clean = text.trim()
        if (clean.isBlank()) return emptyList()

        val words = clean.split(EXTRA_SPACES_REGEX)
        if (words.size <= maxWordsPerPassage) {
            return listOf(clean)
        }

        val passages = mutableListOf<String>()
        var start = 0
        while (start < words.size) {
            val end = min(start + maxWordsPerPassage, words.size)
            val chunk = words.subList(start, end).joinToString(" ")
            passages.add(chunk)
            if (end >= words.size) break
            start += (maxWordsPerPassage - overlapWords)
        }
        return passages
    }

    /**
     * Levenshtein Distance for Arabic typo tolerance
     */
    fun levenshteinDistance(s1: String, s2: String): Int {
        val dp = Array(s1.length + 1) { IntArray(s2.length + 1) }

        for (i in 0..s1.length) dp[i][0] = i
        for (j in 0..s2.length) dp[0][j] = j

        for (i in 1..s1.length) {
            for (j in 1..s2.length) {
                val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                dp[i][j] = min(
                    dp[i - 1][j] + 1,
                    min(dp[i][j - 1] + 1, dp[i - 1][j - 1] + cost)
                )
            }
        }
        return dp[s1.length][s2.length]
    }

    /**
     * Calculates fuzzy similarity between two Arabic strings (0.0 to 1.0)
     */
    fun fuzzySimilarity(s1: String, s2: String): Float {
        val n1 = normalize(s1)
        val n2 = normalize(s2)
        if (n1 == n2) return 1.0f
        if (n1.isEmpty() || n2.isEmpty()) return 0.0f

        val dist = levenshteinDistance(n1, n2)
        val maxLen = max(n1.length, n2.length)
        return 1.0f - (dist.toFloat() / maxLen.toFloat())
    }

    /**
     * Converts Eastern Arabic numerals (٠١٢٣٤٥٦٧٨٩) to standard (0123456789)
     */
    private fun convertArabicNumeralsToStandard(text: String): String {
        val sb = StringBuilder(text.length)
        for (ch in text) {
            when (ch) {
                '٠' -> sb.append('0')
                '١' -> sb.append('1')
                '٢' -> sb.append('2')
                '٣' -> sb.append('3')
                '٤' -> sb.append('4')
                '٥' -> sb.append('5')
                '٦' -> sb.append('6')
                '٧' -> sb.append('7')
                '٨' -> sb.append('8')
                '٩' -> sb.append('9')
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }
}
