package misk.hibernate.advisorylocks

import jakarta.inject.Inject
import misk.hibernate.Movies
import misk.hibernate.MoviesTestModule
import misk.jdbc.DataSourceType
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import org.assertj.core.api.Assertions.assertThat
import org.hibernate.Session
import org.hibernate.SessionFactory
import org.hibernate.resource.jdbc.spi.PhysicalConnectionHandlingMode
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import java.util.concurrent.Callable
import java.util.concurrent.LinkedBlockingDeque
import kotlin.test.assertTrue

abstract class HibernateSessionLocksTest {
  @Inject
  @Movies
  private lateinit var sessionFactory: SessionFactory

  @Test
  fun `acquiring and releasing`() {
    val lockKey2 = "test-lock-2"

    val session = openSession()
    session.use {
      assertTrue(it.tryAcquireLock(LOCK_KEY))
      // The same session should be able to acquire the lock again
      assertTrue(it.tryAcquireLock(LOCK_KEY))
      // The same session should be able to acquire a different lock
      assertTrue(it.tryAcquireLock(lockKey2))

      // Release the locks
      it.tryReleaseLock(LOCK_KEY)
      it.tryReleaseLock(lockKey2)
    }
  }

  @Test
  fun `only one session may hold a lock`() {
    val linkedBlockingQueue = LinkedBlockingDeque<Boolean>()
    val firstSession = openSession()
    firstSession.use { outer ->
      linkedBlockingQueue.add(outer.tryAcquireLock(LOCK_KEY))

      val callable = Callable {
        val session = openSession()
        session.use { inner ->
          linkedBlockingQueue.add(inner.tryAcquireLock(LOCK_KEY))
        }
      }
      val thread = Thread { callable.call() }
      thread.start()
      thread.join()
    }
    assertThat(linkedBlockingQueue)
      .hasSize(2)
      .containsExactlyInAnyOrder(true, false)
  }

  @Test
  fun `should implicitly release lock on session close`() {
    openSession().use { session ->
      assertTrue(session.tryAcquireLock(LOCK_KEY))
    }

    // The lock should be released when the session is closed without calling release lock explicitly
    openSession().use { session ->
      assertTrue(session.tryAcquireLock(LOCK_KEY))
    }
  }

  @Test
  fun `releasing non-existent lock`() {
    assertThrows<IllegalStateException> {
      openSession().use { session ->
        session.tryReleaseLock("test-lock")
      }
    }
  }

  protected fun openSession(): Session =
    sessionFactory
      .withOptions()
      .connectionHandlingMode(PhysicalConnectionHandlingMode.IMMEDIATE_ACQUISITION_AND_HOLD)
      .openSession()

  companion object {
    private const val LOCK_KEY = "test-lock"
  }
}

@MiskTest(startService = true)
class MySQLSessionLocksTest : HibernateSessionLocksTest() {
  @MiskTestModule
  val module = MoviesTestModule(DataSourceType.MYSQL)

  @Test
  fun `should throw if key is too long`() {
    val key = "a".repeat(256)
    assertThrows<IllegalArgumentException> {
      openSession().use { session ->
        session.tryAcquireLock(key)
      }
    }
  }

  @Test
  fun `should NOT throw if key is the maximum length`() {
    val key = "a".repeat(64)
    assertDoesNotThrow {
      openSession().use { session ->
        session.tryAcquireLock(key)
      }
    }
  }
}

@MiskTest(startService = true)
class PostgresSessionLocksTest : HibernateSessionLocksTest() {
  @MiskTestModule
  val module = MoviesTestModule(DataSourceType.POSTGRESQL)

  @Test
  fun `should NOT throw if key is too long`() {
    val key = "a".repeat(256)
    assertDoesNotThrow {
      openSession().use { session ->
        session.tryAcquireLock(key)
      }
    }
  }
}

@MiskTest(startService = true)
class VitessSessionLocksTest : HibernateSessionLocksTest() {
  @MiskTestModule
  val module = MoviesTestModule(DataSourceType.VITESS_MYSQL)

  @Test
  fun `should throw if key is too long`() {
    val key = "a".repeat(256)
    assertThrows<IllegalArgumentException> {
      openSession().use { session ->
        session.tryAcquireLock(key)
      }
    }
  }

  @Test
  fun `should NOT throw if key is the maximum length`() {
    val key = "a".repeat(64)
    assertDoesNotThrow {
      openSession().use { session ->
        session.tryAcquireLock(key)
      }
    }
  }
}
