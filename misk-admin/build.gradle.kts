import com.vanniktech.maven.publish.JavadocJar.Dokka
import com.vanniktech.maven.publish.KotlinJvm
import java.io.ByteArrayOutputStream
import java.nio.charset.Charset

plugins {
  alias(libs.plugins.kotlinJvm)
  alias(libs.plugins.mavenPublishBase)
  alias(libs.plugins.wire)
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

private class ExecOperationsHelper(
  private val ops: ExecOperations
) {
  internal data class ResultWithOutput(
    val stdOut: String,
    val stdErr: String,
    val workingDir: String,
    val exitCode: Int
  ) {
    internal fun assertNormalExitValue() {
      check(exitCode == 0) {
        "Command failed with exit code $exitCode: $stdErr"
      }
    }
  }

  internal fun exec(vararg command: String, action: Action<ExecSpec>? = null): ResultWithOutput {
    val stdOut = ByteArrayOutputStream()
    val stdErr = ByteArrayOutputStream()
    lateinit var workingDir: String
    val result = ops.exec {
      commandLine(*command)
      standardOutput = stdOut
      errorOutput = stdErr
      action?.execute(this)
      workingDir = this.workingDir.absolutePath
    }
    return ResultWithOutput(
      stdOut = String(stdOut.toByteArray(), Charset.defaultCharset()),
      stdErr = String(stdErr.toByteArray(), Charset.defaultCharset()),
      workingDir = workingDir,
      exitCode = result.exitValue
    )
  }
}

@CacheableTask
abstract class MiskWebBuildTask @Inject constructor(
  @Internal val execOps: ExecOperations
): DefaultTask() {
  @get:Internal
  abstract val rootDir: RegularFileProperty

  @get:Internal
  abstract val projectDir: RegularFileProperty

  @get:PathSensitive(PathSensitivity.RELATIVE)
  @get:InputFiles
  abstract val inputFiles: ConfigurableFileCollection

  @get:OutputDirectories
  abstract val outputFiles: ConfigurableFileCollection

  @TaskAction
  fun buildTabs() {
    val rootFile = rootDir.asFile.get()
    val projectDir = projectDir.asFile.get()

    logger.lifecycle("Running miskweb build...")

    val npmPath = rootFile.resolve("bin/npm").absolutePath
    val npmInstallResult = ExecOperationsHelper(execOps).exec(
      npmPath, "install", "@misk/cli"
    ) {
      // This needs to run from the project root dir, so that the node_modules directory is
      // just inside HERMIT_ENV. This is because the hermit node package explicitly adds
      // "$HERMIT_ENV/node_modules/.bin" to the PATH, and we need that for miskweb to be found.
      // Except sometimes we have a different hermit env active, so we deal with that below.
      workingDir = rootFile
      isIgnoreExitValue = true
    }
    logger.quiet("Ran $npmPath from ${npmInstallResult.workingDir}")
    logger.quiet(npmInstallResult.stdOut)
    npmInstallResult.assertNormalExitValue()

    // Per the Node docs (https://docs.npmjs.com/cli/v10/configuring-npm/folders#executables),
    // executables are installed in 'node_modules/.bin'.
    val nodeModulesBin = rootFile.resolve("node_modules/.bin").absolutePath
    val hermitBin = rootFile.resolve("bin").absolutePath
    val miskWebPath = rootFile.resolve("node_modules/.bin/miskweb").absolutePath
    val miskWebBuildResult = ExecOperationsHelper(execOps).exec(
      miskWebPath, "ci-build", "-e",
    ) {
      // this should run in one of the parent directories of the one containing the miskTab.json.
      // What ExecOps does varies by gradle version, it seems, so use the project directory for
      // the current project explicitly.
      workingDir = projectDir
      isIgnoreExitValue = true // we will assert ourselves after dumping stdout
      // We need miskweb to be on the PATH. In some environments we have multiple hermit environments
      // and the misk hermit env is not the one that is active, so the misk/node_modules/.bin folder
      // isn't automatically on the PATH. Let's add it explicitly to make sure it's there.
      environment(mapOf("PATH" to "$nodeModulesBin:$hermitBin:${environment["PATH"]}"))
    }
    logger.quiet("Ran $miskWebPath from ${miskWebBuildResult.workingDir}")
    logger.quiet(miskWebBuildResult.stdOut)
    miskWebBuildResult.assertNormalExitValue()

    logger.lifecycle("Miskweb build complete!")
  }
}

val tabDirs = listOf(
  "web/tabs/database",
  "web/tabs/web-actions",
)

val buildMiskWeb = tasks.register("buildMiskWeb", MiskWebBuildTask::class.java) {
  // For each folder containing a miskTab.json, take the entire folder as an input,
  // minus the listed excludes. Those files are generated by the miskweb invocation.
  // The lib subdirectory is the one we actually care about as an output; the
  // other ones are not bundled into the jar and can be ignored for caching
  // purposes.
  val inputs = tabDirs.map { tabDir ->
    project.fileTree(tabDir) {
      exclude("lib/")
      exclude("node_modules/")
      exclude("tsconfig.json")
      exclude("tslint.json")
      exclude("webpack.config.js")
    }
  }
  val outputs = tabDirs.map { tabDir ->
    File(tabDir, "lib")
  }
  rootDir.set(project.rootDir)
  projectDir.set(project.projectDir)
  inputFiles.setFrom(inputs)
  outputFiles.setFrom(outputs)
}

// buildMiskWeb is expensive and generally not needed locally. Only build it on CI, or if
// specifically requested.
val isCi = System.getenv("CI") == "true" || System.getenv("GITHUB_ACTIONS") != null
if (isCi || System.getProperty("misk.admin.buildMiskWeb") == "true") {
  tasks.named { it == "explodeCodeSourceMain" || it == "processResources" }.configureEach {
    dependsOn(buildMiskWeb)
    dependsOn("buildAndCopyWebActions")
  }
}

tasks.register<Copy>("buildAndCopyWebActions") {
  from({
    project(":misk-admin:web-actions").tasks.named("build").get().outputs.files
  })
  into(project.layout.buildDirectory.dir("resources/main/web/_tab/web-actions-v4"))
  dependsOn(":misk-admin:web-actions:build")
}

sourceSets {
  main {
    resources.srcDir(tabDirs.map { tabDir ->
      "$tabDir/lib"
    })
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
