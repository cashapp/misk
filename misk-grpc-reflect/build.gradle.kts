import com.vanniktech.maven.publish.JavadocJar.Dokka
import com.vanniktech.maven.publish.KotlinJvm

plugins {
  id("org.jetbrains.kotlin.jvm")
  id("com.vanniktech.maven.publish.base")
  id("com.squareup.wire")
}

val testGeneratedSourceDir = layout.buildDirectory.dir("generated/source/wire-test").get().asFile.path

wire {
  sourcePath {
    srcJar(libs.wireReflector)
    srcDir("src/test/proto/")
  }
  // Generate service interfaces only; the client comes with wire-reflector.
  kotlin {
    includes = listOf("grpc.reflection.v1alpha.ServerReflection")
    rpcRole = "server"
    rpcCallStyle = "blocking"
    singleMethodServices = true
  }

  // Generate test message types for transitive import tests.
  java {
    out = testGeneratedSourceDir
    includes = listOf("transitive.*")
    exclusive = false
  }

  // Generate test service interfaces for transitive import tests.
  kotlin {
    out = testGeneratedSourceDir
    includes = listOf("transitive.MainService")
    exclusive = false
    rpcRole = "server"
    rpcCallStyle = "blocking"
    singleMethodServices = true
  }
}

// Make sure the Wire-generated test sources are test-only.
afterEvaluate {
  val generatedSourceGlob = "$testGeneratedSourceDir/**"

  sourceSets {
    main {
      java.setSrcDirs(java.srcDirs.filter { !it.path.contains(testGeneratedSourceDir) })
    }
    test {
      java.srcDir(testGeneratedSourceDir)
      resources.srcDir("src/test/proto/")
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

dependencies {
  api(libs.guice)
  api(libs.jakartaInject)
  api(libs.wireReflector)
  api(libs.wireSchema)
  api(project(":misk-actions"))
  api(project(":misk-inject"))
  implementation(libs.loggingApi)
  implementation(libs.kotlinReflect)
  implementation(libs.okio)
  implementation(libs.wireRuntime)
  implementation(project(":misk-logging"))

  testImplementation(libs.assertj)
  testImplementation(libs.junitApi)
  testImplementation(libs.okHttp)
  testImplementation(libs.protobufJava)
  testImplementation(libs.slf4jApi)
  testImplementation(project(":misk"))
  testImplementation(project(":misk-core"))
  testImplementation(project(":misk-grpc-tests"))
  testImplementation(project(":misk-testing"))
}

mavenPublishing {
  configure(
    KotlinJvm(javadocJar = Dokka("dokkaGfm"))
  )
}
