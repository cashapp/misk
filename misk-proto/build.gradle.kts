@Suppress("DSL_SCOPE_VIOLATION")
plugins {
  kotlin("jvm")
  `java-library`
  
  alias(libs.plugins.wireGradlePlugin)
}

wire {
  sourcePath {
    srcDir("src/main/proto/")
  }

  kotlin {
    javaInterop = true
  }
}

dependencies {
  api(libs.okio)
}
