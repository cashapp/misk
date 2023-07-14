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
  implementation(project(":misk:misk"))
  implementation(project(":misk:misk-actions"))
  implementation(project(":misk:misk-clustering"))
  implementation(project(":misk:misk-core"))
  implementation(project(":misk:misk-inject"))
  implementation(project(":misk:misk-prometheus"))
  implementation(project(":misk:misk-redis"))
  implementation(Dependencies.guice)
  implementation(Dependencies.jedis)
  implementation(Dependencies.okHttp)
  implementation(Dependencies.wispDeployment)
  implementation(project(":misk:misk-config"))
  implementation(project(":misk:misk-redis-testing"))

  testImplementation(Dependencies.assertj)
  testImplementation(Dependencies.junitApi)
  testImplementation(project(":misk:misk-testing"))
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
