import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
  kotlin("jvm")
  application
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

val jar by tasks.getting(Jar::class) {
  manifest {
    attributes("Main-Class" to "com.squareup.chat.ChatServiceKt")
  }
  classifier = "unshaded"
}

val compileKotlin by tasks.getting(KotlinCompile::class) {
  kotlinOptions {
    // TODO(alec): Enable again once Environment enum is deleted
    allWarningsAsErrors = false
  }
}
