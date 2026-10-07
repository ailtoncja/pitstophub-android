package com.pitstophub.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalConfiguration
import java.util.Locale

/** Locale do app, lido da configuracao observavel do Compose (recompõe se o idioma mudar). */
@Composable
@ReadOnlyComposable
fun currentLocale(): Locale = LocalConfiguration.current.locales[0]

/** O catalogo traz textos em portugues e ingles; o idioma do aparelho escolhe qual mostrar. */
@Composable
@ReadOnlyComposable
fun isPortuguese(): Boolean = currentLocale().language == "pt"
