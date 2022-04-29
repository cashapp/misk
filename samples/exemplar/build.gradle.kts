plugins {
  kotlin("jvm")

  id("com.squareup.wire")
}

sourceSets {
  val main by getting {
    java.srcDir("src/main/kotlin/")
  }
}

dependencies {
  // TODO: these should be implementation("com.squareup.misk:*")
  implementation(project(":misk"))
  implementation(project(":misk-actions"))
  implementation(project(":misk-admin"))
  implementation(project(":misk-core"))
  implementation(project(":misk-inject"))
  implementation(project(":misk-prometheus"))

  testImplementation(project(":misk-testing"))
  testImplementation("org.assertj:assertj-core:3.22.0")
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
