plugins {
  id("com.squareup.wire")
}

sourceSets {
  val main by getting {
    java.srcDir("src/main/kotlin/")
  }
}

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
  api(project(":wisp-config"))
  api(project(":wisp-deployment"))

  testImplementation(project(":misk-testing"))
  testImplementation(Dependencies.assertj)
  testImplementation(Dependencies.kotlinTest)
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
    includes = listOf("com.squareup.exemplar.protos")
    rpcCallStyle = "blocking"
    rpcRole = "server"
    singleMethodServices = true
    emitDeclaredOptions = true
    emitAppliedOptions = true
  }
}
