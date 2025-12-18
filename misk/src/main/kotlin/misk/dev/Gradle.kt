package misk.dev

import java.io.File
import java.io.InputStream
import java.util.concurrent.atomic.AtomicBoolean

fun runGradleAsyncCompile(projectDir: String, compilationComplete: () -> Unit, additionalGradleArgs: List<String>) {
  val t = Thread {
    val pb = ProcessBuilder(listOf("gradle", "classes", "--continuous") + additionalGradleArgs)
    pb.environment().put("MISK_HOT_RELOAD", "true")
    pb.directory(File(projectDir))

    val first = AtomicBoolean(false)
    val process = pb.start()!!
    Runtime.getRuntime().addShutdownHook(Thread { process.destroy() })

    val lineHandler: (String) -> Unit = {
      if (it.contains("Waiting for changes to input files...")) {
        if (!first.compareAndSet(false, true)) {
          compilationComplete.invoke()
        }
      }
      println(it)
    }
    Thread(Reader(lineHandler, process.errorStream)).start()
    Thread(Reader(lineHandler, process.inputStream)).start()
    val result = process.waitFor()
    if (result != 0) {
      println("Gradle compilation failed, continuous compilation not available")
    }
  }
  t.start()
}

internal class Reader(val lineHandler: (line: String) -> Unit, val stream: InputStream) : Runnable {
  override fun run() {
    stream.bufferedReader().forEachLine(lineHandler)
  }
}
