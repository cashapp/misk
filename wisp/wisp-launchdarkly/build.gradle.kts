plugins {
  id("org.jetbrains.kotlin.jvm")
  id("com.vanniktech.maven.publish")
}

dependencies {
    api(libs.launchDarkly)
    api(libs.loggingApi)
    api(libs.micrometerCore)
    api(libs.moshiCore)
    api(project(":wisp:wisp-client"))
    api(project(":wisp:wisp-config"))
    api(project(":wisp:wisp-feature"))
    api(project(":wisp:wisp-resource-loader"))
    api(project(":wisp:wisp-ssl"))

    implementation(libs.guava)

    testImplementation(libs.assertj)
    testImplementation(libs.junitApi)
    testImplementation(libs.mockitoCore)
    testImplementation(project(":wisp:wisp-moshi"))
}
