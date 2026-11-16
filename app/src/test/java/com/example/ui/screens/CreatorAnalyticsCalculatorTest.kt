package com.example.ui.screens

import com.example.data.model.CreatorPayout
import com.example.data.model.MediaPost
import com.google.firebase.Timestamp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class CreatorAnalyticsCalculatorTest {

    private val utc: TimeZone = TimeZone.getTimeZone("UTC")

    private fun utcTimestamp(year: Int, monthZeroBased: Int, day: Int): Timestamp {
        val cal = Calendar.getInstance(utc, Locale.US).apply {
            set(year, monthZeroBased, day, 12, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return Timestamp(Date(cal.timeInMillis))
    }

    @Test
    fun calculatesTotalEarningsAndStreamBreakdownAccurately() {
        val nowMillis = utcTimestamp(2026, Calendar.NOVEMBER, 15).toDate().time
        val payouts = listOf(
            CreatorPayout(
                payoutId = "p1",
                creatorId = "c1",
                payerId = "u1",
                payerName = "Alice",
                sourceType = CreatorPayout.SOURCE_MIC_CALL,
                grossTokens = 25L,
                creatorSharePercent = 70L,
                creatorShareCents = 175L,
                createdAt = utcTimestamp(2026, Calendar.NOVEMBER, 10)
            ),
            CreatorPayout(
                payoutId = "p2",
                creatorId = "c1",
                payerId = "u2",
                payerName = "Bob",
                sourceType = CreatorPayout.SOURCE_GALLERY_UNLOCK,
                grossTokens = 3L,
                creatorSharePercent = 70L,
                creatorShareCents = 21L,
                createdAt = utcTimestamp(2026, Calendar.NOVEMBER, 12)
            ),
            CreatorPayout(
                payoutId = "p3",
                creatorId = "c1",
                payerId = "u3",
                payerName = "Cara",
                sourceType = CreatorPayout.SOURCE_GALLERY_UNLOCK,
                grossTokens = 10L,
                creatorSharePercent = 70L,
                creatorShareCents = 70L,
                createdAt = utcTimestamp(2026, Calendar.OCTOBER, 20)
            )
        )

        val summary = CreatorAnalyticsCalculator.calculate(
            payouts = payouts,
            myPosts = listOf(MediaPost(id = "post1", creatorId = "c1")),
            isAccountRestricted = false,
            nowMillis = nowMillis,
            timeZone = utc
        )

        assertEquals(38L, summary.totalGrossTokens)
        assertEquals(266L, summary.totalEarnedCents)
        assertEquals(266L, summary.totalEarnedTokensTenths) // 26.6 earned tokens
        assertEquals(114L, summary.platformShareCents) // $3.80 - $2.66 = $1.14
        assertEquals(3, summary.totalTransactions)

        // Private chats breakdown: 175 / 266 ~= 65%
        assertEquals(1, summary.privateChats.transactionCount)
        assertEquals(175L, summary.privateChats.earnedCents)
        assertEquals(65, summary.privateChats.shareOfTotalPercent)

        // Content unlocks breakdown: 91 / 266 -> 35% remainder
        assertEquals(2, summary.contentUnlocks.transactionCount)
        assertEquals(91L, summary.contentUnlocks.earnedCents)
        assertEquals(35, summary.contentUnlocks.shareOfTotalPercent)

        // 6-month window ends in Nov 2026
        assertEquals(6, summary.monthlyHistory.size)
        assertEquals("2026-10", summary.monthlyHistory[4].monthKey)
        assertEquals(70L, summary.monthlyHistory[4].contentUnlockCents)
        assertEquals("2026-11", summary.monthlyHistory[5].monthKey)
        assertEquals(175L, summary.monthlyHistory[5].privateChatCents)
        assertEquals(21L, summary.monthlyHistory[5].contentUnlockCents)
    }

    @Test
    fun tracksMonthlyPayoutEligibilityThresholdAndGoodStanding() {
        val nowMillis = utcTimestamp(2026, Calendar.NOVEMBER, 15).toDate().time

        // $10.00+ earned inside November -> Eligible
        val eligiblePayouts = listOf(
            CreatorPayout(
                payoutId = "p_nov",
                creatorId = "c1",
                sourceType = CreatorPayout.SOURCE_MIC_CALL,
                grossTokens = 170L, // 119 creator tokens = $11.90
                creatorSharePercent = 70L,
                creatorShareCents = 1190L,
                createdAt = utcTimestamp(2026, Calendar.NOVEMBER, 5)
            )
        )

        val eligibleSummary = CreatorAnalyticsCalculator.calculate(
            payouts = eligiblePayouts,
            myPosts = listOf(MediaPost(id = "post1", creatorId = "c1")),
            isAccountRestricted = false,
            nowMillis = nowMillis,
            timeZone = utc
        )
        assertTrue(eligibleSummary.eligibility.hasThresholdMet)
        assertTrue(eligibleSummary.eligibility.hasGoodStanding)
        assertEquals(MonthlyEligibilityStatus.ELIGIBLE, eligibleSummary.eligibility.status)
        assertEquals(0L, eligibleSummary.eligibility.remainingCents)
        assertEquals(1f, eligibleSummary.eligibility.progressFraction)
        assertEquals(15, eligibleSummary.eligibility.daysRemainingInMonth)
        assertEquals("Dec 1, 2026", eligibleSummary.eligibility.nextPayoutDateLabel)

        // Restricted account pauses eligibility even when threshold is met
        val restrictedSummary = CreatorAnalyticsCalculator.calculate(
            payouts = eligiblePayouts,
            myPosts = listOf(MediaPost(id = "post1", creatorId = "c1")),
            isAccountRestricted = true,
            nowMillis = nowMillis,
            timeZone = utc
        )
        assertFalse(restrictedSummary.eligibility.hasGoodStanding)
        assertEquals(MonthlyEligibilityStatus.PAUSED_RESTRICTED, restrictedSummary.eligibility.status)
    }

    @Test
    fun filtersPayoutLedgerBySourceType() {
        val payouts = listOf(
            CreatorPayout(payoutId = "1", sourceType = CreatorPayout.SOURCE_MIC_CALL),
            CreatorPayout(payoutId = "2", sourceType = CreatorPayout.SOURCE_GALLERY_UNLOCK),
            CreatorPayout(payoutId = "3", sourceType = CreatorPayout.SOURCE_VIDEO_CALL)
        )

        assertEquals(3, CreatorAnalyticsCalculator.filterPayouts(payouts, AnalyticsPayoutFilter.ALL).size)
        assertEquals(2, CreatorAnalyticsCalculator.filterPayouts(payouts, AnalyticsPayoutFilter.PRIVATE_CHATS).size)
        assertEquals(1, CreatorAnalyticsCalculator.filterPayouts(payouts, AnalyticsPayoutFilter.CONTENT_UNLOCKS).size)
    }

    @Test
    fun countsPrivateChatVideoCallAtSeventyPercentCreatorShare() {
        val nowMillis = utcTimestamp(2026, Calendar.NOVEMBER, 15).toDate().time
        val payouts = listOf(
            CreatorPayout(
                payoutId = "vid_1",
                creatorId = "c1",
                payerId = "u1",
                payerName = "Fan",
                sourceType = CreatorPayout.SOURCE_VIDEO_CALL,
                sourceId = "s1",
                grossTokens = 100L, // 1 min video = 100 tokens -> 70 creator tokens = $7.00
                creatorSharePercent = 70L,
                creatorShareCents = 700L,
                createdAt = utcTimestamp(2026, Calendar.NOVEMBER, 15)
            )
        )

        val summary = CreatorAnalyticsCalculator.calculate(
            payouts = payouts,
            myPosts = listOf(MediaPost(id = "post1", creatorId = "c1")),
            isAccountRestricted = false,
            nowMillis = nowMillis,
            timeZone = utc
        )

        assertEquals(100L, summary.totalGrossTokens)
        assertEquals(700L, summary.totalEarnedCents)
        assertEquals(300L, summary.platformShareCents)
        assertEquals(1, summary.privateChats.transactionCount)
        assertEquals(700L, summary.privateChats.earnedCents)
        assertEquals(100, summary.privateChats.shareOfTotalPercent)
        assertEquals(300L, summary.eligibility.remainingCents) // $3.00 remaining toward $10.00 threshold
    }
}
