package misk.jooq.listeners

import jakarta.inject.Inject
import misk.jooq.JooqTransacter
import misk.jooq.config.ClientJooqTestingModule
import misk.jooq.config.JooqDBIdentifier
import misk.jooq.model.Genre
import misk.jooq.testgen.tables.references.MOVIE
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import org.assertj.core.api.Assertions.assertThatExceptionOfType
import org.assertj.core.api.Assertions.assertThatNoException
import org.junit.jupiter.api.Test

@MiskTest(startService = true)
class AvoidUsingSelectStarListenerTest {
  @SuppressWarnings("unused") @MiskTestModule private var module = ClientJooqTestingModule()
  @Inject
  @JooqDBIdentifier private lateinit var transacter: JooqTransacter

  @Test fun `using select star throws an exception`() {
    transacter.transaction { (ctx) ->
      ctx.newRecord(MOVIE).apply {
        this.genre = Genre.COMEDY.name
        this.name = "Enter the dragon"
      }.also { it.store() }
    }

    assertThatExceptionOfType(AvoidUsingSelectStarException::class.java).isThrownBy {
      transacter.transaction {
        (ctx) -> ctx.select(MOVIE.asterisk()).from(MOVIE).fetchOne()
      }
    }
  }

  @Test fun `selecting all the fields does not throw an exception`() {
    transacter.transaction { (ctx) ->
      ctx.newRecord(MOVIE).apply {
        this.genre = Genre.COMEDY.name
        this.name = "Enter the dragon"
      }.also { it.store() }
    }

    assertThatNoException().isThrownBy {
      transacter.transaction {
        (ctx) -> ctx.select(*MOVIE.fields()).from(MOVIE).fetchOne()
      }
    }
  }
}
