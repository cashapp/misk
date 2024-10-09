import com.vanniktech.maven.publish.JavadocJar.Dokka
import com.vanniktech.maven.publish.KotlinJvm

plugins {
  alias(libs.plugins.kotlinJvm)
  alias(libs.plugins.mavenPublishBase)
  alias(libs.plugins.wire)
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
