plugins {
    `java-library`
}

dependencies {
    api(project(":wisp:wisp-resource-loader"))
    implementation(Dependencies.bouncycastle)
    implementation(Dependencies.okio)

    testImplementation(Dependencies.assertj)
    testImplementation(Dependencies.junitApi)
}
