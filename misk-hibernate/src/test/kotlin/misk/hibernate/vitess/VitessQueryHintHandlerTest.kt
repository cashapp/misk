package misk.hibernate.vitess

import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test

/**
 * Test suite that verifies Vitess query hints are correctly added to SQL queries.
 */
class VitessQueryHintHandlerTest {
  private val queryHintHandler: VitessQueryHintHandler = VitessQueryHintHandler()

  @Test
  fun `test addQueryHints with single hint`() {
    val query = "select * from customers;"
    val hints = "vt+ ALLOW_SCATTER"

    val result = queryHintHandler.addQueryHints(query, hints)
    Assertions.assertThat(result).isEqualTo("select /*vt+ ALLOW_SCATTER */ * from customers;")
  }

  @Test
  fun `test addQueryHints with multiple hints`() {
    val query = "select * from customers;"
    val hints = "vt+ ALLOW_SCATTER,vt+ QUERY_TIMEOUT_MS=1"

    val result = queryHintHandler.addQueryHints(query, hints)
    Assertions.assertThat(result).isEqualTo("select /*vt+ ALLOW_SCATTER QUERY_TIMEOUT_MS=1 */ * from customers;")
  }

  @Test
  fun `test addQueryHints with complex hints and non-Vitess hint`() {
    val query = "select * from customers;"
    val hints = "vt+ RANGE_OPT=[a:b],vt+ ANOTHER,vt+ ANOTHER_WITH_VAL=val,vt+ AND_ONE_WITH_EQ==,unq_token_idx"

    val result = queryHintHandler.addQueryHints(query, hints)
    Assertions.assertThat(result).isEqualTo("select /*vt+ RANGE_OPT=[a:b] ANOTHER ANOTHER_WITH_VAL=val AND_ONE_WITH_EQ== */ * from customers;")
  }

  @Test
  fun `test addQueryHints with no Vitess hints`() {
    val query = "select * from customers;"
    val hints = "unq_token_idx"

    val result = queryHintHandler.addQueryHints(query, hints)
    Assertions.assertThat(result).isEqualTo(query)
  }

  @Test
  fun `test addQueryHints with empty hints`() {
    val query = "select * from customers;"
    val hints = ""

    val result = queryHintHandler.addQueryHints(query, hints)
    Assertions.assertThat(result).isEqualTo(query)
  }
}
