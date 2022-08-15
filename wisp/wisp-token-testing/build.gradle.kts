plugins {
    `java-library`
}


dependencies {
    api(project(":wisp-token"))

    testImplementation(libs.bundles.kotest)
    testImplementation(libs.assertj)
    testRuntimeOnly(libs.junitEngine)
}
