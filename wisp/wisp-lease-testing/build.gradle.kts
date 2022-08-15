plugins {
    `java-library`
}

dependencies {
    api(project(":wisp-lease"))
    implementation(libs.kotlinStdLibJdk8)
    implementation(libs.kotlinReflection)

    testImplementation(libs.assertj)
    testImplementation(libs.kotlinTest)
}
