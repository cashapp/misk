import com.vanniktech.maven.publish.JavadocJar.Dokka
import com.vanniktech.maven.publish.KotlinJvm
import com.vanniktech.maven.publish.MavenPublishBaseExtension

plugins {
  kotlin("jvm")
  `java-library`
  id("com.vanniktech.maven.publish.base")
}

dependencies {
  api(Dependencies.awsJavaSdkCore)
  api(Dependencies.awsS3)
  api(Dependencies.awsSqs)
  api(Dependencies.guice)
  api(Dependencies.javaxInject)
  api(Dependencies.wispAwsEnvironment)
  api(Dependencies.wispConfig)
  api(Dependencies.wispLease)
  api(project(":misk"))
  api(project(":misk-config"))
  api(project(":misk-feature"))
  api(project(":misk-inject"))
  api(project(":misk-jobqueue"))
  implementation(Dependencies.kotlinLogging)
  implementation(Dependencies.moshi)
  implementation(Dependencies.openTracingApi)
  implementation(Dependencies.openTracingDatadog)
  implementation(Dependencies.prometheusClient)
  implementation(Dependencies.slf4jApi)
  implementation(Dependencies.tracingDatadog)
  implementation(Dependencies.wispDeployment)
  implementation(Dependencies.wispLogging)
  implementation(Dependencies.wispTracing)
  implementation(project(":misk-core"))
  implementation(project(":misk-hibernate"))
  implementation(project(":misk-metrics"))
  implementation(project(":misk-service"))
  implementation(project(":misk-transactional-jobqueue"))

  testImplementation(Dependencies.assertj)
  testImplementation(Dependencies.awaitility)
  testImplementation(Dependencies.dockerApi)
  testImplementation(Dependencies.junitApi)
  testImplementation(Dependencies.junitParams)
  testImplementation(Dependencies.kotlinTest)
  testImplementation(Dependencies.mockitoCore)
  testImplementation(Dependencies.wispContainersTesting)
  testImplementation(Dependencies.wispFeatureTesting)
  testImplementation(Dependencies.wispTimeTesting)
  testImplementation(project(":misk-clustering"))
  testImplementation(project(":misk-feature-testing"))
  testImplementation(project(":misk-testing"))
}

configure<MavenPublishBaseExtension> {
  configure(
    KotlinJvm(javadocJar = Dokka("dokkaGfm"))
  )
}
