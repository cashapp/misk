plugins {
    `java-library`
}

dependencies {
    implementation(Dependencies.kotlinStdLibJdk8)
    api(libs.bundles.hoplite)
    api(project(":wisp-resource-loader"))

    testImplementation(testLibs.assertj)
    testImplementation(Dependencies.kotlinTest)
}
