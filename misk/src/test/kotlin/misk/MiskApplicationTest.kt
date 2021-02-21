package misk

import com.beust.jcommander.Parameter
import misk.inject.KAbstractModule
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import javax.inject.Inject
import kotlin.test.assertFailsWith

internal class MiskApplicationTest {
  class WithRequiredArguments : MiskCommand("with-required-args") {
    @Parameter(names = ["-f", "--file"], required = true)
    var filename: String? = null

    var run: Boolean = false

    override fun run() {
      run = true
    }
  }

  class WithPreconditions : MiskCommand("with-preconditions") {
    @Parameter(names = ["-f", "--file"])
    var filename: String? = null

    @Parameter(names = ["-d", "--dir"])
    var directory: String? = null

    var run: Boolean = false
    var preconditionsMet: Boolean = false

    override fun run() {
      run = true
      requireCli(filename != null || directory != null) {
        "one of -f or -d must be specified"
      }
      preconditionsMet = true
    }
  }

  class WithModule : MiskCommand("with-module", AppNameModule()) {
    @Inject lateinit var injectedValue: MyInjectedValue

    var run: Boolean = false

    override fun run() {
      run = true
    }

    data class MyInjectedValue(val message: String)

    class AppNameModule : KAbstractModule() {
      override fun configure() {
        bind<MyInjectedValue>().toInstance(MyInjectedValue("foo"))
      }
    }
  }

  class Commands {
    val withRequiredArguments = WithRequiredArguments()
    val withPreconditions = WithPreconditions()
    val withModule = WithModule()
    val all = arrayOf(withRequiredArguments, withPreconditions, withModule)
  }

  @Test
  fun withModule() {
    val commands = Commands()
    MiskApplication(*commands.all).doRun(arrayOf("with-module"))
    assertThat(commands.withModule.run).isTrue()
    assertThat(commands.withModule.injectedValue.message).isEqualTo("foo")
  }

  @Test
  fun withRequiredArguments() {
    val commands = Commands()
    MiskApplication(*commands.all).doRun(arrayOf("with-required-args", "-f", "my-file"))
    assertThat(commands.withRequiredArguments.run).isTrue()
    assertThat(commands.withRequiredArguments.filename).isEqualTo("my-file")
  }

  @Test
  fun withPreconditions() {
    val commands = Commands()
    MiskApplication(*commands.all).doRun(arrayOf("with-preconditions", "-f", "my-file"))
    assertThat(commands.withPreconditions.run).isTrue()
    assertThat(commands.withPreconditions.preconditionsMet).isTrue()
    assertThat(commands.withPreconditions.filename).isEqualTo("my-file")
  }

  @Test
  fun missingRequiredArgument() {
    val exception = assertFailsWith<MiskApplication.CliException> {
      val commands = Commands()
      MiskApplication(*commands.all).doRun(arrayOf("with-required-args", "-f"))
    }

    // Error message should be specific to the command
    assertThat(exception.message).isEqualTo(
      """
        |
        |Expected a value after parameter -f
        |
        |Usage: with-required-args [options]
        |  Options:
        |  * -f, --file
        |
        |""".trimMargin()
    )
  }

  @Test
  fun unknownCommand() {
    val exception = assertFailsWith<MiskApplication.CliException> {
      val commands = Commands()
      MiskApplication(*commands.all).doRun(arrayOf("unknown"))
    }

    // Error message should include the entire usage
    assertThat(exception.message).isEqualTo(
      """
        |
        |Expected a command, got unknown
        |
        |Usage: <main class> [command] [command options]
        |  Commands:
        |    with-required-args      null
        |      Usage: with-required-args [options]
        |        Options:
        |        * -f, --file
        |
        |
        |    with-preconditions      null
        |      Usage: with-preconditions [options]
        |        Options:
        |          -d, --dir
        |
        |          -f, --file
        |
        |
        |    with-module      null
        |      Usage: with-module
        |
        |""".trimMargin()
    )
  }

  @Test
  fun commandPreconditionsNotMet() {
    val exception = assertFailsWith<MiskApplication.CliException> {
      val commands = Commands()
      MiskApplication(*commands.all).doRun(arrayOf("with-preconditions"))
    }

    // Error message should be specific to the command
    assertThat(exception.message).isEqualTo(
      """
        |
        |one of -f or -d must be specified
        |
        |Usage: with-preconditions [options]
        |  Options:
        |    -d, --dir
        |
        |    -f, --file
        |
        |""".trimMargin()
    )
  }
}
