package misk

import com.beust.jcommander.JCommander
import com.beust.jcommander.ParameterException
import com.google.inject.Module
import javax.inject.Inject

/**
 * A command to run from the command line. Each command has an associated name and
 * the list of modules to use in initializing the command. Commands can specify
 * optional and required arguments via JCommander annotations. The command line will
 * pick the appropriate command based on the name, create an injector based on that
 * command's modules, use the injector to initialize the command, and then run the command.
 */
abstract class MiskCommand(
    internal val name: String,
    internal val modules: List<Module>
) : Runnable {
  constructor(
      name: String,
      vararg modules: Module
  ) : this(name, modules.toList())

  @Inject
  private lateinit var jc: JCommander

  /**
   * Confirms that the given precondition is true, otherwise throws a [ParameterException]
   * with the supplied message
   * */
  fun requireCli(
      value: Boolean,
      lazyMessage: () -> String
  ) {
    if (!value) {
      val exception = ParameterException(lazyMessage())
      exception.jCommander = jc
      throw exception
    }
  }
}

