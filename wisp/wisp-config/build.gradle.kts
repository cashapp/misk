plugins {
    `java-library`
}

dependencies {
    api(libs.hopliteCore)
    api(project(":wisp-resource-loader"))
    implementation(libs.hopliteJson)
    implementation(libs.hopliteToml)
    implementation(libs.hopliteYaml)
    runtimeOnly(libs.hopliteHocon)

    testImplementation(libs.junitApi)
    testImplementation(libs.kotlinTest)
}
