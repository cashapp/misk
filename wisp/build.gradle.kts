subprojects {

  if (!path.startsWith(":wisp:wisp-bom")) {
    apply(plugin = "java")
    apply(plugin = "kotlin")
    apply(plugin = "com.google.protobuf")
    apply(plugin = "com.vanniktech.maven.publish")
  }
}
