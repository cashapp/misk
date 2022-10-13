plugins {
    `java-library`
}

dependencies {

    testImplementation(libs.bundles.kotest)
    testImplementation(libs.assertj)
    testRuntimeOnly(libs.junitEngine)
}
