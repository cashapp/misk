package misk.hibernate

import jakarta.inject.Inject
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@MiskTest(startService = true)
class PrimitiveColumnsTest {
  @MiskTestModule val module = PrimitivesDbTestModule()

  @Inject @PrimitivesDb lateinit var transacter: Transacter
  @Inject lateinit var queryFactory: Query.Factory

  @Test
  fun happyPath() {
    transacter.transaction { session ->
      session.save(DbPrimitiveTour(false, 9, 8, 7, 6, '5', 4.0f, 3.0))
      session.save(DbPrimitiveTour(true, 2, 3, 4, 5, '6', 7.0f, 8.0))
    }
    transacter.transaction { session ->
      val primitiveTour =
        queryFactory.newQuery(PrimitiveTourQuery::class).allowTableScan().i1(true).listAsPrimitiveTour(session)
      assertThat(primitiveTour).containsExactly(PrimitiveTour(true, 2, 3, 4, 5, '6', 7.0f, 8.0))
    }
  }
}
