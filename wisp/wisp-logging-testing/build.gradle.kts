plugins {
    `java-library`
}

dependencies {
    implementation(Dependencies.kotlinStdLibJdk8)
    implementation(Dependencies.kotlinReflection)
    api(Dependencies.loggingApi)
    api(Dependencies.logbackClassic)
    implementation(Dependencies.slf4jApi)
    implementation(testLibs.assertj)

    testImplementation(Dependencies.kotlinTest)
    testImplementation(Dependencies.kotlinxCoroutines)
    testImplementation(project(":wisp-logging"))
}
