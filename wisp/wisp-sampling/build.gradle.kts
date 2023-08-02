plugins {
    `java-library`
}

dependencies {
    testImplementation(Dependencies.assertj)
    testImplementation(Dependencies.junitApi)
    testRuntimeOnly(Dependencies.junitEngine)
    testRuntimeOnly(Dependencies.kotestJunitRunnerJvm)
}
