plugins {
  kotlin("jvm")
  id("com.squareup.wire")
}

dependencies {
  api(Dependencies.guice)
  api(Dependencies.javaxInject)
  api(Dependencies.okHttp)
  api(Dependencies.okio)
  api(Dependencies.wispConfig)
  api(Dependencies.wispToken)
  api(project(":misk"))
  api(project(":misk-actions"))
  api(project(":misk-config"))
  api(project(":misk-inject"))
  api(project(":misk-prometheus"))
  implementation(Dependencies.wispDeployment)
  implementation(project(":misk-admin"))
  implementation(project(":misk-core"))

  testImplementation(Dependencies.assertj)
  testImplementation(Dependencies.junitApi)
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
