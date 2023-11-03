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
  implementation(Dependencies.jakartaInject)
  implementation(Dependencies.kotlinxHtml)
  implementation(Dependencies.logbackClassic)
  implementation(Dependencies.slf4jApi)
  implementation(Dependencies.okHttp)
  implementation(Dependencies.okio)
  implementation(project(":wisp:wisp-config"))
  implementation(project(":wisp:wisp-deployment"))
  implementation(project(":wisp:wisp-token"))
  implementation(project(":misk"))
  implementation(project(":misk-actions"))
  implementation(project(":misk-admin"))
  implementation(project(":misk-config"))
  implementation(project(":misk-core"))
  implementation(project(":misk-inject"))
  implementation(project(":misk-hotwire"))
  implementation(project(":misk-prometheus"))
  implementation(project(":misk-rate-limiting-bucket4j-redis"))
  implementation(project(":misk-rate-limiting-bucket4j-dynamodb-v1"))

  testImplementation(Dependencies.assertj)
  testImplementation(Dependencies.junitApi)
  testImplementation(Dependencies.micrometerPrometheus)
  testImplementation(project(":misk-testing"))

  testImplementation(testFixtures(project(":misk-aws-dynamodb")))
  testImplementation(testFixtures(project(":misk-redis")))
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
