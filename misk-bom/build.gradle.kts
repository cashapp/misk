plugins {
  id("java-platform")
  alias(libs.plugins.mavenPublishBase)
}

dependencies {
  constraints {
    project.parent?.subprojects?.forEach { subproject ->
      if (subproject.name != "misk-bom") {
        api(subproject.path)
      }
    }
  }
}

publishing {
  publications.create("maven", MavenPublication::class) {
    from(components.getByName("javaPlatform"))
  }
}
