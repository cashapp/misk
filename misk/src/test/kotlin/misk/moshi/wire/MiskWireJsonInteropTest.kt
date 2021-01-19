package misk.moshi.wire

import com.google.inject.Guice
import com.squareup.moshi.Moshi
import kotlin.reflect.KClass
import misk.moshi.MoshiModule
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import squareup.proto2.keywords.KeywordKotlin

/**
 * Migration phases:
 *
 * MISK_ONLY:
 *   Misk writes
 *   Misk reads
 *
 * WIRE_READS:
 *   Misk writes
 *   Wire reads
 *   (deploy everything on this)
 *
 * WIRE_ONLY:
 *   Wire writes
 *   Wire reads
 */
internal class MiskWireJsonInteropTest {
  private val wireOnlyMoshi: Moshi = Guice.createInjector(
    MoshiModule(useWireToRead = true, useWireToWrite = true)
  ).getInstance(Moshi::class.java)

  private val wireReadsMoshi: Moshi = Guice.createInjector(
    MoshiModule(useWireToRead = true, useWireToWrite = false)
  ).getInstance(Moshi::class.java)

  private val miskOnlyMoshi: Moshi = Guice.createInjector(
    MoshiModule(useWireToRead = false, useWireToWrite = false)
  ).getInstance(Moshi::class.java)

  @Test
  fun simpleTypes() {
    testAllPhases("hello", String::class)
  }

  @Test
  fun keywordAsFieldName() {
    val value = KeywordKotlin.Builder()
      .fun_(mapOf("a" to "apple"))
      .when_(3)
      .build()

    testAllPhases(value, KeywordKotlin::class)
  }

  @Test
  fun backwardsCompatibleOnUpgradeToWireWithKeywordThing() {
    val json = """{"fun":{"a":"apple"},"return":[],"enums":[],"when":3}"""
    val wireFromMisk = wireOnlyMoshi.adapter(KeywordKotlin::class.java).fromJson(json)
    assertThat(wireFromMisk).isEqualTo(
      KeywordKotlin.Builder()
        .fun_(mapOf("a" to "apple"))
        .when_(3)
        .build()
    )
  }

  private fun <T : Any> testAllPhases(message: T, type: KClass<T>) {
    phaseTransitioningToWireReads(message, type, miskOnlyMoshi, miskOnlyMoshi)
    phaseTransitioningToWireReads(message, type, miskOnlyMoshi, wireReadsMoshi)
    phaseTransitioningToWireReads(message, type, wireReadsMoshi, wireReadsMoshi)
    phaseTransitioningToWireReads(message, type, wireReadsMoshi, wireOnlyMoshi)
    phaseTransitioningToWireReads(message, type, wireOnlyMoshi, wireOnlyMoshi)
  }

  private fun <T : Any> phaseTransitioningToWireReads(
    message: T, type: KClass<T>, a: Moshi, b: Moshi
  ) {
    val aAdapter = a.adapter(type.java)
    val bAdapter = b.adapter(type.java)
    val aWritesJson = aAdapter.toJson(message)
    assertThat(bAdapter.fromJson(aWritesJson)).isEqualTo(message)
    val bWritesJson = bAdapter.toJson(message)
    println(bWritesJson)
    assertThat(aAdapter.fromJson(bWritesJson)).isEqualTo(message)
  }
}
