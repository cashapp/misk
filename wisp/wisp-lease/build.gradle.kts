plugins {
    `java-library`
}

dependencies {
    implementation(Dependencies.kotlinStdLibJdk8)
    implementation(Dependencies.kotlinReflection)

    testImplementation(testLibs.assertj)
    testImplementation(Dependencies.kotlinTest)
}
