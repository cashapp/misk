package misk.web.formatter

import com.google.common.base.CharMatcher
import misk.web.NetworkChain
import misk.web.NetworkInterceptor
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ClassNameFormatterTest {
  fun isValid(className: String) {
    assertTrue(className.isNotBlank())
    assertNotNull(className)
    assertTrue(
        CharMatcher.inRange('A', 'Z').or(CharMatcher.inRange('a', 'z')).matchesAnyOf(className))
  }

  @Test fun validQualifiedName() {
    val formatted = ClassNameFormatter.format(ValidQualifiedNameClass::class)
    isValid(formatted)
    assertEquals(formatted, ValidQualifiedNameClass::class.qualifiedName.toString())
  }

  @Test fun missingQualifiedName() {
    val noQualifiedNameClass = NoQualifiedNameFactory().create()
    assertNull(noQualifiedNameClass::class.qualifiedName)

    val formatted = ClassNameFormatter.format(noQualifiedNameClass::class)
    isValid(formatted)
    assertEquals(formatted, noQualifiedNameClass::class.toString().split("class ").last())
  }
}

class ValidQualifiedNameClass

class NoQualifiedNameFactory {
  fun create(): NetworkInterceptor {
    return NoQualifiedNameClass
  }

  private companion object {
    val NoQualifiedNameClass = object : NetworkInterceptor {
      override fun intercept(chain: NetworkChain) {
      }
    }
  }
}