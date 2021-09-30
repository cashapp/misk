plugins {
  id("com.squareup.wire")
}

dependencies {
  implementation(Dependencies.kotlinReflection)
  implementation(project(":misk"))
  implementation(project(":misk-core"))
  api(project(":misk-inject"))
  api(project(":wisp-deployment"))

  testImplementation(project(":misk-testing"))
  testImplementation(Dependencies.assertj)
  testImplementation(Dependencies.kotlinTest)
  testImplementation(Dependencies.moshiKotlin)
  testImplementation(Dependencies.wireRuntime)
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

apply(from = "$rootDir/gradle-mvn-publish.gradle")
