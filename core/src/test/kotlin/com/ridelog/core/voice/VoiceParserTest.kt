package com.ridelog.core.voice

import com.ridelog.core.model.Member
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VoiceParserTest {
    private val members = listOf(Member("me", "지훈"), Member("m", "민수"), Member("s", "서연"))

    @Test fun `korean amounts`() {
        mapOf(
            "3만 2천" to 32_000L, "삼만 이천" to 32_000L, "32,000" to 32_000L, "2만4천" to 24_000L,
            "오천" to 5_000L, "만" to 10_000L, "십만" to 100_000L, "천오백" to 1_500L, "1만 5천" to 15_000L,
        ).forEach { (text, expected) -> assertEquals(text, expected, KoreanNumberParser.parse(text)) }
    }

    @Test fun `rejects non numbers`() {
        assertNull(KoreanNumberParser.parse("간식"))
        assertNull(KoreanNumberParser.parse(""))
    }

    @Test fun `expense with me as payer`() {
        val p = VoiceExpenseParser.parse("간식 3만원 내가 냈어", members)!!
        assertEquals("간식", p.label)
        assertEquals(30_000L, p.amountWon)
        assertEquals(Payer.Me, p.payer)
    }

    @Test fun `wake word and compound amount`() {
        val p = VoiceExpenseParser.parse("Gleo야, 휴게소 간식 3만 2천원 내가 냈어", members)!!
        assertEquals("휴게소 간식", p.label)
        assertEquals(32_000L, p.amountWon)
    }

    @Test fun `named payer`() {
        val p = VoiceExpenseParser.parse("주차비 만원 민수가 냈어", members)!!
        assertEquals("주차비", p.label)
        assertEquals(10_000L, p.amountWon)
        assertEquals(Payer.Named(members[1]), p.payer)
    }

    @Test fun `stt spacing variants`() {
        assertEquals(24_000L, VoiceExpenseParser.parse("커피 2만 4천 원 제가 냈어요", members)!!.amountWon)
        assertEquals(32_000L, VoiceExpenseParser.parse("저녁 32,000원", members)!!.amountWon)
        assertEquals("저녁", VoiceExpenseParser.parse("저녁으로 5만원", members)!!.label)
    }

    @Test fun `unknown payer and missing amount`() {
        assertEquals(Payer.Unknown, VoiceExpenseParser.parse("톨게이트 4천원", members)!!.payer)
        assertNull(VoiceExpenseParser.parse("간식 샀어", members))
    }

    @Test fun `commands`() {
        assertEquals(VoiceCommand.START_TRIP, VoiceCommandParser.parse("여행 시작"))
        assertEquals(VoiceCommand.END_TRIP, VoiceCommandParser.parse("여행 끝"))
        assertEquals(VoiceCommand.END_TRIP, VoiceCommandParser.parse("기록 종료해줘"))
        assertEquals(VoiceCommand.CANCEL, VoiceCommandParser.parse("취소"))
        assertNull(VoiceCommandParser.parse("간식 3만원"))
    }
}
