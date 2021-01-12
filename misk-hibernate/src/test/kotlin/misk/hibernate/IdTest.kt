package misk.hibernate

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class IdTest {
  @Test
  fun compareToTest() {
    val id1 = Id<DbTest>(1234)
    val id2 = Id<DbTest>(1234)
    val id3 = Id<DbTest>(1235)

    assertThat(id1.compareTo(id2)).isEqualTo(0)
    assertThat(id1.compareTo(id3)).isLessThan(0)
    assertThat(id3.compareTo(id2)).isGreaterThan(0)
  }

  class DbTest(override val id: Id<DbTest>) : DbEntity<DbTest>
}
