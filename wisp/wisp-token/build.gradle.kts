plugins {
    `java-library`
}

dependencies {

    testImplementation(testLibs.bundles.kotest)
    testImplementation(testLibs.assertj)
    testRuntimeOnly(Dependencies.junitEngine)
}
