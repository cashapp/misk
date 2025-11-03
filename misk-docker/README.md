# misk-docker

A helper class which attempts to fetch docker registry credentials via the credential store defined
in `$HOME/.docker/config.json`.

This is necessary for pulling images that require authentication.

# Basic Usage

```kotlin
import misk.docker.withMiskDefaults

val defaultDockerClientConfig = DefaultDockerClientConfig
    .createDefaultConfigBuilder()
    .withMiskDefaults()
    .build()
```

# Basic Usage with custom registry URL

```kotlin
import misk.docker.withMiskDefaults

val defaultDockerClientConfig = DefaultDockerClientConfig
    .createDefaultConfigBuilder()
    .withMiskDefaults()
    .withLocalDockerCredentials("https://custom.registry.url/v1/")
    .build()
```

# Advanced Usage

```kotlin
import misk.docker.withMiskDefaults

val credentials = DockerCredentials.getDockerCredentials("https://index.docker.io/v1/")

val defaultDockerClientConfig = DefaultDockerClientConfig
    .createDefaultConfigBuilder()
    .withMiskDefaults()
    // Set the retrieved username and password
    .withRegistryUsername(credentials?.username)
    .withRegistryPassword(credentials?.password)
    .build()
```