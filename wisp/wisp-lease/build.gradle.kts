plugins {
  id("org.jetbrains.kotlin.jvm")
  id("com.vanniktech.maven.publish")
}

dependencies {
    api(project(":wisp:wisp-deployment"))

    testImplementation(libs.junitApi)
    testImplementation(libs.kotlinTest)
    testImplementation(libs.mockitoCore)
}
