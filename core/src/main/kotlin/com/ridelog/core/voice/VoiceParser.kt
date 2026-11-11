package com.ridelog.core.voice

import com.ridelog.core.model.Member

object KoreanNumberParser {
    private val nativeDigits = mapOf(
        '일' to 1L, '이' to 2L, '삼' to 3L, '사' to 4L, '오' to 5L,
        '육' to 6L, '칠' to 7L, '팔' to 8L, '구' to 9L,
    )
    private val smallUnits = mapOf('십' to 10L, '백' to 100L, '천' to 1_000L)
    private val bigUnits = mapOf('만' to 10_000L, '억' to 100_000_000L)
    val numeralChars: Set<Char> = nativeDigits.keys + smallUnits.keys + bigUnits.keys + ('0'..'9') + ','

    /** "3만 2천", "삼만이천", "32,000", "2만4천", "만", "오천" → 원 단위 정수. 해석 불가면 null. */
    fun parse(text: String): Long? {
        val s = text.filterNot { it.isWhitespace() || it == ',' }
        if (s.isEmpty() || s.any { it !in numeralChars }) return null
        var total = 0L
        var section = 0L
        var current = 0L
        var i = 0
        while (i < s.length) {
            val ch = s[i]
            when {
                ch.isDigit() -> {
                    var j = i
                    while (j < s.length && s[j].isDigit()) j++
                    current = s.substring(i, j).toLong()
                    i = j
                    continue
                }
                ch in nativeDigits -> current = nativeDigits.getValue(ch)
                ch in smallUnits -> {
                    section += (if (current == 0L) 1L else current) * smallUnits.getValue(ch)
                    current = 0
                }
                ch in bigUnits -> {
                    val block = section + current
                    total += (if (block == 0L) 1L else block) * bigUnits.getValue(ch)
                    section = 0; current = 0
                }
            }
            i++
        }
        val value = total + section + current
        return if (value > 0) value else null
    }
}

sealed interface Payer {
    data object Me : Payer
    data class Named(val member: Member) : Payer
    data object Unknown : Payer
}

data class ParsedExpense(val label: String, val amountWon: Long, val payer: Payer)

/** "Gleo야, 휴게소 간식 3만 2천원 내가 냈어" → ParsedExpense("휴게소 간식", 32000, Me) */
object VoiceExpenseParser {
    private val amountRegex = Regex("([0-9일이삼사오육칠팔구십백천만억,\\s]*[0-9일이삼사오육칠팔구십백천만억])\\s*원")
    private val wakePrefix = Regex("^\\s*(gleo|글레오|클레오)\\s*(야|아)?[,\\s]*", RegexOption.IGNORE_CASE)
    private val meWords = listOf("내가", "제가", "나가", "저가")
    private val trailingParticles = Regex("(으로|로|에|를|을|은|는|비용|값)?\\s*$")

    fun parse(utterance: String, members: List<Member>): ParsedExpense? {
        val text = utterance.replace(wakePrefix, "").trim()
        val match = amountRegex.findAll(text)
            .mapNotNull { m -> KoreanNumberParser.parse(m.groupValues[1])?.let { m to it } }
            .lastOrNull() ?: return null
        val (m, amount) = match
        val before = text.substring(0, m.range.first).trim()
        val after = text.substring(m.range.last + 1)
        val label = before.replace(trailingParticles, "").trim().ifEmpty { "기타 경비" }
        return ParsedExpense(label, amount, payerOf(after + " " + before, members))
    }

    private fun payerOf(text: String, members: List<Member>): Payer {
        val compact = text.replace(" ", "")
        if (meWords.any { compact.contains(it) }) return Payer.Me
        val named = members
            .sortedByDescending { it.name.length }
            .firstOrNull { member -> listOf("가", "이가", "이", "께서", "님이").any { compact.contains(member.name + it) } }
        return named?.let { Payer.Named(it) } ?: Payer.Unknown
    }
}

enum class VoiceCommand { START_TRIP, END_TRIP, CANCEL }

object VoiceCommandParser {
    fun parse(utterance: String): VoiceCommand? {
        val t = utterance.replace(" ", "")
        return when {
            t.contains("취소") -> VoiceCommand.CANCEL
            Regex("(여행|기록).*(끝|종료|마쳐)").containsMatchIn(t) || t.contains("도착했") -> VoiceCommand.END_TRIP
            Regex("(여행|기록).*시작").containsMatchIn(t) || t.contains("출발") -> VoiceCommand.START_TRIP
            else -> null
        }
    }
}
