package misk.logging

import jakarta.inject.Inject
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.delay
import kotlinx.coroutines.slf4j.MDCContext
import kotlinx.coroutines.test.runTest
import misk.MiskTestingServiceModule
import misk.logging.coroutines.withMdc as withMdcCoroutines
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import org.junit.jupiter.api.Test

@MiskTest(startService = false)
internal class ScopedMdcKtTest {
  @MiskTestModule val module = MiskTestingServiceModule()

  @Inject lateinit var mdc: Mdc

  @Test
  fun `test withMdc in a coroutine for key value pairs`() =
    runTest(MDCContext()) {
      val tags = (1..3).map { "key$it" to "value$it" }.toTypedArray()
      mdc.withMdcCoroutines(*tags) {
        tags.assertTags()
        delay(100)
        tags.assertTags()
      }
      tags.asserMissingTags()
    }

  @Test
  fun `test withMdc for key value pairs`() {
    val tags = (1..3).map { "key$it" to "value$it" }.toTypedArray()
    mdc.withMdc(*tags) { tags.assertTags() }
    tags.asserMissingTags()
  }

  @Test
  fun `test withMdc in a coroutine for key value pair overrides`() =
    runTest(MDCContext()) {
      val tags = (1..3).map { "key$it" to "value$it" }.toTypedArray()
      mdc.withMdcCoroutines(*tags) {
        tags.assertTags()
        delay(100)
        tags.assertTags()
        val updatedTags =
          tags
            .map {
              if (it.first == "key1") {
                it.first to it.second + "00"
              } else {
                it
              }
            }
            .toTypedArray()
        mdc.withMdcCoroutines(*updatedTags) {
          updatedTags.assertTags()
          delay(100)
          updatedTags.assertTags()
        }
        tags.forEach { it.asserTag() }
      }
      tags.asserMissingTags()
    }

  @Test
  fun `test withMdc for key value pair overrides`() {
    val tags = (1..3).map { "key$it" to "value$it" }.toTypedArray()
    mdc.withMdc(*tags) {
      tags.assertTags()
      val updatedTags =
        tags
          .map {
            if (it.first == "key1") {
              it.first to it.second + "00"
            } else {
              it
            }
          }
          .toTypedArray()
      mdc.withMdc(*updatedTags) { updatedTags.assertTags() }
      tags.forEach { it.asserTag() }
    }
    tags.asserMissingTags()
  }

  fun Pair<String, String>.asserTag() = assertEquals(second, mdc.get(first))

  fun Array<Pair<String, String>>.assertTags() = forEach { it.asserTag() }

  fun Pair<String, String>.asserMissingTag() = assertNull(mdc.get(first))

  fun Array<Pair<String, String>>.asserMissingTags() = forEach { it.asserMissingTag() }
}
