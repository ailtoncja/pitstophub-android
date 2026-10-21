package com.pitstophub.app.ui

/**
 * Mapas de circuito: os mapas oficiais da F1 2026 e os desenhos do OpenStreetMap do site, exportados para
 * `assets/circuits/<nome>.webp` por tools/export-web-data.mjs. A ficha guarda o caminho do site
 * ("/circuits/daytona-oval.png"); aqui ele vira o endereco do asset para o Coil.
 */
object CircuitArt {

    const val OSM_COPYRIGHT_URL = "https://www.openstreetmap.org/copyright"

    /** Endereco do asset do mapa ("file:///android_asset/circuits/sepang.webp"), ou null sem mapa. */
    fun assetFor(trackImage: String?): String? {
        val name = trackImage?.substringAfterLast('/')?.substringBeforeLast('.')?.takeIf { it.isNotBlank() } ?: return null
        return "file:///android_asset/circuits/$name.webp"
    }
}
