package misk.exceptions

import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import org.junit.jupiter.api.Test

class ExceptionsTest {
  @Test
  fun `requireRequestNotNull throws if value is null`() {
    var exception: BadRequestException? = null
    try {
      @Suppress("IMPLICIT_NOTHING_TYPE_ARGUMENT_IN_RETURN_POSITION") requireRequestNotNull(null) { "lazy message" }
    } catch (e: BadRequestException) {
      exception = e
    }

    assertNotNull(exception)
    assertEquals("lazy message", exception.message)
  }

  @Test
  fun `requireRequestNotNull returns non-null value and contractually implies value is non-null`() {
    // don't let the kotlin compiler infer this is non-null
    val value: String? =
      lazy {
          if (true) {
            "value"
          } else {
            null
          }
        }
        .value

    val theValue = requireRequestNotNull(value) { "message" }
    assertEquals("value", theValue)

    // note: this is not value!!.length - the contract from the check implies this is non-null
    assertEquals("value".length, value.length)
  }
}
