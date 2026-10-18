package com.pitstophub.app.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Paleta "Reel" (a mesma do site, DESIGN.md do repositorio pitstophub): fundo preto de midia, paineis neutros,
 * branco como cor de interface e o vermelho de marca reservado para largada, ao vivo e "seguindo".
 * Os nomes antigos continuam existindo com valores novos.
 */
object PitColors {
    // Superficies
    val Carbon = Color(0xFF000000)
    val Surface = Color(0xFF000000)
    val SurfaceLowest = Color(0xFF060607)
    val SurfaceLow = Color(0xFF121214)
    val SurfaceContainer = Color(0xFF17171A)
    val SurfaceHigh = Color(0xFF1E1E22)
    val SurfaceHighest = Color(0xFF26262B)
    val SurfaceBright = Color(0xFF2A2A2E)
    val Asphalt = Color(0xFF0E0E10)
    val AsphaltAlt = Color(0xFF151518)

    // Texto
    val OnSurface = Color(0xFFF4F4F6)
    val OnSurfaceVariant = Color(0xFFB9B9C0)
    val Silver = Color(0xFFB9B9C0)
    val SilverDim = Color(0xFF8F8F98)
    val Outline = Color(0xFF6F6F78)
    val OutlineVariant = Color(0xFF3A3A40)

    // Interface: branco. Vermelho so para largada, ao vivo e seguindo.
    val Primary = Color(0xFFFFFFFF)
    val OnPrimary = Color(0xFF000000)
    val PrimaryContainer = Color(0xFFE10600)
    val OnPrimaryContainer = Color(0xFFFFFFFF)
    val InversePrimary = Color(0xFFC00500)
    val RacingRed = Color(0xFFE10600)
    val Signal = Color(0xFFFF5A4F) // vermelho legivel como texto sobre preto

    val Secondary = Color(0xFFB9B9C0)
    val SecondaryContainer = Color(0xFF26262B)
    val Tertiary = Color(0xFFB9B9C0)
    val TertiaryContainer = Color(0xFF3A3A40)

    val Error = Color(0xFFFF8A80)
    val ErrorContainer = Color(0xFF93000A)

    // Estados
    val Success = Color(0xFF4AE176)
    val Warning = Color(0xFFF5B301)

    val Hairline = Color(0x1FFFFFFF)     // branco a 12%
    val HairlineSoft = Color(0x12FFFFFF) // branco a 7%
}
