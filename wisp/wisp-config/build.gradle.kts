plugins {
  alias(libs.plugins.kotlinJvm)
  alias(libs.plugins.mavenPublish)
}

dependencies {
    api(libs.hopliteCore)
    api(project(":wisp:wisp-resource-loader"))
    implementation(libs.hopliteJson)
    implementation(libs.hopliteToml)
    implementation(libs.hopliteYaml)
    runtimeOnly(libs.hopliteHocon)

    testImplementation(libs.junitApi)
    testImplementation(libs.kotlinTest)
}
