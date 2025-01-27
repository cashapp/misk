plugins {
  id("org.jetbrains.kotlin.jvm")
  id("com.vanniktech.maven.publish")
}

dependencies {
    api(project(":wisp:wisp-lease"))
    api(project(":misk-testing-api"))

    testImplementation(libs.assertj)
    testImplementation(libs.junitApi)
}
