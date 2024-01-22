plugins {
  `java-platform`
  id("com.vanniktech.maven.publish.base")
}

dependencies {
  constraints {
    project.parent?.subprojects?.forEach { subproject ->
      if (subproject.name != "misk-bom") {
        api(subproject)
      }
    }
  }
}

extensions.configure<PublishingExtension> {
  publications.create("maven", MavenPublication::class) {
    from(project.components.getByName("javaPlatform"))
  }
}
