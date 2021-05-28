package misk.jooq

import app.cash.backfila.client.misk.jooq.gen.tables.references.BALANCE
import misk.jooq.JooqTransacter.Companion.noRetriesOptions
import misk.jooq.config.ClientJooqTestingModule
import misk.jooq.config.JooqDBIdentifier
import misk.jooq.model.BalanceEntity
import misk.jooq.model.Currency
import misk.jooq.model.Jurisdiction
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.time.FakeClock
import org.assertj.core.api.Assertions
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.assertj.core.api.Assertions.assertThatExceptionOfType
import org.jooq.exception.DataAccessException
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@MiskTest(startService = true)
internal class JooqTransacterTest {
  @MiskTestModule private var module = ClientJooqTestingModule()
  @Inject @JooqDBIdentifier private lateinit var transacter: JooqTransacter
  @Inject private lateinit var clock: FakeClock

  @Test fun `retries to the max number of retries in case of a data access exception`() {
    var numberOfRetries = 0
    assertThatExceptionOfType(DataAccessException::class.java).isThrownBy {
      transacter.transaction {
        numberOfRetries++
        throw DataAccessException("")
      }
    }
    assertThat(numberOfRetries).isEqualTo(3)
  }

  @Test fun `retries and succeeds after the first attempt in case of a data access exception`() {
    var numberOfRetries = 0
    assertThatCode {
      transacter.transaction {
        numberOfRetries++
        // Throw an exception only once
        if (numberOfRetries <= 1) {
          throw DataAccessException("")
        }
      }
    }.doesNotThrowAnyException()
    // It will retry once, so totally it should have executed twice.
    assertThat(numberOfRetries).isEqualTo(2)
  }

  @Test fun `does not retry in case of any other exception except DataAccessException`() {
    var numberOfRetries = 0
    assertThatExceptionOfType(IllegalStateException::class.java).isThrownBy {
      transacter.transaction {
        numberOfRetries++
        throw IllegalStateException("")
      }
    }
    assertThat(numberOfRetries).isEqualTo(1)
  }

  @Test fun `retries and succeeds in case of an optimistic lock exception`() {
    val balance = transacter.transaction { ctx ->
      ctx.newRecord(BALANCE).apply {
        this.entity = BalanceEntity.BTC_FLOAT.name
        this.jurisdiction = Jurisdiction.USA.name
        this.currency = Currency.BTC.name
        this.amountCents = 1000L
        createdAt = clock.instant().toLocalDateTime()
        updatedAt = clock.instant().toLocalDateTime()
      }.also { it.store() }
    }
    executeInThreadsAndWait(
      {
        transacter.transaction { ctx ->
          ctx.selectFrom(BALANCE).where(BALANCE.ID.eq(balance.id))
            .fetchOne()
            ?.apply { this.amountCents = 2000L }
            ?.also { it.store() }
        }
      },
      {
        transacter.transaction { ctx ->
          ctx.selectFrom(BALANCE).where(BALANCE.ID.eq(balance.id))
            .fetchOne()
            ?.apply { this.jurisdiction = Jurisdiction.CAN.name }
            ?.also { it.store() }
        }
      }
    )

    val updatedBalance = transacter.transaction { ctx ->
      ctx.selectFrom(BALANCE).where(BALANCE.ID.eq(balance.id))
        .fetchOne()
    }
    assertThat(updatedBalance).isNotNull
    assertThat(updatedBalance!!.amountCents).isEqualTo(2000) // updated balance
    assertThat(updatedBalance.jurisdiction).isEqualTo(Jurisdiction.CAN.name) // updated balance
  }

  private fun executeInThreadsAndWait(vararg tasks: () -> Unit) {
    val countDownLatch = CountDownLatch(tasks.size)
    val threads = tasks.map { task ->
      Thread {
        task()
        countDownLatch.countDown()
        try {
          countDownLatch.await(10, TimeUnit.SECONDS)
        } catch (e: InterruptedException) {
          throw RuntimeException(e)
        }
      }
    }
    threads.forEach { it.start() }
    threads.forEach { thread -> thread.join(10000) }
  }

  @Test fun `does not retry in case of no exception`() {
    var numberOfRetries = 0
    transacter.transaction {
      numberOfRetries++
    }
    assertThat(numberOfRetries).isEqualTo(1)
  }

  @Test fun `does not retry in a noRetriesTransaction in case of any exception`() {
    var numberOfRetries = 0
    assertThatExceptionOfType(IllegalStateException::class.java).isThrownBy {
      transacter.transaction(noRetriesOptions) {
        numberOfRetries++
        throw IllegalStateException()
      }
    }
    assertThat(numberOfRetries).isEqualTo(1)
  }

  @Test fun `should rollback in case there is an exception thrown`() {
    Assertions.assertThatThrownBy {
      transacter.transaction(noRetriesOptions) { ctx ->
        ctx.newRecord(BALANCE).apply {
          this.entity = BalanceEntity.BTC_FLOAT.name
          this.jurisdiction = Jurisdiction.USA.name
          this.currency = Currency.BTC.name
          this.amountCents = 1000L
          createdAt = clock.instant().toLocalDateTime()
          updatedAt = clock.instant().toLocalDateTime()
        }.also { it.store() }
        throw IllegalStateException("") // to force a rollback
      }
    }

    val numberOfRecords = transacter.transaction(noRetriesOptions) { ctx ->
      ctx.selectCount().from(BALANCE).fetchOne()!!.component1()
    }
    assertThat(numberOfRecords).isEqualTo(0)
  }

  /**
   * The way the code works is that, every time we call transacter.transaction { } it
   * gets a new connection from the connection pool. So even though it looks like a
   * nested transaction, it's not. The inner transaction works on a new connection and
   * will commit and rollback based on what happens in its boundary.
   * This test is to prove that this works.
   */
  @Test fun `nested transactions work as they are not really nested transactions`() {
    transacter.transaction(noRetriesOptions) {
      val balance = transacter.transaction(noRetriesOptions) { ctx ->
        ctx.newRecord(BALANCE).apply {
          this.entity = BalanceEntity.BTC_FLOAT.name
          this.jurisdiction = Jurisdiction.USA.name
          this.currency = Currency.BTC.name
          this.amountCents = 1000L
          createdAt = clock.instant().toLocalDateTime()
          updatedAt = clock.instant().toLocalDateTime()
        }.also { it.store() }
      }

      transacter.transaction(noRetriesOptions) { ctx ->
        ctx.update(BALANCE).set(BALANCE.AMOUNT_CENTS, 2000L).execute()
      }

      val updatedBalance = transacter.transaction { ctx ->
        ctx.selectFrom(BALANCE).where(BALANCE.ID.eq(balance.id))
          .fetchOne()
      }

      assertThat(updatedBalance!!.amountCents).isEqualTo(2000L)
    }
  }
}
