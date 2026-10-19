package com.example.ui.theme

import androidx.compose.ui.graphics.Color

// Pixel-inspired M3 Palette - Deep Sapphire / Slate / Warm Neutral
val PixelNavyPrimaryLight = Color(0xFF1D4ED8) // Deep Blue
val PixelNavyOnPrimaryLight = Color(0xFFFFFFFF)
val PixelPrimaryContainerLight = Color(0xFFDBEAFE)
val PixelOnPrimaryContainerLight = Color(0xFF1E3A8A)

val PixelSecondaryLight = Color(0xFF475569) // Slate
val PixelOnSecondaryLight = Color(0xFFFFFFFF)
val PixelSecondaryContainerLight = Color(0xFFF1F5F9)
val PixelOnSecondaryContainerLight = Color(0xFF0F172A)

val PixelTertiaryLight = Color(0xFF0284C7) // Sky / Accent
val PixelOnTertiaryLight = Color(0xFFFFFFFF)
val PixelTertiaryContainerLight = Color(0xFFE0F2FE)
val PixelOnTertiaryContainerLight = Color(0xFF0369A1)

val PixelBackgroundLight = Color(0xFFF8FAFC)
val PixelOnBackgroundLight = Color(0xFF0F172A)
val PixelSurfaceLight = Color(0xFFFFFFFF)
val PixelOnSurfaceLight = Color(0xFF0F172A)
val PixelSurfaceVariantLight = Color(0xFFF1F5F9)
val PixelOnSurfaceVariantLight = Color(0xFF475569)
val PixelOutlineLight = Color(0xFFCBD5E1)
val PixelOutlineVariantLight = Color(0xFFE2E8F0)

// Dark Theme Palette - Deep Slate / Charcoal
val PixelNavyPrimaryDark = Color(0xFF60A5FA)
val PixelNavyOnPrimaryDark = Color(0xFF0F172A)
val PixelPrimaryContainerDark = Color(0xFF1E3A8A)
val PixelOnPrimaryContainerDark = Color(0xFFDBEAFE)

val PixelSecondaryDark = Color(0xFF94A3B8)
val PixelOnSecondaryDark = Color(0xFF0F172A)
val PixelSecondaryContainerDark = Color(0xFF1E293B)
val PixelOnSecondaryContainerDark = Color(0xFFF8FAFC)

val PixelTertiaryDark = Color(0xFF38BDF8)
val PixelOnTertiaryDark = Color(0xFF0F172A)
val PixelTertiaryContainerDark = Color(0xFF0369A1)
val PixelOnTertiaryContainerDark = Color(0xFFE0F2FE)

val PixelBackgroundDark = Color(0xFF0B1120)
val PixelOnBackgroundDark = Color(0xFFF8FAFC)
val PixelSurfaceDark = Color(0xFF111827)
val PixelOnSurfaceDark = Color(0xFFF8FAFC)
val PixelSurfaceVariantDark = Color(0xFF1F2937)
val PixelOnSurfaceVariantDark = Color(0xFF9CA3AF)
val PixelOutlineDark = Color(0xFF374151)
val PixelOutlineVariantDark = Color(0xFF1F2937)

val ClarityCyan = Color(0xFF0EA5E9)
val ClarityEmerald = Color(0xFF10B981)
val ClarityAmber = Color(0xFFF59E0B)
val ClarityRose = Color(0xFFF43F5E)

// Distinctive element gradients to make UI components stand out
val LightGradientWhite = androidx.compose.ui.graphics.Brush.verticalGradient(
    listOf(Color(0xFFFFFFFF), Color(0xFFF1F5F9))
)
val LightCardGradient = androidx.compose.ui.graphics.Brush.linearGradient(
    listOf(Color(0xFFFFFFFF), Color(0xFFF8FAFC), Color(0xFFF1F5F9))
)

val DarkGradientBlack = androidx.compose.ui.graphics.Brush.verticalGradient(
    listOf(Color(0xFF1E293B), Color(0xFF0F172A))
)
val DarkCardGradient = androidx.compose.ui.graphics.Brush.linearGradient(
    listOf(Color(0xFF1E293B), Color(0xFF131D2E), Color(0xFF0B1120))
)
