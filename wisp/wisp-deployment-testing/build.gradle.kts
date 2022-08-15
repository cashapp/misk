plugins {
    `java-library`
}

dependencies {
    implementation(libs.kotlinStdLibJdk8)
    api(project(":wisp-deployment"))

    testImplementation(libs.assertj)
    testImplementation(libs.kotlinTest)
}
