plugins {
    kotlin("jvm")
    `java-library`
}

dependencies {
    implementation(Dependencies.kotlinStdLibJdk8)
    implementation(Dependencies.kotlinReflection)

    testImplementation(Dependencies.assertj)
    testImplementation(Dependencies.kotlinTest)
}
