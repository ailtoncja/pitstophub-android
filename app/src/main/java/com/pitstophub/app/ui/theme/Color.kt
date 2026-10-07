package com.pitstophub.app.ui.theme

import androidx.compose.ui.graphics.Color

/** Paleta "Velocity Control" (DESIGN.md do Stitch). O app e escuro por padrao, como o design. */
object PitColors {
    // Niveis de superficie
    val Carbon = Color(0xFF000000)            // Level 0: fundo
    val Surface = Color(0xFF121414)
    val SurfaceLowest = Color(0xFF0C0F0F)
    val SurfaceLow = Color(0xFF1A1C1C)
    val SurfaceContainer = Color(0xFF1E2020)
    val SurfaceHigh = Color(0xFF282A2B)
    val SurfaceHighest = Color(0xFF333535)
    val SurfaceBright = Color(0xFF37393A)
    val Asphalt = Color(0xFF15151E)           // Level 1: cards
    val AsphaltAlt = Color(0xFF1A1A24)        // linhas alternadas de tabela

    // Texto
    val OnSurface = Color(0xFFE2E2E2)
    val OnSurfaceVariant = Color(0xFFE9BCB5)
    val Outline = Color(0xFFAF8781)
    val OutlineVariant = Color(0xFF5E3F3A)

    // Marca
    val Primary = Color(0xFFFFB4A8)
    val OnPrimary = Color(0xFF680200)
    val PrimaryContainer = Color(0xFFE10600)  // Racing Red
    val OnPrimaryContainer = Color(0xFFFFF2F0)
    val InversePrimary = Color(0xFFC00500)

    val Secondary = Color(0xFFC7C5D2)
    val SecondaryContainer = Color(0xFF494853)
    val Tertiary = Color(0xFFC6C6C6)
    val TertiaryContainer = Color(0xFF707070)

    val Error = Color(0xFFFFB4AB)
    val ErrorContainer = Color(0xFF93000A)

    // Estados de pista
    val Success = Color(0xFF00D21D)
    val Warning = Color(0xFFFFD200)

    val Hairline = Color(0x1AFFFFFF)          // branco a 10%: borda de cards
    val HairlineSoft = Color(0x0DFFFFFF)      // branco a 5%
}
