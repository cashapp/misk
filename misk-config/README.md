# misk-config

The Misk Config package includes useful tools for loading config from YAML including from other resource locations (classpath, environment variables...).

## Config

Define a config class by extending `Config`.

```kotlin
data class ExemplarConfig(
  val name: String,
  val port: Int,
) : Config
```

Then in `src/main/resources/exemplar-common.yaml` where `exemplar` is your service name:

```YAML
name: alpha
port: 9000
```

Misk Config handles overrides by deployment and region, checking for files like `exemplar-production.yaml` to use to override the common YAML.

Then you can load the resolved config with the following:

```kotlin
val deployment = getDeploymentFromEnvironmentVariable()
val config = MiskConfig.load<ExemplarConfig>("exemplar", depoyment)
```

## Redacted

For sensitive fields, you can annotate them with `@misk.config.Redact` and the field value will not appear in serialized YAML.

```kotlin
data class ExemplarConfig(
  val name: String,
  val port: Int,
  @Redact
  val api_key: String,
) : Config
```

```bash
> println(MiskConfig.toRedactedYaml(ExemplarConfig("alpha", 90000, "abc123")))
name: alpha
port: 9000
api_key: ████████
```

Note: The redacted value will still appear in `toString()` output. If you want to redact the value in `toString()` as well, use the `Secret<T>` type as described below.

## Secrets

For secret fields where the value is not included in YAML but is provided via some other resource location, use the `Secret<T>` class in your Config definition.

```kotlin
data class ExemplarConfig(
  val name: String,
  val port: Int,
  @Redact
  val api_key: String,
  val api_secret: Secret<String>,
) : Config
```

In the YAML, the field takes a resource path where the secret value can be retrieved.

```YAML
name: alpha
port: 9000
api_key: abc123
api_secret: filesystem:/etc/secrets/service/api_secret.txt
```

Secrets are automatically redacted in serialized YAML and `toString()` output.

```bash
> println(exemplarConfig)
ExemplarConfig(name=alpha, port=9000, api_key=████████, api_secret=RealSecret(value=████████, reference=filesystem:/etc/secrets/service/api_secret.txt))
```

## Resource Loader

The Resource Loader provides a way to load from many locations using a simple path scheme. Included supported backends are:

- `filesystem:` loads from the filesystem
- `classpath:` loads from the JVM runtime classpath
- `memory:` loads from an in-memory map, useful for tests
- `environment:` loads from host environment variables
- `1password:` loads from the 1Password password manager app

Resource locations are defined by path like `filesystem:/path/to/file` or `environment:MY_ENV_VAR`.

## Non-Secret Resources

For non-Secret fields where a value is to be provided by resource path, YAML variable syntax can be used.

```YAML
name: ${environment:SERVICE_NAME}
port: ${environment:PORT}
```

## Defaults

Default values can be set in either the config class definition,

```kotlin
data class ExemplarConfig(
  val name: String,
  val port: Int = 9000,
) : Config
```

or in YAML files using Bash default syntax.

```YAML
name: ${environment:SERVICE_NAME:-exemplar}
port: ${environment:PORT:-9000}
```
