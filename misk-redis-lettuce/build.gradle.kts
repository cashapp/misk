import com.vanniktech.maven.publish.JavadocJar.Dokka
import com.vanniktech.maven.publish.KotlinJvm

plugins {
  id("org.jetbrains.kotlin.jvm")
  id("com.vanniktech.maven.publish.base")
  id("java-test-fixtures")
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
  compilerOptions {
    freeCompilerArgs.addAll(
      "-opt-in=kotlin.time.ExperimentalTime",
      "-opt-in=io.lettuce.core.ExperimentalLettuceCoroutinesApi",
    )
  }
}

dependencies {
  api(libs.lettuceCore)
  api(project(":misk-config"))
  api(libs.guava)
  api(project(":misk-inject"))
  api(libs.jakartaInject)
  implementation(libs.guice)
  implementation(libs.kotlinxCoroutinesCore)
  implementation(libs.loggingApi)
  implementation(libs.prometheusClient)
  implementation(project(":misk-backoff"))
  implementation(project(":misk-metrics"))
  implementation(project(":misk-service"))
  implementation(project(":misk-logging"))


  testImplementation(libs.junitApi)
  testImplementation(libs.kotlinTest)
  testImplementation(libs.kotlinxCoroutinesTest)
  testImplementation(libs.kotestAssertions)
  testImplementation(project(":misk-testing"))
  testImplementation(testFixtures(project(":misk-redis-lettuce")))

  testFixturesImplementation(project(":misk-testing"))
  testFixturesImplementation(project(":misk-logging"))

}


tasks.withType<Test>().configureEach {
  doFirst {
    environment(mapOf(
      "REDIS_PORT" to  "6379",
      "REDIS_CLUSTER_SEED_PORT" to "7000"
    ))
  }
  dependsOn(":startRedis")
  dependsOn(":startRedisCluster")
}


mavenPublishing {
  configure(
    KotlinJvm(javadocJar = Dokka("dokkaGfm"))
  )
}
