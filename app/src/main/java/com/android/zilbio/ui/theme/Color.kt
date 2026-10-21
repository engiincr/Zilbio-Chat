package com.android.zilbio.ui.theme

import androidx.compose.ui.graphics.Color

// Primary Background: Deep Midnight Navy
val BackgroundMidnightNavy = Color(0xFF0B101B)
val BackgroundMidnightNavyAlt = Color(0xFF0E1422)

// Elevated Surface / Header / Input Pill: Dark Slate Navy
val SurfaceSlateNavy = Color(0xFF171F30)
val SurfaceSlateNavyElevated = Color(0xFF1C2539)
val BorderSlateNavy = Color(0xFF28324B)

// Primary Accent (Mic Button / Active Badges): Vibrant Neon Lime-Green
val AccentNeonLime = Color(0xFFA8FF00)
val AccentNeonLimeDark = Color(0xFF8CE000)
val OnAccentNeonLime = Color(0xFF0B101B)

// Reaction Pill & Reply Highlight: Dark Emerald / Forest Green
val EmeraldReactionBg = Color(0xFF163324)
val EmeraldReactionBorder = Color(0xFF2E6B47)
val EmeraldReactionText = Color(0xFF52D68A)

// Secondary Warm Accents
val AccentWarmPeach = Color(0xFFE5854B)
val AccentWarmPeachLight = Color(0xFFF2B896)
val AccentAmberOrange = Color(0xFFE5854B)

// Destructive / Delete / Missed Call Text: Coral Red
val DestructiveCoralRed = Color(0xFFE56B63)
val CallEndCoralRed = Color(0xFFE56B63)

// Text Colors
val TextOffWhite = Color(0xFFF5EFEA)
val TextMutedSlateGrey = Color(0xFF8E9BB0)
val TextMutedWarmGrey = Color(0xFF8E9BB0)

// Status & Action Colors
val OnlineStatusGreen = Color(0xFF2ECC71)
val CallAcceptGreen = Color(0xFF2ECC71)
val QuotaWarningAmber = Color(0xFFFFB020)

// Delivery Status Tick Colors
val TickGrey = Color(0xFF7E8B9F)
val TickReadLime = Color(0xFFA8FF00)

// Scrims & Overlays
val ScrimDarkBlur = Color(0xCC0B101B)
val BorderDarkSubtle = Color(0xFF28324B)

// Legacy aliases for backward compatibility across screens
val BackgroundDarkEspresso = BackgroundMidnightNavy
val BackgroundDarkEspressoAlt = BackgroundMidnightNavyAlt
val SurfaceDarkWarm = SurfaceSlateNavy
val SurfaceDarkWarmElevated = SurfaceSlateNavyElevated
val OutgoingTerracotta = Color(0xFF1C273D)
val OutgoingTerracottaLight = Color(0xFF24324E)
val IncomingDarkSlate = Color(0xFF151C2C)
val BackgroundAlabasterCream = BackgroundMidnightNavy
val SurfaceCardWhite = SurfaceSlateNavy
val BorderWarmSubtle = BorderSlateNavy
val OutgoingBubblePeach = OutgoingTerracotta
val OriginalSpeechGrey = TextMutedSlateGrey
val TranslatedSpeechOrange = AccentNeonLime
val TextPrimaryEspresso = TextOffWhite
val TextSecondaryWarmGrey = TextMutedSlateGrey
val InputFieldBackground = SurfaceSlateNavy
val BottomNavBackground = BackgroundMidnightNavyAlt
val NeonLimeAccent = AccentNeonLime
val SurfaceMidnightDark = SurfaceSlateNavyElevated

// 8 Per-Chat Custom Themes
data class ChatThemePalette(
    val id: String,
    val name: String,
    val background: Color,
    val bubbleOutgoing: Color,
    val bubbleIncoming: Color,
    val accent: Color
)

val ChatThemeOptions = listOf(
    ChatThemePalette("midnight_navy", "Midnight Navy (Default)", Color(0xFF0B101B), Color(0xFF1C273D), Color(0xFF151C2C), Color(0xFFA8FF00)),
    ChatThemePalette("deep_emerald", "Deep Emerald", Color(0xFF071A12), Color(0xFF123B2A), Color(0xFF0D281C), Color(0xFF4EFAAA)),
    ChatThemePalette("royal_amethyst", "Royal Amethyst", Color(0xFF120B1D), Color(0xFF2B1A45), Color(0xFF1D1230), Color(0xFFD67BFF)),
    ChatThemePalette("warm_terracotta", "Warm Terracotta", Color(0xFF18100E), Color(0xFF422119), Color(0xFF2B1611), Color(0xFFFF8A65)),
    ChatThemePalette("ocean_abyss", "Ocean Abyss", Color(0xFF07141E), Color(0xFF12324A), Color(0xFF0D2233), Color(0xFF40C4FF)),
    ChatThemePalette("rose_quartz", "Rose Quartz", Color(0xFF1A0E14), Color(0xFF3F1F30), Color(0xFF2B1521), Color(0xFFFF69B4)),
    ChatThemePalette("cyber_slate", "Cyber Slate", Color(0xFF111418), Color(0xFF242B33), Color(0xFF181D24), Color(0xFF00E5FF)),
    ChatThemePalette("sunset_amber", "Sunset Amber", Color(0xFF1A1208), Color(0xFF422F12), Color(0xFF2B1E0C), Color(0xFFFFB300))
)

fun getChatTheme(themeId: String): ChatThemePalette {
    return ChatThemeOptions.find { it.id == themeId } ?: ChatThemeOptions.first()
}
