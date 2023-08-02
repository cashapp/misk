plugins {
    `java-library`
}


dependencies {
    api(project(":wisp:wisp-token"))

    testImplementation(Dependencies.kotestAssertionsShared)
    testImplementation(Dependencies.kotestCommon)
    testImplementation(Dependencies.kotestFrameworkApi)
    testRuntimeOnly(Dependencies.junitEngine)
    testRuntimeOnly(Dependencies.kotestJunitRunnerJvm)
}
