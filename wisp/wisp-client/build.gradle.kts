sourceSets {
  val main by getting {
    java.srcDir("src/main/kotlin/")
  }
}

dependencies {
  api(project(":wisp-ssl"))
  api(project(":wisp-logging"))
  implementation(Dependencies.jnrUnixsocket)
  implementation(Dependencies.kotlinStdLibJdk8)
  implementation(Dependencies.moshiKotlin)
  implementation(Dependencies.okHttp)
  implementation(Dependencies.okio)
  implementation(Dependencies.openTracing)
  implementation(Dependencies.openTracingOkHttp)
  implementation(Dependencies.retrofit)
  implementation(Dependencies.retrofitMoshi)
  implementation(Dependencies.retrofitProtobuf)
  implementation(Dependencies.retrofitWire)
}

apply(from = "$rootDir/gradle-mvn-publish.gradle")
