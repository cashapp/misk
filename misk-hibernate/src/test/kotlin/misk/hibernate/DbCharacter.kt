package misk.hibernate

import org.hibernate.annotations.GenericGenerator
import org.hibernate.annotations.Parameter
import java.time.Instant
import java.time.LocalDate
import javax.persistence.AttributeOverride
import javax.persistence.Column
import javax.persistence.EmbeddedId
import javax.persistence.Entity
import javax.persistence.FetchType
import javax.persistence.GeneratedValue
import javax.persistence.JoinColumn
import javax.persistence.ManyToOne
import javax.persistence.Table

@Entity
@Table(name = "characters")
class DbCharacter() : DbChild<DbMovie, DbCharacter>, DbTimestampedEntity {
  @EmbeddedId
  @AttributeOverride(name = "rootId", column = Column(name = "movie_id"))
  @GeneratedValue(generator = "child")
  @GenericGenerator(
    name = "child", strategy = "misk.hibernate.GidGenerator",
    parameters = [Parameter(name = "rootColumn", value = "movie_id")]
  )
  override lateinit var gid: Gid<DbMovie, DbCharacter>

  @Column(updatable = false, insertable = false)
  override lateinit var id: Id<DbCharacter>

  override val rootId: Id<DbMovie>
    get() = movie_id

  @Column
  override lateinit var updated_at: Instant

  @Column
  override lateinit var created_at: Instant

  @Column(nullable = false)
  lateinit var name: String

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "movie_id", updatable = false, insertable = false)
  lateinit var movie: DbMovie

  @Column(updatable = false, insertable = false)
  lateinit var movie_id: Id<DbMovie>

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "actor_id", updatable = false, insertable = false)
  var actor: DbActor? = null

  @Column
  var actor_id: Id<DbActor>? = null

  constructor(name: String, movie: DbMovie, actor: DbActor? = null) : this() {
    this.name = name
    this.movie = movie
    this.movie_id = movie.id
    this.actor = actor
    this.actor_id = actor?.id
  }
}

interface CharacterQuery : Query<DbCharacter> {
  @Constraint("name")
  fun name(name: String): CharacterQuery

  @Constraint("name", operator = Operator.LT)
  fun nameLessThan(name: String): CharacterQuery

  @Constraint("id", operator = Operator.GT)
  fun idMoreThan(id: Id<DbCharacter>): CharacterQuery

  @Constraint("actor.name")
  fun actorName(name: String): CharacterQuery

  @Constraint(path = "name", operator = Operator.IN)
  fun names(name: List<String>): CharacterQuery

  @Constraint(path = "movie_id", operator = Operator.EQ)
  fun movieId(id: Id<DbMovie>): CharacterQuery

  @Constraint(path = "movie.release_date", operator = Operator.LT)
  fun movieReleaseDateBefore(upperBound: LocalDate): CharacterQuery

  @Select("movie")
  fun listAsMovieNameAndReleaseDate(session: Session): List<NameAndReleaseDate>

  @Select
  fun listAsActorAndReleaseDate(session: Session): List<ActorAndReleaseDate>

  @Order(path = "id", asc = true)
  fun idAsc(): CharacterQuery

  @Order(path = "id", asc = false)
  fun idDesc(): CharacterQuery

  @Order(path = "name", asc = false)
  fun nameDesc(): CharacterQuery
}

data class NameAndReleaseDate(
  @Property("name") var name: String,
  @Property("release_date") var releaseDate: LocalDate?
) : Projection

data class ActorAndReleaseDate(
  @Property("actor.name") var actorName: String,
  @Property("movie.release_date") var movieReleaseDate: LocalDate?
) : Projection
