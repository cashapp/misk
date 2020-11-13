package org.assertj.core.api

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import kotlin.test.assertFailsWith

internal class AssertExtensionsTest {
  @Test
  fun assertAsJsonMatches() {
    // Should ignore all of the whitespace differences here
    assertThat("""
{"my_structure"    :   [  "this", 45,   "zip" ],
   "my_value":"another value"


}
""").isEqualToAsJson("""
{
  "my_structure" : ["this", 45, "zip"],
  "my_value"     : "another value"
}
""")
  }

  @Test
  fun assertAsJsonMismatchedFieldName() {
    assertThat(assertFailsWith<AssertionError> {
      assertThat("""
{
  "my_structure2"   : ["this", 45, "zip" ],
  "my_value"        : "another value"
}
""").isEqualToAsJson("""
{
  "my_structure" : ["this", 45, "zip"],
  "my_value"     : "another value"
}
""")
    }).hasMessage(
        """
Expecting:
 <"{ "my_structure2" : [ "this" , 45, "zip" ], "my_value" : "another value" }">
to be equal to:
 <"{ "my_structure" : [ "this" , 45, "zip" ], "my_value" : "another value" }">
but was not.""")
  }

  @Test
  fun assertAsJsonMismatchedFieldValue() {
    assertThat(assertFailsWith<AssertionError> {
      assertThat("""
{
  "my_structure"    : ["thisisit", 45, "zip" ],
  "my_value"        : "another value"
}
""").isEqualToAsJson("""
{
  "my_structure" : ["this", 45, "zip"],
  "my_value"     : "another value"
}
""")
    }).hasMessage(
        """
Expecting:
 <"{ "my_structure" : [ "thisisit" , 45, "zip" ], "my_value" : "another value" }">
to be equal to:
 <"{ "my_structure" : [ "this" , 45, "zip" ], "my_value" : "another value" }">
but was not.""")
  }

  @Test
  fun assertAsJsonMismatchedWhitespaceInStringFieldValue() {
    assertThat(assertFailsWith<AssertionError> {
      assertThat("""
{
  "my_structure"    : ["this", 45, "zip" ],
  "my_value"        : "another value"
}
""").isEqualToAsJson("""
{
  "my_structure" : ["thi  s", 45, "zip"],
  "my_value"     : "another value"
}
""")
    }).hasMessage(
  """
Expecting:
 <"{ "my_structure" : [ "this" , 45, "zip" ], "my_value" : "another value" }">
to be equal to:
 <"{ "my_structure" : [ "thi  s" , 45, "zip" ], "my_value" : "another value" }">
but was not.""")
  }
}
