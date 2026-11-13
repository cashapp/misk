package misk.jooq

import jakarta.inject.Inject
import misk.jooq.config.ClientJooqTestingModule
import misk.jooq.config.JooqDBIdentifier
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

  @Test
  fun `opens a new transaction when no ambient session is supplied`() {
    val sessionSeenInside = transacter.transactionOrAmbient { session -> session }
    assertThat(sessionSeenInside).isNotNull
  }

  @Test
  fun `nested call reuses the supplied ambient session`() {
    transacter.transactionOrAmbient { outer ->
      transacter.transactionOrAmbient(ambient = outer) { inner -> assertThat(inner).isSameAs(outer) }
    }
  }

  @Test
  fun `nested writes participate in the outer transaction's commit`() {
    val movieId =
      transacter.transactionOrAmbient { outer ->
        val (outerCtx) = outer
        val record =
          outerCtx.newRecord(MOVIE).apply {
            name = "Outer"
            genre = Genre.COMEDY.name
          }
        record.store()
        val outerId = record.id!!

        transacter.transactionOrAmbient(ambient = outer) { inner ->
          val (innerCtx) = inner
          innerCtx
            .newRecord(MOVIE)
            .apply {
              name = "Inner"
              genre = Genre.HORROR.name
            }
            .store()
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
      transacter.transactionOrAmbient { outer ->
        val (outerCtx) = outer
        outerCtx
          .newRecord(MOVIE)
          .apply {
            name = "RollbackMe"
            genre = Genre.COMEDY.name
          }
          .store()

        transacter.transactionOrAmbient(ambient = outer) { inner ->
          val (innerCtx) = inner
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
  fun `omitting ambient opens a new independent transaction`() {
    val outerSession = transacter.transactionOrAmbient { it }
    val innerSession = transacter.transactionOrAmbient { it }
    assertThat(innerSession).isNotSameAs(outerSession)
  }
}
