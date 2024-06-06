import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("usvm.kotlin-conventions")
    id("io.ktor.plugin") version Versions.ktor_version
    kotlin("plugin.serialization") version Versions.kotlinVersion
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
    implementation("${Versions.jacodbPackage}:jacodb-api-jvm:${Versions.jacodb}")
    implementation("${Versions.jacodbPackage}:jacodb-core:${Versions.jacodb}")

    implementation("io.ktor:ktor-server-core:${Versions.ktor_version}")
    implementation("io.ktor:ktor-server-netty:${Versions.ktor_version}")
    implementation("io.ktor:ktor-server-websockets:${Versions.ktor_version}")
    implementation("io.ktor:ktor-serialization-kotlinx-json:${Versions.ktor_version}")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:${Versions.kotlinxSerialization}")

    implementation("org.jetbrains.kotlinx:kotlinx-cli:${Versions.kotlinxCLI}")
    implementation("org.slf4j:slf4j-simple:${Versions.samplesSl4j}")

    testImplementation(kotlin("test"))
}
