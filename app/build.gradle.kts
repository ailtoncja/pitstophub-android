import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// Segredos e URLs ficam em local.properties (fora do git). Ver README.md.
val localProps = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

// Chave que assina o APK de release (keystore.properties fica fora do git). Sem o arquivo o release sai sem assinatura
// e nao instala no celular.
val keystoreProps = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

// -PSUPABASE_URL=... na linha de comando vence o local.properties (serve para apontar o app a um servidor de teste).
fun buildConfigString(name: String): String {
    val value = (project.findProperty(name) as String?) ?: localProps.getProperty(name) ?: ""
    return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
}

android {
    namespace = "com.pitstophub.app"

    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.pitstophub.app"
        minSdk = 26
        targetSdk = 37
        // Quem ja instalou so atualiza por cima se o versionCode do novo APK for maior.
        versionCode = 6
        versionName = "1.3.2"

        // Mesmos valores do .env do PWA (VITE_SUPABASE_URL / VITE_SUPABASE_ANON_KEY).
        buildConfigField("String", "SUPABASE_URL", buildConfigString("SUPABASE_URL"))
        buildConfigField("String", "SUPABASE_ANON_KEY", buildConfigString("SUPABASE_ANON_KEY"))
        // Mesmo valor de VITE_VAPID_PUBLIC_KEY, caso o push seja portado depois.
        buildConfigField("String", "VAPID_PUBLIC_KEY", buildConfigString("VAPID_PUBLIC_KEY"))

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (keystoreProps.getProperty("storeFile") != null) {
            create("release") {
                storeFile = file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    lint {
        // ic_launcher_logo.png (a logo do PWA) preenche o quadrado, mas entra no icone adaptativo
        // com inset de 18% (ic_launcher_foreground.xml), entao fica dentro da zona segura.
        disable += "IconLauncherShape"
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.work.runtime.ktx)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.okhttp)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
