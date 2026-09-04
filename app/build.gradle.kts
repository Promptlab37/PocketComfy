// Musí být úplně nahoře: v build.gradle.kts jinak `java.util.Properties`
// naráží na Gradle rozšíření `java` a nepřeloží se.
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

/**
 * Token pro kontrolu aktualizací se do APK zapéká, aby appka fungovala bez
 * nastavování. Čte se z `local.properties`, který je v .gitignore – do
 * repozitáře se tedy nikdy nedostane. Když chybí, appka se zeptá v Nastavení
 * jako dřív, jen se nesestaví s předvyplněným tokenem.
 */
val localProps: Properties = run {
    val props = Properties()
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { props.load(it) }
    props
}
val githubToken: String = localProps.getProperty("githubToken", "")

/**
 * Osobní síťové údaje (výchozí adresa serveru, rychlé volby) žijí taky jen
 * v `local.properties`. Veřejné sestavení je nemá – appka se pak při prvním
 * spuštění zeptá na adresu ComfyUI místo toho, aby v sobě nesla cizí IP.
 * Formát rychlých voleb: `url|popisek;url|popisek` (bez diakritiky, soubor
 * .properties se čte v Latin-1).
 */
val defaultServer: String = localProps.getProperty("defaultServer", "")
val serverPresets: String = localProps.getProperty("serverPresets", "")

android {
    namespace = "cz.promptlab.h3video"
    compileSdk = 35

    defaultConfig {
        // Filament (prohlížeč 3D modelu) nese nativní knihovny pro každou
        // architekturu zvlášť a se všemi čtyřmi má APK přes 30 MB. Zůstávají
        // dvě, které se opravdu použijí: arm64 pro telefony a x86_64 pro
        // emulátor. Starší 32bitové telefony (armeabi-v7a) appka stejně
        // neutáhne a x86 emulátor už nikdo nepoužívá.
        ndk { abiFilters += listOf("arm64-v8a", "x86_64") }

        applicationId = "cz.promptlab.h3video"
        minSdk = 26
        targetSdk = 35
        versionCode = 131
        versionName = "3.19"
        vectorDrawables { useSupportLibrary = true }
        buildConfigField("String", "GITHUB_TOKEN", "\"$githubToken\"")
        buildConfigField("String", "DEFAULT_SERVER", "\"$defaultServer\"")
        buildConfigField("String", "SERVER_PRESETS", "\"$serverPresets\"")
    }

    // Hesla podpisu drží jen local.properties (v .gitignore), do repozitáře
    // nesmí. Bez nich se release sestaví nepodepsaný — do telefonu pak nejde
    // nainstalovat, což je viditelnější než tiché heslo v repozitáři.
    val storePwd = localProps.getProperty("storePassword")
    val keyPwd = localProps.getProperty("keyPassword")
    signingConfigs {
        if (storePwd != null && keyPwd != null) {
            create("release") {
                storeFile = file("../keystore/h3video.jks")
                storePassword = storePwd
                keyAlias = localProps.getProperty("keyAlias") ?: "h3video"
                keyPassword = keyPwd
            }
        } else {
            logger.warn("local.properties nemá storePassword/keyPassword – release APK bude nepodepsané.")
        }
    }

    buildTypes {
        release {
            // R8 v plném režimu + zahození nepoužitých zdrojů. Bez toho se do APK
            // balí celý Compose, Media3 i OkHttp včetně kódu, který appka nevolá.
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.findByName("release")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        debug {
            applicationIdSuffix = ".debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-process:2.8.7")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.exifinterface:exifinterface:1.3.7")

    // Prohlížeč 3D modelu (karta 3D model). Compose-nativní nadstavba nad
    // Google Filamentem — jediná udržovaná cesta, jak na Androidu vykreslit
    // .glb. Sceneform Google v roce 2021 zahodil a holý Filament je stovky
    // řádků na jednu kostku. WebView s model-viewerem to nezvládl.
    // POZOR na verzi: 4.x je přeložené Kotlinem 2.4 a projekt jede na 2.0 —
    // překlad pak padá na „incompatible version of Kotlin". 2.2.1 je poslední,
    // která stojí na Kotlinu 1.9, tedy na tom, co náš překladač přečte.
    implementation("io.github.sceneview:sceneview:2.2.1")

    val composeBom = platform("androidx.compose:compose-bom:2024.09.03")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    implementation("androidx.media3:media3-exoplayer:1.4.1")
    implementation("androidx.media3:media3-ui:1.4.1")

    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    testImplementation("junit:junit:4.13.2")
    // org.json je v android.jar jen prázdná skořápka, která v testech hází
    // „not mocked". Skutečná knihovna je stejná implementace, takže testy
    // ověřují přesně to, co poběží v telefonu.
    testImplementation("org.json:json:20240303")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
























