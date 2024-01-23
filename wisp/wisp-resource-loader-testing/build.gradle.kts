plugins {
    `java-library`
}

dependencies {
    api(project(":wisp:wisp-resource-loader"))
    implementation(libs.okio)
    runtimeOnly(libs.bouncycastle)

    testImplementation(libs.assertj)
    testImplementation(libs.junitApi)
}
