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
import misk.jdbc.DataSourceType
import misk.jdbc.ScaleSafetyChecks
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import org.assertj.core.api.Assertions.assertThat
import org.hibernate.SessionFactory
import org.junit.jupiter.api.Test
import java.sql.Timestamp
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject

@MiskTest(startService = true)
class RealPagerTest {

  @MiskTestModule
  val module = MoviesTestModule(DataSourceType.MYSQL)

  @Inject @Movies private lateinit var transacter: Transacter
  @Inject @Movies lateinit var sessionFactory: SessionFactory
  @Inject lateinit var queryFactory: Query.Factory

  @Test fun idAscPagination() {
    val movieId = givenStarWarsMovie()
    givenStormtrooperCharacters(movieId, count = 100)
    val expectedCharacterIds = transacter.transaction { session ->
      queryFactory.newQuery(CharacterQuery::class)
        .movieId(movieId)
        .idAsc()
        .list(session)
        .map { it.id }
    }
    val actualCharacterIds = queryFactory.newQuery(CharacterQuery::class)
      .movieId(movieId)
      .newPager(idAscPaginator(), pageSize = 3)
      .listAll(transacter) { it.id }
    assertThat(actualCharacterIds).containsExactlyElementsOf(expectedCharacterIds)
  }

  @Test fun idDescPagination() {
    val movieId = givenStarWarsMovie()
    givenStormtrooperCharacters(movieId, count = 100)
    val expectedCharacterIds = transacter.transaction { session ->
      queryFactory.newQuery(CharacterQuery::class)
        .movieId(movieId)
        .idDesc()
        .list(session)
        .map { it.id }
    }
    val actualCharacterIds = queryFactory.newQuery(CharacterQuery::class)
      .movieId(movieId)
      .newPager(idDescPaginator(), pageSize = 4)
      .listAll(transacter) { it.id }
    assertThat(actualCharacterIds).containsExactlyElementsOf(expectedCharacterIds)
  }

  @Test fun customPaginationWithDuplicateNames() {
    val movieId = givenStarWarsMovie()
    givenStormtrooperCharacters(movieId, initialOperatingNumber = 1, count = 50)
    // The first page is split between rows with the same character name.
    givenStormtrooperCharacters(movieId, initialOperatingNumber = 4, count = 20)
    // More rows with duplicate names.
    givenStormtrooperCharacters(movieId, initialOperatingNumber = 24, count = 20)
    val expectedCharacterNames = transacter.transaction { session ->
      queryFactory.newQuery(CharacterQuery::class)
        .movieId(movieId)
        .nameDesc()
        .idAsc()
        .list(session)
        .map { it.name }
    }
    val actualCharacterNames = queryFactory.newQuery(CharacterQuery::class)
      .movieId(movieId)
      .newPager(CharacterNameDescIdAscPaginator, pageSize = 5)
      .listAll(transacter) { it.name }
    assertThat(actualCharacterNames).containsExactlyElementsOf(expectedCharacterNames)
  }

  @Test fun `hasNext always true on first page`() {
    val movieId = givenStarWarsMovie()
    val emptyListPager = queryFactory.newQuery(CharacterQuery::class)
      .movieId(movieId)
      .newPager(idDescPaginator(), pageSize = 4)

    // Even when the the first page is empty.
    assertThat(emptyListPager.hasNext()).isTrue

    // Add some entries.
    givenStormtrooperCharacters(movieId, count = 3)
    val pagerWithContents = queryFactory.newQuery(CharacterQuery::class)
      .movieId(movieId)
      .newPager(idDescPaginator(), pageSize = 4)
    assertThat(pagerWithContents.hasNext()).isTrue
  }

  @Test fun `hasNext true when there are more pages`() {
    val pageSize = 4
    val pageCount = 3
    val movieId = givenStarWarsMovie()
    givenStormtrooperCharacters(movieId, count = pageCount * pageSize)

    val pager = queryFactory.newQuery(CharacterQuery::class)
      .movieId(movieId)
      .newPager(idDescPaginator(), pageSize = pageSize)

    transacter.transaction { session -> pager.nextPage(session) }
    // 2 pages left
    assertThat(pager.hasNext()).isTrue

    transacter.transaction { session -> pager.nextPage(session) }
    // 1 page left
    assertThat(pager.hasNext()).isTrue
  }

  @Test fun `nextPage is null when there are no pages left`() {
    val pageSize = 4
    val pageCount = 3
    val movieId = givenStarWarsMovie()
    givenStormtrooperCharacters(movieId, count = pageCount * pageSize)

    val pager = queryFactory.newQuery(CharacterQuery::class)
      .movieId(movieId)
      .newPager(idDescPaginator(), pageSize = pageSize)
    // Go through all the pages.
    repeat(pageCount) {
      transacter.transaction { session -> pager.nextPage(session) }
    }

    val nextPage = transacter.transaction { session -> pager.nextPage(session) }
    assertThat(nextPage).isNull()
  }

  @Test fun `paging does not generate duplicate constraints and order by`() {
    val pageSize = 4
    val pageCount = 3
    val movieId = givenStarWarsMovie()
    givenStormtrooperCharacters(movieId, count = pageCount * pageSize)

    val pager = queryFactory.newQuery(CharacterQuery::class)
      .movieId(movieId)
      .newPager(idDescPaginator(), pageSize = pageSize)

    sessionFactory.openSession().use { session ->
      session.doWork { connection ->
        ScaleSafetyChecks.turnOnSqlGeneralLogging(connection)
      }
    }

    val start = Timestamp.from(Instant.now())

    transacter.transaction { session ->
      pager.nextPage(session)
      pager.nextPage(session)
      pager.nextPage(session)
    }

    val queries = sessionFactory.openSession().use { session ->
      session.doReturningWork { connection ->
        ScaleSafetyChecks.extractQueriesSince(connection, start)
      }
    }

    val pageQueries = queries.reversed().filter { it.contains("select") }.map {
      Regex("""/\* (.*) \*/""").find(it)!!.groups[1]!!.value
    }
    assertThat(pageQueries).hasSize(3)
    // The first page doesn't have an (id > ?) condition
    assertThat(countClauses(pageQueries[0])).isEqualTo(Clauses(1, 1))
    // The second page each have exactly one (id > ?) condition and one ORDER BY column
    assertThat(countClauses(pageQueries[1])).isEqualTo(Clauses(2, 1))
    assertThat(countClauses(pageQueries[2])).isEqualTo(Clauses(2, 1))
  }

  private fun countClauses(query: String): Clauses {
    val whereMatches = Regex("where (.*) order by").find(query)
    val whereCount = if (whereMatches != null) {
      whereMatches.groups[1]!!.value.split(" and ").size
    } else 0
    val orderByMatches = Regex("order by (.*)").find(query)
    val orderByCount = if (orderByMatches != null) {
      orderByMatches.groups[1]!!.value.split(",").size
    } else 0

    return Clauses(whereCount, orderByCount)
  }

  private data class Clauses(val where: Int, val orderBy: Int)

  @Test fun `hasNext is false where there are no pages left`() {
    val pageSize = 4
    val pageCount = 3
    val movieId = givenStarWarsMovie()
    givenStormtrooperCharacters(movieId, count = pageCount * pageSize)

    val pager = queryFactory.newQuery(CharacterQuery::class)
      .movieId(movieId)
      .newPager(idDescPaginator(), pageSize = pageSize)
    // Go through all the pages.
    repeat(pageCount) {
      transacter.transaction { session -> pager.nextPage(session) }
    }
    assertThat(pager.hasNext()).isFalse
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
