import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.kover)
}

android {
    namespace = "com.bytegrep.oplist_manager"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.bytegrep.oplist_manager"
        minSdk = 30
        targetSdk = 36
        // CI 发版时通过 -PappVersionCode / -PappVersionName 注入（取自 Git tag），本地构建使用默认值
        versionCode = providers.gradleProperty("appVersionCode").orNull?.toInt() ?: 1
        versionName = providers.gradleProperty("appVersionName").orNull ?: "1.0.0"

        // 仪器测试使用 Hilt 测试 Application，见 androidTest/.../HiltTestRunner.kt
        testInstrumentationRunner = "com.bytegrep.oplist_manager.HiltTestRunner"
    }

    // 正式签名：读取根目录 keystore/keystore.properties（已被 git 忽略）；缺失时 release 包不签名
    val keystoreProps = rootProject.file("keystore/keystore.properties")
        .takeIf { it.isFile }
        ?.let { file -> Properties().apply { file.inputStream().use(::load) } }
    val releaseSigning = keystoreProps?.let { props ->
        signingConfigs.create("release") {
            storeFile = rootProject.file("keystore/${props.getProperty("storeFile")}")
            storePassword = props.getProperty("storePassword")
            keyAlias = props.getProperty("keyAlias")
            keyPassword = props.getProperty("keyPassword")
            enableV1Signing = false
            enableV2Signing = true
            // AGP 9 在 minSdk>=28 且启用 v3 时会丢弃 v2，部分国产安装器仅认 v2，故只签 v2
            enableV3Signing = false
        }
    }

    buildTypes {
        release {
            signingConfig = releaseSigning
            // 整包 R8 优化；仅限定 packageScope 时 kotlin 标准库类被重命名后会出现跨包 IllegalAccessError
            optimization {
                enable = true
            }
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
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.exoplayer.hls)
    implementation(libs.androidx.media3.exoplayer.dash)
    implementation(libs.androidx.media3.datasource.okhttp)
    implementation(libs.androidx.media3.ui)
    // FFmpeg 音频解码扩展（DTS / TrueHD / AC3 等软解兜底），由 .github/workflows/build-ffmpeg-decoder.yml 构建；
    // 版本须与 media3 一致，升级 media3 时需重新构建并替换
    implementation(files("libs/lib-decoder-ffmpeg-release.aar"))
    implementation(libs.androidx.media3.decoder)
    implementation(libs.androidx.media3.ui.compose)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.work.runtime.ktx)

    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.mockk)
    testImplementation(libs.okhttp.mockwebserver)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.hilt.android.testing)
    androidTestImplementation(libs.okhttp.mockwebserver)
    kspAndroidTest(libs.hilt.compiler)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}

// 单元测试覆盖率：./gradlew koverHtmlReportDebug / koverXmlReportDebug
// 只统计业务逻辑层；Compose 界面、Activity、DI 与生成代码由仪器测试或人工验证覆盖
kover {
    reports {
        filters {
            excludes {
                classes(
                    "*.BuildConfig",
                    "*_Factory", "*_Factory\$*", "*_MembersInjector",
                    "*_HiltModules*", "*Hilt_*", "hilt_aggregated_deps.*", "dagger.hilt.*",
                    "*_Impl*", "*\$\$serializer",
                    "*ComposableSingletons*",
                    "com.bytegrep.oplist_manager.di.*",
                    "com.bytegrep.oplist_manager.navigation.*",
                    "com.bytegrep.oplist_manager.core.designsystem.*",
                    "com.bytegrep.oplist_manager.MainActivity*",
                    "com.bytegrep.oplist_manager.OpenListApp*",
                    "com.bytegrep.oplist_manager.feature.player.VideoPlayerActivity*",
                    "*ScreenKt*", "*ComponentsKt*", "*SheetsKt*", "*DialogKt*",
                )
                annotatedBy("androidx.compose.runtime.Composable")
            }
        }
        // 行覆盖率低于 80% 时 koverVerifyDebug 失败（CI 执行）
        verify {
            rule {
                minBound(80)
            }
        }
    }
}
