plugins {
  alias(libs.plugins.kotlinJvm)
  alias(libs.plugins.mavenPublish)
}

dependencies {
    api(project(":wisp:wisp-deployment"))
    implementation(platform(libs.aws2Bom))

    testImplementation(libs.junitApi)
    testImplementation(libs.kotlinTest)
    testImplementation(project(":wisp:wisp-deployment-testing"))
}
