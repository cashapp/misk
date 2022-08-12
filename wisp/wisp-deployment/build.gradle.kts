plugins {
    `java-library`
}

dependencies {
    implementation(Dependencies.kotlinStdLibJdk8)

    testImplementation(testLibs.assertj)
    testImplementation(Dependencies.kotlinTest)
    testImplementation(project(":wisp-deployment-testing"))
}
