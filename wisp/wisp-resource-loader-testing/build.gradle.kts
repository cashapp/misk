plugins {
    `java-library`
}

dependencies {
    implementation(libs.bouncycastle)
    implementation(libs.kotlinStdLibJdk8)
    implementation(libs.okio)
    api(project(":wisp-resource-loader"))

    testImplementation(libs.assertj)
    testImplementation(libs.junitApi)
    testImplementation(libs.junitEngine)
    testImplementation(libs.kotlinTest)
}
