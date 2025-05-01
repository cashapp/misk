plugins {
  id("org.jetbrains.kotlin.jvm")
  id("com.vanniktech.maven.publish")
}

dependencies {
    api(project(":wisp:wisp-resource-loader"))
    implementation(libs.okio)
    runtimeOnly(libs.bouncyCastleProvider)

    testImplementation(libs.assertj)
    testImplementation(libs.junitApi)
}
