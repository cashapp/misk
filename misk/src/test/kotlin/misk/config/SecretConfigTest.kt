//package misk.config
//
//import com.google.inject.util.Modules
//import misk.resources.FakeFilesModule
//import misk.resources.ResourceLoader
//import misk.resources.TestingResourceLoaderModule
//import misk.testing.MiskTest
//import misk.testing.MiskTestModule
//import org.assertj.core.api.Assertions.assertThat
//import org.junit.jupiter.api.BeforeEach
//import org.junit.jupiter.api.Test
//import org.junit.jupiter.api.assertThrows
//import wisp.config.ConfigSource
//import wisp.config.WispConfig
//import wisp.config.addWispConfigSources
//import wisp.deployment.TESTING
//import javax.inject.Inject
//
//@MiskTest
//class SecretConfigTest {
//
//  @MiskTestModule
//  private val module = Modules.combine(
//    TestingResourceLoaderModule(),
//    FakeFilesModule(
//      mapOf(
//        "/misk/resources/secrets/secret_information_values.yaml"
//          to """
//            |answer_to_universe: 42
//            |limit: 5
//          """.trimMargin()
//      )
//    )
//  )
//
//  private lateinit var secretConfig: SuperSecretConfig
//
//  @Inject
//  private lateinit var resourceLoader: ResourceLoader
//
//  @BeforeEach
//  fun setConfig() {
//    secretConfig =  WispConfig.builder().addWispConfigSources(
//      listOf(
//        ConfigSource("classpath:/secret_config_app.yaml"),
//      )
//    ).build().loadConfigOrThrow()
//  }
//
//  @Test
//  fun topLevelSecretsLoaded() {
//    // Basic secrets.
//    assertThat(secretConfig.secret_information.value.answer_to_universe).isEqualTo("42")
//    assertThat(secretConfig.secret_information.value.limit).isEqualTo(5)
//
//    // Secrets of basic types.
//    assertThat(secretConfig.secret_api_key.value).isEqualTo("the api key is 42")
//    assertThat(secretConfig.secret_number.value).isEqualTo(73)
//
//    // Secret of a list type.
//    assertThat(secretConfig.secrets_list.value).containsAll(
//      listOf(
//        SecretInformationConfig("42", 5),
//        SecretInformationConfig("43", 6)
//      )
//    )
//
//    // Secret with nested generic types.
//    assertThat(secretConfig.secrets_list_map.value).containsAll(
//      listOf(
//        mapOf("answer_to_universe" to 42, "limit" to 5),
//        mapOf("answer_to_universe" to 43, "limit" to 6)
//      )
//    )
//
//    // Nested Secrets.
//    assertThat(
//      secretConfig.nested_secret.value.nested_nested.secret_information.value.answer_to_universe
//    ).isEqualTo(
//      "42"
//    )
//    assertThat(
//      secretConfig.nested_secret.value.nested_nested.secret_information.value.limit
//    ).isEqualTo(5)
//
//    // A non-supported extension should work if the secret is a String
//    assertThat(secretConfig.secret_string.value).contains("\"answer_to_universe\"")
//    assertThat(secretConfig.secret_bytearray.value)
//      .containsSubsequence(*"\"answer_to_universe\"".toByteArray())
//  }
//
//  @Test
//  fun secretsLoadedRecursively() {
//    assertThat(secretConfig.secret_information_wrapper.secret_information.value.answer_to_universe)
//      .isEqualTo("nothing")
//    assertThat(
//      secretConfig.secret_information_wrapper.secret_information.value.limit
//    ).isEqualTo(73)
//  }
//
//  @Test
//  fun throwsIfServiceConfigIsSecret() {
//    val expectedError = "Top level service config cannot be a Secret<*>"
//    val selfSecretMessage = assertThrows<IllegalStateException> {
//      WispConfig.builder().addWispConfigSources(
//        listOf(
//          ConfigSource("classpath:/secret_config_app.yaml"),
//        )
//      ).build().loadConfigOrThrow()
//
//      val builder = WispConfig.builder()
//      builder.addWispConfigSources(
//        listOf(
//          ConfigSource("classpath:/d.yaml"),
//        )
//      )
//      WispConfig.builder()
//        .load<SelfSecretConfigRoot>("nested_secrets_test", TESTING)
//    }
//    assertThat(selfSecretMessage).hasMessageContaining(expectedError)
//
//    val secretMessage = assertThrows<IllegalStateException> {
//      WispConfig.load<SecretConfigRoot>("nested_secrets_test", TESTING)
//    }
//    assertThat(secretMessage).hasMessageContaining(expectedError)
//  }
//
//  @Test
//  fun throwOnUnknownExtension() {
//    val unknownExtensionError = assertThrows<IllegalStateException> {
//      WispConfig.builder().addWispConfigSources(
//        listOf(
//          ConfigSource("classpath:/secret_config_unknown_extension.yaml"),
//        )
//      ).build().loadConfigOrThrow()
//    }
//    assertThat(unknownExtensionError).hasMessageContaining(
//      "Unknown file extension \"json\" for secret " +
//        "[classpath:/misk/resources/secrets/secret_information_values.json]."
//    )
//  }
//
//  @Test
//  fun throwOnStringTxtMismatch() {
//    val stringTxtMismatchError = assertThrows<IllegalStateException> {
//      WispConfig.load<SecretInformationWrapperConfig>(
//        "secret_config_badtxt_extension",
//        TESTING
//      )
//    }
//    assertThat(stringTxtMismatchError).hasMessageContaining(
//      "Secrets with the .txt extension map to Secret<String> fields in Config classes."
//    )
//  }
//
//  @Test
//  fun throwOnStringMissingExtension() {
//    val stringTxtMismatchError = assertThrows<IllegalStateException> {
//      WispConfig.load<SecretInformationWrapperConfig>(
//        "secret_config_missing_extension",
//        TESTING
//      )
//    }
//    assertThat(stringTxtMismatchError).hasMessageContaining(
//      "Secret [classpath:/misk/resources/secrets/secret_information_values] " +
//        "needs a file extension for parsing."
//    )
//  }
//
//  @Test
//  fun throwMissingFile() {
//    val stringTxtMismatchError = assertThrows<IllegalStateException> {
//      WispConfig.load<SecretInformationWrapperConfig>(
//        "secret_config_missing_file",
//        TESTING
//      )
//    }
//    assertThat(stringTxtMismatchError).hasMessageContaining(
//      "No secret found at: classpath:/file_does_not_exist."
//    )
//  }
//}
