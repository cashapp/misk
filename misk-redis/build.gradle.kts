import com.vanniktech.maven.publish.JavadocJar.Dokka
import com.vanniktech.maven.publish.KotlinJvm

plugins {
  id("org.jetbrains.kotlin.jvm")
  id("com.vanniktech.maven.publish.base")
  id("java-test-fixtures")
}

dependencies {
  api(libs.guava)
  api(libs.jakartaInject)
  api(libs.jedis)
  api(project(":misk-config"))
  api(project(":misk-inject"))
  api(project(":misk-metrics"))
  implementation(libs.apacheCommonsPool)
  implementation(libs.apacheCommonsIo)
  implementation(libs.guice)
  implementation(libs.okio)
  implementation(libs.prometheusClient)
  implementation(project(":misk-logging"))
  implementation(libs.loggingApi)
  implementation(project(":wisp:wisp-deployment"))
  implementation(project(":misk-service"))

  testFixturesApi(libs.jedis)
  testFixturesApi(project(":misk-inject"))
  testFixturesApi(project(":misk-redis"))
  testFixturesApi(project(":misk-testing"))
  testFixturesImplementation(project(":misk-service"))
  testFixturesImplementation(libs.dockerApi)
  testFixturesImplementation(libs.guava)
  testFixturesImplementation(libs.guice)
  testFixturesImplementation(libs.loggingApi)
  testFixturesImplementation(libs.okio)
  testFixturesImplementation(project(":misk-testing"))
  testFixturesImplementation(project(":misk-logging"))

  testImplementation(libs.assertj)
  testImplementation(libs.junitApi)
  testImplementation(libs.kotlinTest)
  testImplementation(project(":misk-testing"))
  testImplementation(project(":misk"))
  testImplementation(project(":misk-redis"))
  testImplementation(testFixtures(project(":misk-redis")))

  testImplementation(project(":misk-service"))
  testImplementation(libs.dockerApi)
  testImplementation(libs.guava)
  testImplementation(libs.guice)
  testImplementation(libs.loggingApi)
  testImplementation(libs.okio)
  testImplementation(project(":misk-testing"))
  testImplementation(project(":misk-logging"))

  testFixturesImplementation(libs.apacheCommonsPool)
  testFixturesImplementation(libs.apacheCommonsIo)
  testFixturesImplementation(libs.guice)
  testFixturesImplementation(libs.okio)
  testFixturesImplementation(libs.prometheusClient)
  testFixturesImplementation(project(":misk-logging"))
  testFixturesImplementation(libs.loggingApi)
  testFixturesImplementation(project(":wisp:wisp-deployment"))
  testFixturesImplementation(project(":misk-service"))
}

tasks.withType<Test>().configureEach {
  dependsOn(":startRedis")
  dependsOn(":startRedisCluster")
}

mavenPublishing {
  configure(
    KotlinJvm(javadocJar = Dokka("dokkaGfm"))
  )
}
