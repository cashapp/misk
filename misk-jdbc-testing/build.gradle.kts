plugins {
  kotlin("jvm")
  `java-library`
}

dependencies {
  api(Dependencies.datasourceProxy)
  api(Dependencies.javaxInject)
  api(Dependencies.moshi)
  api(Dependencies.okHttp)
  api(project(":misk-inject"))
  api(project(":misk-jdbc"))
  implementation(Dependencies.guice)
  implementation(Dependencies.hikariCp)
  implementation(Dependencies.kotlinLogging)
  implementation(Dependencies.okio)
  implementation(Dependencies.wispContainersTesting)
  implementation(Dependencies.wispDeployment)
  implementation(Dependencies.wispLogging)
  implementation(project(":misk"))
  implementation(project(":misk-core"))
  implementation(project(":misk-service"))
  runtimeOnly(Dependencies.hsqldb)
}
