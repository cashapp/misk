plugins {
  `java-platform`
  alias(libs.plugins.mavenPublishGradlePlugin)
}

dependencies {
  constraints {
    // TODO - check constraints...
    project.rootProject.subprojects.forEach { subproject ->
      if (subproject.name != "wisp-bom") {
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
    publishToMavenCentral(com.vanniktech.maven.publish.SonatypeHost.DEFAULT, true)
    signAllPublications()
  }
  publishingExtension.publications.create<MavenPublication>("maven") {
    from(components["javaPlatform"])
  }
}