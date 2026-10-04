plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.kotlin.plugin.serialization")
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "failed", "skipped")
    }
}

// 协议测试向量与 C# 端共用同一份文件（protocol/testvectors/frames.json），
// 由 tools/gen_testvectors.py 生成。两端对同一字节序列必须得出同一结论。
val protocolTestVectors = rootProject.projectDir.resolve("../../protocol/testvectors")

require(protocolTestVectors.isDirectory) {
    "找不到协议测试向量目录：$protocolTestVectors（先运行 tools/gen_testvectors.py）"
}

tasks.named<ProcessResources>("processTestResources") {
    from(protocolTestVectors) { into("testvectors") }
}
