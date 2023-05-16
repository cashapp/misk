import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
  kotlin("jvm")
}

dependencies {
  api(Dependencies.javaxInject)
  api(Dependencies.wispConfig)
  api(project(":misk"))
  api(project(":misk-actions"))
  api(project(":misk-clustering"))
  api(project(":misk-core"))
  api(project(":misk-inject"))
  api(project(":misk-prometheus"))
  api(project(":misk-redis"))
  implementation(Dependencies.guice)
  implementation(Dependencies.jedis)
  implementation(Dependencies.okHttp)
  implementation(Dependencies.wispDeployment)
  implementation(project(":misk-config"))
  implementation(project(":misk-redis-testing"))

  testImplementation(Dependencies.assertj)
  testImplementation(Dependencies.junitApi)
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
