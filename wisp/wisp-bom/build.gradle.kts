plugins {
  `java-platform`
  id("com.vanniktech.maven.publish.base")
}

dependencies {
  constraints {
    // TODO - check constraints...

    // Prometheus 0.10+ enforce _total in counters. Opt out of this version (but we should update
    // and deal with this breaking change).
    // See: https://github.com/prometheus/client_java/releases/tag/parent-0.10.0
    api(Dependencies.prometheusClient)

    project.rootProject.subprojects.forEach { subproject ->
      if (subproject.name != "wisp-bom") {
        api(subproject)
      }
    }
  }
}

mavenPublishing {
  pomFromGradleProperties()

  publishing {
    publications {
      create<MavenPublication>("maven") {
        from(components["javaPlatform"])
      }
    }
  }
}
