package misk.dev

import java.lang.reflect.Method
import java.nio.file.Paths
import kotlin.io.path.absolute
import kotlin.io.path.exists
import kotlin.reflect.KFunction
import kotlin.reflect.jvm.javaMethod
import misk.MiskApplication
import misk.RunningMiskApplication

internal class DevApplicationState {
  companion object {
    @Volatile var isRunning = false
  }
}

@misk.annotation.ExperimentalMiskApi
fun isRunningDevApplication(): Boolean {
  return DevApplicationState.isRunning
}

/**
 * Intended for use by advanced hot reload use cases that extend the existing hot reload code.
 *
 * Should not be called by regular code.
 */
@misk.annotation.ExperimentalMiskApi
fun setDevApplication() {
  DevApplicationState.isRunning = true
}

@misk.annotation.ExperimentalMiskApi
fun runDevApplication(
  miskApplicationBuilder: KFunction<MiskApplication>,
  additionalGradleArgs: List<String> = emptyList(),
) {
  DevApplicationState.isRunning = true
  System.setProperty(
    "misk.dev.running",
    "true",
  ) // Some code may not depend on misk-core but still need to know about dev mode
  val lock = Object()
  var restart = false
  var javaMethod: Method? = null
  try {
    javaMethod =
      miskApplicationBuilder.javaMethod
        ?: throw IllegalArgumentException(
          "You cannot pass a lambda to runDevApplication, you must use a method reference"
        )
  } catch (e: ClassNotFoundException) {
    throw java.lang.RuntimeException("Unable to init live reload, do you have kotlin-reflect as a dependency?", e)
  }
  val className = javaMethod.declaringClass.name

  // We need to discover the project directory, if we are running from an IDE the current directory will be the root
  // Which is not what we want in a monorepo
  val dir = discoverProjectRoot(className)

  runGradleAsyncCompile(
    dir,
    {
      synchronized(lock) {
        restart = true
        lock.notifyAll()
      }
    },
    additionalGradleArgs,
  )
  val parent = Thread.currentThread().contextClassLoader
  while (true) {
    restart = false
    val cl = DevClassLoader(parent)
    var running: RunningMiskApplication? = null
    try {
      Thread.currentThread().contextClassLoader = cl

      val cls = cl.loadClass(className)!!
      val app: MiskApplication = cls.getDeclaredMethod(javaMethod.name).invoke(cls) as MiskApplication
      running = app.start()
    } catch (e: Exception) {
      if (e.cause is ClassNotFoundException || e.cause is NoClassDefFoundError) {
        Thread.sleep(100)
        continue
      }
      e.printStackTrace()
    }
    while (!restart) {
      synchronized(lock) { lock.wait() }
    }
    running?.stop()
    running?.awaitTerminated()
  }
}

private fun discoverProjectRoot(className: String): String {
  val res = Thread.currentThread().contextClassLoader.getResource(className.replace('.', '/') + ".class")
  var dir = System.getProperty("user.dir")
  if (res != null && res.protocol == "file") {
    var current = Paths.get(res.toURI())
    while (current.parent != null) {
      current = current.parent
      if (current.resolve("build.gradle.kts").exists() || current.resolve("build.gradle").exists()) {
        dir = current.absolute().toString()
        break
      }
    }
  }
  return dir
}
