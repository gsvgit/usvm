import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("usvm.kotlin-conventions")
    kotlin
}

repositories {
    mavenCentral()
}

tasks.test {
    useJUnitPlatform()
}

tasks.withType<KotlinCompile> {
    kotlinOptions.allWarningsAsErrors = false
}

dependencies {
    implementation(project(":usvm-jvm"))
    implementation(project(":usvm-core"))
    implementation(Libs.jacodb_api_jvm)
    implementation(Libs.jacodb_core)
    implementation(Libs.kotlinx_cli)

    implementation(Libs.slf4j_simple)
    implementation(Libs.logback)

    implementation("io.github.rchowell:dotlin:1.0.2")
    testImplementation(kotlin("test"))
}
