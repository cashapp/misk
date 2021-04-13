import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

dependencies {
  implementation(Dependencies.kotlinStdLibJdk8)
  implementation(Dependencies.guava)
  implementation(Dependencies.guice)
  implementation(Dependencies.logbackClassic)
  implementation(Dependencies.okHttp)
  api(project(":misk"))
  api(project(":misk-actions"))
  api(project(":misk-core"))
  api(project(":misk-inject"))
  api(project(":misk-prometheus"))
  api(project(":misk-eventrouter"))
  api(project(":wisp-config"))
  api(project(":wisp-deployment"))

  testImplementation(project(":misk-testing"))
  testImplementation(Dependencies.assertj)
  testImplementation(Dependencies.kotlinTest)
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
