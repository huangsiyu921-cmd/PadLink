// 版本组合沿用本机已验证可用的搭配（Kanji-Dojo）：Gradle 8.7 + AGP 8.5.2 + Kotlin 2.1.20 + JDK 17。
// 注意：JDK 25 会破坏 Kotlin 编译器，构建必须用 JDK 17（JAVA_HOME）。
plugins {
    id("com.android.application") version "8.5.2" apply false
    id("org.jetbrains.kotlin.android") version "2.1.20" apply false
    id("org.jetbrains.kotlin.jvm") version "2.1.20" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.1.20" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.20" apply false
}
