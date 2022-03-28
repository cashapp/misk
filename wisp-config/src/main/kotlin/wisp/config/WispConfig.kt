package wisp.config

import com.sksamuel.hoplite.ConfigLoader
import com.sksamuel.hoplite.ConfigLoaderBuilder
import com.sksamuel.hoplite.json.JsonPropertySource
import com.sksamuel.hoplite.sources.InputStreamPropertySource
import com.sksamuel.hoplite.toml.TomlPropertySource
import com.sksamuel.hoplite.yaml.YamlPropertySource
import wisp.config.PrefixResourceLoaderPreprocessor.Companion.CLASSPATH_PREFIX
import wisp.config.PrefixResourceLoaderPreprocessor.Companion.FILESYSTEM_PREFIX
import wisp.resources.ResourceLoader

/**
 * WispConfig is a wrapper around the Hoplite config library:
 * https://github.com/sksamuel/hoplite
 *
 * Config precedence is in order of sources added for loading, so if you want an environment
 * config file to override values in the default file, it has to be added first.
 *
 * Note that config values from Environment Variables, System Properties and User Settings are
 * always loaded first, see: https://github.com/sksamuel/hoplite#property-sources for details.
 *
 * Secrets in config are automatically handled, see:
 * https://github.com/sksamuel/hoplite#masked-values
 *
 * Note that if you are passing configuration fragments within Wisp, your config class should
 * implement [Config]
 */
object WispConfig {

  /**
   * Get a config builder.
   *
   * Example use:
   *
   * val myConfig = WispConfig.builder()
   *                ...
   *                .addWispConfigSources(...)
   *                ...
   *                .build()
   *                .loadConfigOrThrow<MyConfig>()
   * Note that if you are passing configuration fragments within Wisp, MyConfig should implement
   * [Config]
   *
   * @return [ConfigLoader.Builder]
   */
  fun builder(): ConfigLoaderBuilder = ConfigLoader.builder()
}

/**
 * Config location and format (default yaml).  The location should be in a format that
 * [ResourceLoader] understands.
 */
data class ConfigSource(val configLocation: String, val format: String = "yml")

/**
 * Add the config sources in the order supplied. If the config source location does not
 * exist it is silently skipped, i.e. the config locations are optional.
 */
fun ConfigLoaderBuilder.addWispConfigSources(
  configSources: List<ConfigSource>,
  resourceLoader: ResourceLoader = ResourceLoader.SYSTEM
): ConfigLoaderBuilder {

  addPreprocessor(PrefixResourceLoaderPreprocessor(CLASSPATH_PREFIX, resourceLoader))
  addPreprocessor(PrefixResourceLoaderPreprocessor(FILESYSTEM_PREFIX, resourceLoader))

  configSources
    .filter {
      (resourceLoader.exists(it.configLocation))
    }
    .forEach {
      val configContents = resourceLoader.utf8(it.configLocation)!!
      when (it.format) {
        "yml", "yaml" ->
          this.addSource(YamlPropertySource(configContents))
        "json" ->
          this.addSource(JsonPropertySource(configContents))
        "toml" ->
          this.addSource(TomlPropertySource(configContents))
        else ->
          this.addSource(
            InputStreamPropertySource(
              configContents.byteInputStream(Charsets.UTF_8),
              it.format,
              it.configLocation
            )
          )
      }
    }

  return this
}

