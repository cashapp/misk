import com.vanniktech.maven.publish.JavadocJar.Dokka
import com.vanniktech.maven.publish.KotlinJvm
import org.jetbrains.kotlin.gradle.dsl.KotlinProjectExtension

plugins {
  id("org.jetbrains.kotlin.jvm")
  id("com.vanniktech.maven.publish.base")
  id("com.squareup.wire")
}

dependencies {
  api(project(":misk-inject"))
  api(libs.guice6)
  api(libs.jakartaInject)
  api(libs.okio)
  api(libs.moshiCore)
  implementation(project(":wisp:wisp-moshi"))
  implementation(libs.moshiAdapters)
  implementation(libs.wireMoshiAdapter)
  implementation(libs.wireRuntime)

  testImplementation(project(":misk-testing"))
  testImplementation(libs.assertj)
  testImplementation(libs.moshiKotlin)
  testRuntimeOnly(libs.junitEngine)
  testImplementation(libs.junitApi)
  testImplementation(libs.kotlinTest)
}

val generatedSourceDir = layout.buildDirectory.dir("generated/source/wire-test").get().asFile.path

wire {
  sourcePath {
    srcDir("src/test/proto/")
  }
  java {
    out = generatedSourceDir
  }
}

// We want the Wire-generated sources to be test-only, so we move them to test source sets here.
afterEvaluate {
  val kotlinSourceSets = extensions.findByType(KotlinProjectExtension::class.java)?.sourceSets

  sourceSets {
    main {
      java.setSrcDirs(java.srcDirs.filter { !it.path.contains(generatedSourceDir) })
    }
    test {
      java.srcDir(generatedSourceDir)
    }
  }

  kotlinSourceSets?.getByName("main")?.kotlin?.setSrcDirs(
    kotlinSourceSets.getByName("main").kotlin.srcDirs.filter { !it.path.contains(generatedSourceDir) }
  )
  kotlinSourceSets?.getByName("test")?.kotlin?.srcDir(generatedSourceDir)

  // Explicit dependency for test scoped protos to be generated if need be.
  tasks.named("compileTestKotlin") { dependsOn("generateMainProtos") }
  tasks.named("compileTestJava") { dependsOn("generateMainProtos") }
}

mavenPublishing {
  configure(
    KotlinJvm(javadocJar = Dokka("dokkaGfm"))
  )
}
