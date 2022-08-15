plugins {
    `java-library`
}

dependencies {

    api(libs.moshiCore)
    api(libs.moshiKotlin)

    testImplementation(libs.bundles.kotest)
    testImplementation(libs.assertj)
    testRuntimeOnly(libs.junitEngine)
}
