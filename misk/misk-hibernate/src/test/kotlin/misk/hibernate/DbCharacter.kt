package misk.hibernate

import java.time.Instant
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.FetchType
import javax.persistence.GeneratedValue
import javax.persistence.JoinColumn
import javax.persistence.ManyToOne
import javax.persistence.Table

@Entity
@Table(name = "characters")
class DbCharacter() : DbEntity<DbCharacter>, DbTimestampedEntity {
  @javax.persistence.Id
  @GeneratedValue
  override lateinit var id: Id<DbCharacter>

  @Column
  override lateinit var updated_at: Instant

  @Column
  override lateinit var created_at: Instant

  @Column(nullable = false)
  lateinit var name: String

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "movie_id", updatable = false, insertable = false)
  lateinit var movie: DbMovie

  @Column
  lateinit var movie_id: Id<DbMovie>

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "actor_id", updatable = false, insertable = false)
  var actor: DbActor? = null

  @Column
  var actor_id: Id<DbActor>? = null

  constructor(name: String, movie: DbMovie, actor: DbActor?) : this() {
    this.name = name
    this.movie = movie
    this.movie_id = movie.id
    this.actor = actor
    this.actor_id = actor?.id
  }
}