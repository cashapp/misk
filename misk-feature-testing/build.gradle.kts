plugins {
  kotlin("jvm")
  `java-library`
}

sourceSets {
  val test by getting {
    java.srcDir("src/test/kotlin/")
  }
}

dependencies {
  implementation(Dependencies.guice)
  implementation(Dependencies.kotlinStdLibJdk8)
  implementation(Dependencies.kotlinReflection)
  implementation(Dependencies.moshiCore)
  implementation(Dependencies.moshiKotlin)
  implementation(Dependencies.moshiAdapters)
  api(project(":misk-feature"))
  api(project(":misk-inject"))
  api(project(":misk-service"))
  api(Dependencies.wispFeature)
  api(Dependencies.wispFeatureTesting)

  testImplementation(Dependencies.assertj)
  testImplementation(Dependencies.kotlinTest)
  testImplementation(project(":misk-testing"))
  testImplementation(Dependencies.wispMoshi)
}
