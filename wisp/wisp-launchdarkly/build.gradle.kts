plugins {
  alias(libs.plugins.kotlinJvm)
  alias(libs.plugins.mavenPublish)
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

    testImplementation(libs.assertj)
    testImplementation(libs.junitApi)
    testImplementation(libs.mockitoCore)
    testImplementation(project(":wisp:wisp-moshi"))
}
