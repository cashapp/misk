subprojects {

  if (!path.startsWith(":wisp:wisp-bom")) {
    apply(plugin = "java")
    apply(plugin = "kotlin")
    apply(plugin = "com.google.protobuf")
//disable wisp publishing until sonatype issue is resolved
//    apply(plugin = "com.vanniktech.maven.publish")
  }
}
