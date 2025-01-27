plugins {
  id("org.jetbrains.kotlin.jvm")
  id("application")
}

val applicationMainClass = "com.squareup.chat.ChatServiceKt"
application {
  mainClass.set(applicationMainClass)
}

dependencies {
  implementation(libs.jakartaInject)
  implementation(project(":wisp:wisp-config"))
  implementation(project(":misk"))
  implementation(project(":misk-actions"))
  implementation(project(":misk-clustering"))
  implementation(project(":misk-core"))
  implementation(project(":misk-inject"))
  implementation(project(":misk-prometheus"))
  implementation(project(":misk-redis"))
  implementation(project(":misk-service"))
  implementation(libs.guice)
  implementation(libs.jedis)
  implementation(libs.logbackClassic)
  implementation(libs.slf4jApi)
  implementation(libs.okHttp)
  implementation(project(":wisp:wisp-deployment"))
  implementation(project(":misk-config"))
  implementation(testFixtures(project(":misk-redis")))

  testImplementation(libs.assertj)
  testImplementation(libs.junitApi)
  testImplementation(project(":misk-testing"))
}

tasks.jar {
  manifest {
    attributes("Main-Class" to applicationMainClass)
  }
  archiveClassifier.set("unshaded")
}

tasks.withType<Test>().configureEach {
  dependsOn(":startRedis")
}
