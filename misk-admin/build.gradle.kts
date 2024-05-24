import com.vanniktech.maven.publish.JavadocJar.Dokka
import com.vanniktech.maven.publish.KotlinJvm
import com.vanniktech.maven.publish.MavenPublishBaseExtension

plugins {
  kotlin("jvm")
  `java-library`
  id("com.vanniktech.maven.publish.base")
  id("com.squareup.wire")
}

dependencies {
  api(libs.guice)
  api(libs.jakartaInject)
  api(libs.kotlinxHtml)
  api(libs.moshiCore)
  api(libs.okio)
  api(project(":wisp:wisp-deployment"))
  api(project(":misk"))
  api(project(":misk-api"))
  api(project(":misk-actions"))
  api(project(":misk-config"))
  api(project(":misk-inject"))
  api(libs.kotlinxHtml)
  implementation(libs.okHttp)
  implementation(project(":misk-core"))
  implementation(project(":misk-hotwire"))
  implementation(project(":misk-tailwind"))
  implementation(project(":wisp:wisp-config"))

  testImplementation(libs.assertj)
  testImplementation(libs.junitApi)
  testImplementation(libs.kotlinReflect)
  testImplementation(libs.kotlinTest)
  testImplementation(libs.wireRuntime)
  testImplementation(project(":misk-api"))
  testImplementation(project(":misk-action-scopes"))
  testImplementation(project(":misk-testing"))
}

sourceSets {
  val main by getting {
    resources.srcDir(listOf(
      "web/tabs/config/lib",
      "web/tabs/database/lib",
      "web/tabs/web-actions/lib"
    ))
    resources.exclude("**/node_modules")
  }
}

val generatedSourceDir = "$buildDir/generated/source/wire-test"

wire {
  sourcePath {
    srcDir("src/test/proto/")
  }
  java {
    out = generatedSourceDir
  }
}

// Make sure the Wire-generated sources are test-only.
afterEvaluate {
  val generatedSourceGlob = "$generatedSourceDir/**"

  sourceSets {
    val main by getting {
      java.setSrcDirs(java.srcDirs.filter { !it.path.contains(generatedSourceDir) })
    }
    val test by getting {
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

configure<MavenPublishBaseExtension> {
  configure(
    KotlinJvm(javadocJar = Dokka("dokkaGfm"))
  )
}
