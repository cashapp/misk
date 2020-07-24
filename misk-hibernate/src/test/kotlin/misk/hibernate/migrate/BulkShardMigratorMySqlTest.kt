package misk.hibernate.migrate

import misk.hibernate.CharacterQuery
import misk.hibernate.DbCharacter
import misk.hibernate.DbMovie
import misk.hibernate.MovieQuery
import misk.hibernate.Movies
import misk.hibernate.MoviesTestModule
import misk.hibernate.Query
import misk.hibernate.Shard
import misk.hibernate.Transacter
import misk.hibernate.allowTableScan
import misk.hibernate.createInSameShard
import misk.hibernate.shard
import misk.hibernate.transaction
import misk.jdbc.DataSourceType
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.ListAssert
import org.hibernate.SessionFactory
import org.junit.jupiter.api.Test
import javax.inject.Inject

@MiskTest(startService = true)
class BulkShardMigratorMySqlTest {
  @MiskTestModule
  val module = MoviesTestModule(DataSourceType.MYSQL)

  @Inject @Movies private lateinit var transacter: Transacter
  @Inject @Movies lateinit var sessionFactory: SessionFactory
  @Inject private lateinit var bulkShardMigratorFactory: BulkShardMigrator.Factory
  @Inject lateinit var queryFactory: Query.Factory

  @Test fun `standard migration`() {
    val sourceId = transacter.transaction { session ->
      val movie = DbMovie("Jurassic Park")
      session.save(movie)
      session.save(DbCharacter("Ellie Sattler", movie))
      session.save(DbCharacter("Ian Malcolm", movie))
      movie.id
    }

    val targetId = transacter.createInSameShard(sourceId) {
      DbMovie("Star Wars")
    }
    val sourceShard = transacter.transaction { sourceId.shard(it) }
    val targetShard = transacter.transaction { targetId.shard(it) }
    assertThat(sourceShard).isEqualTo(targetShard)

    bulkShardMigratorFactory.create(transacter, sessionFactory, DbMovie::class, DbCharacter::class)
        .rootColumn("movie_id")
        .source(sourceId)
        .target(targetId)
        .execute()

    // No records were lost.
    assertMovieNamesInShard(sourceShard).containsExactly("Jurassic Park", "Star Wars")
    assertCharacterNamesInShard(sourceShard).containsExactly("Ellie Sattler", "Ian Malcolm")
  }

  private fun assertMovieNamesInShard(shard: Shard): ListAssert<String> {
    return transacter.transaction(shard) { session ->
      ListAssert(
        queryFactory.newQuery(MovieQuery::class)
          .allowTableScan()
          .list(session)
          .map { it.name }
          .toList()
      )
    }
  }

  private fun assertCharacterNamesInShard(shard: Shard): ListAssert<String> {
    return transacter.transaction(shard) { session ->
      ListAssert(
        queryFactory.newQuery(CharacterQuery::class)
          .allowTableScan()
          .list(session)
          .map { it.name }
          .toList()
      )
    }
  }
}
