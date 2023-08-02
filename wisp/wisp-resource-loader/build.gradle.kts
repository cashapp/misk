plugins {
    `java-library`
}

dependencies {
    implementation(Dependencies.kotlinLogging)
    implementation(Dependencies.okio)
    implementation(project(":wisp:wisp-logging"))
    runtimeOnly(Dependencies.bouncycastle)

    testImplementation(Dependencies.assertj)
    testImplementation(Dependencies.junitApi)
    testImplementation(Dependencies.kotlinTest)
}
