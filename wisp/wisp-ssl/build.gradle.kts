plugins {
    `java-library`
}

dependencies {
    api(project(":wisp-resource-loader"))
    implementation(libs.bouncycastle)
    implementation(libs.okio)

    testImplementation(libs.assertj)
    testImplementation(libs.junitApi)
    testImplementation(libs.junitEngine)
    testImplementation(libs.kotlinTest)
}
