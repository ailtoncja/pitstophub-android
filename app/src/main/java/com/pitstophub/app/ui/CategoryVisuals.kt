package com.pitstophub.app.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Terrain
import androidx.compose.material.icons.filled.Timer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

/** Identidade visual de cada serie: o mesmo icone e a mesma cor de destaque do site. */
object CategoryVisuals {

    private val icons: Map<String, ImageVector> = mapOf(
        "f1" to Icons.Filled.Speed,
        "f2" to Icons.Filled.Speed,
        "f3" to Icons.Filled.Speed,
        "f1-academy" to Icons.Filled.EmojiEvents,
        "indy" to Icons.Filled.Flag,
        "nascar" to Icons.Filled.DirectionsCar,
        "imsa" to Icons.Filled.Bolt,
        "wec" to Icons.Filled.Timer,
        "dtm" to Icons.Filled.DirectionsCar,
        "gtwc-europe" to Icons.Filled.DirectionsCar,
        "gtwc-america" to Icons.Filled.DirectionsCar,
        "gtwc-asia" to Icons.Filled.DirectionsCar,
        "endurance-brasil" to Icons.Filled.Shield,
        "wrc" to Icons.Filled.Terrain,
    )

    private val accents: Map<String, Color> = mapOf(
        "f1" to Color(0xFFE10600),
        "f2" to Color(0xFF0093CC),
        "f3" to Color(0xFFFF8700),
        "f1-academy" to Color(0xFFE91E8C),
        "wec" to Color(0xFF00A19B),
        "imsa" to Color(0xFFFFC107),
        "dtm" to Color(0xFF6C63FF),
        "indy" to Color(0xFF38BDF8),
        "nascar" to Color(0xFFF59E0B),
        "wrc" to Color(0xFF22C55E),
        "gtwc-europe" to Color(0xFF2ECC71),
        "gtwc-america" to Color(0xFFF39C12),
        "gtwc-asia" to Color(0xFF9B59B6),
        "endurance-brasil" to Color(0xFF009B3A),
    )

    fun icon(categoryId: String): ImageVector = icons[categoryId] ?: Icons.Filled.Speed

    fun accent(categoryId: String): Color = accents[categoryId] ?: Color(0xFFE10600)
}
