plugins {
    `java-library`
}

dependencies {
    testImplementation(libs.assertj)
    testImplementation(libs.junitApi)
    testRuntimeOnly(libs.junitEngine)
    testRuntimeOnly(libs.kotestJunitRunnerJvm)
}
