import org.jetbrains.kotlin.allopen.gradle.AllOpenExtension

apply(plugin = "org.jetbrains.kotlin.plugin.allopen")
apply(plugin = "kotlin-jpa")

configure<AllOpenExtension> {
  annotation("javax.persistence.Entity")
  annotation("javax.persistence.Embeddable")
  annotation("javax.persistence.MappedSuperclass")
}

dependencies {
  implementation(Dependencies.hibernateCore)
  implementation(Dependencies.hikariCp)
  implementation(Dependencies.hsqldb)
  implementation(Dependencies.mysql)
  implementation(Dependencies.openTracing)
  implementation(Dependencies.openTracingUtil)
  implementation(Dependencies.openTracingJdbc)
  implementation(Dependencies.vitess)
  implementation(project(":misk"))
  api(project(":misk-events-core"))
  implementation(project(":misk-hibernate"))

  testImplementation(project(":misk-testing"))
  testImplementation(project(":misk-hibernate-testing"))
}

apply(from = "$rootDir/gradle-mvn-publish.gradle")
