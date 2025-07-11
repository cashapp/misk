import com.vanniktech.maven.publish.JavadocJar.Dokka
import com.vanniktech.maven.publish.KotlinJvm
import java.io.ByteArrayOutputStream
import java.nio.charset.Charset

plugins {
  id("org.jetbrains.kotlin.jvm")
  id("com.vanniktech.maven.publish.base")
  id("com.squareup.wire")
}

dependencies {
  api(libs.guice)
  api(libs.jakartaInject)
  api(libs.kotlinXHtml)
  api(libs.okio)
  api(project(":wisp:wisp-deployment"))
  api(project(":misk"))
  api(project(":misk-api"))
  api(project(":misk-actions"))
  api(project(":misk-config"))
  api(project(":misk-inject"))
  api(project(":misk-service"))
  api(libs.kotlinXHtml)
  implementation(libs.loggingApi)
  implementation(libs.moshiCore)
  implementation(libs.okHttp)
  implementation(project(":misk-core"))
  implementation(project(":misk-hotwire"))
  implementation(project(":misk-tailwind"))
  implementation(project(":wisp:wisp-config"))
  implementation(project(":wisp:wisp-logging"))
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

val buildMiskWeb = tasks.register("buildMiskWeb", Exec::class.java) {
  setWorkingDir(layout.projectDirectory)
  commandLine("web/build.sh")
  inputs.files(project.fileTree(layout.projectDirectory.dir("web")) {
    exclude("tabs/database/node_modules/**")
    exclude("tabs/database/lib/**")
    exclude("tabs/database/tsconfig.json")
    exclude("tabs/database/tslint.json")
    exclude("tabs/database/webpack.config.js")
  }).withPropertyName("webfiles").withPathSensitivity(PathSensitivity.RELATIVE)
  inputs.files(project.file("../package-lock.json")).withPropertyName("packagelock").withPathSensitivity(PathSensitivity.RELATIVE) // for misk-cli install
  outputs.dir(layout.buildDirectory.dir("web"))

  outputs.cacheIf("all inputs/outputs are declared") { true }
}

val buildWebActions = tasks.register("buildWebActions", Exec::class.java) {
  setWorkingDir(layout.projectDirectory)
  commandLine("web-actions/build.sh")

  inputs.files(project.fileTree(layout.projectDirectory.dir("web-actions")) {
    include("src/**")
    include("babel.config.json")
    include("jest.config.js")
    include("package.json")
    include("tsconfig.json")
    include("webpack.config.js")
    include("build.sh")
  }).withPropertyName("webfiles").withPathSensitivity(PathSensitivity.RELATIVE)
  outputs.dir(layout.buildDirectory.dir("web-actions"))

  outputs.cacheIf("all inputs/outputs are declared") { true }
}

tasks.named { it == "explodeCodeSourceMain" || it == "processResources" || it == "sourcesJar" }.configureEach {
  dependsOn(buildMiskWeb)
  dependsOn(buildWebActions)
}

sourceSets {
  main {
    resources.srcDir(buildMiskWeb)
    resources.srcDir(buildWebActions)
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

