plugins {
    `java-library`
}

dependencies {
    api(project(":wisp:wisp-deployment"))

    testImplementation(Dependencies.junitApi)
    testImplementation(Dependencies.kotlinTest)
    testImplementation(Dependencies.mockitoCore)
}
