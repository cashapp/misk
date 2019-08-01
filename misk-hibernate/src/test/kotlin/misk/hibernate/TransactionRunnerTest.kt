package misk.hibernate

import com.google.inject.Module
import com.google.inject.util.Modules
import misk.concurrent.FakeScheduledExecutorService
import misk.inject.KAbstractModule
import misk.inject.keyOf
import misk.mockito.Mockito
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.time.FakeClock
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Duration
import java.time.LocalDate
import java.util.concurrent.LinkedBlockingDeque
import java.util.concurrent.ScheduledExecutorService
import javax.inject.Inject

@MiskTest(startService = true)
class TransactionRunnerTest {
  @Suppress("unused")
  @MiskTestModule val module: Module =
      Modules.override(MoviesTestModule())
          .with(object : KAbstractModule() {
            override fun configure() {
              bind(keyOf<ScheduledExecutorService>(ForHibernate::class))
                  .to(keyOf<FakeScheduledExecutorService>(ForHibernate::class))
              bind(keyOf<FakeScheduledExecutorService>(ForHibernate::class)).to(keyOf())
            }
          })

  @Inject @Movies lateinit var transacter: Transacter
  @Inject @ForHibernate lateinit var executor: FakeScheduledExecutorService
  @Inject lateinit var queryFactory: Query.Factory
  @Inject lateinit var clock: FakeClock

  val timeoutsConfig = TimeoutsConfig(
      transaction = Timeouts.create(
          warnAfter = Duration.ofSeconds(2),
          killAfter = Duration.ofSeconds(10)
      ),
      query = Timeouts.NONE,
      slowQuery = Timeouts.NONE
  )

  @Test fun runnerTriggersCallbacks() {
    val log = LinkedBlockingDeque<String>()
    val factory = TransactionRunner.Factory(executor)
    val runner = factory.create(
        session = Mockito.mock(),
        timeouts = Timeouts.create(Duration.ofSeconds(10), Duration.ofSeconds(30))
    )
    runner.doWork({
      clock.add(Duration.ofSeconds(31))
      executor.tick()
    }, warnCallback = {
      log.add("Warning fired")
    }, killCallback = {
      log.add("Kill callback fired")
    })

    assertThat(log).containsExactly(
        "Warning fired",
        "Kill callback fired"
    )
  }

  @Test fun killsTransactionsAfterThreshold() {
    // Assert this transaction is killed by the built in runner.
    assertThrows<IllegalStateException> {
      transacter.timed().transaction { session ->
        val movie = DbMovie("Star Wars", LocalDate.of(1993, 6, 9))
        clock.add(Duration.ofHours(1)) // Make this a ridiculously long transaction.
        executor.tick()
        session.save(movie)
      }
    }

    // Assert that the killed transaction was rolled back and no data was saved.
    assertThat(transacter.transaction { session: Session ->
      queryFactory.newQuery<MovieQuery>()
          .allowFullScatter()
          .allowTableScan()
          .list(session)
    }).isEmpty()
  }

  @Test fun transactionsNotKilledWhenNoTimeoutsSet() {
    val starWars = DbMovie("Star Wars", LocalDate.of(1993, 6, 9))
    transacter.noTimeouts().transaction { session ->
      clock.add(Duration.ofHours(1)) // Make this a ridiculously long transaction.
      executor.tick()
      session.save(starWars)
    }

    val lookup = transacter.noTimeouts().transaction { session: Session ->
      val query = queryFactory.newQuery<MovieQuery>()
      clock.add(Duration.ofHours(1)) // Make this a ridiculously long transaction.
      executor.tick()
      query.allowFullScatter()
          .allowTableScan()
          .uniqueResult(session)
    }
    assertThat(lookup).isEqualToComparingFieldByField(starWars)
  }

  @Test fun transactionsAreSafeIfWithinTimeouts() {
    val starWars = DbMovie("Star Wars", LocalDate.of(1993, 6, 9))

    transacter.timed().transaction { session ->
      clock.add(Duration.ofSeconds(5))
      executor.tick()
      session.save(starWars)
    }

    val lookup = transacter.transaction { session: Session ->
      val query = queryFactory.newQuery<MovieQuery>()
      query.allowFullScatter()
          .allowTableScan()
          .uniqueResult(session)
    }
    assertThat(lookup).isEqualToComparingFieldByField(starWars)
  }

  private fun Transacter.timed(): Transacter {
    return this.withTimeouts(timeoutsConfig.transaction)
        .withQueryTimeouts(timeoutsConfig.query)
        .withSlowQueryTimeouts(timeoutsConfig.slowQuery)
  }
}