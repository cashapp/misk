plugins {
  id("org.jetbrains.kotlin.jvm")
  id("com.squareup.wire")
}

dependencies {
  api(libs.okio)
}

wire {
  kotlin {
    includes = listOf(
      "test.kt.*",
    )
  }
  java {
  }
}
