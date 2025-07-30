package misk.dev

import misk.MiskApplication
import misk.RunningMiskApplication
import misk.web.actions.javaMethod
import java.lang.reflect.Method
import kotlin.reflect.KFunction

internal class DevApplicationState {
  companion object {
     @Volatile
     var isRunning = false
  }
}

@misk.annotation.ExperimentalMiskApi
fun isRunningDevApplication(): Boolean {
  return DevApplicationState.isRunning
}

@misk.annotation.ExperimentalMiskApi
fun runDevApplication(modules : KFunction<MiskApplication>, additionalGradleArgs : List<String> = emptyList()) {
  DevApplicationState.isRunning = true
  System.setProperty("misk.dev.running", "true") // Some code may not depend on misk-core but still need to know about dev mode
  val lock = Object()
  var restart = false

  runGradleAsyncCompile({
    synchronized(lock) {
      restart = true
      lock.notifyAll()
    }
  }, additionalGradleArgs)
  val parent = Thread.currentThread().contextClassLoader
  while (true) {
    restart = false
    val cl = DevClassLoader(parent)
    var running: RunningMiskApplication? = null
    try {
      Thread.currentThread().contextClassLoader = cl
      var javaMethod : Method? = null
      try {
         javaMethod = modules.javaMethod
          ?: throw IllegalArgumentException("You cannot pass a lambda to runDevApplication, you must use a method reference")
      } catch (e : ClassNotFoundException) {
        throw java.lang.RuntimeException("Unable to init live reload, do you have kotlin-reflect as a dependency?",e)
      }
      val cls = cl.loadClass(javaMethod.declaringClass.name)!!

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
      synchronized(lock) {
        lock.wait()
      }
    }
    running?.stop()
    running?.awaitTerminated()

  }

}
