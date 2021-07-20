sourceSets {
  val main by getting {
    resources.srcDir(listOf(
      "web/tabs/admin-dashboard/lib",
      "web/tabs/config/lib",
      "web/tabs/database/lib",
      "web/tabs/web-actions/lib"
    ))
    resources.exclude("**/node_modules")
  }
  val test by getting {
    java.srcDir("src/test/kotlin/")
  }
}

dependencies {
  implementation(Dependencies.kotlinReflection)
  implementation(project(":misk"))
  implementation(project(":misk-core"))
  api(project(":misk-inject"))
  api(project(":wisp-deployment"))

  testImplementation(project(":misk-testing"))
  testImplementation(Dependencies.assertj)
  testImplementation(Dependencies.kotlinTest)
  testImplementation(Dependencies.moshiKotlin)
  testImplementation(Dependencies.wireRuntime)
}

apply(from = "$rootDir/gradle-mvn-publish.gradle")
