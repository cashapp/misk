import java.io.InputStream

plugins {

}

dependencies {

}

tasks.register<MiskConsoleBuildTask>("build") {
  group = "build"
  description = "Run webpack build for misk console"

  miskConsoleRootDir.set(projectDir)

  inputFiles.setFrom(
    projectDir.listFiles().filter {
      it.name in listOf(
        "src",
        "babel.config.json",
        "jest.config.js",
        "package.json",
        "tsconfig.json",
        "webpack.config.js",
      )
    }
  )

  outputFiles.setFrom(projectDir.resolve("lib"))
}

@CacheableTask
abstract class MiskConsoleBuildTask : DefaultTask() {

  @get:Internal
  abstract val miskConsoleRootDir: RegularFileProperty

  @get:PathSensitive(PathSensitivity.RELATIVE)
  @get:InputFiles
  abstract val inputFiles: ConfigurableFileCollection

  @get:OutputDirectories
  abstract val outputFiles: ConfigurableFileCollection

  private fun InputStream.pipeTo(handleLine: (String) -> Unit): Thread {
    return Thread {
      reader().useLines { lines ->
        for (line in lines) {
          handleLine(line)
        }
      }
    }.also { it.start() }
  }

  private fun ProcessBuilder.execute() {
    logger.lifecycle("Executing ${command()}")
    val process = start()

    val readOut = process.inputStream.pipeTo(logger::lifecycle)
    val readErr = process.errorStream.pipeTo(logger::error)

    val code = process.waitFor()
    readOut.join()
    readErr.join()

    if (code != 0) {
      error("${command()} exited with $code")
    }
  }

  @TaskAction
  fun build() {
    logger.lifecycle("Running misk-console build \uD83C\uDFD7\uFE0F")

    val projectDir = miskConsoleRootDir.get().asFile
    val npm = projectDir.resolve("bin/npm").absolutePath
    val npx = projectDir.resolve("bin/npx").absolutePath

    fun exec(vararg cmd: String) {
      ProcessBuilder()
        .command(*cmd)
        .directory(projectDir)
        .execute()
    }

    exec(npm, "install")
    exec(npx, "webpack", "--mode", "production")
    exec(npm, "run-script", "lint")
  }
}
