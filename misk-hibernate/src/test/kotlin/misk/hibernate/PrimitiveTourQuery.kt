package misk.hibernate

interface PrimitiveTourQuery : Query<DbPrimitiveTour> {
  @Constraint(path = "i1") fun i1(i1: Boolean): PrimitiveTourQuery

  @Select fun listAsPrimitiveTour(session: Session): List<PrimitiveTour>

  @Select(path = "i64", aggregation = AggregationType.AVG) fun averageI64(session: Session): Double?

  @Select(path = "i64", aggregation = AggregationType.COUNT) fun countI64(session: Session): Long?

  @Select(path = "i64", aggregation = AggregationType.COUNT_DISTINCT) fun countDistinctI64(session: Session): Long?

  @Select(path = "i64", aggregation = AggregationType.MAX) fun maxI64(session: Session): Long?

  @Select(path = "i64", aggregation = AggregationType.MIN) fun minI64(session: Session): Long?

  @Select(path = "i64", aggregation = AggregationType.SUM) fun sumI64(session: Session): Long?

  @Select fun averageAll(session: Session): AveragePrimitiveTour?

  @Select fun countAll(session: Session): CountPrimitiveTour?

  @Select fun countDistinctAll(session: Session): CountDistinctPrimitiveTour?

  @Select fun maxAll(session: Session): MaxPrimitiveTour?

  @Select fun minAll(session: Session): MinPrimitiveTour?

  @Select fun sumAll(session: Session): SumPrimitiveTour?

  @Group(paths = ["i1", "c16"]) fun groupByI1AndC16(): PrimitiveTourQuery

  @Select fun listI1C16AndMaxI8(session: Session): List<I1C16AndMaxI8>
}

data class PrimitiveTour(
  @Property("i1") var i1: Boolean,
  @Property("i8") var i8: Byte,
  @Property("i16") var i16: Short,
  @Property("i32") var i32: Int,
  @Property("i64") var i64: Long,
  @Property("c16") var c16: Char,
  @Property("f32") var f32: Float,
  @Property("f64") var f64: Double,
) : Projection

data class AveragePrimitiveTour(
  @Property("i8", aggregation = AggregationType.AVG) var i8: Double?,
  @Property("i16", aggregation = AggregationType.AVG) var i16: Double?,
  @Property("i32", aggregation = AggregationType.AVG) var i32: Double?,
  @Property("i64", aggregation = AggregationType.AVG) var i64: Double?,
  @Property("f32", aggregation = AggregationType.AVG) var f32: Double?,
  @Property("f64", aggregation = AggregationType.AVG) var f64: Double?,
) : Projection

data class CountPrimitiveTour(
  @Property("i1", aggregation = AggregationType.COUNT) var i1: Long?,
  @Property("i8", aggregation = AggregationType.COUNT) var i8: Long?,
  @Property("i16", aggregation = AggregationType.COUNT) var i16: Long?,
  @Property("i32", aggregation = AggregationType.COUNT) var i32: Long?,
  @Property("i64", aggregation = AggregationType.COUNT) var i64: Long?,
  @Property("c16", aggregation = AggregationType.COUNT) var c16: Long?,
  @Property("f32", aggregation = AggregationType.COUNT) var f32: Long?,
  @Property("f64", aggregation = AggregationType.COUNT) var f64: Long?,
) : Projection

data class CountDistinctPrimitiveTour(
  @Property("i1", aggregation = AggregationType.COUNT_DISTINCT) var i1: Long?,
  @Property("i8", aggregation = AggregationType.COUNT_DISTINCT) var i8: Long?,
  @Property("i16", aggregation = AggregationType.COUNT_DISTINCT) var i16: Long?,
  @Property("i32", aggregation = AggregationType.COUNT_DISTINCT) var i32: Long?,
  @Property("i64", aggregation = AggregationType.COUNT_DISTINCT) var i64: Long?,
  @Property("c16", aggregation = AggregationType.COUNT_DISTINCT) var c16: Long?,
  @Property("f32", aggregation = AggregationType.COUNT_DISTINCT) var f32: Long?,
  @Property("f64", aggregation = AggregationType.COUNT_DISTINCT) var f64: Long?,
) : Projection

data class MaxPrimitiveTour(
  @Property("i1") var i1: Boolean,
  @Property("i8", aggregation = AggregationType.MAX) var i8: Byte?,
  @Property("i16", aggregation = AggregationType.MAX) var i16: Short?,
  @Property("i32", aggregation = AggregationType.MAX) var i32: Int?,
  @Property("i64", aggregation = AggregationType.MAX) var i64: Long?,
  @Property("c16", aggregation = AggregationType.MAX) var c16: Char?,
  @Property("f32", aggregation = AggregationType.MAX) var f32: Float?,
  @Property("f64", aggregation = AggregationType.MAX) var f64: Double?,
) : Projection

data class MinPrimitiveTour(
  @Property("i1") var i1: Boolean,
  @Property("i8", aggregation = AggregationType.MIN) var i8: Byte?,
  @Property("i16", aggregation = AggregationType.MIN) var i16: Short?,
  @Property("i32", aggregation = AggregationType.MIN) var i32: Int?,
  @Property("i64", aggregation = AggregationType.MIN) var i64: Long?,
  @Property("c16", aggregation = AggregationType.MIN) var c16: Char?,
  @Property("f32", aggregation = AggregationType.MIN) var f32: Float?,
  @Property("f64", aggregation = AggregationType.MIN) var f64: Double?,
) : Projection

data class SumPrimitiveTour(
  @Property("i8", aggregation = AggregationType.SUM) var i8: Long?,
  @Property("i16", aggregation = AggregationType.SUM) var i16: Long?,
  @Property("i32", aggregation = AggregationType.SUM) var i32: Long?,
  @Property("i64", aggregation = AggregationType.SUM) var i64: Long?,
  @Property("f32", aggregation = AggregationType.SUM) var f32: Double?,
  @Property("f64", aggregation = AggregationType.SUM) var f64: Double?,
) : Projection

data class I1C16AndMaxI8(
  @Property("i1") var i1: Boolean,
  @Property("c16") var c16: Char,
  @Property("i8", aggregation = AggregationType.MAX) var i8: Byte?,
) : Projection
