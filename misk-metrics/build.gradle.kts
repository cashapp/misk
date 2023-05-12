plugins {
  kotlin("jvm")
  `java-library`
}

dependencies {
  api(libs.prometheusClient)
  implementation(libs.guava)
  implementation(libs.kotlinStdLibJdk8)

  testImplementation(libs.assertj)
  testImplementation(libs.junitApi)
}
