package misk.web.metadata.all

import jakarta.inject.Inject
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.web.metadata.MetadataTestingModule
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

@MiskTest(startService = true)
class AllMetadataAcionTest {
  @MiskTestModule
  val module = MetadataTestingModule()

  @Inject lateinit var action: AllMetadataAction

  @Test
  fun `happy path`() {
    val actual = action.getAll()
    val actualIds = actual.all.map { it.id }
    assertEquals(listOf(), actualIds)
  }
}
