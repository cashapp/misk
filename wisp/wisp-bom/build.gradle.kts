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
