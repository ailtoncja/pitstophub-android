package com.pitstophub.app.ui

import androidx.compose.runtime.staticCompositionLocalOf
import com.pitstophub.app.AppContainer

/** Acesso aos repositorios para telas e folhas simples, sem criar um ViewModel para cada uma. */
val LocalAppContainer = staticCompositionLocalOf<AppContainer> { error("AppContainer nao foi fornecido") }
