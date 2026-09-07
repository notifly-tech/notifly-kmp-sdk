plugins {
    kotlin("jvm") version "1.8.10"
    application
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    kotlinOptions.jvmTarget = "1.8"
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

dependencies {
    val notiflyKmpVersion = providers.environmentVariable("VERSION").getOrElse("0.1.0-alpha.1")
    implementation("tech.notifly:notifly-kmp-sdk:$notiflyKmpVersion")
}

application {
    mainClass.set("MainKt")
}
