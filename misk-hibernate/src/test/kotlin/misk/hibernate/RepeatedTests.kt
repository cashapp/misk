package misk.hibernate

import misk.inject.KAbstractModule
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream
import javax.inject.Inject

@MiskTest(startService = true)
class RepeatedTests {
  @MiskTestModule
  val module = object : KAbstractModule() {
    override fun configure() {
      install(MoviesTestModule())
    }
  }

  companion object {
    @JvmStatic
    fun `runs hibernate`(): Stream<Arguments> = IntRange(0, 500)
      .map { Arguments.of(it) }
      .stream()
  }

  @Inject @Movies lateinit var transacter: Transacter

  @ParameterizedTest
  @MethodSource
  fun `runs hibernate`(x: Int) {
    transacter.transaction { session ->
      session.save(DbMovie(name = "movie"))
    }
  }
}
