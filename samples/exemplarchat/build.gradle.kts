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
  implementation(Dependencies.javaxInject)
  implementation(Dependencies.wispConfig)
  implementation(project(":misk"))
  implementation(project(":misk-actions"))
  implementation(project(":misk-clustering"))
  implementation(project(":misk-core"))
  implementation(project(":misk-inject"))
  implementation(project(":misk-prometheus"))
  implementation(project(":misk-redis"))
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
