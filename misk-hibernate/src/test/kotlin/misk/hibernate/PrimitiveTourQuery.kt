package misk.hibernate

interface PrimitiveTourQuery : Query<DbPrimitiveTour> {
  @Constraint(path = "i1")
  fun i1(i1: Boolean): PrimitiveTourQuery

  @Select
  fun listAsPrimitiveTour(session: Session): List<PrimitiveTour>
}

data class PrimitiveTour(
  @Property("i1") var i1: Boolean,
  @Property("i8") var i8: Byte,
  @Property("i16") var i16: Short,
  @Property("i32") var i32: Int,
  @Property("i64") var i64: Long,
  @Property("c16") var c16: Char,
  @Property("f32") var f32: Float,
  @Property("f64") var f64: Double
) : Projection
