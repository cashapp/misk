package misk.vitess.testing

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertNull
import org.junit.jupiter.api.assertThrows

/** This test suite contains tests that validate behavior on non-running VitessTestDb instances. */
class NonRunningValidationTest {
  @Test
  fun `test truncate fails when database is not running`() {
    val nonRunningDb = VitessTestDb(containerName = "non_running_vitess_test_db", port = 50003)
    val exception = assertThrows<VitessTestDbTruncateException>(nonRunningDb::truncate)
    assertEquals("Failed to truncate tables", exception.message)
    assertEquals(
      "Container `non_running_vitess_test_db` not found, unable to get host port mappings.",
      exception.cause!!.message!!,
    )
  }

  @Test
  fun `test shutdown on nonexistent container`() {
    val nonExistentDb = VitessTestDb(containerName = "vitess_does_not_exist_db")
    val shutdownResult = nonExistentDb.shutdown()
    assertNull(shutdownResult.containerId)
    assertFalse(shutdownResult.containerRemoved)
  }
}
