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

// This requires a separate publish block because it's a javaPlatform, not a source library.
plugins.withId("com.vanniktech.maven.publish.base") {
  val publishingExtension = extensions.getByType(PublishingExtension::class.java)
  configure<com.vanniktech.maven.publish.MavenPublishBaseExtension> {
    pomFromGradleProperties()
    publishToMavenCentral(com.vanniktech.maven.publish.SonatypeHost.S01, true)
    signAllPublications()
  }
  publishingExtension.publications.create<MavenPublication>("maven") {
    from(components["javaPlatform"])
  }
}
