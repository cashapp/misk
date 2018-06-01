package misk.hibernate

import misk.testing.MiskTest
import misk.testing.MiskTestModule
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

@MiskTest(startService = true)
class TransacterTest {
  @MiskTestModule
  val module = HibernateTestModule()

  @Inject @Movies lateinit var transacter: Transacter
  @Inject lateinit var queryFactory: Query.Factory
  @Inject lateinit var clock: Clock

  @Test
  fun test() {
    // Insert some movies, characters and actors.
    transacter.transaction { session ->
      val jp = session.save(DbMovie("Jurassic Park", LocalDate.of(1993, 6, 9), clock.instant()))
      val sw = session.save(DbMovie("Star Wars", LocalDate.of(1977, 5, 25), clock.instant()))
      assertThat(setOf(jp, sw)).hasSize(2) // Uniqueness check.

      val ld = session.save(DbActor("Laura Dern", LocalDate.of(1967, 2, 10)))
      val jg = session.save(DbActor("Jeff Goldblum", LocalDate.of(1952, 10, 22)))
      val cf = session.save(DbActor("Carrie Fisher", null))
      assertThat(setOf(ld, jg, cf)).hasSize(3) // Uniqueness check.

      val ah = session.save(DbCharacter("Amilyn Holdo", session.load(sw), session.load(ld)))
      val es = session.save(DbCharacter("Ellie Sattler", session.load(jp), session.load(ld)))
      val im = session.save(DbCharacter("Ian Malcolm", session.load(jp), session.load(jg)))
      val lo = session.save(DbCharacter("Leia Organa", session.load(sw), session.load(cf)))
      assertThat(setOf(ah, es, im, lo)).hasSize(4) // Uniqueness check.
    }

    // Query that data.
    transacter.transaction { session ->
      val ianMalcolm = queryFactory.newQuery<CharacterQuery>()
          .name("Ian Malcolm")
          .uniqueResult(session)!!
      assertThat(ianMalcolm.actor.name).isEqualTo("Jeff Goldblum")
      assertThat(ianMalcolm.movie.name).isEqualTo("Jurassic Park")

      val lauraDernMovies = queryFactory.newQuery<CharacterQuery>()
          .actorName("Laura Dern")
          .listAs<MovieNameAndReleaseDate>(session)
      assertThat(lauraDernMovies).containsExactly(
          MovieNameAndReleaseDate("Star Wars", LocalDate.of(1977, 5, 25)),
          MovieNameAndReleaseDate("Jurassic Park", LocalDate.of(1993, 6, 9)))

      val actorsInOldMovies = queryFactory.newQuery<CharacterQuery>()
          .movieReleaseDateBefore(LocalDate.of(1980, 1, 1))
          .listAs<ActorAndReleaseDate>(session)
      assertThat(actorsInOldMovies).containsExactly(
          ActorAndReleaseDate("Laura Dern", LocalDate.of(1977, 5, 25)),
          ActorAndReleaseDate("Carrie Fisher", LocalDate.of(1977, 5, 25)))
    }
  }

  interface CharacterQuery : Query<DbCharacter> {
    @Constraint("name")
    fun name(name: String): CharacterQuery

    @Constraint("actor.name")
    fun actorName(name: String): CharacterQuery

    @Constraint("movie.name")
    fun movieName(name: String): CharacterQuery

    @Constraint(path = "movie.release_date", operator = "<")
    fun movieReleaseDateBefore(upperBound: LocalDate): CharacterQuery
  }

  data class MovieNameAndReleaseDate(
    @Property("movie.name") var movieName: String,
    @Property("movie.release_date") var movieReleaseDate: LocalDate?
  ) : Projection

  data class ActorAndReleaseDate(
    @Property("actor.name") var actorName: String,
    @Property("movie.release_date") var movieReleaseDate: LocalDate?
  ) : Projection
}
