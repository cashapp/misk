plugins {
    `java-library`
}

dependencies {
    api(project(":wisp:wisp-resource-loader"))
    implementation(Dependencies.okio)
    runtimeOnly(Dependencies.bouncycastle)

    testImplementation(Dependencies.assertj)
    testImplementation(Dependencies.junitApi)
}
