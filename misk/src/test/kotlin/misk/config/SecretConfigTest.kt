package misk.config

import com.google.inject.util.Modules
import misk.environment.Environment
import misk.environment.EnvironmentModule
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import javax.inject.Inject
import javax.inject.Named

@MiskTest
class SecretConfigTest {
  val environment = Environment.TESTING
  val config = MiskConfig.load<SuperSecretConfig>("secret_config_app", environment)

  @MiskTestModule
  val module = Modules.combine(
      ConfigModule.create("secret_app", config),
      EnvironmentModule(environment))

  @Inject
  private lateinit var secretConfig: SuperSecretConfig

  @Inject
  lateinit var secretInformationConfig: SecretInformationConfig

  @field:[Inject Named("consumer_a")]
  private lateinit var consumerA: SecretInformationConfig

  @field:[Inject Named("consumer_b")]
  private lateinit var consumerB: SecretInformationConfig

  @Test
  fun topLevelSecretsLoaded() {
    // Basic secrets.
    assertThat(secretConfig.secret_information.value.answer_to_universe).isEqualTo("42")
    assertThat(secretConfig.secret_information.value.limit).isEqualTo(5)

    // Secrets of basic types.
    assertThat(secretConfig.secret_api_key.value).isEqualTo("the api key is 42")
    assertThat(secretConfig.secret_number.value).isEqualTo(73)

    // Secret of a list type.
    assertThat(secretConfig.secrets_list.value).containsAll(
        listOf(SecretInformationConfig("42", 5), SecretInformationConfig("43", 6)))

    // Secret with nested generic types.
    assertThat(secretConfig.secrets_list_map.value).containsAll(
        listOf(
            mapOf("answer_to_universe" to 42, "limit" to 5),
            mapOf("answer_to_universe" to 43, "limit" to 6)))

    // Nested Secrets.
    assertThat(
        secretConfig.nested_secret.value.nested_nested.secret_information.value.answer_to_universe).isEqualTo(
        "42")
    assertThat(
        secretConfig.nested_secret.value.nested_nested.secret_information.value.limit).isEqualTo(5)
  }

  @Test
  fun secretsLoadedRecursively() {
    assertThat(secretConfig.secret_information_wrapper.secret_information.value.answer_to_universe)
        .isEqualTo("nothing")
    assertThat(
        secretConfig.secret_information_wrapper.secret_information.value.limit).isEqualTo(73)
  }

  @Test
  fun injectSecretValue() {
    assertThat(secretInformationConfig.answer_to_universe).isEqualTo("42")
    assertThat(secretInformationConfig.limit).isEqualTo(5)
  }

  @Test
  fun subSecretConfigsWithCustomNamesAreBoundWithNamedQualifiers() {
    assertThat(consumerA.answer_to_universe).isEqualTo("this is consumer A")
    assertThat(consumerB.answer_to_universe).isEqualTo("this is consumer B")
  }

  @Test
  fun throwsIfServiceConfigIsSecret() {
    val expectedError = "Top level service config cannot be a Secret<*>"
    val selfSecretMessage = assertThrows<IllegalStateException> {
      MiskConfig.load<SelfSecretConfigRoot>("nested_secrets_test", environment)
    }
    assertThat(selfSecretMessage).hasMessageContaining(expectedError)

    val secretMessage = assertThrows<IllegalStateException> {
      MiskConfig.load<SecretConfigRoot>("nested_secrets_test", environment)
    }
    assertThat(secretMessage).hasMessageContaining(expectedError)
  }

  @Test
  fun throwOnUnknownExtension() {
    val unknownExtensionError = assertThrows<IllegalStateException> {
      MiskConfig.load<SecretInformationWrapperConfig>("secret_config_unknown_extension",
          environment)
    }
    assertThat(unknownExtensionError).hasMessageContaining(
        "Unknown file extension \"json\" for secret [classpath:/misk/resources/secrets/secret_information_values.json].")
  }

  @Test
  fun throwOnStringTxtMismatch() {
    val stringTxtMismatchError = assertThrows<IllegalStateException> {
      MiskConfig.load<SecretInformationWrapperConfig>("secret_config_badtxt_extension", environment)
    }
    assertThat(stringTxtMismatchError).hasMessageContaining(
        "Secrets with the .txt extension map to Secret<String> fields in Config classes.")
  }

  @Test
  fun throwOnStringMissingExtension() {
    val stringTxtMismatchError = assertThrows<IllegalStateException> {
      MiskConfig.load<SecretInformationWrapperConfig>("secret_config_missing_extension",
          environment)
    }
    assertThat(stringTxtMismatchError).hasMessageContaining(
        "Secret [classpath:/misk/resources/secrets/secret_information_values] needs a file extension for parsing.")
  }

  @Test
  fun throwMissingFile() {
    val stringTxtMismatchError = assertThrows<IllegalStateException> {
      MiskConfig.load<SecretInformationWrapperConfig>("secret_config_missing_file", environment)
    }
    assertThat(stringTxtMismatchError).hasMessageContaining(
        "No secret found at: classpath:/file_does_not_exist.")
  }
}


