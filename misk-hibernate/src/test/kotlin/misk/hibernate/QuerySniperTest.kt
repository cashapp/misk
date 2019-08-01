package misk.hibernate

import com.google.inject.Module
import misk.inject.KAbstractModule
import misk.inject.keyOf
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import org.assertj.core.api.Assertions.assertThat
import org.hibernate.SessionFactory
import org.junit.jupiter.api.Test
import java.time.Duration
import java.util.concurrent.LinkedBlockingDeque
import javax.inject.Inject
import javax.inject.Provider
import javax.persistence.PersistenceException
import kotlin.test.fail

@MiskTest(startService = true)
class QuerySniperTest {
  @Suppress("unused")
  @MiskTestModule val module: Module = object : KAbstractModule() {
    override fun configure() {
      install(MoviesTestModule())
      val qualifier = Movies::class
      val querySniperFactoryProvider = getProvider(QuerySniper.Factory::class.java)
      val timeoutsConfigProvider = getProvider(TimeoutsConfig::class.java)
      val transactionRunnerFactoryProvider = getProvider(TransactionRunner.Factory::class.java)
      val sessionFactoryProvider = getProvider(keyOf<SessionFactory>(qualifier))

      bind(keyOf<RealTransacter>(qualifier)).toProvider(object : Provider<RealTransacter> {
        @Inject lateinit var queryTracingListener: QueryTracingListener
        override fun get() = RealTransacter(
            qualifier = qualifier,
            sessionFactoryProvider = sessionFactoryProvider,
            queryTracingListener = queryTracingListener,
            tracer = null,
            transactionRunnerFactory = transactionRunnerFactoryProvider.get(),
            querySniperFactory = querySniperFactoryProvider.get(),
            timeoutsConfig = timeoutsConfigProvider.get()
        )
      })
    }
  }

  @Inject @Movies internal lateinit var transacter: RealTransacter
  @Inject lateinit var queryFactory: Query.Factory

  @Test fun sniperTriggersCallbacksAfterThreshold() {
    val log = LinkedBlockingDeque<String>()

    transacter.timed(log).transaction { session ->
      session.hibernateSession.createNativeQuery("SELECT sleep(0.3)").uniqueResult()
    }

    assertThat(log).containsExactly(
        "Warning fired",
        "Kill callback fired"
    )
  }

  @Test fun markedSlowQueriesAreNotKilledOrWarned() {
    val log = LinkedBlockingDeque<String>()

    transacter.timed(log).transaction { session ->
      session.runSlowQuery {
        hibernateSession.createNativeQuery("SELECT sleep(0.3)").uniqueResult()
      }
    }

    assertThat(log).isEmpty()
  }

  @Test fun warnOnlyTimeout() {
    val log = LinkedBlockingDeque<String>()

    transacter.warnOnly(log).transaction { session ->
      session.hibernateSession.createNativeQuery("SELECT sleep(0.3)").uniqueResult()
    }

    assertThat(log).containsExactly("Warning fired")
  }

  @Test fun querySniperWatchesStatementsNotTheTransaction() {
    val log = LinkedBlockingDeque<String>()

    transacter.timed(log).transaction { session ->
      // This will take about 10 seconds to run. Timeouts for this transaction are (15s, 30s).
      for (i in 1..100) {
        session.hibernateSession.createNativeQuery("SELECT sleep(0.1)").uniqueResult()
      }
    }

    assertThat(log).isEmpty()
  }

  @Test fun malformedQueriesNotAffected() {
    val log = LinkedBlockingDeque<String>()

    transacter.timed(log).transaction { session ->
      try {
        session.hibernateSession.createNativeQuery("Not valid sql").uniqueResult()
        fail()
      } catch (_: PersistenceException) {
      }
      session.hibernateSession.createNativeQuery("SELECT sleep(0.3)").uniqueResult()
    }

    assertThat(log).containsExactly(
        "Warning fired",
        "Kill callback fired"
    )
  }

  /**
   * For these tests, we specify custom callbacks for the [QuerySniper] that is installed on each
   * session of the transacter. This is done so that we can assert state. In this test, the queries
   * are not killed; we only care that the events fire. Outside of this test, [QuerySniper.watch]
   * will install callbacks that actually do log warnings and kill queries.
   */
  private fun RealTransacter.timed(log: LinkedBlockingDeque<String>): Transacter {
    return this.withSniperActions(log)
        .withTimeouts(Timeouts.create(Duration.ofSeconds(15), Duration.ofSeconds(30)))
        .withQueryTimeouts(Timeouts.create(Duration.ofMillis(200), Duration.ofMillis(250)))
        .withSlowQueryTimeouts(Timeouts.NONE)
  }

  private fun RealTransacter.warnOnly(log: LinkedBlockingDeque<String>): Transacter {
    return this.withSniperActions(log)
        .withTimeouts(Timeouts.NONE)
        .withQueryTimeouts(Timeouts.create(warnAfter = Duration.ofMillis(150)))
        .withSlowQueryTimeouts(Timeouts.NONE)
  }

  private fun RealTransacter.withSniperActions(log: LinkedBlockingDeque<String>): Transacter {
    return this.withOptions(options.copy(
        sniperWarning = { log.add("Warning fired") },
        sniperAction = { log.add("Kill callback fired") }
    ))
  }
}