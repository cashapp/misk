plugins {
    `java-platform`
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
