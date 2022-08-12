plugins {
    `java-library`
}

dependencies {
    implementation(Dependencies.bouncycastle)
    implementation(Dependencies.kotlinStdLibJdk8)
    implementation(Dependencies.okio)
    api(project(":wisp-resource-loader"))

    testImplementation(testLibs.assertj)
    testImplementation(Dependencies.junitApi)
    testImplementation(Dependencies.junitEngine)
    testImplementation(Dependencies.kotlinTest)
}
