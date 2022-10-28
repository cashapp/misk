plugins {
  `java-platform`
  id("com.vanniktech.maven.publish.base")
}

dependencies {
  constraints {
    project.rootProject.subprojects.forEach { subproject ->
      if (subproject.name != "misk-bom") {
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
