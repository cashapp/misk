plugins {
    `java-library`
}

dependencies {
    implementation(Dependencies.kotlinStdLibJdk8)
    implementation(Dependencies.kotlinReflection)
    api(Dependencies.loggingApi)
    implementation(Dependencies.slf4jApi)

    testImplementation(Dependencies.logbackClassic)
    testImplementation(testLibs.assertj)
    testImplementation(Dependencies.kotlinTest)
    testImplementation(Dependencies.kotlinxCoroutines)
    testImplementation(project(":wisp-logging-testing"))
}
