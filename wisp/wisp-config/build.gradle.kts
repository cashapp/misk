plugins {
    `java-library`
}

dependencies {
    api(libs.bundles.hoplite)
    api(project(":wisp-resource-loader"))

    testImplementation(libs.assertj)
    testImplementation(libs.kotlinTest)
}
