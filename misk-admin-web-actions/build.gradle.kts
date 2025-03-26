import java.io.InputStream

tasks.register<WebActionsTabBuildTask>("buildWebActionsTab") {
  group = "build"
  description = "Run webpack build for misk web actions"

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

  dependsOn("checkInternalRepos")
}

@CacheableTask
abstract class WebActionsTabBuildTask : DefaultTask() {

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
    logger.lifecycle("Running misk web-actions build \uD83C\uDFD7\uFE0F")

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
    exec(npm, "run-script", "test")
  }
}

abstract class CheckInternalReposTask : DefaultTask() {
  @get:InputFiles
  @get:PathSensitive(PathSensitivity.RELATIVE)
  abstract val packageLockFiles: ConfigurableFileCollection

  init {
    packageLockFiles.setFrom(
      project.projectDir.walk()
        .filter { it.name == "package-lock.json" }
        .toList()
    )
  }

  @TaskAction
  fun check() {
    val internalRepos = listOf(
      "global.block-artifacts.com",
      "artifactory.global.square"
    )

    val violations = mutableListOf<Pair<File, List<String>>>()

    packageLockFiles.forEach { file ->
      try {
        val content = file.readText()
        val foundRepos = internalRepos.filter { repo -> repo in content }

        if (foundRepos.isNotEmpty()) {
          violations.add(file to foundRepos)
        }
      } catch (e: Exception) {
        logger.error("Error processing ${file.relativeTo(project.projectDir)}: ${e.message}")
      }
    }

    if (violations.isNotEmpty()) {
      val message = buildString {
        appendLine("Found internal repository references:")
        violations.forEach { (file, repos) ->
          appendLine("- ${file.relativeTo(project.projectDir)} contains references to:")
          repos.forEach { repo ->
            appendLine("  * '$repo'")
          }
        }
        appendLine("\nPlease remove references to internal npm repositories.")
      }
      throw GradleException(message)
    } else {
      logger.lifecycle("No internal repository references found.")
    }
  }
}

tasks.register<CheckInternalReposTask>("checkInternalRepos") {
  group = "verification"
  description = "Check for internal repository references in package-lock.json files"
}
