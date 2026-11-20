package misk.mcp.internal

import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.jupiter.api.Test

class UriTemplateTest {
  @Test
  fun `simple variables match one segment and are percent-decoded`() {
    val template = UriTemplate("users://{userId}/profile")

    assertEquals(mapOf("userId" to "alice b"), template.match("users://alice%20b/profile")?.variables)
    assertNull(template.match("users://alice/b/profile"))
    assertNull(template.match("users://alice/settings"))
  }

  @Test
  fun `reserved variables match across segments`() {
    assertEquals(mapOf("path" to "a/b/c.txt"), UriTemplate("file:///{+path}").match("file:///a/b/c.txt")?.variables)
  }

  @Test
  fun `dot segments are resolved before matching`() {
    assertNull(UriTemplate("files://host/public/{name}").match("files://host/public/../private"))
  }

  @Test
  fun `more specific templates rank higher`() {
    val uri = "users://alice/profile"
    val specific = UriTemplate("users://{id}/profile").match(uri)!!
    val generic = UriTemplate("users://{id}/{section}").match(uri)!!
    val reserved = UriTemplate("users://{+rest}").match(uri)!!
    val simple = UriTemplate("users://{id}").match("users://alice")!!
    val reservedSimple = UriTemplate("users://{+id}").match("users://alice")!!

    assertEquals(listOf(specific, generic, reserved), listOf(reserved, specific, generic).sortedDescending())
    assert(simple > reservedSimple)
  }
}
