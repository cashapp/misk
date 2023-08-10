plugins {
    `java-library`
}

dependencies {
    testImplementation(Dependencies.junitApi)
    testImplementation(Dependencies.kotlinTest)
		testImplementation(project(":wisp:wisp-deployment"))
    testImplementation(project(":wisp:wisp-deployment-testing"))
}
