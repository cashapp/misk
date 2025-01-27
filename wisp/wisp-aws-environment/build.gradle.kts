plugins {
  id("org.jetbrains.kotlin.jvm")
  id("com.vanniktech.maven.publish")
}

dependencies {
    api(project(":wisp:wisp-deployment"))
    implementation(platform(libs.aws2Bom))

    testImplementation(libs.junitApi)
    testImplementation(libs.kotlinTest)
    testImplementation(project(":wisp:wisp-deployment-testing"))
}
