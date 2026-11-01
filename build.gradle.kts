// Top-level build file — shared configuration across modules.
plugins {
    alias(libs.plugins.android.application) apply false
    // AGP 9 는 Kotlin 이 내장돼 모듈엔 적용하지 않는다 — 여기 선언은 내장 Kotlin 버전(기본 2.2.10)을 올리는 용도뿐.
    alias(libs.plugins.kotlin.android) apply false
}
