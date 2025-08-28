package misk

import com.beust.jcommander.JCommander
import com.beust.jcommander.ParameterException
import com.google.common.annotations.VisibleForTesting
import com.google.common.util.concurrent.ServiceManager
import com.google.inject.Guice
import com.google.inject.Injector
import com.google.inject.Module
import misk.inject.KAbstractModule
import misk.inject.getInstance
import misk.web.jetty.JettyHealthService
import misk.web.jetty.JettyService
import misk.logging.getLogger
import java.io.Closeable
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlin.concurrent.thread
import kotlin.system.measureTimeMillis
import kotlin.time.Duration.Companion.milliseconds

/** The entry point for misk applications */
class MiskApplication private constructor(
  private val injectorGenerator: () -> Injector,
  commands: List<MiskCommand> = listOf(),
) {

  constructor(vararg modules: Module) : this({ Guice.createInjector(modules.toList()) })
  constructor(vararg commands: MiskCommand) : this({ Guice.createInjector() }, commands.toList())
  constructor(
    modules: List<Module>,
    commands: List<MiskCommand> = listOf(),
  ) : this({ Guice.createInjector(modules) }, commands)

  constructor(injector: Injector) : this({ injector })

  private val commands = commands.associateBy { it.name }
  private val jc: JCommander

  init {
    // TODO(mmihic): program name
    val jcBuilder = JCommander.newBuilder()
    commands.forEach { jcBuilder.addCommand(it.name, it) }
    jc = jcBuilder.build()
  }

  /**
   * Runs the application, finding and executing the appropriate command based on the
   * provided command line arguments
   */
  fun run(args: Array<String>) {
    try {
      doRun(args)
    } catch (e: CliException) {
      log.info(e.message)
    }
  }

  /**
   * Runs the application, raising a [CliException] if an error occurs. used for testing,
   * to ensure that properly friendly error message are printed when command line parsing
   * fails or when command preconditions (required arguments etc) are not met.
   *
   * If no command line arguments are specified, the service starts and blocks until terminated.
   */
  internal fun doRun(args: Array<String>) {
    if (args.isEmpty()) {
      startServiceAndAwaitTermination()
      return
    }

    try {
      jc.parse(*args)
      val command = commands[jc.parsedCommand]
        ?: throw ParameterException("unknown command ${jc.parsedCommand}")

      val injector = Guice.createInjector(
        object : KAbstractModule() {
          override fun configure() {
            bind<JCommander>().toInstance(jc)
            binder().requireAtInjectOnConstructors()
          }
        },
        *command.modules.toTypedArray(),
      )

      injector.injectMembers(command)
      command.run()
    } catch (e: ParameterException) {
      val sb = StringBuilder().append('\n')
      e.message?.let { sb.append(it).append("\n\n") }
      if (e.jCommander?.parsedCommand != null) {
        jc.usageFormatter.usage(e.jCommander.parsedCommand, sb)
      } else {
        jc.usageFormatter.usage(sb)
      }
      throw CliException(sb.toString())
    }
  }

  /**
   * Provides internal testing the ability to mimic system exit without actually
   * exiting the process.
   */
  @VisibleForTesting
  internal lateinit var shutdownHook: Thread

  /**
   * Provides internal testing the ability to get instances this used by the application.
   */
  @VisibleForTesting
  internal lateinit var injector: Injector

  fun start(): RunningMiskApplication {
    log.info { "creating application injector" }
    injector = injectorGenerator()
    val serviceManager = injector.getInstance<ServiceManager>()


    // We manage JettyHealthService outside ServiceManager because it must start and
    // shutdown last to keep the container alive via liveness checks.
    val jettyHealthService: JettyHealthService
    measureTimeMillis {
      log.info { "starting services" }
      serviceManager.startAsync()
      try {
        serviceManager.awaitHealthy()
      } catch (e: Exception) {
        try {
          serviceManager.stopAsync()
          serviceManager.awaitStopped()
        } catch (ex : Exception) {
          log.error(ex) { "Failed to stop service after failed start" }
        }
        throw e
      }

      // Start Health Service Last to ensure any dependencies are started.
      jettyHealthService = injector.getInstance<JettyHealthService>()
      jettyHealthService.startAsync()
      jettyHealthService.awaitRunning()
    }.also {
      log.info { "all services started successfully in ${it.milliseconds}" }
    }

    shutdownHook = thread(start = false) {
      measureTimeMillis {
        log.info { "received a shutdown hook! performing an orderly shutdown" }
        serviceManager.stopAsync()
        serviceManager.awaitStopped()

        // Synchronously stops Jetty Service if it is still running, otherwise no-ops.
        // Jetty will already be shut down when either:
        // webConfig.sleep_in_ms is <= 0 OR
        // (webConfig.health_port >= 0 AND webConfig.health_dedicated_jetty_instance == true)
        val jettyService = injector.getInstance<JettyService>()
        jettyService.stop()

        // Synchronously stop Jetty Health Service if it is running, otherwise no-ops.
        // Use Guava Service methods to ensure main thread awaitTerminated is handled correctly.
        val jettyHealthService = injector.getInstance<JettyHealthService>()
        jettyHealthService.stopAsync()
        jettyHealthService.awaitTerminated()
      }.also {
        log.info { "orderly shutdown complete in ${it.milliseconds}" }
      }
    }
    val shutdown: RunningMiskApplication = object : RunningMiskApplication {
      override fun stop() {
        shutdownHook.start()
      }

      override fun awaitTerminated() {
        serviceManager.awaitStopped()
        jettyHealthService.awaitTerminated()
        log.info { "all services stopped" }
      }

      override fun awaitTerminated(time : Long, timeUnit: TimeUnit) : Boolean{
        val deadline : Long = timeUnit.toMillis(time) + System.currentTimeMillis()
        try {
          serviceManager.awaitStopped(time, timeUnit)
          jettyHealthService.awaitTerminated(deadline - System.currentTimeMillis(), TimeUnit.MILLISECONDS)
        } catch (_ : TimeoutException) {
          return false
        }
        log.info { "all services stopped" }
        return true
      }

      override fun app(): MiskApplication {
        return this@MiskApplication
      }
    }
    return shutdown
  }

  private fun startServiceAndAwaitTermination() {
    val app = start()
    Runtime.getRuntime().addShutdownHook(shutdownHook)
    app.awaitTerminated()
  }

  private companion object {
    val log = getLogger<MiskApplication>()
  }

  internal class CliException(message: String) : RuntimeException(message)
}


interface RunningMiskApplication {
  fun stop()

  fun awaitTerminated()

  fun app() : MiskApplication
  fun awaitTerminated(i: Long, seconds: TimeUnit): Boolean
}
