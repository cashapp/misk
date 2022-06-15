plugins {
    kotlin("jvm")
    `java-library`
}

dependencies {
    implementation(Dependencies.kotlinStdLibJdk8)

    testImplementation(Dependencies.assertj)
    testImplementation(Dependencies.kotlinTest)
    testImplementation(project(":wisp-deployment-testing"))
}
