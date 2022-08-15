import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.jetbrains.kotlin.gradle.plugin.KotlinPluginWrapper
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    alias(libs.plugins.kotlinGradlePlugin) apply false
    alias(libs.plugins.kotlinBinaryCompatibilityPlugin) apply false
    alias(libs.plugins.protobufGradlePlugin) apply false
    id("com.github.ben-manes.versions") version "0.42.0"
    id("nl.littlerobots.version-catalog-update") version "0.5.3"
}

repositories {
    mavenCentral()
    gradlePluginPortal()
}

buildscript {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

subprojects {
    buildscript {
        repositories {
            mavenCentral()
            gradlePluginPortal()
        }
    }

    repositories {
        mavenCentral()
        maven(url = "https://s3-us-west-2.amazonaws.com/dynamodb-local/release")
    }

    if (!path.startsWith(":wisp-bom")) {
        apply(plugin = "kotlin")
        apply(plugin = "org.jetbrains.kotlinx.binary-compatibility-validator")
        apply(plugin = "com.google.protobuf")
    }
    apply(plugin = "maven-publish")
    apply(plugin = "version-catalog")

    // Only apply if the project has the kotlin plugin added:
    plugins.withType<KotlinPluginWrapper> {
        val compileKotlin by tasks.getting(KotlinCompile::class) {
            kotlinOptions {
                jvmTarget = "11"
                allWarningsAsErrors = true
            }
        }
        val compileTestKotlin by tasks.getting(KotlinCompile::class) {
            kotlinOptions {
                jvmTarget = "11"
                allWarningsAsErrors = true
            }
        }

        dependencies {
            add("testImplementation", project.rootProject.libs.junitApi)
            add("testRuntimeOnly", project.rootProject.libs.junitEngine)

            // Platform/BOM dependencies constrain versions only.
            // Enforce wisp-bom -- it should take priority over external BOMs.
            add("api", enforcedPlatform(project(":wisp-bom")))
            add("api", platform(project.rootProject.libs.grpcBom))
            add("api", platform(project.rootProject.libs.jacksonBom))
            add("api", platform(project.rootProject.libs.nettyBom))

            // The kotlin API surface used in this library is not exposed via
            // the external API, so we shouldn't be forcing downstream consumers
            // to use a particular kotlin version. Doing this can cause compilation
            // failures for downstream repositories that want to use wisp APIs
            // in their buildscripts or plugins but still want to use an older
            // version of the kotlin compiler. Use 'implementation' instead of 'api'
            // so that we're not forcing downstream consumers to adopt our kotlin
            // BOM versions.
            add("implementation", platform(project.rootProject.libs.kotlinBom))
        }

        tasks.withType<GenerateModuleMetadata> {
            suppressedValidationErrors.add("enforced-platform")
        }
    }

    tasks.withType<Test> {
        useJUnitPlatform()
        testLogging {
            events("started", "passed", "skipped", "failed")
            exceptionFormat = TestExceptionFormat.FULL
            showStandardStreams = false
        }
    }

    //apply(plugin = "com.vanniktech.maven.publish")
    //apply(from = "$rootDir/gradle-mvn-publish.gradle")

}

