import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("usvm.kotlin-conventions")
    id(Plugins.Ktor)
    kotlin("plugin.serialization") version Versions.kotlin
    application
}

application {
    mainClass.set("org.usvm.MainKt")
}

ktor {
    fatJar {
        archiveFileName.set("app.jar")
    }
}

repositories {
    mavenCentral()
    maven { url = uri("https://maven.pkg.jetbrains.space/public/p/ktor/eap") }
}
tasks.withType<KotlinCompile> {
    kotlinOptions.allWarningsAsErrors = false
}

tasks.test {
    useJUnitPlatform()
}

dependencies {
    implementation(project(":usvm-jvm"))
    implementation(project(":usvm-core"))
    implementation(project(":usvm-runner"))
    implementation(Libs.jacodb_api_jvm)
    implementation(Libs.jacodb_core)

    implementation(Libs.ktor_server_core)
    implementation(Libs.ktor_server_netty)
    implementation(Libs.ktor_server_websockets)
    implementation(Libs.ktor_serialization)
    implementation(Libs.kotlinx_serialization_core)

    implementation(Libs.kotlinx_cli)
    implementation(Libs.slf4j_simple)

    testImplementation(kotlin("test"))
}
