plugins {
  alias(libs.plugins.kotlinJvm)
  alias(libs.plugins.mavenPublish)
}

dependencies {
    implementation(libs.loggingApi)
    implementation(libs.okio)
    implementation(project(":wisp:wisp-logging"))
    runtimeOnly(libs.bouncycastle)

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
