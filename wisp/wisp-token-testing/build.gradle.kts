plugins {
    `java-library`
}


dependencies {
    api(project(":wisp-token"))

    testImplementation(testLibs.bundles.kotest)
    testImplementation(testLibs.assertj)
    testRuntimeOnly(Dependencies.junitEngine)
}
