@Suppress("DSL_SCOPE_VIOLATION")
plugins {
  kotlin("jvm")
  alias(libs.plugins.wireGradlePlugin)
}

dependencies {
  api(libs.guice)
  api(libs.javaxInject)
  api(libs.okHttp)
  api(libs.okio)
  api(libs.wispConfig)
  api(libs.wispToken)
  api(project(":misk"))
  api(project(":misk-actions"))
  api(project(":misk-config"))
  api(project(":misk-inject"))
  api(project(":misk-prometheus"))
  implementation(libs.wispDeployment)
  implementation(project(":misk-admin"))
  implementation(project(":misk-core"))

  testImplementation(libs.assertj)
  testImplementation(libs.junitApi)
  testImplementation(project(":misk-testing"))
}

val jar by tasks.getting(Jar::class) {
  manifest {
    attributes("Main-Class" to "com.squareup.exemplar.ExemplarServiceKt")
  }
  classifier = "unshaded"
}

sourceSets {
  val main by getting {
    java.srcDir("$buildDir/generated/source/wire/")
  }
}

wire {
  sourcePath {
    srcDir("src/main/proto/")
  }

  kotlin {
    javaInterop = true
  }
}
