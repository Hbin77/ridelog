package com.ridelog.core.share

import com.ridelog.core.fare.Settlement
import com.ridelog.core.model.Member
import java.text.NumberFormat
import java.util.Locale

/** 동승자 폰으로 보낼 정산 요약. "청구"가 아니라 "공동 경비 분담"으로 표기한다. */
object ShareTextBuilder {
    private val won = NumberFormat.getNumberInstance(Locale.KOREA)

    fun build(title: String, totalKm: Double, members: List<Member>, settlement: Settlement, account: String?): String {
        val name = members.associate { it.id to it.name }
        return buildString {
            appendLine("[라이드로그] $title 공동 경비 분담")
            appendLine("총 ${"%.1f".format(totalKm)}km · 총 경비 ${won.format(settlement.totalWon)}원")
            appendLine()
            settlement.items.forEach { item ->
                val mark = if (item.estimated) " (추정)" else ""
                appendLine("· ${item.label}$mark ${won.format(item.amountWon)}원")
            }
            appendLine()
            if (settlement.transfers.isEmpty()) {
                appendLine("보낼 금액이 없어요.")
            } else {
                settlement.transfers.forEach { t ->
                    appendLine("${name[t.fromId] ?: t.fromId} → ${name[t.toId] ?: t.toId}  ${won.format(t.amountWon)}원")
                }
            }
            if (!account.isNullOrBlank()) {
                appendLine()
                appendLine("받는 계좌: $account")
            }
        }.trimEnd()
    }
}
