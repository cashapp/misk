plugins {
    `java-library`
}

dependencies {
    implementation(libs.kotlinStdLibJdk8)
    api(libs.bundles.hoplite)
    api(project(":wisp-resource-loader"))

    testImplementation(libs.assertj)
    testImplementation(libs.kotlinTest)
}
