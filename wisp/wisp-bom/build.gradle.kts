plugins {
  `java-platform`
  //disable wisp publishing until sonatype issue is resolved
  //id("com.vanniktech.maven.publish.base")
}

dependencies {
  constraints {
    // TODO - check constraints...
    project.rootProject.subprojects.forEach { subproject ->
      if (subproject.name != "wisp-bom" && subproject.name.startsWith("wisp-")) {
        api(subproject)
      }
    }
  }
}

/*
extensions.configure<PublishingExtension> {
  publications.create("maven", MavenPublication::class) {
    from(project.components.getByName("javaPlatform"))
  }
}
*/
