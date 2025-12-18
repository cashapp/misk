package cash.detektive.javacompat

import cash.detektive.javacompat.AnnotatePublicApisWithJvmOverloads.ElementType
import io.github.detekt.parser.DetektPomModel
import io.github.detekt.test.utils.compileForTest
import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.Severity
import io.gitlab.arturbosch.detekt.api.internal.CompilerResources
import io.gitlab.arturbosch.detekt.rules.KotlinCoreEnvironmentTest
import io.gitlab.arturbosch.detekt.test.TestConfig
import io.gitlab.arturbosch.detekt.test.compileAndLintWithContext
import io.gitlab.arturbosch.detekt.test.getContextForPaths
import java.io.File
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.jetbrains.kotlin.com.intellij.mock.MockProject
import org.jetbrains.kotlin.com.intellij.openapi.diagnostic.Logger
import org.jetbrains.kotlin.com.intellij.pom.PomModel
import org.jetbrains.kotlin.config.CompilerConfigurationKey
import org.jetbrains.kotlin.config.languageVersionSettings
import org.jetbrains.kotlin.resolve.calls.smartcasts.DataFlowValueFactoryImpl
import org.jetbrains.kotlin.utils.PrintingLogger
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

@KotlinCoreEnvironmentTest
internal class AnnotatePublicApisWithJvmOverloadsTest(private val env: KotlinCoreEnvironment) {
  @BeforeEach fun setUp() {}

  @Test
  fun testAutoCorrect() {
    (env.project as MockProject).registerService(PomModel::class.java, DetektPomModel(env.project))
    env.configuration.add(CompilerConfigurationKey(Config.AUTO_CORRECT_KEY), true)
    val languageVersionSettings = env.configuration.languageVersionSettings

    val ktFile = compileForTest(File("src/test/kotlin/cash/detektive/javacompat/TestCode.kt").toPath())

    AnnotatePublicApisWithJvmOverloads(TestConfig(Config.AUTO_CORRECT_KEY to true))
      .visitFile(
        ktFile,
        env.getContextForPaths(listOf(ktFile)),
        CompilerResources(languageVersionSettings, DataFlowValueFactoryImpl(languageVersionSettings)),
      )

    val modifiedContents = ktFile.viewProvider.contents
    assertThat(modifiedContents)
      .contains("class TestCode @JvmOverloads constructor(val things: List<String> = emptyList())")
    assertThat(modifiedContents).contains("@JvmOverloads\n fun doIt")
  }

  @ParameterizedTest
  @MethodSource("errorTestCases")
  fun reportsError(testCase: ErrorTestCase) {
    val findings = AnnotatePublicApisWithJvmOverloads(Config.empty).compileAndLintWithContext(env, testCase.code)

    assertThat(findings).hasSize(1)
    with(findings[0]) {
      assertThat(issue.severity).isEqualTo(Severity.Defect)
      assertThat(issue.id).isEqualTo("AnnotatePublicApisWithJvmOverloads")
      assertThat(entity.signature).contains(testCase.elementName)
      assertThat(message)
        .contains(
          "Public ${testCase.elementType.name.lowercase()} '${testCase.elementName}' " +
            "with default arguments, but without @JvmOverloads annotation"
        )
    }
  }

  @ParameterizedTest
  @MethodSource("noErrorTestCases")
  fun doesntReportsError(testCase: NoErrorTestCase) {
    val findings = AnnotatePublicApisWithJvmOverloads(Config.empty).compileAndLintWithContext(env, testCase.code)

    assertThat(findings).hasSize(0)
  }

  companion object {
    data class ErrorTestCase(
      val description: String,
      val elementType: ElementType,
      val elementName: String,
      val code: String,
    )

    private var defaultLoggerFactory = Logger.getFactory()

    @BeforeAll
    @JvmStatic
    fun beforeAll() {
      Logger.setFactory { PrintingLogger(System.out) }
    }

    @AfterAll
    @JvmStatic
    fun afterAll() {
      Logger.setFactory(defaultLoggerFactory)
    }

    @JvmStatic
    fun errorTestCases() =
      listOf(
        ErrorTestCase(
          description = "Public function with any default arguments, but without @JvmOverloads",
          elementType = ElementType.FUNCTION,
          elementName = "doIt",
          code =
            """
        class A {
          public fun doIt(x: String = "", y: Int) {}
        }
        """,
        ),
        ErrorTestCase(
          description = "Public constructor with any default arguments, but without @JvmOverloads",
          elementType = ElementType.CONSTRUCTOR,
          elementName = "Subject",
          code =
            """
        class Subject(x: String = "", y: Int) {}
        """,
        ),
      )

    data class NoErrorTestCase(val description: String, val code: String)

    @JvmStatic
    fun noErrorTestCases() =
      listOf(
        // Functions
        NoErrorTestCase(
          description = "Public function without default arguments",
          code =
            """
        class A {
          public fun doIt(x: String, y: Int) {}
        }
        """,
        ),
        NoErrorTestCase(
          description = "Public function with default arguments and with @JvmOverloads",
          code =
            """
        class A {
          @JvmOverloads
          public fun doIt(x: String = "", y: Int) {}
        }
        """,
        ),
        NoErrorTestCase(
          description = "Private function with default arguments",
          code =
            """
        class A {
          private fun doIt(x: String = "", y: Int) {}
        }
        """,
        ),
        NoErrorTestCase(
          description = "Protected function with default arguments",
          code =
            """
        class A {
          protected fun doIt(x: String = "", y: Int) {}
        }
        """,
        ),
        NoErrorTestCase(
          description = "Public function in an interface",
          code =
            """
        interface A {
          fun doIt(x: String = "")
        }
        """,
        ),
        NoErrorTestCase(
          description = "Public function in a private class",
          code =
            """
        private class A {
          fun doIt(x: String = "") = {}
        }
        """,
        ),
        // Constructors
        NoErrorTestCase(
          description = "Public constructor annotated with javax Inject",
          code =
            """
        import jakarta.inject.Inject

        class Subject @Inject constructor(x: String = "", y: Int) {}
        """,
        ),
        NoErrorTestCase(
          description = "Public constructor annotated with jakarta Inject",
          code =
            """
        import jakarta.inject.Inject

        class Subject @Inject constructor(x: String = "", y: Int) {}
        """,
        ),
        NoErrorTestCase(
          description = "Public constructor annotated with guice Inject",
          code =
            """
        import jakarta.inject.Inject

        class Subject @Inject constructor(x: String = "", y: Int) {}
        """,
        ),
        NoErrorTestCase(
          description = "Public constructor without default arguments",
          code =
            """
        class Subject(x: String, y: Int) {}
        """,
        ),
        NoErrorTestCase(
          description = "Public annotation constructor with default arguments and without @JvmOverloads",
          code =
            """
        @Retention(AnnotationRetention.RUNTIME)
        @Target(AnnotationTarget.FUNCTION)
        annotation class Authenticated(
          val services: Array<String> = [],
          val capabilities: Array<String> = []
        )
        """,
        ),
        NoErrorTestCase(
          description = "Public constructor with default arguments and with @JvmOverloads",
          code =
            """
        class Subject @JvmOverloads constructor(x: String, y: Int = 0) {}
        """,
        ),
        NoErrorTestCase(
          description = "Private constructor with default arguments, but without @JvmOverloads",
          code =
            """
        class Subject private constructor(x: String, y: Int = 0) {}
        """,
        ),
        NoErrorTestCase(
          description = "Internal class constructor with default arguments, but without @JvmOverloads",
          code =
            """
        internal class Subject constructor(x: String, y: Int = 0) {}
        """,
        ),
      )
  }
}
