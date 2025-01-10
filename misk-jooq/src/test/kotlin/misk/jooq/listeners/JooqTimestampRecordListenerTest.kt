package misk.jooq.listeners

import jakarta.inject.Inject
import misk.jooq.JooqTransacter
import misk.jooq.config.ClientJooqTestingModule
import misk.jooq.config.JooqDBIdentifier
import misk.jooq.model.Genre
import misk.jooq.testgen.tables.references.MOVIE
import misk.jooq.toLocalDateTime
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import wisp.time.FakeClock
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

@MiskTest(startService = true)
class JooqTimestampRecordListenerTest {
  @SuppressWarnings("unused") @MiskTestModule private var module = ClientJooqTestingModule()
  @Inject
  @JooqDBIdentifier private lateinit var transacter: JooqTransacter
  @Inject
  private lateinit var clock: FakeClock

  private val anHourBefore: LocalDateTime by lazy {
    clock.instant().minus(1, ChronoUnit.HOURS).toLocalDateTime()
  }

  private val aMinuteBefore: LocalDateTime by lazy {
    clock.instant().minus(1, ChronoUnit.MINUTES).toLocalDateTime()
  }

  @Test fun `should set the created and updated times if not already set`() {
    val movie = transacter.transaction { (ctx) ->
      ctx.newRecord(MOVIE).apply {
        this.genre = Genre.COMEDY.name
        this.name = "Enter the dragon"
      }.also { it.store() }
    }

    assertThat(movie.createdAt).isEqualTo(clock.instant().toLocalDateTime())
    assertThat(movie.updatedAt).isEqualTo(clock.instant().toLocalDateTime())

    val fetchedMovie = transacter.transaction { (ctx) ->
      ctx.selectFrom(MOVIE).where(MOVIE.NAME.eq("Enter the dragon")).fetchOne()!!
    }

    assertThat(fetchedMovie.createdAt).isEqualTo(clock.instant().toLocalDateTime())
    assertThat(fetchedMovie.updatedAt).isEqualTo(clock.instant().toLocalDateTime())
  }

  @Test fun `should not set the created and updated times if already set`() {
    val movie = transacter.transaction { (ctx) ->
      ctx.newRecord(MOVIE).apply {
        this.genre = Genre.COMEDY.name
        this.name = "Enter the dragon"
        this.createdAt = anHourBefore
        this.updatedAt = anHourBefore
      }.also { it.store() }
    }

    assertThat(movie.createdAt).isEqualTo(anHourBefore)
    assertThat(movie.updatedAt).isEqualTo(anHourBefore)

    val fetchedMovie = transacter.transaction { (ctx) ->
      ctx.selectFrom(MOVIE).where(MOVIE.NAME.eq("Enter the dragon")).fetchOne()!!
    }

    assertThat(fetchedMovie.createdAt).isEqualTo(anHourBefore)
    assertThat(fetchedMovie.updatedAt).isEqualTo(anHourBefore)
  }

  @Test fun `updates the updated at column during an update`() {
    transacter.transaction { (ctx) ->
      val record = ctx.newRecord(MOVIE).apply {
        this.genre = Genre.COMEDY.name
        this.name = "Enter the dragon"
        this.createdAt = anHourBefore
        this.updatedAt = anHourBefore
      }.also { it.store() }

      // force an update
      record.store()
    }

    val movie = transacter.transaction { (ctx) ->
      ctx.selectFrom(MOVIE).where(MOVIE.NAME.eq("Enter the dragon")).fetchOne()!!
    }
    assertThat(movie.updatedAt).isEqualTo(clock.instant().toLocalDateTime())
  }

  @Test fun `updated at will not be set if it has been changed before store is called`() {
    transacter.transaction { (ctx) ->
      val record = ctx.newRecord(MOVIE).apply {
        this.genre = Genre.COMEDY.name
        this.name = "Enter the dragon"
        this.createdAt = anHourBefore
        this.updatedAt = anHourBefore
      }.also { it.store() }

      // force an update
      record.updatedAt = aMinuteBefore
      record.store()
    }

    val movie = transacter.transaction { (ctx) ->
      ctx.selectFrom(MOVIE).where(MOVIE.NAME.eq("Enter the dragon")).fetchOne()!!
    }
    assertThat(movie.updatedAt).isEqualTo(aMinuteBefore)
  }
}
