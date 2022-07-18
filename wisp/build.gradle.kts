import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.jetbrains.kotlin.gradle.plugin.KotlinPluginWrapper
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

buildscript {
    repositories {
        mavenCentral()
        maven(url = "https://plugins.gradle.org/m2/")
    }

    dependencies {
        classpath(Dependencies.kotlinGradlePlugin)
        classpath(Dependencies.junitGradlePlugin)
        classpath(Dependencies.mavenPublishGradlePlugin)
        classpath(Dependencies.protobufGradlePlugin)
        classpath(Dependencies.kotlinBinaryCompatibilityPlugin)
    }
}

subprojects {
    buildscript {
        repositories {
            mavenCentral()
        }
    }

    repositories {
        mavenCentral()
        maven(url = "https://s3-us-west-2.amazonaws.com/dynamodb-local/release")
    }

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
            add("testImplementation", Dependencies.junitApi)
            add("testRuntimeOnly", Dependencies.junitEngine)

            // Platform/BOM dependencies constrain versions only.
            // Enforce wisp-bom -- it should take priority over external BOMs.
            add("api", enforcedPlatform(project(":wisp-bom")))
            add("api", platform(Dependencies.grpcBom))
            add("api", platform(Dependencies.jacksonBom))
            add("api", platform(Dependencies.nettyBom))

            // The kotlin API surface used in this library is not exposed via
            // the external API, so we shouldn't be forcing downstream consumers
            // to use a particular kotlin version. Doing this can cause compilation
            // failures for downstream repositories that want to use wisp APIs
            // in their buildscripts or plugins but still want to use an older
            // version of the kotlin compiler. Use 'implementation' instead of 'api'
            // so that we're not forcing downstream consumers to adopt our kotlin
            // BOM versions.
            add("implementation", platform(Dependencies.kotlinBom))
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

    apply(plugin = "com.vanniktech.maven.publish")
    apply(plugin = "org.jetbrains.kotlinx.binary-compatibility-validator")
    apply(from = "$rootDir/gradle-mvn-publish.gradle")

}

