plugins {
    `java-library`
}

dependencies {
    api(project(":wisp:wisp-resource-loader"))
    implementation(libs.bouncycastle)
    implementation(libs.okio)

    testImplementation(libs.assertj)
    testImplementation(libs.junitApi)
}
