package misk

import com.beust.jcommander.JCommander
import com.beust.jcommander.ParameterException
import com.google.common.annotations.VisibleForTesting
import com.google.common.util.concurrent.ServiceManager
import com.google.inject.Guice
import com.google.inject.Module
import misk.inject.KAbstractModule
import misk.inject.getInstance
import misk.logging.getLogger

/** The entry point for misk applications */
class MiskApplication(private val modules: List<Module>, commands: List<MiskCommand> = listOf()) {

  constructor(vararg modules: Module) : this(modules.toList())
  constructor(vararg commands: MiskCommand) : this(listOf(), commands.toList())

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
  @VisibleForTesting
  internal fun doRun(args: Array<String>) {
    if (args.isEmpty()) {
      startServiceAndAwaitTermination()
      return
    }

    try {
      jc.parse(*args)
      val command = commands[jc.parsedCommand]
          ?: throw ParameterException("unknown command ${jc.parsedCommand}")

      val injector = Guice.createInjector(object : KAbstractModule() {
        override fun configure() {
          bind<JCommander>().toInstance(jc)
          binder().requireAtInjectOnConstructors()
        }
      }, *command.modules.toTypedArray())

      injector.injectMembers(command)
      command.run()
    } catch (e: ParameterException) {
      val sb = StringBuilder().append('\n')
      e.message?.let { sb.append(it).append("\n\n") }
      if (e.jCommander?.parsedCommand != null) {
        jc.usage(e.jCommander.parsedCommand, sb)
      } else {
        jc.usage(sb)
      }
      throw CliException(sb.toString())
    }
  }

  private fun startServiceAndAwaitTermination() {
    log.info { "creating application injector" }
    val injector = Guice.createInjector(modules)
    val serviceManager = injector.getInstance<ServiceManager>()
    Runtime.getRuntime().addShutdownHook(object : Thread() {
      override fun run() {
        serviceManager.stopAsync()
        serviceManager.awaitStopped()
      }
    })

    log.info { "starting services and waiting for them to be healthy" }
    serviceManager.startAsync()
    serviceManager.awaitHealthy()
    log.info { "all services are healthy" }
    serviceManager.awaitStopped()
    log.info { "all services stopped" }
  }

  private companion object {
    val log = getLogger<MiskApplication>()
  }

  internal class CliException(message: String) : RuntimeException(message)
}
