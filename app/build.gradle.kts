import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

/**
 * versionName 을 마지막 커밋 날짜에서 "yy.M.d" 형식(예: 2026-09-19 → "26.9.19")으로 자동
 * 계산한다. ⚠️ **여기서 리터럴 문자열로 되돌리지 말 것** — CLAUDE.md "버전 관리" 절 참조.
 *
 * 현재 wall-clock 날짜가 아니라 **커밋 날짜**를 쓰는 이유: 이 값은 "이 코드가 언제 것인가"를
 * 나타내야 한다. 빌드 시각 기준이면 몇 달 뒤 같은 커밋을 다시 빌드했을 때(예: 오래된 브랜치를
 * 다시 빌드) 그 코드와 무관한 "오늘" 날짜가 찍혀 버전이 실제 코드 시점과 어긋난다. git 이
 * 없거나(소스만 배포된 경우) 커밋이 없는 새 저장소면 조용히 현재 날짜로 폴백한다 — 버전
 * 표시가 부정확해질지언정 빌드 자체가 깨지면 안 된다.
 */
fun gitCommitDateVersionName(): String {
    val fmt = DateTimeFormatter.ofPattern("yy.M.d")
    return runCatching {
        val proc = ProcessBuilder("git", "log", "-1", "--format=%cd", "--date=short")
            .directory(rootDir)
            .redirectErrorStream(true)
            .start()
        val output = proc.inputStream.bufferedReader().readText().trim()
        val exited = proc.waitFor(10, TimeUnit.SECONDS)
        check(exited && proc.exitValue() == 0 && output.isNotEmpty())
        LocalDate.parse(output).format(fmt)
    }.getOrElse { LocalDate.now().format(fmt) }
}

android {
    namespace = "com.langsense.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.langsense.app"
        minSdk = 29
        targetSdk = 35
        // ⚠️ versionCode 는 날짜와 무관한, 릴리스마다 수동으로 정확히 1씩 올리는 평범한 정수다.
        // (a) Play 스토어가 업로드마다 반드시 증가하는 정수를 요구하고, (b) Prefs.MARKER_SINCE /
        // isMarkerFresh 의 NEW·이사 안내 자동 만료 계산("도입 후 3버전 지나면 사라짐")이 이 값이
        // 매번 +1 이라는 전제에 의존한다. 날짜 기반(예: 20260919)으로 바꾸면 하루에 여러 번
        // 빌드/릴리스할 때 값이 그날 안에서 올라가지 않아 위 계산이 깨진다.
        versionCode = 2
        versionName = gitCommitDateVersionName()
    }

    // ── 고정 공유 서명키 (요청 사항: 삭제 없이 덮어쓰기 설치) ─────────────────────────
    // 모든 빌드(로컬/CI)가 같은 키로 서명되면 기존 설치 위에 바로 덮어쓰기가 된다.
    // 키스토어 경로/비밀번호는 저장소에 박지 않고 Gradle 속성(또는 환경변수 ORG_GRADLE_PROJECT_*)에서
    // 읽는다. 값이 없거나 파일이 없으면 기본 디버그 서명으로 안전하게 폴백한다(저장소에 비밀 없음).
    val sharedStoreFile = (project.findProperty("KIKI_STORE_FILE") as String?) ?: "kiki-shared.jks"
    val sharedStorePassword = project.findProperty("KIKI_STORE_PASSWORD") as String?
    val sharedKeyAlias = project.findProperty("KIKI_KEY_ALIAS") as String?
    val sharedKeyPassword = project.findProperty("KIKI_KEY_PASSWORD") as String?
    val sharedKeystore = file(sharedStoreFile)
    val useSharedSigning = sharedKeystore.exists() &&
        sharedStorePassword != null && sharedKeyAlias != null && sharedKeyPassword != null

    signingConfigs {
        if (useSharedSigning) {
            create("shared") {
                storeFile = sharedKeystore
                storePassword = sharedStorePassword
                keyAlias = sharedKeyAlias
                keyPassword = sharedKeyPassword
            }
        }
    }

    buildTypes {
        release {
            // 저사양 타겟: R8 로 미사용 코드 제거·최적화. 접근성 서비스/액티비티/JS 브리지는
            // proguard-rules.pro 의 keep 규칙으로 보호한다(래디얼 메뉴는 JS 가 문자열 이름으로
            // KikiNative.onItemTap/onDismiss/onReady 를 부르므로 난독화되면 조용히 먹통이 된다).
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (useSharedSigning) signingConfig = signingConfigs.getByName("shared")
        }
        debug {
            isMinifyEnabled = false
            // 공유 키가 설정돼 있으면 디버그도 그 키로 서명(머신/CI 간 서명 일치 → 덮어쓰기 설치 가능).
            if (useSharedSigning) signingConfig = signingConfigs.getByName("shared")
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
        viewBinding = true
    }

    sourceSets {
        getByName("main") {
            java.srcDirs("src/main/kotlin")
        }
        getByName("test") {
            java.srcDirs("src/test/kotlin")
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)

    testImplementation(libs.junit)
}

/**
 * 버전 문자열을 콘솔에 찍기만 하는 태스크. CI(`ci.yml`)가 매 실행마다 이 값을 Job Summary 에
 * 남겨, 저장소를 열어보지 않아도 "이 실행이 어떤 버전을 빌드했는지" Actions 탭에서 바로 보인다.
 */
tasks.register("printVersion") {
    doLast {
        println("${android.defaultConfig.versionName} (code ${android.defaultConfig.versionCode})")
    }
}
