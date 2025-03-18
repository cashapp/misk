package misk.vitess.testing.internal

import misk.vitess.testing.VitessTestDb
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class VitessQueryExecutorTest {
  @Test
  fun `explicit exception thrown`() {
    val port = 27703
    val testDb =
      VitessTestDb(containerName = "vitess_query_executor_vitess_db", autoApplySchemaChanges = false, port = port)
    testDb.run()

    val vitessQueryExecutor = VitessQueryExecutor(VitessClusterConfig(port))
    val executeQueryException =
      assertThrows<VitessQueryExecutorException> {
        vitessQueryExecutor.executeQuery("SELECT * FROM non_existent_table")
      }
    assertEquals("Failed to run executeQuery on query: SELECT * FROM non_existent_table", executeQueryException.message)

    val executeException =
      assertThrows<VitessQueryExecutorException> {
        vitessQueryExecutor.execute("UPDATE non_existent_table SET column = value WHERE id = 1")
      }
    assertEquals(
      "Failed to run execute on query: UPDATE non_existent_table SET column = value WHERE id = 1",
      executeException.message,
    )
  }
}
