plugins {
  id("java-platform")
  id("com.vanniktech.maven.publish.base")
}

dependencies {
  constraints {
    project.parent?.subprojects?.forEach { subproject ->
      if (subproject.name != "misk-bom") {
        api(project(subproject.path))
      }
    }
  }
}

publishing {
  publications.create("maven", MavenPublication::class) {
    from(components.getByName("javaPlatform"))
  }
}
