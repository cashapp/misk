plugins {
  id("org.jetbrains.kotlin.jvm")
  id("com.vanniktech.maven.publish")
}

dependencies {
    api(project(":wisp:wisp-resource-loader"))
    api(libs.okio)
    implementation(libs.bouncyCastleProvider)

    testImplementation(libs.assertj)
    testImplementation(libs.junitApi)
}
