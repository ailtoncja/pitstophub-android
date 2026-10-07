// Plugins declarados aqui sem aplicar; cada modulo aplica o que precisa.
// O AGP 9 ja traz o suporte a Kotlin embutido, entao nao ha plugin kotlin-android.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}
