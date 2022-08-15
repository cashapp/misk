plugins {
    `java-library`
}

dependencies {
    implementation(libs.kotlinStdLibJdk8)
    implementation(libs.kotlinReflection)

    testImplementation(libs.assertj)
    testImplementation(libs.kotlinTest)
}
