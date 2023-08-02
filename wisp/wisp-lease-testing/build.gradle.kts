plugins {
    `java-library`
}

dependencies {
    api(project(":wisp:wisp-lease"))

    testImplementation(Dependencies.assertj)
    testImplementation(Dependencies.junitApi)
}
