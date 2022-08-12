plugins {
    `java-library`
}

dependencies {
    api(project(":wisp-lease"))
    implementation(Dependencies.kotlinStdLibJdk8)
    implementation(Dependencies.kotlinReflection)

    testImplementation(testLibs.assertj)
    testImplementation(Dependencies.kotlinTest)
}
