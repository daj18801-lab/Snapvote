package com.example.ui.screens

import com.example.data.model.CreatorPayout
import com.example.data.model.MediaPost
import com.example.data.model.UserProfile
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

enum class AnalyticsPayoutFilter(val label: String) {
    ALL("All"),
    PRIVATE_CHATS("Private Chats"),
    CONTENT_UNLOCKS("Content Unlocks")
}

enum class MonthlyEligibilityStatus(val badgeLabel: String) {
    ELIGIBLE("Eligible for Payout"),
    IN_PROGRESS("In Progress"),
    PAUSED_RESTRICTED("Paused (Account Restricted)")
}

data class IncomeStreamStats(
    val transactionCount: Int,
    val grossTokens: Long,
    val earnedTokensTenths: Long,
    val earnedCents: Long,
    val shareOfTotalPercent: Int
)

data class MonthlyEarningsPoint(
    val monthKey: String,
    val shortLabel: String,
    val fullLabel: String,
    val privateChatCents: Long,
    val contentUnlockCents: Long,
    val privateChatTokensTenths: Long,
    val contentUnlockTokensTenths: Long
) {
    val totalCents: Long
        get() = privateChatCents + contentUnlockCents

    val totalTokensTenths: Long
        get() = privateChatTokensTenths + contentUnlockTokensTenths
}

data class MonthlyPayoutEligibility(
    val currentMonthLabel: String,
    val monthEarnedCents: Long,
    val monthEarnedTokensTenths: Long,
    val thresholdCents: Long,
    val thresholdTokens: Long,
    val remainingCents: Long,
    val progressFraction: Float,
    val daysRemainingInMonth: Int,
    val nextPayoutDateLabel: String,
    val hasThresholdMet: Boolean,
    val hasGoodStanding: Boolean,
    val hasActiveContentOrSession: Boolean,
    val status: MonthlyEligibilityStatus
)

data class CreatorAnalyticsSummary(
    val totalGrossTokens: Long,
    val totalEarnedTokensTenths: Long,
    val totalEarnedCents: Long,
    val platformShareCents: Long,
    val totalTransactions: Int,
    val privateChats: IncomeStreamStats,
    val contentUnlocks: IncomeStreamStats,
    val monthlyHistory: List<MonthlyEarningsPoint>,
    val eligibility: MonthlyPayoutEligibility,
    val recentPayouts: List<CreatorPayout>
)

object CreatorAnalyticsCalculator {
    const val MONTHLY_PAYOUT_THRESHOLD_TOKENS = UserProfile.SUPPORT_PAY_WEEKLY_TOKENS // 100 earned tokens
    const val MONTHLY_PAYOUT_THRESHOLD_CENTS = MONTHLY_PAYOUT_THRESHOLD_TOKENS * CreatorPayout.CENTS_PER_TOKEN // $10.00
    const val CHART_MONTH_WINDOW = 6

    fun calculate(
        payouts: List<CreatorPayout>,
        myPosts: List<MediaPost>,
        isAccountRestricted: Boolean,
        nowMillis: Long = System.currentTimeMillis(),
        timeZone: TimeZone = TimeZone.getDefault()
    ): CreatorAnalyticsSummary {
        val sortedPayouts = payouts.sortedByDescending { payout ->
            payout.createdAt?.toDate()?.time ?: nowMillis
        }

        val chatPayouts = sortedPayouts.filter { isPrivateChatSource(it.sourceType) }
        val unlockPayouts = sortedPayouts.filter { !isPrivateChatSource(it.sourceType) }

        val chatGross = chatPayouts.sumOf { it.grossTokens.coerceAtLeast(0L) }
        val chatEarnedTenths = chatPayouts.sumOf { earnedTokensTenthsFor(it) }
        val chatCents = chatPayouts.sumOf { it.creatorShareCents.coerceAtLeast(0L) }

        val unlockGross = unlockPayouts.sumOf { it.grossTokens.coerceAtLeast(0L) }
        val unlockEarnedTenths = unlockPayouts.sumOf { earnedTokensTenthsFor(it) }
        val unlockCents = unlockPayouts.sumOf { it.creatorShareCents.coerceAtLeast(0L) }

        val totalGross = chatGross + unlockGross
        val totalEarnedTenths = chatEarnedTenths + unlockEarnedTenths
        val totalCents = chatCents + unlockCents
        val totalGrossCents = totalGross * CreatorPayout.CENTS_PER_TOKEN
        val platformShareCents = (totalGrossCents - totalCents).coerceAtLeast(0L)

        val chatSharePercent = if (totalCents > 0L) {
            ((chatCents * 100.0) / totalCents).toInt().coerceIn(0, 100)
        } else {
            0
        }
        val unlockSharePercent = if (totalCents > 0L) {
            (100 - chatSharePercent).coerceIn(0, 100)
        } else {
            0
        }

        val monthKeyFmt = SimpleDateFormat("yyyy-MM", Locale.US).apply { this.timeZone = timeZone }
        val shortMonthFmt = SimpleDateFormat("MMM", Locale.US).apply { this.timeZone = timeZone }
        val fullMonthFmt = SimpleDateFormat("MMMM yyyy", Locale.US).apply { this.timeZone = timeZone }
        val payoutDateFmt = SimpleDateFormat("MMM d, yyyy", Locale.US).apply { this.timeZone = timeZone }

        // Build chronological 6-month window ending with current month
        val windowMonths = buildList {
            for (offset in (CHART_MONTH_WINDOW - 1) downTo 0) {
                val cal = Calendar.getInstance(timeZone, Locale.US).apply {
                    timeInMillis = nowMillis
                    set(Calendar.DAY_OF_MONTH, 1)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                    add(Calendar.MONTH, -offset)
                }
                val date = cal.time
                add(
                    Triple(
                        monthKeyFmt.format(date),
                        shortMonthFmt.format(date),
                        fullMonthFmt.format(date)
                    )
                )
            }
        }

        val chatCentsByMonth = mutableMapOf<String, Long>()
        val unlockCentsByMonth = mutableMapOf<String, Long>()
        val chatTenthsByMonth = mutableMapOf<String, Long>()
        val unlockTenthsByMonth = mutableMapOf<String, Long>()

        sortedPayouts.forEach { payout ->
            val tsMillis = payout.createdAt?.toDate()?.time ?: nowMillis
            val key = monthKeyFmt.format(Date(tsMillis))
            val cents = payout.creatorShareCents.coerceAtLeast(0L)
            val tenths = earnedTokensTenthsFor(payout)
            if (isPrivateChatSource(payout.sourceType)) {
                chatCentsByMonth[key] = (chatCentsByMonth[key] ?: 0L) + cents
                chatTenthsByMonth[key] = (chatTenthsByMonth[key] ?: 0L) + tenths
            } else {
                unlockCentsByMonth[key] = (unlockCentsByMonth[key] ?: 0L) + cents
                unlockTenthsByMonth[key] = (unlockTenthsByMonth[key] ?: 0L) + tenths
            }
        }

        val monthlyHistory = windowMonths.map { (key, shortLabel, fullLabel) ->
            MonthlyEarningsPoint(
                monthKey = key,
                shortLabel = shortLabel,
                fullLabel = fullLabel,
                privateChatCents = chatCentsByMonth[key] ?: 0L,
                contentUnlockCents = unlockCentsByMonth[key] ?: 0L,
                privateChatTokensTenths = chatTenthsByMonth[key] ?: 0L,
                contentUnlockTokensTenths = unlockTenthsByMonth[key] ?: 0L
            )
        }

        val currentCal = Calendar.getInstance(timeZone, Locale.US).apply {
            timeInMillis = nowMillis
        }
        val currentMonthKey = monthKeyFmt.format(currentCal.time)
        val currentMonthLabel = fullMonthFmt.format(currentCal.time)
        val currentDay = currentCal.get(Calendar.DAY_OF_MONTH)
        val maxDay = currentCal.getActualMaximum(Calendar.DAY_OF_MONTH)
        val daysRemaining = (maxDay - currentDay).coerceAtLeast(0)

        val nextPayoutCal = Calendar.getInstance(timeZone, Locale.US).apply {
            timeInMillis = nowMillis
            set(Calendar.DAY_OF_MONTH, 1)
            add(Calendar.MONTH, 1)
        }
        val nextPayoutDateLabel = payoutDateFmt.format(nextPayoutCal.time)

        val monthEarnedCents = (chatCentsByMonth[currentMonthKey] ?: 0L) +
            (unlockCentsByMonth[currentMonthKey] ?: 0L)
        val monthEarnedTenths = (chatTenthsByMonth[currentMonthKey] ?: 0L) +
            (unlockTenthsByMonth[currentMonthKey] ?: 0L)

        val remainingCents = (MONTHLY_PAYOUT_THRESHOLD_CENTS - monthEarnedCents).coerceAtLeast(0L)
        val progress = (monthEarnedCents.toFloat() / MONTHLY_PAYOUT_THRESHOLD_CENTS.toFloat())
            .coerceIn(0f, 1f)

        val hasThresholdMet = monthEarnedCents >= MONTHLY_PAYOUT_THRESHOLD_CENTS
        val hasGoodStanding = !isAccountRestricted
        val hasActiveContentOrSession = myPosts.isNotEmpty() || sortedPayouts.isNotEmpty()

        val status = when {
            !hasGoodStanding -> MonthlyEligibilityStatus.PAUSED_RESTRICTED
            hasThresholdMet && hasActiveContentOrSession -> MonthlyEligibilityStatus.ELIGIBLE
            else -> MonthlyEligibilityStatus.IN_PROGRESS
        }

        return CreatorAnalyticsSummary(
            totalGrossTokens = totalGross,
            totalEarnedTokensTenths = totalEarnedTenths,
            totalEarnedCents = totalCents,
            platformShareCents = platformShareCents,
            totalTransactions = sortedPayouts.size,
            privateChats = IncomeStreamStats(
                transactionCount = chatPayouts.size,
                grossTokens = chatGross,
                earnedTokensTenths = chatEarnedTenths,
                earnedCents = chatCents,
                shareOfTotalPercent = chatSharePercent
            ),
            contentUnlocks = IncomeStreamStats(
                transactionCount = unlockPayouts.size,
                grossTokens = unlockGross,
                earnedTokensTenths = unlockEarnedTenths,
                earnedCents = unlockCents,
                shareOfTotalPercent = unlockSharePercent
            ),
            monthlyHistory = monthlyHistory,
            eligibility = MonthlyPayoutEligibility(
                currentMonthLabel = currentMonthLabel,
                monthEarnedCents = monthEarnedCents,
                monthEarnedTokensTenths = monthEarnedTenths,
                thresholdCents = MONTHLY_PAYOUT_THRESHOLD_CENTS,
                thresholdTokens = MONTHLY_PAYOUT_THRESHOLD_TOKENS,
                remainingCents = remainingCents,
                progressFraction = progress,
                daysRemainingInMonth = daysRemaining,
                nextPayoutDateLabel = nextPayoutDateLabel,
                hasThresholdMet = hasThresholdMet,
                hasGoodStanding = hasGoodStanding,
                hasActiveContentOrSession = hasActiveContentOrSession,
                status = status
            ),
            recentPayouts = sortedPayouts
        )
    }

    fun filterPayouts(
        payouts: List<CreatorPayout>,
        filter: AnalyticsPayoutFilter
    ): List<CreatorPayout> = when (filter) {
        AnalyticsPayoutFilter.ALL -> payouts
        AnalyticsPayoutFilter.PRIVATE_CHATS -> payouts.filter { isPrivateChatSource(it.sourceType) }
        AnalyticsPayoutFilter.CONTENT_UNLOCKS -> payouts.filter { !isPrivateChatSource(it.sourceType) }
    }

    private fun isPrivateChatSource(sourceType: String): Boolean =
        sourceType == CreatorPayout.SOURCE_MIC_CALL || sourceType == CreatorPayout.SOURCE_VIDEO_CALL

    fun earnedTokensTenthsFor(payout: CreatorPayout): Long =
        (payout.grossTokens.coerceAtLeast(0L) * payout.creatorSharePercent.coerceAtLeast(0L)) / 10L

    fun formatTokensFromTenths(tenths: Long): String {
        val safeTenths = tenths.coerceAtLeast(0L)
        val whole = safeTenths / 10L
        val rem = safeTenths % 10L
        return if (rem == 0L) "$whole" else "$whole.$rem"
    }

    fun formatUsdFromCents(cents: Long): String =
        String.format(Locale.US, "$%.2f", cents.coerceAtLeast(0L) / 100.0)
}
