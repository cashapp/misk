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
  api(Dependencies.guava)
  api(Dependencies.guice)
  api(Dependencies.javaxInject)
  api(Dependencies.wispAwsEnvironment)
  api(Dependencies.wispConfig)
  api(Dependencies.wispLease)
  api(project(":misk:misk"))
  api(project(":misk:misk-config"))
  api(project(":misk:misk-feature"))
  api(project(":misk:misk-inject"))
  api(project(":misk:misk-jobqueue"))
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
  implementation(project(":misk:misk-core"))
  implementation(project(":misk:misk-hibernate"))
  implementation(project(":misk:misk-metrics"))
  implementation(project(":misk:misk-service"))
  implementation(project(":misk:misk-transactional-jobqueue"))
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
  testImplementation(project(":misk:misk-clustering"))
  testImplementation(project(":misk:misk-feature-testing"))
  testImplementation(project(":misk:misk-testing"))
}

configure<MavenPublishBaseExtension> {
  configure(
    KotlinJvm(javadocJar = Dokka("dokkaGfm"))
  )
}
