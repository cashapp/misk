plugins {
  id("org.jetbrains.kotlin.jvm")
  id("com.vanniktech.maven.publish")
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
