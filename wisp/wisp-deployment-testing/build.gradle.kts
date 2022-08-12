plugins {
    `java-library`
}

dependencies {
    implementation(Dependencies.kotlinStdLibJdk8)
    api(project(":wisp-deployment"))

    testImplementation(testLibs.assertj)
    testImplementation(Dependencies.kotlinTest)
}
