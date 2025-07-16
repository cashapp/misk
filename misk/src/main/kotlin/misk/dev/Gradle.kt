package misk.dev

import java.io.InputStream
import java.util.concurrent.atomic.AtomicBoolean

internal fun runGradleAsyncCompile(compilationComplete: () -> Unit) {
  val t = Thread {
    val pb = ProcessBuilder("gradle", "compileKotlin", "--continuous")

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
    Thread(
      Reader(
        lineHandler,
        process.errorStream,
      ),
    ).start()
    Thread(
      Reader(
        lineHandler,
        process.inputStream,
      ),
    ).start()
    val result = process.waitFor()
    if (result != 0) {
      println("Gradle compilation failed, continuous compilation not available")
    }
  }
  t.setDaemon(true)
  t.start()
}

internal class Reader(val lineHandler: (line: String) -> Unit, val stream: InputStream) : Runnable {
  override fun run() {
    stream.bufferedReader().forEachLine(lineHandler)
  }

}
