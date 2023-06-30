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
  api(Dependencies.guice)
  api(Dependencies.javaxInject)
  api(Dependencies.kotlinxHtml)
  api(Dependencies.moshi)
  api(Dependencies.okio)
  api(Dependencies.wispConfig)
  api(Dependencies.wispDeployment)
  api(project(":misk"))
  api(project(":misk-action-scopes"))
  api(project(":misk-actions"))
  api(project(":misk-config"))
  api(project(":misk-inject"))
  api(project(":misk-tailwind"))
  api(Dependencies.kotlinxHtml)
  implementation(Dependencies.okHttp)
  implementation(project(":misk-core"))
  implementation(project(":misk-hotwire"))

  testImplementation(Dependencies.assertj)
  testImplementation(Dependencies.junitApi)
  testImplementation(Dependencies.kotlinReflect)
  testImplementation(Dependencies.kotlinTest)
  testImplementation(Dependencies.wireRuntime)
  testImplementation(project(":misk-testing"))
}

sourceSets {
  val main by getting {
    resources.srcDir(listOf(
      "web/tabs/admin-dashboard/lib",
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
