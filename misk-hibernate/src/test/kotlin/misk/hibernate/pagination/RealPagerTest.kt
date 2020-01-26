package misk.hibernate.pagination

import misk.hibernate.CharacterQuery
import misk.hibernate.DbCharacter
import misk.hibernate.DbMovie
import misk.hibernate.Id
import misk.hibernate.Movies
import misk.hibernate.MoviesTestModule
import misk.hibernate.Query
import misk.hibernate.Transacter
import misk.hibernate.load
import misk.hibernate.or
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import org.assertj.core.api.Assertions.assertThat
import org.hibernate.SessionFactory
import org.junit.jupiter.api.Test
import java.time.LocalDate
import javax.inject.Inject

@MiskTest(startService = true)
class RealPagerTest {

  @MiskTestModule
  val module = MoviesTestModule()

  @Inject @Movies private lateinit var transacter: Transacter
  @Inject @Movies lateinit var sessionFactory: SessionFactory
  @Inject lateinit var queryFactory: Query.Factory

  @Test fun idAscPagination() {
    val movieId = givenStarWarsMovie()
    givenStormtrooperCharacters(movieId, count = 100)
    transacter.transaction { session ->
      val expectedCharacterIds = queryFactory.newQuery(CharacterQuery::class)
          .movieId(movieId)
          .idAsc()
          .list(session)
          .map { it.id }
      val actualCharacterIds = queryFactory.newQuery(CharacterQuery::class)
          .movieId(movieId)
          .newPager(idAscPaginator(), pageSize = 3)
          .listAll(session) { it.id }
      assertThat(actualCharacterIds).containsExactlyElementsOf(expectedCharacterIds)
    }
  }

  @Test fun idDescPagination() {
    val movieId = givenStarWarsMovie()
    givenStormtrooperCharacters(movieId, count = 100)
    transacter.transaction { session ->
      val expectedCharacterIds = queryFactory.newQuery(CharacterQuery::class)
          .movieId(movieId)
          .idDesc()
          .list(session)
          .map { it.id }
      val actualCharacterIds = queryFactory.newQuery(CharacterQuery::class)
          .movieId(movieId)
          .newPager(idDescPaginator(), pageSize = 4)
          .listAll(session) { it.id }
      assertThat(actualCharacterIds).containsExactlyElementsOf(expectedCharacterIds)
    }
  }

  @Test fun customPaginationWithDuplicateNames() {
    val movieId = givenStarWarsMovie()
    givenStormtrooperCharacters(movieId, initialOperatingNumber = 1, count = 50)
    // The first page is split between rows with the same character name.
    givenStormtrooperCharacters(movieId, initialOperatingNumber = 4, count = 20)
    // More rows with duplicate names.
    givenStormtrooperCharacters(movieId, initialOperatingNumber = 24, count = 20)
    transacter.transaction { session ->
      val expectedCharacterNames = queryFactory.newQuery(CharacterQuery::class)
          .movieId(movieId)
          .nameDesc()
          .idAsc()
          .list(session)
          .map { it.name }
      val actualCharacterNames = queryFactory.newQuery(CharacterQuery::class)
          .movieId(movieId)
          .newPager(CharacterNameDescIdAscPaginator, pageSize = 5)
          .listAll(session) { it.name }
      assertThat(actualCharacterNames).containsExactlyElementsOf(expectedCharacterNames)
    }
  }

  private fun givenStarWarsMovie(): Id<DbMovie> {
    return transacter.transaction { session ->
      val movie = DbMovie("Star Wars: The Force Awakens", LocalDate.of(2015, 12, 14))
      session.save(movie)
      movie.id
    }
  }

  private fun givenStormtrooperCharacters(
    movieId: Id<DbMovie>,
    initialOperatingNumber: Int = 1,
    count: Int
  ) {
    return transacter.transaction { session ->
      val movie = session.load(movieId)
      for (i in initialOperatingNumber..count) {
        val operatingNumber = "$i".padStart(4, '0')
        val character = DbCharacter("Stormtrooper FN-$operatingNumber", movie)
        session.save(character)
      }
      movie.id
    }
  }
}

private object CharacterNameDescIdAscPaginator : Paginator<DbCharacter, CharacterQuery> {

  override fun getOffset(row: DbCharacter): Offset {
    return encodeOffset(row.name, row.id)
  }

  override fun applyOffset(query: CharacterQuery, offset: Offset?) {
    query.nameDesc()
    query.idAsc()
    if (offset == null) {
      return
    }
    val (name, id) = decodeOffset(offset)
    query.or {
      option {
        nameLessThan(name)
      }
      option {
        name(name).idMoreThan(id)
      }
    }
  }

  private fun encodeOffset(name: String, id: Id<DbCharacter>): Offset {
    return Offset("$name/$id")
  }

  private fun decodeOffset(offset: Offset): Pair<String, Id<DbCharacter>> {
    val (name, id) = offset.offset.split("/")
    return name to Id((id.toLong()))
  }
}
