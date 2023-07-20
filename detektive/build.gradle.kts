plugins {
  kotlin("jvm")
  `java-library`
}

dependencies {
  compileOnly(Dependencies.detektApi)
  compileOnly(Dependencies.detektPsiUtils)
  compileOnly(Dependencies.kotlinCompilerEmbeddable)

  testImplementation(Dependencies.assertj)
  testImplementation(Dependencies.detektTest)
  testImplementation(Dependencies.detektTestUtils)
  testImplementation(Dependencies.guice)
  testImplementation(Dependencies.junitApi)
  testImplementation(Dependencies.junitParams)
  testImplementation(Dependencies.kotlinTest)

  testRuntimeOnly(Dependencies.junitEngine)
}
