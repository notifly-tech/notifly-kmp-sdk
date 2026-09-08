import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.jvm.tasks.Jar

plugins {
    kotlin("multiplatform") version "2.2.21"
    id("dev.petuska.npm.publish") version "3.5.3"
    `maven-publish`
    signing
}

group = providers.environmentVariable("GROUP").getOrElse("tech.notifly")
version = providers.environmentVariable("VERSION").getOrElse("0.1.0-alpha.1")

val mavenRootArtifactId = providers.environmentVariable("MAVEN_ROOT_ARTIFACT_ID").getOrElse("notifly-kmp-sdk")
val mavenJvmArtifactId = providers.environmentVariable("MAVEN_JVM_ARTIFACT_ID").getOrElse("notifly-kmp-sdk-jvm")
val npmPackageName = providers.environmentVariable("NPM_PACKAGE_NAME").getOrElse("notifly-kmp-sdk")
val appleFrameworkName = providers.environmentVariable("APPLE_FRAMEWORK_NAME").getOrElse("NotiflyKMP")
val appleFrameworkIsStatic = providers.environmentVariable("APPLE_FRAMEWORK_IS_STATIC")
    .map(String::toBooleanStrict)
    .getOrElse(true)

kotlin {
    compilerOptions {
        // Keep published common/JVM metadata consumable by the Notifly Android SDK's Kotlin 1.8.10 compiler.
        languageVersion.set(KotlinVersion.fromVersion("1.8"))
        apiVersion.set(KotlinVersion.fromVersion("1.8"))
    }

    jvm()

    js(IR) {
        nodejs()
        binaries.library()
        generateTypeScriptDefinitions()
    }

    val xcframework = XCFramework(appleFrameworkName)
    listOf(
        iosArm64(),
        iosSimulatorArm64(),
        iosX64(),
    ).forEach { target ->
        target.binaries.framework {
            baseName = appleFrameworkName
            isStatic = appleFrameworkIsStatic
            xcframework.add(this)
        }
    }

    sourceSets {
        commonMain.dependencies {
            api("org.jetbrains.kotlin:kotlin-stdlib-common:1.8.10")
        }

        jvmMain.dependencies {
            api("org.jetbrains.kotlin:kotlin-stdlib:1.8.10")
        }

        jsMain.dependencies {
            // Kotlin/JS requires the stdlib version matching the Kotlin Gradle plugin.
            api(kotlin("stdlib-js"))
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

npmPublish {
    packages {
        named("js") {
            packageName = npmPackageName
            version = project.version.toString()
            readme = rootProject.file("README.md")
            files {
                from(rootProject.file("LICENSE"))
            }

            packageJson {
                license = "MIT"
                description = "Shared Kotlin Multiplatform implementation used by the Notifly SDKs"
                repository {
                    type = "git"
                    url = "https://github.com/notifly-tech/notifly-kmp-sdk.git"
                }
            }
        }
    }
}

val emptyJavadocJar by tasks.registering(Jar::class) {
    archiveClassifier.set("javadoc")
}

val centralPublicationNames = setOf("kotlinMultiplatform", "jvm")
publishing {
    publications.withType<MavenPublication>().configureEach {
        if (name in centralPublicationNames) {
            artifactId = if (name == "kotlinMultiplatform") {
                mavenRootArtifactId
            } else {
                mavenJvmArtifactId
            }
            artifact(emptyJavadocJar)
            pom {
                name.set("Notifly KMP SDK")
                description.set("Shared Kotlin Multiplatform implementation used by the Notifly SDKs")
                url.set("https://github.com/notifly-tech/notifly-kmp-sdk")
                licenses {
                    license {
                        name.set("MIT License")
                        url.set("https://opensource.org/licenses/MIT")
                        distribution.set("repo")
                    }
                }
                developers {
                    developer {
                        name.set("Notifly")
                        organization.set("Notifly")
                        organizationUrl.set("https://notifly.tech")
                    }
                }
                scm {
                    connection.set("scm:git:https://github.com/notifly-tech/notifly-kmp-sdk.git")
                    developerConnection.set("scm:git:ssh://git@github.com/notifly-tech/notifly-kmp-sdk.git")
                    url.set("https://github.com/notifly-tech/notifly-kmp-sdk")
                    tag.set(providers.environmentVariable("KMP_SOURCE_TAG").getOrElse("HEAD"))
                }
                properties.put(
                    "notifly.kmp.source.commit",
                    providers.environmentVariable("KMP_SOURCE_COMMIT").getOrElse("unknown"),
                )
            }
        }
    }

    repositories {
        maven {
            name = "centralStaging"
            url = uri(
                providers.environmentVariable("CENTRAL_STAGING_REPOSITORY")
                    .getOrElse(rootProject.layout.buildDirectory.dir("central-staging").get().asFile.toURI().toString()),
            )
        }
    }
}

val signingKey = providers.environmentVariable("SIGNING_KEY")
val signingPassword = providers.environmentVariable("SIGNING_PASSWORD")
if (signingKey.isPresent) {
    signing {
        useInMemoryPgpKeys(signingKey.get(), signingPassword.orNull)
        sign(publishing.publications.matching { it.name in centralPublicationNames })
    }
}
