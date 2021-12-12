plugins {
  `java-platform`
  `maven-publish`
}

dependencies {
  constraints {
    // Jetty 10+ uses SLF4J 2.0-alpha, which is unstable. It works well with 1.7 substituted in.
    // See: https://github.com/eclipse/jetty.project/issues/5943
    api(Dependencies.slf4jApi)
  }
}


publishing {
  publications {
    create<MavenPublication>("maven") {
      from(components["javaPlatform"])
    }
  }
}
