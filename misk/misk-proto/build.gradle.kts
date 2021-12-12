plugins {
  kotlin("jvm")
  `java-library`
  
  id("com.squareup.wire")
}

wire {
  sourcePath {
    srcDir("src/main/proto/")
  }

  kotlin {
    javaInterop = true
  }
}
