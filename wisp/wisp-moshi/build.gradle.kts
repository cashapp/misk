plugins {
    `java-library`
}

dependencies {

    api(Dependencies.moshiCore)
    api(Dependencies.moshiKotlin)

    testImplementation(testLibs.bundles.kotest)
    testImplementation(testLibs.assertj)
    testRuntimeOnly(Dependencies.junitEngine)
}
