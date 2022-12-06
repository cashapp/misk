plugins {
  kotlin("jvm")
  `java-library`
}

dependencies {
  api(Dependencies.kotlinRetry)

  testImplementation(Dependencies.assertj)
  testImplementation(Dependencies.kotlinTest)
}
