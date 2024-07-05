import com.vanniktech.maven.publish.JavadocJar.Dokka
import com.vanniktech.maven.publish.KotlinJvm

plugins {
  alias(libs.plugins.kotlinJvm)
  alias(libs.plugins.mavenPublishBase)
  alias(libs.plugins.wire)
}

dependencies {
  api(libs.guice)
  api(libs.jakartaInject)
  api(libs.kotlinxHtml)
  api(libs.okio)
  api(project(":wisp:wisp-deployment"))
  api(project(":misk"))
  api(project(":misk-api"))
  api(project(":misk-actions"))
  api(project(":misk-config"))
  api(project(":misk-inject"))
  api(libs.kotlinxHtml)
  implementation(libs.moshiCore)
  implementation(libs.okHttp)
  implementation(project(":misk-core"))
  implementation(project(":misk-hotwire"))
  implementation(project(":misk-tailwind"))
  implementation(project(":wisp:wisp-config"))
  implementation(project(":wisp:wisp-moshi"))

  testImplementation(libs.assertj)
  testImplementation(libs.junitApi)
  testImplementation(libs.kotlinReflect)
  testImplementation(libs.kotlinTest)
  testImplementation(libs.wireRuntime)
  testImplementation(project(":misk-api"))
  testImplementation(project(":misk-action-scopes"))
  testImplementation(project(":misk-service"))
  testImplementation(project(":misk-testing"))
}

sourceSets {
  main {
    resources.srcDir(listOf(
      "web/tabs/database/lib",
      "web/tabs/web-actions/lib"
    ))
    resources.exclude("**/node_modules")
  }
}

val generatedSourceDir = layout.buildDirectory.dir("generated/source/wire-test").get().asFile.path

wire {
  sourcePath {
    srcDir("src/test/proto/")
  }
  kotlin {
    out = generatedSourceDir
    includes = listOf(
      "test.kt.*",
    )
  }
  java {
    out = generatedSourceDir
  }
}

// Make sure the Wire-generated sources are test-only.
afterEvaluate {
  val generatedSourceGlob = "$generatedSourceDir/**"

  sourceSets {
    main {
      java.setSrcDirs(java.srcDirs.filter { !it.path.contains(generatedSourceDir) })
    }
    test {
      java.srcDir(generatedSourceDir)
    }
  }

  tasks {
    compileJava {
      exclude(generatedSourceGlob)
    }
    compileTestJava {
      include(generatedSourceGlob)
    }
  }
}

mavenPublishing {
  configure(
    KotlinJvm(javadocJar = Dokka("dokkaGfm"))
  )
}
