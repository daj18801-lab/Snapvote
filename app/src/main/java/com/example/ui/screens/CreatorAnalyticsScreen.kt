package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.AutoGraph
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.MonetizationOn
import androidx.compose.material.icons.rounded.PhoneInTalk
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.CreatorPayout
import com.example.data.model.MediaPost
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberBottom
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberStart
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.common.ProvideVicoTheme
import com.patrykandpatrick.vico.compose.m3.common.rememberM3VicoTheme
import com.patrykandpatrick.vico.core.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.core.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.core.cartesian.data.columnSeries
import java.text.DateFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CreatorAnalyticsScreen(
    creatorDisplayName: String,
    creatorPayouts: List<CreatorPayout>,
    myPosts: List<MediaPost>,
    isAccountRestricted: Boolean,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler { onBack() }

    var selectedFilter by remember { mutableStateOf(AnalyticsPayoutFilter.ALL) }

    val summary = remember(creatorPayouts, myPosts, isAccountRestricted) {
        CreatorAnalyticsCalculator.calculate(
            payouts = creatorPayouts,
            myPosts = myPosts,
            isAccountRestricted = isAccountRestricted
        )
    }
    val filteredPayouts = remember(summary.recentPayouts, selectedFilter) {
        CreatorAnalyticsCalculator.filterPayouts(summary.recentPayouts, selectedFilter)
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets.navigationBars,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = stringResource(R.string.creator_analytics_title),
                            style = MaterialTheme.typography.headlineSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "$creatorDisplayName • ${CreatorPayout.CREATOR_CONTENT_SHARE_PERCENT}% content • ${CreatorPayout.CREATOR_SHARE_PERCENT}% live calls",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("analytics_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back to profile"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item(key = "hero_earnings") {
                TotalEarningsHeroCard(summary = summary)
            }

            item(key = "income_breakdown") {
                IncomeSourceBreakdownCard(summary = summary)
            }

            item(key = "monthly_chart") {
                MonthlyTokenIncomeChartCard(monthlyHistory = summary.monthlyHistory)
            }

            item(key = "payout_eligibility") {
                MonthlyPayoutEligibilityCard(eligibility = summary.eligibility)
            }

            item(key = "ledger_header") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Earnings Ledger",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${filteredPayouts.size} entries",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AnalyticsPayoutFilter.entries.forEach { filter ->
                            val tag = when (filter) {
                                AnalyticsPayoutFilter.ALL -> "analytics_filter_all"
                                AnalyticsPayoutFilter.PRIVATE_CHATS -> "analytics_filter_private_chats"
                                AnalyticsPayoutFilter.CONTENT_UNLOCKS -> "analytics_filter_content_unlocks"
                            }
                            FilterChip(
                                selected = selectedFilter == filter,
                                onClick = { selectedFilter = filter },
                                label = { Text(filter.label) },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag(tag)
                            )
                        }
                    }
                }
            }

            if (filteredPayouts.isEmpty()) {
                item(key = "empty_ledger") {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        shape = MaterialTheme.shapes.large,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.MonetizationOn,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(32.dp)
                            )
                            Text(
                                text = "No ${selectedFilter.label.lowercase(Locale.US)} earnings recorded yet",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Upload extra photos & videos for fans to unlock — you earn ${CreatorPayout.CREATOR_CONTENT_SHARE_PERCENT}% on every gallery unlock!",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(
                    items = filteredPayouts.take(30),
                    key = { it.payoutId.ifBlank { it.id } }
                ) { payout ->
                    PayoutLedgerRow(payout = payout)
                }
            }

            item(key = "bottom_spacer") {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun TotalEarningsHeroCard(summary: CreatorAnalyticsSummary) {
    val earnedUsd = CreatorAnalyticsCalculator.formatUsdFromCents(summary.totalEarnedCents)
    val earnedTokens = CreatorAnalyticsCalculator.formatTokensFromTenths(summary.totalEarnedTokensTenths)
    val platformUsd = CreatorAnalyticsCalculator.formatUsdFromCents(summary.platformShareCents)

    Card(
        shape = MaterialTheme.shapes.extraLarge,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.45f)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("analytics_hero_card")
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        listOf(
                            MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.65f),
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                            MaterialTheme.colorScheme.surfaceContainer
                        )
                    )
                )
                .padding(20.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "TOTAL CREATOR EARNINGS",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.tertiary,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = earnedUsd,
                            style = MaterialTheme.typography.displaySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.testTag("analytics_total_usd")
                        )
                    }
                    Surface(
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        shape = RoundedCornerShape(50),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.5f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.AccountBalanceWallet,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "$earnedTokens tokens",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.tertiary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    HeroMetricPill(
                        label = "Gross Spent",
                        value = "${summary.totalGrossTokens}t",
                        modifier = Modifier.weight(1f)
                    )
                    HeroMetricPill(
                        label = "Content Split",
                        value = "${CreatorPayout.CREATOR_CONTENT_SHARE_PERCENT}%",
                        modifier = Modifier.weight(1f)
                    )
                    HeroMetricPill(
                        label = "Calls Split",
                        value = "${CreatorPayout.CREATOR_SHARE_PERCENT}%",
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun HeroMetricPill(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun IncomeSourceBreakdownCard(summary: CreatorAnalyticsSummary) {
    val chatColor = MaterialTheme.colorScheme.primary
    val unlockColor = MaterialTheme.colorScheme.tertiary
    val trackColor = MaterialTheme.colorScheme.surfaceContainerHighest

    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("analytics_breakdown_card")
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Column {
                Text(
                    text = "Income Breakdown",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Private mic & video chats vs. extra content unlocks",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    modifier = Modifier.size(116.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val strokeWidth = 14.dp.toPx()
                        val inset = strokeWidth / 2f
                        val arcSize = Size(size.width - strokeWidth, size.height - strokeWidth)
                        val topLeft = Offset(inset, inset)

                        drawArc(
                            color = trackColor,
                            startAngle = 0f,
                            sweepAngle = 360f,
                            useCenter = false,
                            topLeft = topLeft,
                            size = arcSize,
                            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                        )

                        if (summary.totalEarnedCents > 0L) {
                            val chatSweep = (summary.privateChats.earnedCents.toFloat() /
                                summary.totalEarnedCents.toFloat()) * 360f
                            val unlockSweep = 360f - chatSweep

                            if (chatSweep > 0f) {
                                drawArc(
                                    color = chatColor,
                                    startAngle = -90f,
                                    sweepAngle = chatSweep,
                                    useCenter = false,
                                    topLeft = topLeft,
                                    size = arcSize,
                                    style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
                                )
                            }
                            if (unlockSweep > 0f) {
                                drawArc(
                                    color = unlockColor,
                                    startAngle = -90f + chatSweep,
                                    sweepAngle = unlockSweep,
                                    useCenter = false,
                                    topLeft = topLeft,
                                    size = arcSize,
                                    style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
                                )
                            }
                        }
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "${summary.totalTransactions}",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "payouts",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    StreamBreakdownRow(
                        icon = Icons.Rounded.PhoneInTalk,
                        accentColor = chatColor,
                        title = "Private Chats",
                        stats = summary.privateChats,
                        unitLabel = "calls"
                    )
                    StreamBreakdownRow(
                        icon = Icons.Rounded.LockOpen,
                        accentColor = unlockColor,
                        title = "Content Unlocks",
                        stats = summary.contentUnlocks,
                        unitLabel = "unlocks"
                    )
                }
            }
        }
    }
}

@Composable
private fun StreamBreakdownRow(
    icon: ImageVector,
    accentColor: Color,
    title: String,
    stats: IncomeStreamStats,
    unitLabel: String
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(accentColor.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(18.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "$title (${stats.shareOfTotalPercent}%)",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${CreatorAnalyticsCalculator.formatTokensFromTenths(stats.earnedTokensTenths)}t • ${stats.transactionCount} $unitLabel",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = CreatorAnalyticsCalculator.formatUsdFromCents(stats.earnedCents),
                style = MaterialTheme.typography.titleSmall,
                color = accentColor,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun MonthlyTokenIncomeChartCard(monthlyHistory: List<MonthlyEarningsPoint>) {
    val hasMonthlyActivity = remember(monthlyHistory) {
        monthlyHistory.any { it.totalTokensTenths > 0L }
    }
    val modelProducer = remember { CartesianChartModelProducer() }

    LaunchedEffect(monthlyHistory, hasMonthlyActivity) {
        if (hasMonthlyActivity) {
            modelProducer.runTransaction {
                columnSeries {
                    series(monthlyHistory.map { it.privateChatTokensTenths / 10.0 })
                    series(monthlyHistory.map { it.contentUnlockTokensTenths / 10.0 })
                }
            }
        }
    }

    val bottomFormatter = remember(monthlyHistory) {
        CartesianValueFormatter { _, x, _ ->
            monthlyHistory.getOrNull(x.toInt())?.shortLabel.orEmpty()
        }
    }
    val startFormatter = remember {
        CartesianValueFormatter { _, y, _ ->
            if (y % 1.0 == 0.0) "${y.toInt()}t" else String.format(Locale.US, "%.1ft", y)
        }
    }

    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("analytics_chart_card")
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AutoGraph,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Column {
                        Text(
                            text = "6-Month Token Income",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Private chats vs. content unlocks (earned tokens)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (!hasMonthlyActivity) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.CalendarMonth,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(28.dp)
                        )
                        Text(
                            text = "Your monthly chart will illuminate automatically once you earn tokens from private mic calls or unlocked galleries.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                ProvideVicoTheme(theme = rememberM3VicoTheme()) {
                    CartesianChartHost(
                        chart = rememberCartesianChart(
                            rememberColumnCartesianLayer(),
                            startAxis = VerticalAxis.rememberStart(valueFormatter = startFormatter),
                            bottomAxis = HorizontalAxis.rememberBottom(valueFormatter = bottomFormatter)
                        ),
                        modelProducer = modelProducer,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .testTag("analytics_chart")
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    LegendDot(color = MaterialTheme.colorScheme.primary, label = "Private Chats")
                    LegendDot(color = MaterialTheme.colorScheme.secondary, label = "Content Unlocks")
                }
            }
        }
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(color)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun MonthlyPayoutEligibilityCard(eligibility: MonthlyPayoutEligibility) {
    val badgeColors = when (eligibility.status) {
        MonthlyEligibilityStatus.ELIGIBLE ->
            MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.tertiary
        MonthlyEligibilityStatus.IN_PROGRESS ->
            MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.secondary
        MonthlyEligibilityStatus.PAUSED_RESTRICTED ->
            MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.error
    }

    val monthUsd = CreatorAnalyticsCalculator.formatUsdFromCents(eligibility.monthEarnedCents)
    val goalUsd = CreatorAnalyticsCalculator.formatUsdFromCents(eligibility.thresholdCents)
    val monthTokens = CreatorAnalyticsCalculator.formatTokensFromTenths(eligibility.monthEarnedTokensTenths)
    val remainingUsd = CreatorAnalyticsCalculator.formatUsdFromCents(eligibility.remainingCents)

    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        border = BorderStroke(1.dp, badgeColors.second.copy(alpha = 0.45f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("analytics_eligibility_card")
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Monthly Payout Eligibility",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = eligibility.currentMonthLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Surface(
                    color = badgeColors.first,
                    shape = RoundedCornerShape(50)
                ) {
                    Text(
                        text = eligibility.status.badgeLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = badgeColors.second,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Text(
                    text = "$monthUsd / $goalUsd",
                    style = MaterialTheme.typography.headlineSmall,
                    color = badgeColors.second,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "$monthTokens / ${eligibility.thresholdTokens} tokens",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            LinearProgressIndicator(
                progress = { eligibility.progressFraction },
                color = badgeColors.second,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(RoundedCornerShape(50))
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = if (eligibility.hasThresholdMet) {
                        "Monthly threshold reached!"
                    } else {
                        "$remainingUsd left to reach monthly goal"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Schedule,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "${eligibility.daysRemainingInMonth}d left • ${eligibility.nextPayoutDateLabel}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                EligibilityRequirementRow(
                    passed = eligibility.hasThresholdMet,
                    label = "Earn $goalUsd (${eligibility.thresholdTokens} creator tokens) in ${eligibility.currentMonthLabel}",
                    isBlocked = false
                )
                EligibilityRequirementRow(
                    passed = eligibility.hasGoodStanding,
                    label = if (eligibility.hasGoodStanding) {
                        "Account in good standing (no active suspension or ban)"
                    } else {
                        "Account standing paused due to active restriction"
                    },
                    isBlocked = !eligibility.hasGoodStanding
                )
                EligibilityRequirementRow(
                    passed = eligibility.hasActiveContentOrSession,
                    label = "Active creator catalog or completed token session",
                    isBlocked = false
                )
            }
        }
    }
}

@Composable
private fun EligibilityRequirementRow(
    passed: Boolean,
    label: String,
    isBlocked: Boolean
) {
    val icon = when {
        isBlocked -> Icons.Rounded.ErrorOutline
        passed -> Icons.Rounded.CheckCircle
        else -> Icons.Rounded.HourglassTop
    }
    val tint = when {
        isBlocked -> MaterialTheme.colorScheme.error
        passed -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.secondary
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(18.dp)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun PayoutLedgerRow(payout: CreatorPayout) {
    val isPrivateChat = payout.sourceType == CreatorPayout.SOURCE_MIC_CALL ||
        payout.sourceType == CreatorPayout.SOURCE_VIDEO_CALL
    val accent = if (isPrivateChat) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary
    val icon = when (payout.sourceType) {
        CreatorPayout.SOURCE_VIDEO_CALL -> Icons.Rounded.Videocam
        CreatorPayout.SOURCE_MIC_CALL -> Icons.Rounded.PhoneInTalk
        else -> Icons.Rounded.LockOpen
    }
    val sourceLabel = when (payout.sourceType) {
        CreatorPayout.SOURCE_VIDEO_CALL -> "Private Video Call"
        CreatorPayout.SOURCE_MIC_CALL -> "Private Mic Chat"
        else -> "Content Unlock"
    }
    val dateLabel = remember(payout.createdAt) {
        payout.createdAt?.toDate()?.let {
            DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(it)
        } ?: "Just now"
    }

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = sourceLabel,
                    tint = accent,
                    modifier = Modifier.size(20.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "$sourceLabel • ${payout.payerName.ifBlank { "Fan" }}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${payout.grossTokens} gross tokens (${payout.creatorSharePercent}% share) • $dateLabel",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "+${payout.creatorShareUsdLabel()}",
                    style = MaterialTheme.typography.titleSmall,
                    color = accent,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "+${payout.creatorShareTokensLabel()}t",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
