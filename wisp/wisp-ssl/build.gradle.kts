plugins {
    `java-library`
}

dependencies {
    api(project(":wisp-resource-loader"))
    implementation(Dependencies.bouncycastle)
    implementation(Dependencies.kotlinStdLibJdk8)
    implementation(Dependencies.okio)

    testImplementation(testLibs.assertj)
    testImplementation(Dependencies.junitApi)
    testImplementation(Dependencies.junitEngine)
    testImplementation(Dependencies.kotlinTest)
}
