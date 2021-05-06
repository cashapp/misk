package wisp.aws2.dynamodb.testing

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class DockerDynamoDbTest {

  @Test
  fun `create a DynamoDb docker container successfully`() {
    val dockerDynamoDb = DockerDynamoDb()
    dockerDynamoDb.startup()
    assertTrue(dockerDynamoDb.composer.running.get())
    dockerDynamoDb.shutdown()
    assertFalse(dockerDynamoDb.composer.running.get())
  }
}
