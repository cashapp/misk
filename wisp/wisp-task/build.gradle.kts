plugins {
    `java-library`
}

dependencies {
    implementation(Dependencies.kotlinxCoroutines)
    implementation(Dependencies.kotlinStdLibJdk8)
    api(Dependencies.kotlinRetry)
    api(Dependencies.micrometerPrometheus)
    implementation(project(":wisp-config"))
    implementation(project(":wisp-logging"))

    testImplementation(testLibs.assertj)
    testImplementation(Dependencies.kotlinTest)
}
