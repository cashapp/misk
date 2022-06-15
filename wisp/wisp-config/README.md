# wisp-config

wisp-config is a wrapper for the [Hoplite config library](https://github.com/sksamuel/hoplite).

Config precedence is in order of sources added for loading, so if you want an environment config file to override values
in the default file, it has to be added first.

Note that config values from Environment Variables, System Properties and User Settings are always loaded first,
see: https://github.com/sksamuel/hoplite#property-sources for details.

Secrets in config are automatically handled, see: https://github.com/sksamuel/hoplite#masked-values

Note that if you are passing configuration fragments within Wisp, your config class should
implement [Config](https://github.com/cashapp/wisp/blob/master/wisp-config/src/main/kotlin/wisp/config/Config.kt).

## Usage

The following example will load the config in the 2 files located on the classpath into the user defined MyConfig class.

```kotlin
data class MyConfig(
  val foo: Foo,
  val baz: String
) : Config

data class Foo(
  val enabled: Boolean,
  val bar: Int
)

val configSources = listOf(
  ConfigSource("classpath:/myapp-config.yaml"),
  ConfigSource("classpath:/myapp-defaults.yml")
)
val myConfig : MyConfig = WispConfig.builder()
                .addWispConfigSources(configSources)
                .build()
                .loadConfigOrThrow<MyConfig>()


```

Assume `myapp-defaults.yml` contains:

```yaml
foo:
  enabled: true
  bar: 72
baz: "abc"
```

And `myapp-config.yaml` contains:

```yaml
foo:
  enabled: false
```

Then the loaded MyConfig from the example above would be equivalent to:

```kotlin
val myConfig : MyConfig = MyConfig(
  foo = Foo(
    enabled = false,
    bar = 72
  ),
  baz = "abc"
)
```
