package misk.hibernate.testing

import jakarta.inject.Inject
import java.sql.SQLException
import kotlin.test.Test
import kotlin.test.assertFailsWith
import misk.hibernate.DbPrimitiveTour
import misk.hibernate.PrimitivesDb
import misk.hibernate.Transacter
import misk.hibernate.load
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertNotNull
import org.junit.jupiter.api.assertThrows

@MiskTest(startService = true)
internal class TransacterFaultInjectorTest {
  @MiskTestModule val module = TransacterFaultInjectorTestModule()

  @Inject @PrimitivesDb lateinit var transacter: Transacter

  @Inject @PrimitivesDb lateinit var transacterFaultInjector: TransacterFaultInjector

  @Test
  fun `transaction works as expected`() {
    assertDoesNotThrow { transacter.transaction { session -> assertNotNull(session) } }
  }

  @Test
  fun `enqueuing null error, transaction works as expected`() {
    transacterFaultInjector.enqueueNoThrow()
    assertDoesNotThrow { saveTransaction() }
  }

  @Test
  fun `enqueuing 1 error throws on transaction`() {
    transacterFaultInjector.enqueueThrow(SQLException("test"))

    val exception = assertFailsWith<SQLException> { saveTransaction() }
    assertThat(exception.message).isEqualTo("test")

    assertDoesNotThrow { saveTransaction() }
  }

  @Test
  fun `enqueued actions in order`() {
    // Setup
    transacterFaultInjector.enqueueNoThrow()
    transacterFaultInjector.enqueueThrow(SQLException("test"))
    transacterFaultInjector.enqueueNoThrow()

    // Exercise
    assertDoesNotThrow { saveTransaction() }

    assertThrows<SQLException> { saveTransaction() }

    assertDoesNotThrow { saveTransaction() }
  }

  @Test
  fun `throw on delete`() {
    // Setup
    val item = transacter.transaction { session -> session.save(DbPrimitiveTour()) }
    transacterFaultInjector.enqueueThrow(SQLException("test"))

    // Verify
    assertThrows<SQLException> {
      // Exercise
      transacter.transaction { session -> session.delete(session.load(item)) }
    }
  }

  private fun saveTransaction() {
    transacter.transaction { session -> session.save(DbPrimitiveTour()) }
  }
}
