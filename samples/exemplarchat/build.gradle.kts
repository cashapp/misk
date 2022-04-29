import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
  kotlin("jvm")
}

dependencies {
  // TODO: these should be implementation("com.squareup.misk:*")
  implementation(project(":misk"))
  implementation(project(":misk-actions"))
  implementation(project(":misk-core"))
  implementation(project(":misk-eventrouter"))
  implementation(project(":misk-inject"))
  implementation(project(":misk-prometheus"))

  testImplementation(project(":misk-testing"))
  testImplementation("org.assertj:assertj-core:3.22.0")
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
