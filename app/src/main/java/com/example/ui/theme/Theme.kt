package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

import com.example.data.model.EffectiveStaffTier
import com.example.data.model.StaffRole
import com.example.data.model.UserProfile
import java.util.Locale

private val SnapVoteDarkColorScheme = darkColorScheme(
    primary = ElectricCoral,
    onPrimary = Color.White,
    primaryContainer = ElectricCoralContainer,
    onPrimaryContainer = Color(0xFFFFD9DA),
    secondary = WarmAmber,
    onSecondary = Color(0xFF1F1400),
    secondaryContainer = WarmAmberContainer,
    onSecondaryContainer = Color(0xFFFFE5B8),
    tertiary = EmeraldMint,
    onTertiary = Color(0xFF002418),
    tertiaryContainer = EmeraldMintContainer,
    onTertiaryContainer = Color(0xFFA7F3D0),
    background = StudioObsidian,
    onBackground = StudioOnDark,
    surface = StudioSurface,
    onSurface = StudioOnDark,
    surfaceVariant = StudioCard,
    onSurfaceVariant = StudioOnDarkMuted,
    surfaceContainerLow = StudioSurface,
    surfaceContainer = StudioCard,
    surfaceContainerHigh = StudioCardElevated,
    outline = StudioOutline,
    outlineVariant = Color(0xFF252A3C),
    error = CoralRoseError,
    onError = Color.White
)

private val SnapVoteLightColorScheme = lightColorScheme(
    primary = CoralPrimaryLight,
    onPrimary = Color.White,
    primaryContainer = CoralContainerLight,
    onPrimaryContainer = Color(0xFF5F0A12),
    secondary = AmberSecondaryLight,
    onSecondary = Color.White,
    secondaryContainer = AmberContainerLight,
    onSecondaryContainer = Color(0xFF452800),
    tertiary = Color(0xFF0E9F6E),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFD1FAE5),
    onTertiaryContainer = Color(0xFF023E29),
    background = StudioPaper,
    onBackground = StudioOnLight,
    surface = StudioLightCard,
    onSurface = StudioOnLight,
    surfaceVariant = StudioLightElevated,
    onSurfaceVariant = StudioOnLightMuted,
    surfaceContainerLow = StudioPaper,
    surfaceContainer = StudioLightCard,
    surfaceContainerHigh = StudioLightElevated,
    outline = StudioLightOutline,
    outlineVariant = Color(0xFFE7EBF3),
    error = Color(0xFFDC2626),
    onError = Color.White
)

val SnapVoteShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun SnapVoteTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) SnapVoteDarkColorScheme else SnapVoteLightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = SnapVoteTypography,
        shapes = SnapVoteShapes,
        content = content
    )
}

fun normalizeStaffRole(role: String?, email: String? = null): String? {
    if (email.equals(StaffRole.OWNER_EMAIL, ignoreCase = true)) return UserProfile.ROLE_ADMIN
    return when (role?.lowercase(Locale.ROOT)) {
        UserProfile.ROLE_ADMIN, "owner" -> UserProfile.ROLE_ADMIN
        UserProfile.ROLE_SUPPORT -> UserProfile.ROLE_SUPPORT
        else -> null
    }
}

fun staffTierToRoleString(tier: EffectiveStaffTier): String? = when (tier) {
    EffectiveStaffTier.OWNER, EffectiveStaffTier.ADMIN -> UserProfile.ROLE_ADMIN
    EffectiveStaffTier.SUPPORT -> UserProfile.ROLE_SUPPORT
    EffectiveStaffTier.NONE -> null
}

fun roleBadgeForegroundColor(role: String?, email: String? = null): Color? =
    when (normalizeStaffRole(role, email)) {
        UserProfile.ROLE_ADMIN -> AdminRed
        UserProfile.ROLE_SUPPORT -> SupportBlue
        else -> null
    }

fun roleBadgeContainerColor(role: String?, email: String? = null): Color? =
    when (normalizeStaffRole(role, email)) {
        UserProfile.ROLE_ADMIN -> AdminRedContainer
        UserProfile.ROLE_SUPPORT -> SupportBlueContainer
        else -> null
    }

fun roleBadgeOnContainerColor(role: String?, email: String? = null): Color? =
    when (normalizeStaffRole(role, email)) {
        UserProfile.ROLE_ADMIN -> AdminRedOnContainer
        UserProfile.ROLE_SUPPORT -> SupportBlueOnContainer
        else -> null
    }

fun formatDisplayNameWithRole(displayName: String, role: String?, email: String? = null): String {
    return displayName.trim().ifBlank { "Creator" }
}

