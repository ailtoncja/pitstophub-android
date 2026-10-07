package com.pitstophub.app.domain

/**
 * Bandeira (emoji) por nacionalidade. O catalogo guarda o pais em portugues ("Reino Unido"), entao o mapa
 * usa o mesmo texto como chave. Copia de src/nationality-flags.ts do PWA.
 */
object Nationality {
    private val flags = mapOf(
        "África do Sul" to "🇿🇦", "Alemanha" to "🇩🇪", "Andorra" to "🇦🇩", "Angola" to "🇦🇴",
        "Argentina" to "🇦🇷", "Austrália" to "🇦🇺", "Áustria" to "🇦🇹", "Bélgica" to "🇧🇪",
        "Brasil" to "🇧🇷", "Bulgária" to "🇧🇬", "Canadá" to "🇨🇦", "Chile" to "🇨🇱",
        "China" to "🇨🇳", "Cingapura" to "🇸🇬", "Colômbia" to "🇨🇴", "Coreia do Sul" to "🇰🇷",
        "Costa Rica" to "🇨🇷", "Dinamarca" to "🇩🇰", "Espanha" to "🇪🇸", "Estônia" to "🇪🇪",
        "EUA" to "🇺🇸", "Finlândia" to "🇫🇮", "França" to "🇫🇷", "Hong Kong" to "🇭🇰",
        "Ilhas Cayman" to "🇰🇾", "Índia" to "🇮🇳", "Indonésia" to "🇮🇩", "Irlanda" to "🇮🇪",
        "Itália" to "🇮🇹", "Japão" to "🇯🇵", "Letônia" to "🇱🇻", "Luxemburgo" to "🇱🇺",
        "México" to "🇲🇽", "Mônaco" to "🇲🇨", "Noruega" to "🇳🇴", "Nova Zelândia" to "🇳🇿",
        "Países Baixos" to "🇳🇱", "Paraguai" to "🇵🇾", "Polônia" to "🇵🇱", "Portugal" to "🇵🇹",
        "Reino Unido" to "🇬🇧", "Romênia" to "🇷🇴", "Rússia" to "🇷🇺", "Sri Lanka" to "🇱🇰",
        "Suécia" to "🇸🇪", "Suíça" to "🇨🇭", "Tailândia" to "🇹🇭", "Turquia" to "🇹🇷",
    )

    fun flag(nationality: String?): String = nationality?.let(flags::get).orEmpty()

    // O catalogo guarda o pais em portugues; em ingles, o nome vem desta tabela (sem entrada, mostra o original).
    private val english = mapOf(
        "África do Sul" to "South Africa", "Alemanha" to "Germany", "Austrália" to "Australia", "Áustria" to "Austria",
        "Bélgica" to "Belgium", "Brasil" to "Brazil", "Bulgária" to "Bulgaria", "Canadá" to "Canada",
        "Cingapura" to "Singapore", "Colômbia" to "Colombia", "Coreia do Sul" to "South Korea", "Dinamarca" to "Denmark",
        "Espanha" to "Spain", "Estônia" to "Estonia", "EUA" to "USA", "Finlândia" to "Finland", "França" to "France",
        "Ilhas Cayman" to "Cayman Islands", "Índia" to "India", "Indonésia" to "Indonesia", "Irlanda" to "Ireland",
        "Itália" to "Italy", "Japão" to "Japan", "Letônia" to "Latvia", "Luxemburgo" to "Luxembourg", "México" to "Mexico",
        "Mônaco" to "Monaco", "Noruega" to "Norway", "Nova Zelândia" to "New Zealand", "Países Baixos" to "Netherlands",
        "Polônia" to "Poland", "Reino Unido" to "United Kingdom", "Romênia" to "Romania", "Rússia" to "Russia",
        "Suécia" to "Sweden", "Suíça" to "Switzerland", "Tailândia" to "Thailand", "Turquia" to "Turkey",
    )

    /** Nome do pais no idioma do app. */
    fun label(nationality: String, portuguese: Boolean): String = if (portuguese) nationality else english[nationality] ?: nationality
}
