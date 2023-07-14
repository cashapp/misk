plugins {
  kotlin("jvm")
  application
  id("com.squareup.wire")
}

val applicationMainClass = "com.squareup.exemplar.ExemplarServiceKt"
application {
  mainClass.set(applicationMainClass)
}

dependencies {
  implementation(Dependencies.findBugs)
  implementation(Dependencies.guava)
  implementation(Dependencies.guice)
  implementation(Dependencies.javaxInject)
  implementation(Dependencies.kotlinxHtml)
  implementation(Dependencies.okHttp)
  implementation(Dependencies.okio)
  implementation(Dependencies.wispConfig)
  implementation(Dependencies.wispDeployment)
  implementation(Dependencies.wispToken)
  implementation(project(":misk:misk"))
  implementation(project(":misk:misk-actions"))
  implementation(project(":misk:misk-admin"))
  implementation(project(":misk:misk-config"))
  implementation(project(":misk:misk-core"))
  implementation(project(":misk:misk-inject"))
  implementation(project(":misk:misk-hotwire"))
  implementation(project(":misk:misk-prometheus"))

  testImplementation(Dependencies.assertj)
  testImplementation(Dependencies.junitApi)
  testImplementation(project(":misk:misk-testing"))
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
