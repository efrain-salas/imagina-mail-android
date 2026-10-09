import java.util.Properties

plugins {
    id(ThunderbirdPlugins.App.androidCompose)
    alias(libs.plugins.tb.app.versioning)
}

val testCoverageEnabled = providers
    .gradleProperty("testCoverageEnabled")
    .isPresent

// imagina/imagina.properties declares every parameter; imagina/imagina.local.properties (ignored by git)
// may only override those, so a leftover or misspelt key never ends up inside the APK
val imaginaParameters: Map<String, String> = run {
    fun read(path: String): Map<String, String> = Properties().apply {
        val file = isolated.rootProject.projectDirectory.file(path).asFile
        if (file.exists()) file.inputStream().use { load(it) }
    }.entries.associate { (key, value) -> key.toString() to value.toString() }

    val declared = read("imagina/imagina.properties")
    declared + read("imagina/imagina.local.properties").filterKeys { it in declared }
}

android {
    namespace = "net.thunderbird.android"

    defaultConfig {
        applicationId = imaginaParameters.getValue("IMAGINA_APPLICATION_ID")
        testApplicationId = imaginaParameters.getValue("IMAGINA_APPLICATION_ID") + ".tests"

        versionCode = 33
        versionName = "0.1"

        buildConfigField("String", "CLIENT_INFO_APP_NAME", "\"Imagina Mail\"")

        // Imagina Mail parameters (IMAGINA.md): imagina/imagina.properties, committed, which
        // imagina/imagina.local.properties (local only) may override
        imaginaParameters.forEach { (name, value) ->
            buildConfigField("String", name, "\"$value\"")
        }
    }

    androidResources {
        // Keep in sync with the resource string array "supported_languages"
        localeFilters += listOf(
            "ar",
            "be",
            "bg",
            "br",
            "ca",
            "co",
            "cs",
            "cy",
            "da",
            "de",
            "el",
            "en",
            "en-rGB",
            "eo",
            "es",
            "et",
            "eu",
            "fa",
            "fi",
            "fr",
            "fy",
            "ga",
            "gd",
            "gl",
            "hr",
            "hu",
            "in",
            "is",
            "it",
            "iw",
            "ja",
            "ko",
            "lt",
            "lv",
            "nb",
            "nl",
            "nn",
            "pl",
            "pt-rBR",
            "pt-rPT",
            "ro",
            "ru",
            "sk",
            "sl",
            "sq",
            "sr",
            "sv",
            "ta-rIN",
            "tr",
            "uk",
            "vi",
            "zh-rCN",
            "zh-rTW",
        )
    }

    signingConfigs {
        val useUploadKey = providers.gradleProperty("tb.useUploadKey")
            .map(String::toBoolean)
            .orElse(true)
            .get()

        createSigningConfig(project, SigningType.TB_RELEASE, isUpload = useUploadKey)
        createSigningConfig(project, SigningType.TB_BETA, isUpload = useUploadKey)
        createSigningConfig(project, SigningType.TB_DAILY, isUpload = useUploadKey)
    }

    buildTypes {
        val isCI = providers.gradleProperty("ci")
            .map(String::toBoolean)
            .orElse(false)
        release {
            signingConfig = signingConfigs.getByType(SigningType.TB_RELEASE)

            isMinifyEnabled = !isCI.get()
            isShrinkResources = !isCI.get()
            isDebuggable = false

            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )

            buildConfigField("String", "GLEAN_RELEASE_CHANNEL", "\"release\"")
        }

        create("beta") {
            initWith(getByName("release"))

            signingConfig = signingConfigs.getByType(SigningType.TB_BETA)

            applicationIdSuffix = ".beta"

            isMinifyEnabled = isCI.get()
            isShrinkResources = isCI.get()
            isDebuggable = false

            matchingFallbacks += listOf("release")

            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )

            buildConfigField("String", "GLEAN_RELEASE_CHANNEL", "\"beta\"")
        }

        create("daily") {
            initWith(getByName("release"))

            signingConfig = signingConfigs.getByType(SigningType.TB_DAILY)

            applicationIdSuffix = ".daily"
            versionNameSuffix = "a1"

            isMinifyEnabled = isCI.get()
            isShrinkResources = isCI.get()
            isDebuggable = false

            matchingFallbacks += listOf("release")

            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )

            // See https://bugzilla.mozilla.org/show_bug.cgi?id=1918151
            buildConfigField("String", "GLEAN_RELEASE_CHANNEL", "\"nightly\"")
        }

        debug {
            versionNameSuffix = "-SNAPSHOT"

            enableUnitTestCoverage = testCoverageEnabled
            enableAndroidTestCoverage = testCoverageEnabled

            isMinifyEnabled = false
            isShrinkResources = false
            isDebuggable = true

            buildConfigField("String", "GLEAN_RELEASE_CHANNEL", "null")
        }
    }

    flavorDimensions += listOf("app")
    productFlavors {
        create("foss") {
            dimension = "app"
            buildConfigField("String", "PRODUCT_FLAVOR_APP", "\"foss\"")
        }

        create("full") {
            dimension = "app"
            buildConfigField("String", "PRODUCT_FLAVOR_APP", "\"full\"")
        }
    }

    bundle {
        language {
            // Don't split by language. Otherwise our in-app language switcher won't work.
            enableSplit = false
        }
    }

    packaging {
        jniLibs {
            excludes += listOf("kotlin/**")
        }

        resources {
            excludes += listOf(
                "META-INF/*.kotlin_module",
                "kotlin/**",
                "DebugProbesKt.bin",
            )
        }
    }
}

androidComponents {
    onVariants(selector().withBuildType("release")) { variant ->
        variant.packaging.resources.excludes.addAll(
            "META-INF/*.version",
        )
    }
}

// Initialize placeholders for the product flavor and build type combinations needed for dependency declarations.
// They are required to avoid "Unresolved configuration" errors.
val fullDebugImplementation = configurations.create("fullDebugImplementation")
val fullDailyImplementation = configurations.create("fullDailyImplementation")
val fullBetaImplementation = configurations.create("fullBetaImplementation")
val fullReleaseImplementation = configurations.create("fullReleaseImplementation")

dependencies {
    implementation(projects.appCommon)
    implementation(projects.core.ui.compose.common)
    implementation(projects.core.ui.legacy.theme2.thunderbird)
    implementation(projects.feature.launcher)

    implementation(projects.legacy.core)
    implementation(projects.legacy.ui.legacy)

    implementation(projects.core.featureflag)

    implementation(projects.feature.account.settings.impl)
    implementation(projects.feature.mail.message.list.api)
    implementation(projects.feature.mail.message.list.internal)
    implementation(projects.feature.mail.message.reader.api)

    implementation(projects.feature.widget.messageList)
    implementation(projects.feature.widget.messageListGlance)
    implementation(projects.feature.widget.shortcut)
    implementation(projects.feature.widget.unread)

    debugImplementation(projects.feature.telemetry.noop)
    "dailyImplementation"(projects.feature.telemetry.noop)
    "betaImplementation"(projects.feature.telemetry.noop)
    releaseImplementation(projects.feature.telemetry.noop)

    implementation(libs.androidx.work.runtime)

    implementation(projects.feature.autodiscovery.api)
    debugImplementation(projects.backend.demo)
    "dailyImplementation"(projects.backend.demo)
    debugImplementation(projects.feature.autodiscovery.demo)
    "dailyImplementation"(projects.feature.autodiscovery.demo)

    "fossImplementation"(projects.feature.funding.link)

    fullDebugImplementation(projects.feature.funding.googleplay)
    fullDailyImplementation(projects.feature.funding.googleplay)
    fullBetaImplementation(projects.feature.funding.googleplay)
    fullReleaseImplementation(projects.feature.funding.googleplay)

    implementation(projects.feature.onboarding.migration.thunderbird)
    implementation(projects.feature.migration.launcher.thunderbird)
    implementation(projects.feature.thundermail.api)
    implementation(projects.feature.thundermail.thunderbird)

    // Entrar con Imagina
    implementation(libs.appauth)
    implementation(libs.kotlinx.serialization.json)
    implementation(projects.core.ui.navigation)
    implementation(projects.feature.account.common)
    implementation(projects.feature.account.edit)
    implementation(projects.feature.account.oauth)
    implementation(projects.feature.account.settings.api)
    implementation(projects.feature.account.setup)
    implementation(projects.feature.onboarding.main)
    implementation(projects.feature.onboarding.permissions)
    implementation(projects.mail.protocols.imap)

    // TODO remove once OAuth ids have been moved from TBD to TBA
    releaseImplementation(libs.appauth)

    // Required for DependencyInjectionTest
    testImplementation(projects.feature.account.api)
    testImplementation(projects.feature.account.common)
    testImplementation(projects.feature.thundermail.internal.common)
    testImplementation(projects.plugins.openpgpApiLib.openpgpApi)
    testImplementation(projects.feature.changelog.internal)

    testImplementation(libs.appauth)

    // Tests of the link with Imagina's devices API
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(projects.core.logging.testing)
}

tasks.register("printConfigurations") {
    doLast {
        configurations.forEach { configuration ->
            println("Configuration: ${configuration.name}")
            configuration.dependencies.forEach { dependency ->
                println("  - ${dependency.group}:${dependency.name}:${dependency.version}")
            }
        }
    }
}

codeCoverage {
    branchCoverage = 0
    lineCoverage = 25
}
