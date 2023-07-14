
subprojects {
  group = project.property("GROUP") as String
  version = project.findProperty("VERSION_NAME") as? String ?: "0.0-SNAPSHOT"
}
