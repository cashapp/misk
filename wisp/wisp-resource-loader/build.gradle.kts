plugins {
  id("org.jetbrains.kotlin.jvm")
  id("com.vanniktech.maven.publish")
}

dependencies {
    implementation(libs.loggingApi)
    implementation(libs.okio)
    implementation(project(":wisp:wisp-logging"))
    runtimeOnly(libs.bouncyCastleProvider)

    testImplementation(libs.assertj)
    testImplementation(libs.junitApi)
    testImplementation(libs.junitParams)
    testImplementation(libs.junitPioneer)
    testImplementation(libs.kotlinTest)
}

// Allows us to set environment variables in tests using JUnit Pioneer
tasks.test {
  jvmArgs("--add-opens", "java.base/java.util=ALL-UNNAMED")
  jvmArgs("--add-opens", "java.base/java.lang=ALL-UNNAMED")
}
