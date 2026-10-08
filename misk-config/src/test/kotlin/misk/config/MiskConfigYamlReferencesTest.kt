package misk.config

import misk.resources.MemoryResourceLoaderBackend
import misk.resources.ResourceLoader
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import tools.jackson.databind.JsonNode
import tools.jackson.dataformat.yaml.YAMLMapper
import wisp.deployment.TESTING

class MiskConfigYamlReferencesTest {
  data class YamlConfig(val values: Map<String, Any?>) : Config

  @Test
  fun aliasesAndMergeKeys() {
    val config =
      load(
        """
      values:
        scalar: &scalar hello
        scalar_copy: *scalar
        list: &list [one, two]
        list_copy: *list
        defaults: &defaults {timeout: 30, retries: 3}
        copy: *defaults
        client:
          timeout: 60
          <<: *defaults
      """
      )
    assertThat(config.values["scalar_copy"]).isEqualTo("hello")
    assertThat(config.values["list_copy"]).isEqualTo(listOf("one", "two"))
    assertThat(config.values["copy"]).isEqualTo(mapOf("timeout" to 30, "retries" to 3))
    assertThat(config.values["client"]).isEqualTo(mapOf("timeout" to 60, "retries" to 3))
  }

  @Test
  fun mergeIsShallowAndEarlierSourcesWin() {
    val config =
      load(
        """
      values:
        first: &first {timeout: 30, nested: {a: 1, b: 2}}
        second: &second {timeout: 40, retries: 3}
        client:
          <<: [*first, *second]
          nested: {a: 9}
        inherited:
          <<: {<<: *first, retries: 5}
      """
      )
    assertThat(config.values["client"]).isEqualTo(mapOf("timeout" to 30, "retries" to 3, "nested" to mapOf("a" to 9)))
    assertThat(config.values["inherited"])
      .isEqualTo(mapOf("timeout" to 30, "retries" to 5, "nested" to mapOf("a" to 1, "b" to 2)))
  }

  @Test
  fun laterFilesOverrideResolvedAliasesWithoutChangingTheirSource() {
    val config =
      load(
        """
      values:
        defaults: &defaults {timeout: 30, retries: 3}
        client: *defaults
      """,
        """
      values:
        client: {timeout: 60}
      """,
      )
    assertThat(config.values["defaults"]).isEqualTo(mapOf("timeout" to 30, "retries" to 3))
    assertThat(config.values["client"]).isEqualTo(mapOf("timeout" to 60, "retries" to 3))
  }

  @Test
  fun anchorsCannotCrossFiles() {
    assertThatThrownBy { load("values: {first: &first hello}", "values: {second: *first}") }
      .hasMessageContaining("could not parse classpath:/yaml-testing.yaml")
      .hasMessageContaining("first")
  }

  @Test
  fun anchorNamesCanBeReusedInDifferentDocuments() {
    val config =
      load(
        "values: {first: &value common, first_copy: *value}",
        "values: {second: &value environment, second_copy: *value}",
      )
    assertThat(config.values["first_copy"]).isEqualTo("common")
    assertThat(config.values["second_copy"]).isEqualTo("environment")
  }

  @Test
  fun aliasesUseTheMostRecentPrecedingAnchor() {
    val config =
      load(
        """
        values:
          first: &anchor Foo
          first_copy: *anchor
          second: &anchor Bar
          second_copy: *anchor
        """
      )
    assertThat(config.values["first_copy"]).isEqualTo("Foo")
    assertThat(config.values["second_copy"]).isEqualTo("Bar")
  }

  @Test
  fun fileOverridesKeepJacksonListAndNullSemantics() {
    val common = "values: {list: [one, two], nested: {a: 1, b: 2}, cleared: present}"
    val environment = "values: {list: [three], nested: {a: 9}, cleared: null}"
    val mapper = YAMLMapper.builder().build()
    val expected = mapper.readerForUpdating(mapper.readTree(common)).readValue<JsonNode>(environment)
    assertThat(mapper.valueToTree<JsonNode>(load(common, environment))).isEqualTo(expected)
  }

  @Test
  fun invalidMergesAndCyclesFailWithTheResourceName() {
    for (yaml in
      listOf(
        "values: {client: {<<: 42}}",
        "values: {client: {<<: [{a: 1}, 42]}}",
        "values: &cycle {self: *cycle}",
        "values: &cycle {<<: *cycle}",
        "values: &cycle {!!merge '<<': *cycle}",
        "values: {unknown: *missing}",
      )) {
      assertThatThrownBy { load(yaml) }.hasMessageContaining("could not parse classpath:/yaml-common.yaml")
    }
  }

  @Test
  fun quotedAndExplicitStringMergeKeysRemainLiteral() {
    val config =
      load(
        """
      values:
        quoted: {"<<": literal}
        tagged: {!!str <<: literal}
    """
      )
    assertThat(config.values["quoted"]).isEqualTo(mapOf("<<" to "literal"))
    assertThat(config.values["tagged"]).isEqualTo(mapOf("<<" to "literal"))
  }

  @Test
  fun scalarTypesAndStylesMatchJackson() {
    val yaml =
      """
      values:
        boolean: true
        legacy_boolean: yes
        legacy_number: 5_000
        leading_zero: 0123
        date: 2026-01-02
        quoted: "42"
        tagged: !!str 42
        empty: ""
        null_value: null
        absent_value:
        decimal: 1.25
        exponent: 1e3
        literal: |
          first
          second
        folded: >-
          first
          second
        resource: '${'$'}{environment:PORT:-8080}'
      """
        .trimIndent()
    val mapper = YAMLMapper.builder().build()
    assertThat(mapper.readTree(YamlReferences.resolve(yaml))).isEqualTo(mapper.readTree(yaml))
  }

  @Test
  fun expansionIsBounded() {
    val yaml = buildString {
      appendLine("values:")
      appendLine("  a0: &a0 [leaf]")
      for (i in 1..18) appendLine("  a$i: &a$i [*a${i - 1}, *a${i - 1}]")
    }
    assertThatThrownBy { load(yaml) }.hasMessageContaining("Expanded YAML exceeds")
  }

  @Test
  fun nestingAndScalarExpansionAreBounded() {
    assertThatThrownBy { load("values: {deep: " + "[".repeat(110) + "leaf" + "]".repeat(110) + "}") }
      .hasMessageContaining("nesting exceeds")
    val yaml = buildString {
      appendLine("values:")
      appendLine("  text: &text " + "x".repeat(1_000_000))
      for (i in 1..10) appendLine("  copy$i: *text")
    }
    assertThatThrownBy { load(yaml) }.hasMessageContaining("characters")
  }

  @Test
  fun aliasesPreserveSecretResolutionAndRedaction() {
    val loader = ResourceLoader(mapOf("classpath:" to MemoryResourceLoaderBackend()))
    loader.put("classpath:/secrets-common.yaml", "first: &secret classpath:/token.txt\nsecond: *secret")
    loader.put("classpath:/token.txt", "sensitive-token")
    val config = MiskConfig.load<SecretConfig>("secrets", TESTING, resourceLoader = loader)
    assertThat(config.first.value).isEqualTo("sensitive-token")
    assertThat(config.second.value).isEqualTo("sensitive-token")
    assertThat(MiskConfig.toRedactedYaml(config, loader)).doesNotContain("sensitive-token")
  }

  data class SecretConfig(val first: Secret<String>, val second: Secret<String>) : Config

  private fun load(common: String, environment: String? = null): YamlConfig {
    val loader = ResourceLoader(mapOf("classpath:" to MemoryResourceLoaderBackend()))
    loader.put("classpath:/yaml-common.yaml", common.trimIndent())
    environment?.let { loader.put("classpath:/yaml-testing.yaml", it.trimIndent()) }
    return MiskConfig.load<YamlConfig>("yaml", TESTING, resourceLoader = loader)
  }
}
