plugins {
    `java-library`
}

dependencies {
    implementation(libs.kotlinStdLibJdk8)

    testImplementation(libs.assertj)
    testImplementation(libs.kotlinTest)
    testImplementation(project(":wisp-deployment-testing"))
}
