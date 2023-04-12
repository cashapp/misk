plugins {
    `java-library`
}

dependencies {
    runtimeOnly(libs.kotestJunitRunnerJvm)
    testImplementation(libs.assertj)
    testImplementation(libs.junitApi)
    testRuntimeOnly(libs.junitEngine)
}
