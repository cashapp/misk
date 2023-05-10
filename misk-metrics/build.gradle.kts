plugins {
  kotlin("jvm")
  `java-library`
}

dependencies {
  api(Dependencies.prometheusClient)
  implementation(Dependencies.guava)
  implementation(Dependencies.kotlinStdLibJdk8)

  testImplementation(Dependencies.assertj)
  testImplementation(Dependencies.junitApi)
}
