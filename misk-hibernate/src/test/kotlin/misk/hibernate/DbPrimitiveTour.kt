package misk.hibernate

import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.GeneratedValue
import javax.persistence.Table

@Entity
@Table(name = "primitive_tours")
class DbPrimitiveTour(
  @Column(nullable = false) var i1: Boolean = false,
  @Column(nullable = false) var i8: Byte = 0,
  @Column(nullable = false) var i16: Short = 0,
  @Column(nullable = false) var i32: Int = 0,
  @Column(nullable = false) var i64: Long = 0,
  @Column(nullable = false) var c16: Char = '\u0000',
  @Column(nullable = false) var f32: Float = 0.0f,
  @Column(nullable = false) var f64: Double = 0.0,
  @Column(nullable = true) var maybe_i1: Boolean? = null,
) : DbUnsharded<DbPrimitiveTour> {
  @javax.persistence.Id @GeneratedValue override lateinit var id: Id<DbPrimitiveTour>
}
