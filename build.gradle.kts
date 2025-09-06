// ルートビルドスクリプト（必要最低限）
plugins {
    // バージョンは settings.gradle.kts の pluginManagement で管理
    id("com.android.application") apply false
    kotlin("android") apply false
}

tasks.register("clean", Delete::class) {
    delete(rootProject.layout.buildDirectory)
}

