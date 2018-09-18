package misk.hibernate

import org.hibernate.annotations.GenericGenerator
import org.hibernate.annotations.Parameter
import java.time.Instant
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
  @GenericGenerator(name = "child", strategy = "misk.hibernate.CidGenerator",
      parameters = [Parameter(name = "rootColumn", value = "movie_id")])
  override lateinit var cid: Cid<DbMovie, DbCharacter>

  override val id: Id<DbCharacter>
    get() = cid.id

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

  constructor(name: String, movie: DbMovie, actor: DbActor?) : this() {
    this.name = name
    this.movie = movie
    this.movie_id = movie.id
    this.actor = actor
    this.actor_id = actor?.id
  }
}