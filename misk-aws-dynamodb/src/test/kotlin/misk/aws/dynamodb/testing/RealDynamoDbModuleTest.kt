package misk.aws.dynamodb.testing

import misk.dynamodb.RealDynamoDbModule
import misk.healthchecks.HealthCheck
import misk.healthchecks.HealthStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class RealDynamoDbModuleTest {
  @Test
  fun `init with mapper classes`() {
    val module =
      RealDynamoDbModule(requiredTableTypes = arrayOf(DyMovie::class, DyCharacter::class))
    val requiredTables = module.provideRequiredTables()
    assertEquals(DyCharacter.tableName, requiredTables[1].name)
    assertNull(requiredTables[1].healthCheck)
  }

  @Test
  fun `init with mapper classes and custom healthchecks`() {
    val healthCheck = FakeHealthyHealthCheck()
    val module = RealDynamoDbModule(
      requiredTableTypes = arrayOf(DyMovie::class, DyCharacter::class),
      customTableHealthChecks = mapOf(DyMovie::class to healthCheck)
    )

    val requiredTables = module.provideRequiredTables()
    assertEquals(2, requiredTables.size)
    assertEquals(DyMovie.tableName, requiredTables[0].name)
    assertEquals(healthCheck, requiredTables[0].healthCheck!!)

    assertEquals(DyCharacter.tableName, requiredTables[1].name)
    assertNull(requiredTables[1].healthCheck)
  }

  internal class FakeHealthyHealthCheck : HealthCheck {
    override fun status(): HealthStatus = HealthStatus.healthy()
  }
}
