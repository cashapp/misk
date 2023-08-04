plugins {
    `java-library`
}

dependencies {
    api(project(":wisp:wisp-deployment"))
    implementation(platform(Dependencies.aws2Bom))

    testImplementation(Dependencies.junitApi)
    testImplementation(Dependencies.kotlinTest)
    testImplementation(project(":wisp:wisp-deployment-testing"))
}
