plugins {
    `java-library`
}

dependencies {
    api(Dependencies.hopliteCore)
    api(project(":wisp:wisp-resource-loader"))
    implementation(Dependencies.hopliteJson)
    implementation(Dependencies.hopliteToml)
    implementation(Dependencies.hopliteYaml)
    runtimeOnly(Dependencies.hopliteHocon)

    testImplementation(Dependencies.junitApi)
    testImplementation(Dependencies.kotlinTest)
}
