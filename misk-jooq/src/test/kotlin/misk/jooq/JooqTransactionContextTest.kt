package misk.jooq

import jakarta.inject.Inject
import misk.jooq.config.ClientJooqTestingModule
import misk.jooq.config.JooqDBIdentifier
import misk.jooq.config.JooqDBReadOnlyIdentifier
import misk.jooq.model.Genre
import misk.jooq.testgen.tables.references.MOVIE
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatExceptionOfType
import org.junit.jupiter.api.Test

@MiskTest(startService = true)
internal class JooqTransactionContextTest {
  @MiskTestModule @Suppress("unused") private var module = ClientJooqTestingModule()
  @Inject @JooqDBIdentifier private lateinit var transacter: JooqTransacter
  @Inject @JooqDBReadOnlyIdentifier private lateinit var readTransacter: JooqTransacter

  @Test
  fun `opens a new transaction when no ambient session is active`() {
    assertThat(JooqTransactionContext.get(transacter)).isNull()

    val sessionSeenInside =
      transacter.transactionOrAmbient { session ->
        assertThat(JooqTransactionContext.get(transacter)).isSameAs(session)
        session
      }

    assertThat(sessionSeenInside).isNotNull
    assertThat(JooqTransactionContext.get(transacter)).isNull()
  }

  @Test
  fun `nested call reuses the outer session`() {
    transacter.transactionOrAmbient { outer ->
      transacter.transactionOrAmbient { inner -> assertThat(inner).isSameAs(outer) }
    }
  }

  @Test
  fun `nested writes participate in the outer transaction's commit`() {
    val movieId =
      transacter.transactionOrAmbient { (outerCtx) ->
        val record =
          outerCtx.newRecord(MOVIE).apply {
            name = "Outer"
            genre = Genre.COMEDY.name
          }
        record.store()
        val outerId = record.id!!

        transacter.transactionOrAmbient { (innerCtx) ->
          val nested =
            innerCtx.newRecord(MOVIE).apply {
              name = "Inner"
              genre = Genre.HORROR.name
            }
          nested.store()
        }

        outerId
      }

    val rows =
      transacter.transaction { (ctx) -> ctx.selectFrom(MOVIE).where(MOVIE.NAME.`in`("Outer", "Inner")).fetch() }
    assertThat(rows.map { it.name }).containsExactlyInAnyOrder("Outer", "Inner")
    assertThat(movieId).isNotNull
  }

  @Test
  fun `outer rollback discards nested writes`() {
    assertThatExceptionOfType(RuntimeException::class.java).isThrownBy {
      transacter.transactionOrAmbient { (outerCtx) ->
        outerCtx
          .newRecord(MOVIE)
          .apply {
            name = "RollbackMe"
            genre = Genre.COMEDY.name
          }
          .store()

        transacter.transactionOrAmbient { (innerCtx) ->
          innerCtx
            .newRecord(MOVIE)
            .apply {
              name = "RollbackMeToo"
              genre = Genre.HORROR.name
            }
            .store()
        }

        throw RuntimeException("rollback")
      }
    }

    val rows =
      transacter.transaction { (ctx) ->
        ctx.selectFrom(MOVIE).where(MOVIE.NAME.`in`("RollbackMe", "RollbackMeToo")).fetch()
      }
    assertThat(rows).isEmpty()
  }

  @Test
  fun `ambient on one transacter does not leak to another`() {
    transacter.transactionOrAmbient { writerSession ->
      assertThat(JooqTransactionContext.get(transacter)).isSameAs(writerSession)
      assertThat(JooqTransactionContext.get(readTransacter)).isNull()

      readTransacter.transactionOrAmbient { readerSession ->
        assertThat(readerSession).isNotSameAs(writerSession)
        assertThat(JooqTransactionContext.get(readTransacter)).isSameAs(readerSession)
        assertThat(JooqTransactionContext.get(transacter)).isSameAs(writerSession)
      }

      assertThat(JooqTransactionContext.get(readTransacter)).isNull()
    }

    assertThat(JooqTransactionContext.get(transacter)).isNull()
    assertThat(JooqTransactionContext.get(readTransacter)).isNull()
  }

  @Test
  fun `withSession restores the previous ambient session after the block exits`() {
    val outer = transacter.transaction { it }
    val inner = transacter.transaction { it }

    JooqTransactionContext.withSession(transacter, outer) {
      assertThat(JooqTransactionContext.get(transacter)).isSameAs(outer)

      JooqTransactionContext.withSession(transacter, inner) {
        assertThat(JooqTransactionContext.get(transacter)).isSameAs(inner)
      }

      assertThat(JooqTransactionContext.get(transacter)).isSameAs(outer)
    }

    assertThat(JooqTransactionContext.get(transacter)).isNull()
  }

  @Test
  fun `withSession clears the thread-local map once the last entry is removed`() {
    val session = transacter.transaction { it }

    JooqTransactionContext.withSession(transacter, session) {
      assertThat(JooqTransactionContext.get(transacter)).isSameAs(session)
    }

    assertThat(JooqTransactionContext.get(transacter)).isNull()
  }
}
