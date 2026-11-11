package com.ridelog.core.voice

import com.ridelog.core.model.Member
import kotlin.math.roundToLong

object KoreanNumberParser {
    private val nativeDigits = mapOf(
        '일' to 1L, '이' to 2L, '삼' to 3L, '사' to 4L, '오' to 5L,
        '육' to 6L, '칠' to 7L, '팔' to 8L, '구' to 9L,
    )
    private val smallUnits = mapOf('십' to 10L, '백' to 100L, '천' to 1_000L)
    private val bigUnits = mapOf('만' to 10_000L, '억' to 100_000_000L)
    val numeralChars: Set<Char> = nativeDigits.keys + smallUnits.keys + bigUnits.keys + ('0'..'9') + ',' + '.'

    /**
     * "3만 2천", "삼만이천", "32,000", "2만4천", "만", "오천", "3.5만" → 원 단위 정수.
     * 단위 없이 숫자가 연달아 오는 경우("사오")처럼 해석이 모호하면 null.
     */
    fun parse(text: String): Long? {
        val s = text.filterNot { it.isWhitespace() || it == ',' }
        if (s.isEmpty() || s.any { it !in numeralChars }) return null
        var total = 0.0
        var section = 0.0
        var current = 0.0
        var hasCurrent = false
        var i = 0
        while (i < s.length) {
            val ch = s[i]
            when {
                ch.isDigit() || ch == '.' -> {
                    if (hasCurrent) return null
                    var j = i
                    while (j < s.length && (s[j].isDigit() || s[j] == '.')) j++
                    current = s.substring(i, j).toDoubleOrNull() ?: return null
                    hasCurrent = true
                    i = j
                    continue
                }
                ch in nativeDigits -> {
                    if (hasCurrent) return null
                    current = nativeDigits.getValue(ch).toDouble()
                    hasCurrent = true
                }
                ch in smallUnits -> {
                    section += (if (hasCurrent) current else 1.0) * smallUnits.getValue(ch)
                    current = 0.0; hasCurrent = false
                }
                ch in bigUnits -> {
                    val block = section + current
                    total += (if (block == 0.0) 1.0 else block) * bigUnits.getValue(ch)
                    section = 0.0; current = 0.0; hasCurrent = false
                }
            }
            i++
        }
        val value = (total + section + current).roundToLong()
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
    private val wakePrefix = Regex("^\\s*(gleo|글레오|클레오)\\s*(야|아)?[,\\s]*", RegexOption.IGNORE_CASE)
    private val meWords = listOf("내가", "제가", "나가", "저가")
    private val trailingParticles = Regex("(으로|로|에|를|을|은|는|비용|값)?\\s*$")

    /**
     * 금액은 "원" 바로 앞에서 공백 단위 토큰을 거꾸로 모아 만든다.
     * 토큰 전체가 숫자여야 금액에 포함되고, 붙어 쓴 "식사3만"처럼 섞인 토큰은
     * 아라비아 숫자로 시작하는 끝부분만 인정한다 → "식사 만원"이 4만원으로 읽히지 않는다.
     */
    fun parse(utterance: String, members: List<Member>): ParsedExpense? {
        val text = utterance.replace(wakePrefix, "").trim()
        val candidates = Regex("원").findAll(text).mapNotNull { m -> amountBefore(text, m.range.first) }.toList()
        val (start, end, amount) = candidates.lastOrNull() ?: return null
        val before = text.substring(0, start).trim()
        val after = text.substring(end + 1)
        val label = before.replace(trailingParticles, "").trim().ifEmpty { "기타 경비" }
        return ParsedExpense(label, amount, payerOf(after, before, members))
    }

    private fun amountBefore(text: String, wonIndex: Int): Triple<Int, Int, Long>? {
        val head = text.substring(0, wonIndex).trimEnd()
        val tokens = head.split(Regex("\\s+")).filter { it.isNotEmpty() }
        val picked = ArrayDeque<String>()
        for (token in tokens.asReversed()) {
            if (token.all { it in KoreanNumberParser.numeralChars }) {
                picked.addFirst(token)
                continue
            }
            // "식사3만" → "3만"
            val idx = token.indexOfFirst { it.isDigit() }
            if (idx > 0 && token.substring(idx).all { it in KoreanNumberParser.numeralChars }) picked.addFirst(token.substring(idx))
            break
        }
        if (picked.isEmpty()) return null
        val amountText = picked.joinToString(" ")
        val amount = KoreanNumberParser.parse(amountText) ?: return null
        val start = head.lastIndexOf(picked.first()).coerceAtLeast(0)
        return Triple(start, wonIndex, amount)
    }

    /** 금액 뒤 문장("민수가 냈어")을 먼저 보고, 이름이 나오면 "나"보다 우선한다. */
    private fun payerOf(after: String, before: String, members: List<Member>): Payer {
        val suffixes = listOf("가", "이가", "이", "께서", "님이")
        fun named(text: String): Member? {
            val compact = text.replace(" ", "")
            return members.sortedByDescending { it.name.length }
                .firstOrNull { m -> suffixes.any { compact.contains(m.name + it) } }
        }
        fun me(text: String) = meWords.any { w -> Regex("(^|\\s)$w").containsMatchIn(text) }
        named(after)?.let { return Payer.Named(it) }
        if (me(after)) return Payer.Me
        named(before)?.let { return Payer.Named(it) }
        if (me(before)) return Payer.Me
        return Payer.Unknown
    }
}

enum class VoiceCommand { START_TRIP, END_TRIP, CANCEL }

/** 짧은 명령 문장만 명령으로 본다 — 경비 문장 속 "출발", "도착했을 때"는 명령이 아니다. */
object VoiceCommandParser {
    private val end = Regex("^(여행|기록)(을|은)?(끝|끝내|종료|마쳐|마치)")
    private val start = Regex("^(여행|기록)(을|은)?시작")

    fun parse(utterance: String): VoiceCommand? {
        val t = utterance.replace(Regex("[\\s.,!?]"), "")
        if (t.length > 12 || Regex("[0-9]").containsMatchIn(t) || t.contains("원")) return null
        return when {
            t == "취소" || t == "취소해줘" -> VoiceCommand.CANCEL
            end.containsMatchIn(t) -> VoiceCommand.END_TRIP
            start.containsMatchIn(t) -> VoiceCommand.START_TRIP
            else -> null
        }
    }
}
