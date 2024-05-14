plugins {
    `kotlin-dsl`
}

val detektVersion = "1.23.5"
val gjavahVersion = "0.3.1"
val kotlinVersion = "1.9.20"
val logback_version = "1.5.6"

repositories {
    mavenCentral()
    gradlePluginPortal()
    maven("https://jitpack.io")
}


dependencies {
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlinVersion")
    implementation("io.gitlab.arturbosch.detekt:detekt-gradle-plugin:$detektVersion")
    implementation("org.glavo:gjavah:$gjavahVersion")
    implementation("ch.qos.logback:logback-classic:$logback_version")
}
