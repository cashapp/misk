package cash.detektive.javacompat

import dev.detekt.api.Config
import dev.detekt.api.modifiedText
import dev.detekt.test.TestConfig
import dev.detekt.test.junit.KotlinCoreEnvironmentTest
import dev.detekt.test.lintWithContext
import dev.detekt.test.utils.KotlinAnalysisApiEngine
import dev.detekt.test.utils.KotlinEnvironmentContainer
import kotlin.io.path.Path
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.kotlin.cli.jvm.config.javaSourceRoots
import org.jetbrains.kotlin.config.languageVersionSettings
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

@KotlinCoreEnvironmentTest
internal class AnnotatePublicApisWithJvmOverloadsTest(private val env: KotlinEnvironmentContainer) {
  @Test
  fun autoCorrectsEligibleDeclarationsWithoutChangingSuppressedOnes() {
    val code =
      """
      class TestCode(val things: List<String> = emptyList()) {
        fun doIt(x: String = "", y: Int) {}

        @Suppress("AnnotatePublicApisWithJvmOverloads")
        fun suppressed(x: Int = 0) {}
      }

      class Inline { fun eligible(x: Int = 0) {} }
      class Published @PublishedApi internal constructor(val x: Int = 0)

      @Suppress("detekt:AnnotatePublicApisWithJvmOverloads")
      class SuppressedConstructor(val x: Int = 0)
      """
        .trimIndent()

    val file = KotlinAnalysisApiEngine.compile(code, javaSourceRoots = env.configuration.javaSourceRoots.map(::Path))
    val findings =
      AnnotatePublicApisWithJvmOverloads(TestConfig(Config.AUTO_CORRECT_KEY to true))
        .visitFile(file, env.configuration.languageVersionSettings)

    assertThat(findings).hasSize(4)
    assertThat(findings).allSatisfy { finding -> assertThat(finding.suppressReasons).contains("Auto correct") }
    assertThat(file.modifiedText)
      .contains("class TestCode @JvmOverloads constructor(val things: List<String> = emptyList())")
      .containsPattern("@JvmOverloads\\s+fun doIt")
      .contains("class Inline { @JvmOverloads fun eligible(x: Int = 0) {} }")
      .contains("class Published @JvmOverloads @PublishedApi internal constructor(val x: Int = 0)")
      .contains("fun suppressed(x: Int = 0)")
      .contains("class SuppressedConstructor(val x: Int = 0)")
      .doesNotContain("@JvmOverloads\n    fun suppressed")
      .doesNotContain("class SuppressedConstructor @JvmOverloads")
    KotlinAnalysisApiEngine.compile(checkNotNull(file.modifiedText))
  }

  @ParameterizedTest
  @MethodSource("errorTestCases")
  fun reportsEligibleDeclaration(testCase: ErrorTestCase) {
    val findings = AnnotatePublicApisWithJvmOverloads(Config.empty).lintWithContext(env, testCase.code)

    assertThat(findings).hasSize(1)
    assertThat(findings.single().entity.signature).contains(testCase.elementName)
  }

  @ParameterizedTest
  @MethodSource("noErrorTestCases")
  fun ignoresIneligibleDeclaration(testCase: NoErrorTestCase) {
    val findings =
      AnnotatePublicApisWithJvmOverloads(Config.empty)
        .lintWithContext(
          env,
          testCase.code,
          "package javax.inject\n@Target(AnnotationTarget.CONSTRUCTOR) annotation class Inject",
          "package jakarta.inject\n@Target(AnnotationTarget.CONSTRUCTOR) annotation class Inject",
          "package com.google.inject\n@Target(AnnotationTarget.CONSTRUCTOR) annotation class Inject",
        )

    assertThat(findings).isEmpty()
  }

  companion object {
    data class ErrorTestCase(val description: String, val elementName: String, val code: String)

    @JvmStatic
    fun errorTestCases() =
      listOf(
        ErrorTestCase(
          description = "Public function with any default arguments, but without @JvmOverloads",
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
          elementName = "Subject",
          code =
            """
        class Subject(x: String = "", y: Int) {}
        """,
        ),
        ErrorTestCase(
          description = "Published internal function participates in the public binary API",
          elementName = "published",
          code =
            """
        class Subject {
          @PublishedApi internal fun published(x: Int = 0) {}
        }
        """,
        ),
        ErrorTestCase(
          description = "Published internal constructor participates in the public binary API",
          elementName = "Subject",
          code =
            """
        class Subject @PublishedApi internal constructor(x: Int = 0)
        """,
        ),
        ErrorTestCase(
          description = "Unrelated Inject annotation does not exempt an exported constructor",
          elementName = "Subject",
          code =
            """
        @Target(AnnotationTarget.CONSTRUCTOR)
        annotation class Inject
        class Subject @Inject constructor(x: Int = 0)
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
          description = "Ordinary internal function is not public binary API",
          code =
            """
        class Subject {
          internal fun hidden(x: Int = 0) {}
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
        import javax.inject.Inject

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
        import com.google.inject.Inject

        class Subject @Inject constructor(x: String = "", y: Int) {}
        """,
        ),
        NoErrorTestCase(
          description = "Aliased Inject annotation is resolved to its declaration",
          code =
            """
        import jakarta.inject.Inject as Dependency

        class Subject @Dependency constructor(x: Int = 0)
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
        NoErrorTestCase(
          description = "Public constructor inside a private class is not exported",
          code =
            """
        private class Subject(x: Int = 0)
        """,
        ),
        NoErrorTestCase(
          description = "Public nested class inside a private class is not exported",
          code =
            """
        private class Outer {
          class Subject(x: Int = 0)
        }
        """,
        ),
      )
  }
}
