import com.vanniktech.maven.publish.JavadocJar.Dokka
import com.vanniktech.maven.publish.KotlinJvm

plugins {
  id("org.jetbrains.kotlin.jvm")
  id("com.vanniktech.maven.publish.base")
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

dependencies {
  api(libs.okio)
}

mavenPublishing {
  configure(
    KotlinJvm(javadocJar = Dokka("dokkaGfm"))
  )
}
