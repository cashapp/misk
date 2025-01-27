plugins {
  id("org.jetbrains.kotlin.jvm")
  id("com.vanniktech.maven.publish")
}

dependencies {
    api(libs.moshiCore)
    implementation(libs.loggingApi)
}
