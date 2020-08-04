package misk.hibernate.migrate

import misk.hibernate.CharacterQuery
import misk.hibernate.DbCharacter
import misk.hibernate.DbMovie
import misk.hibernate.Id
import misk.hibernate.MovieQuery
import misk.hibernate.Movies
import misk.hibernate.MoviesTestModule
import misk.hibernate.Query
import misk.hibernate.Session
import misk.hibernate.Shard
import misk.hibernate.Transacter
import misk.hibernate.allowTableScan
import misk.hibernate.createInSameShard
import misk.hibernate.createInSeparateShard
import misk.hibernate.shard
import misk.hibernate.shards
import misk.hibernate.transaction
import misk.jdbc.DataSourceType
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.IntegerAssert
import org.assertj.core.api.ListAssert
import org.hibernate.SessionFactory
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import javax.inject.Inject

abstract class BulkShardMigratorTest {
  @Inject @Movies lateinit var transacter: Transacter
  @Inject @Movies lateinit var sessionFactory: SessionFactory
  @Inject lateinit var bulkShardMigratorFactory: BulkShardMigrator.Factory
  @Inject lateinit var queryFactory: Query.Factory

  @Test fun sameShardMigrate() {
    val sourceId = transacter.transaction { session ->
      val movie = DbMovie("Jurassic Park")
      session.save(movie)
      session.save(DbCharacter("Ellie Sattler", movie))
      session.save(DbCharacter("Ian Malcolm", movie))
      movie.id
    }

    val targetId = transacter.createInSameShard(sourceId) { DbMovie("Star Wars") }

    val sourceShard = transacter.transaction { sourceId.shard(it) }
    val targetShard = transacter.transaction { targetId.shard(it) }

    assertThat(sourceShard).isEqualTo(targetShard)

    assertMovieNamesInShard(sourceShard).containsExactlyInAnyOrder("Jurassic Park", "Star Wars")
    assertCharacterNamesInShard(sourceShard).containsExactly("Ellie Sattler", "Ian Malcolm")

    assertRowCount(sourceId, "Ellie Sattler", "Ian Malcolm").isEqualTo(2)
    assertRowCount(targetId, "Ellie Sattler", "Ian Malcolm").isEqualTo(0)

    // It is expected that we would work on two root entities while merging though on the same shard
    // for this case. The vitess safey checks throw, disabling it for now.
    bulkShardMigratorFactory.create(transacter, sessionFactory, DbMovie::class, DbCharacter::class)
        .rootColumn("movie_id")
        .source(sourceId)
        .target(targetId)
        .execute()

    // Movie remained in the same shard
    assertMovieNamesInShard(targetShard).containsExactlyInAnyOrder("Jurassic Park", "Star Wars")
    assertCharacterNamesInShard(targetShard).containsExactly("Ellie Sattler", "Ian Malcolm")

    assertRowCount(sourceId, "Ellie Sattler", "Ian Malcolm").isEqualTo(0)
    assertRowCount(targetId, "Ellie Sattler", "Ian Malcolm").isEqualTo(2)
  }

  fun executeStatement(session: Session, sql: String): Boolean {
    return session.hibernateSession.doReturningWork {
      connection -> connection.prepareStatement(sql).execute()
    }
  }

  fun assertMovieNamesInShard(shard: Shard): ListAssert<String> {
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

  fun assertCharacterNamesInShard(shard: Shard): ListAssert<String> {
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

  fun assertRowCount(movieId: Id<DbMovie>, vararg names: String): IntegerAssert {
    return IntegerAssert(
        transacter.transaction { session ->
          queryFactory.newQuery(CharacterQuery::class)
              .names(names.asList())
              .movieId(movieId)
              .list(session)
              .size
        }
    )
  }
}

@MiskTest(startService = true)
class BulkShardMigratorVitessMySqlTest: BulkShardMigratorTest() {
  @MiskTestModule
  val module = MoviesTestModule(DataSourceType.VITESS_MYSQL)

  @BeforeEach fun setup() {
    val movieShards = transacter.shards().filter { it.keyspace.name.startsWith("movie") }
    assertThat(movieShards).hasSize(2)
  }

  /** Create a root entity and some child entities on one shard and migrate them to another.  */
  @Test fun distinctShardsMigration() {
    val sourceId = transacter.transaction { session ->
      val movie = DbMovie("Jurassic Park")
      session.save(movie)
      session.save(DbCharacter("Ellie Sattler", movie))
      session.save(DbCharacter("Ian Malcolm", movie))
      movie.id
    }

    val targetId = transacter.createInSeparateShard(sourceId) {
      DbMovie("Star Wars")
    }

    val sourceShard = transacter.transaction { sourceId.shard(it) }
    val targetShard = transacter.transaction { targetId.shard(it) }

    assertThat(sourceShard).isNotEqualTo(targetShard)

    assertMovieNamesInShard(sourceShard).containsExactly("Jurassic Park")
    assertMovieNamesInShard(targetShard).containsExactly("Star Wars")

    assertCharacterNamesInShard(sourceShard).containsExactly("Ellie Sattler", "Ian Malcolm")
    assertCharacterNamesInShard(targetShard).isEmpty()

    assertRowCount(sourceId, "Ellie Sattler", "Ian Malcolm").isEqualTo(2)
    assertRowCount(targetId, "Ellie Sattler", "Ian Malcolm").isEqualTo(0)

    bulkShardMigratorFactory.create(transacter, sessionFactory, DbMovie::class, DbCharacter::class)
        .rootColumn("movie_id")
        .source(sourceId)
        .target(targetId)
        .execute()

    // Movie remained in the same shard
    assertMovieNamesInShard(sourceShard).containsExactly("Jurassic Park")
    assertMovieNamesInShard(targetShard).containsExactly("Star Wars")

    // Characters were moved
    assertCharacterNamesInShard(sourceShard).isEmpty()
    assertCharacterNamesInShard(targetShard).containsExactly("Ellie Sattler", "Ian Malcolm")

    assertRowCount(sourceId, "Ellie Sattler", "Ian Malcolm").isEqualTo(0)
    assertRowCount(targetId, "Ellie Sattler", "Ian Malcolm").isEqualTo(2)
  }

  @Test fun bulkMigrateDuringSchemaChange() {
    val sourceId = transacter.transaction { session ->
      val movie = DbMovie("Jurassic Park")
      session.save(movie)
      session.save(DbCharacter("Ellie Sattler", movie))
      session.save(DbCharacter("Ian Malcolm", movie))
      movie.id
    }

    val targetId = transacter.createInSeparateShard(sourceId) {
      DbMovie("Star Wars")
    }

    val sourceShard = transacter.transaction { sourceId.shard(it) }
    val targetShard = transacter.transaction { targetId.shard(it) }

    assertThat(sourceShard).isNotEqualTo(targetShard)

    assertMovieNamesInShard(sourceShard).containsExactly("Jurassic Park")
    assertMovieNamesInShard(targetShard).containsExactly("Star Wars")

    assertCharacterNamesInShard(sourceShard).containsExactly("Ellie Sattler", "Ian Malcolm")
    assertCharacterNamesInShard(targetShard).isEmpty()

    assertRowCount(sourceId, "Ellie Sattler", "Ian Malcolm").isEqualTo(2)
    assertRowCount(targetId, "Ellie Sattler", "Ian Malcolm").isEqualTo(0)

    try {
      // Change the schema on one the two shards. Previously this would cause BulkShardMigrator to
      // crash because it wasn't expecting shards to have mismatching columns.
      for (shard in listOf(sourceShard, targetShard)) {
        transacter.transaction(shard) { session ->
          executeStatement(session, "ALTER TABLE characters ADD COLUMN age tinyint NULL")
        }
      }

      bulkShardMigratorFactory.create(transacter, sessionFactory, DbMovie::class,
          DbCharacter::class)
          .rootColumn("movie_id")
          .source(sourceId)
          .target(targetId)
          .execute()

      // Movie remained in the same shard
      assertMovieNamesInShard(sourceShard).containsExactly("Jurassic Park")
      assertMovieNamesInShard(targetShard).containsExactly("Star Wars")

      // Characters were moved
      assertCharacterNamesInShard(sourceShard).isEmpty()
      assertCharacterNamesInShard(targetShard).containsExactly("Ellie Sattler", "Ian Malcolm")

      assertRowCount(sourceId, "Ellie Sattler", "Ian Malcolm").isEqualTo(0)
      assertRowCount(targetId, "Ellie Sattler", "Ian Malcolm").isEqualTo(2)
    } finally {
      // Remove the age column that was added. The drop assumes that age column was successfully
      // added which is fine for now.
      for (shard in listOf(sourceShard, targetShard)) {
        transacter.transaction(shard) { session ->
          executeStatement(session, "ALTER TABLE characters DROP COLUMN age")
        }
      }
    }
  }

  @Test fun batchingTest() {
    val sourceId = transacter.transaction { session ->
      val movie = DbMovie("Jurassic Park")
      session.save(movie)
      session.save(DbCharacter("Ellie Sattler", movie))
      session.save(DbCharacter("Ian Malcolm", movie))
      movie.id
    }

    val targetId = transacter.createInSeparateShard(sourceId) {
      DbMovie("Star Wars")
    }

    val sourceShard = transacter.transaction { sourceId.shard(it) }
    val targetShard = transacter.transaction { targetId.shard(it) }

    assertThat(sourceShard).isNotEqualTo(targetShard)

    assertMovieNamesInShard(sourceShard).containsExactly("Jurassic Park")
    assertMovieNamesInShard(targetShard).containsExactly("Star Wars")

    assertCharacterNamesInShard(sourceShard).containsExactly("Ellie Sattler", "Ian Malcolm")
    assertCharacterNamesInShard(targetShard).isEmpty()

    assertRowCount(sourceId, "Ellie Sattler", "Ian Malcolm").isEqualTo(2)
    assertRowCount(targetId, "Ellie Sattler", "Ian Malcolm").isEqualTo(0)

    // Change the schema on one the two shards. Previously this would cause BulkShardMigrator to
    // crash because it wasn't expecting shards to have mismatching columns.
    for (shard in listOf(sourceShard, targetShard)) {
      transacter.transaction(shard) { session ->
        executeStatement(session, "ALTER TABLE characters ADD COLUMN age tinyint NULL")
      }
    }

    // First batch is been moved
    bulkShardMigratorFactory.create(transacter, sessionFactory, DbMovie::class, DbCharacter::class)
        .rootColumn("movie_id")
        .source(sourceId)
        .target(targetId)
        .batched()
        .batchSize(1)
        .latestBatchOnly(true)
        .execute()

    // Movie remained in the same shard
    assertMovieNamesInShard(sourceShard).containsExactly("Jurassic Park")
    assertMovieNamesInShard(targetShard).containsExactly("Star Wars")

    // Only 1 characters were moved
    assertCharacterNamesInShard(sourceShard).containsExactly("Ellie Sattler")
    assertCharacterNamesInShard(targetShard).containsExactly("Ian Malcolm")

    assertRowCount(sourceId, "Ellie Sattler", "Ian Malcolm").isEqualTo(1)
    assertRowCount(targetId, "Ellie Sattler", "Ian Malcolm").isEqualTo(1)

    // Second batch is been moved
    bulkShardMigratorFactory.create(transacter, sessionFactory, DbMovie::class, DbCharacter::class)
        .rootColumn("movie_id")
        .source(sourceId)
        .target(targetId)
        .batched()
        .batchSize(1)
        .latestBatchOnly(true)
        .execute()

    // Movie remained in the same shard
    assertMovieNamesInShard(sourceShard).containsExactly("Jurassic Park")
    assertMovieNamesInShard(targetShard).containsExactly("Star Wars")

    // Only 1 characters were moved
    assertCharacterNamesInShard(sourceShard).isEmpty()
    assertCharacterNamesInShard(targetShard).containsExactly("Ellie Sattler", "Ian Malcolm")

    assertRowCount(sourceId, "Ellie Sattler", "Ian Malcolm").isEqualTo(0)
    assertRowCount(targetId, "Ellie Sattler", "Ian Malcolm").isEqualTo(2)
  }
}

@MiskTest(startService = true)
class BulkShardMigratorMySqlTest: BulkShardMigratorTest() {
  @MiskTestModule
  val module = MoviesTestModule(type = DataSourceType.MYSQL)
}

@MiskTest(startService = true)
class BulkShardMigratorTidbTest: BulkShardMigratorTest() {
  @MiskTestModule
  val module = MoviesTestModule(type = DataSourceType.TIDB)
}