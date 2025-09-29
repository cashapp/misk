package misk.hibernate.vitess

import jakarta.inject.Inject
import misk.hibernate.DbMovie
import misk.hibernate.Id
import misk.hibernate.MovieQuery
import misk.hibernate.Movies
import misk.hibernate.MoviesTestModule
import misk.hibernate.Query
import misk.hibernate.Transacter
import misk.hibernate.VitessTestExtensions.createInSeparateShard
import misk.hibernate.VitessTestExtensions.save
import misk.hibernate.VitessTestExtensions.shard
import misk.hibernate.allowTableScan
import misk.hibernate.failSafeRead
import misk.hibernate.newQuery
import misk.jdbc.DataSourceType
import misk.testing.MiskExternalDependency
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.vitess.Destination
import misk.vitess.TabletType
import misk.vitess.testing.utilities.DockerVitess
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDate

@MiskTest(startService = true)
class VitessShardTargetIntegrationTest {
  @MiskExternalDependency
  private val dockerVitess = DockerVitess()

  @MiskTestModule
  val module = MoviesTestModule(
    type = DataSourceType.VITESS_MYSQL, 
    allowScatters = true
  )

  @Inject @Movies lateinit var transacter: Transacter
  @Inject lateinit var queryFactory: Query.Factory

  lateinit var jp: Id<DbMovie>
  lateinit var sw: Id<DbMovie>

  @BeforeEach
  fun setup() {
    jp = transacter.save(
      DbMovie("Jurassic Park", LocalDate.of(1993, 6, 9))
    )
    sw = transacter.createInSeparateShard(jp) {
      DbMovie("Star Wars", LocalDate.of(1977, 5, 25))
    }
  }

  @Test
  fun `target(shard) works in replica reads`() {
    transacter.replicaRead { session ->
      session.target(jp.shard(session)) {
        assertThat(
          queryFactory.newQuery<MovieQuery>().allowTableScan()
            .name("Jurassic Park").uniqueResult(session)
        ).isNotNull

        assertThat(
          queryFactory.newQuery<MovieQuery>().allowTableScan()
            .name("Star Wars").uniqueResult(session)
        ).isNull()
      }

      session.target(sw.shard(session)) {
        assertThat(
          queryFactory.newQuery<MovieQuery>().allowTableScan()
            .name("Jurassic Park").uniqueResult(session)
        ).isNull()

        assertThat(
          queryFactory.newQuery<MovieQuery>().allowTableScan()
            .name("Star Wars").uniqueResult(session)
        ).isNotNull
      }
    }
  }

  @Test
  fun `target(destination) works in replica reads`() {
    transacter.replicaRead { session ->
      session.target(Destination(shard = jp.shard(session), tabletType = TabletType.REPLICA)) {
        assertThat(
          queryFactory.newQuery<MovieQuery>().allowTableScan()
            .name("Jurassic Park").uniqueResult(session)
        ).isNotNull

        assertThat(
          queryFactory.newQuery<MovieQuery>().allowTableScan()
            .name("Star Wars").uniqueResult(session)
        ).isNull()
      }

      session.target(Destination(shard = sw.shard(session), tabletType = TabletType.REPLICA)) {
        assertThat(
          queryFactory.newQuery<MovieQuery>().allowTableScan()
            .name("Jurassic Park").uniqueResult(session)
        ).isNull()

        assertThat(
          queryFactory.newQuery<MovieQuery>().allowTableScan()
            .name("Star Wars").uniqueResult(session)
        ).isNotNull
      }
    }
  }

  @Test
  fun `target(shard) works in fail safe reads`() {
    transacter.failSafeRead { session ->
      session.target(jp.shard(session)) {
        assertThat(
          queryFactory.newQuery<MovieQuery>().allowTableScan()
            .name("Jurassic Park").uniqueResult(session)
        ).isNotNull

        assertThat(
          queryFactory.newQuery<MovieQuery>().allowTableScan()
            .name("Star Wars").uniqueResult(session)
        ).isNull()
      }

      session.target(sw.shard(session)) {
        assertThat(
          queryFactory.newQuery<MovieQuery>().allowTableScan()
            .name("Jurassic Park").uniqueResult(session)
        ).isNull()

        assertThat(
          queryFactory.newQuery<MovieQuery>().allowTableScan()
            .name("Star Wars").uniqueResult(session)
        ).isNotNull
      }
    }
  }

  @Test
  fun `target(destination) works in fail safe reads`() {
    transacter.failSafeRead { session ->
      session.target(Destination(shard = jp.shard(session), tabletType = TabletType.PRIMARY)) {
        assertThat(
          queryFactory.newQuery<MovieQuery>().allowTableScan()
            .name("Jurassic Park").uniqueResult(session)
        ).isNotNull

        assertThat(
          queryFactory.newQuery<MovieQuery>().allowTableScan()
            .name("Star Wars").uniqueResult(session)
        ).isNull()
      }

      session.target(Destination(shard = sw.shard(session), tabletType = TabletType.PRIMARY)) {
        assertThat(
          queryFactory.newQuery<MovieQuery>().allowTableScan()
            .name("Jurassic Park").uniqueResult(session)
        ).isNull()

        assertThat(
          queryFactory.newQuery<MovieQuery>().allowTableScan()
            .name("Star Wars").uniqueResult(session)
        ).isNotNull
      }
    }
  }

  @Test
  fun `target(shard) works in transactions`() {
    transacter.transaction { session ->
      session.target(jp.shard(session)) {
        assertThat(
          queryFactory.newQuery<MovieQuery>().allowTableScan()
            .name("Jurassic Park").uniqueResult(session)
        ).isNotNull

        assertThat(
          queryFactory.newQuery<MovieQuery>().allowTableScan()
            .name("Star Wars").uniqueResult(session)
        ).isNull()
      }

      session.target(sw.shard(session)) {
        assertThat(
          queryFactory.newQuery<MovieQuery>().allowTableScan()
            .name("Jurassic Park").uniqueResult(session)
        ).isNull()

        assertThat(
          queryFactory.newQuery<MovieQuery>().allowTableScan()
            .name("Star Wars").uniqueResult(session)
        ).isNotNull
      }
    }
  }

  @Test
  fun `target(destination) works in transactions`() {
    transacter.transaction { session ->
      session.target(Destination(shard = jp.shard(session), tabletType = TabletType.PRIMARY)) {
        assertThat(
          queryFactory.newQuery<MovieQuery>().allowTableScan()
            .name("Jurassic Park").uniqueResult(session)
        ).isNotNull

        assertThat(
          queryFactory.newQuery<MovieQuery>().allowTableScan()
            .name("Star Wars").uniqueResult(session)
        ).isNull()
      }

      session.target(Destination(shard = sw.shard(session), tabletType = TabletType.PRIMARY)) {
        assertThat(
          queryFactory.newQuery<MovieQuery>().allowTableScan()
            .name("Jurassic Park").uniqueResult(session)
        ).isNull()

        assertThat(
          queryFactory.newQuery<MovieQuery>().allowTableScan()
            .name("Star Wars").uniqueResult(session)
        ).isNotNull
      }
    }
  }
}
