plugins {
  alias(libs.plugins.kotlinJvm)
  alias(libs.plugins.mavenPublish)
}

dependencies {
    api(libs.moshiCore)
    implementation(libs.loggingApi)
}
