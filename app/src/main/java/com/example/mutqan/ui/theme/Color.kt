package com.example.mutqan.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// ── Paper, light: cream paper, ink text, deep green accent ─────────────────────
val PaperBg                    = Color(0xFFF4EDDC)
val PaperSurface               = Color(0xFFF8F2E4)
val PaperSurfaceVariant        = Color(0xFFEBE1C8)
val PaperInk                   = Color(0xFF1F1A14)
val PaperInkSoft               = Color(0xFF6B5F4B)
val PaperRule                  = Color(0xFFCDBB8E)
val PaperRuleSoft              = Color(0xFFDDD0AE)
val PaperGreen                 = Color(0xFF1F5A4A)
val PaperGreenContainer        = Color(0xFFCFE0D2)
val PaperOnGreenContainer      = Color(0xFF123A2F)
val PaperGold                  = Color(0xFFA8812F)
val PaperGoldText              = Color(0xFF7D5F16)
val PaperError                 = Color(0xFFA33A2C)

// ── Paper, dark: warm near-black, parchment text, gold accent ──────────────────
val NightBg                    = Color(0xFF14110D)
val NightSurface               = Color(0xFF1B1712)
val NightSurfaceVariant        = Color(0xFF241F18)
val NightInk                   = Color(0xFFEDE3CF)
val NightInkSoft               = Color(0xFFA89B83)
val NightRule                  = Color(0xFF4A3F2A)
val NightRuleSoft              = Color(0xFF2E271B)
val NightGold                  = Color(0xFFD4A94C)
val NightGoldContainer         = Color(0xFF3A2F17)
val NightOnGoldContainer       = Color(0xFFF0D9A0)
val NightSage                  = Color(0xFF7FB9A3)
val NightError                 = Color(0xFFE9897F)

// ── Status colours: good (teal), fair (amber), weak (crimson) ─────────────────
// Used for text as well as for marks, so each comes in two versions: lighter on dark backgrounds and deeper shades of
// the same hues on light ones. Both pass 4.5:1 on every palette's background, surface and surface variant.
// The three hues sit far apart (about 190, 65 and 5 degrees on the colour wheel) and differ in lightness too, so they
// stay apart for readers who cannot tell red from green; the earlier sage, ochre and terracotta were all muted warm-ish
// tones of one lightness and looked alike. StatusColorsTest keeps the contrast and the distance.

class StatusColors(val good: Color, val fair: Color, val weak: Color)

val StatusOnLight = StatusColors(good = Color(0xFF076B6C), fair = Color(0xFF8A5206), weak = Color(0xFF830F3D))
val StatusOnDark  = StatusColors(good = Color(0xFF2DB9BD), fair = Color(0xFFF2CB62), weak = Color(0xFFDB6F63))

/** The status colours for text and marks on a light ([onLight]) or a dark background. */
fun statusColors(onLight: Boolean): StatusColors = if (onLight) StatusOnLight else StatusOnDark

/** Set by [MutqanTheme] for the app's own background. A surface with its own colour (the Mushaf page) uses [statusColors]. */
val LocalStatusColors = staticCompositionLocalOf { StatusOnLight }

val DifficultySmooth: Color    @Composable @ReadOnlyComposable get() = LocalStatusColors.current.good
val DifficultyStruggled: Color @Composable @ReadOnlyComposable get() = LocalStatusColors.current.fair
val DifficultyCritical: Color  @Composable @ReadOnlyComposable get() = LocalStatusColors.current.weak

// ── Structural Flow Palette ──────────────────────────────────────────────────
val StructuralEmerald = Color(0xFF2ECC71)
val StructuralTeal    = Color(0xFF1ABC9C)
val StructuralIndigo  = Color(0xFF3498DB)
val StructuralAmber   = Color(0xFFF1C40F)
