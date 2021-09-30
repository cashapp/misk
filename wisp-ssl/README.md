# wisp-ssl

Loads and parses SSL certificates into
a [`KeyStore`](https://docs.oracle.com/en/java/javase/15/docs/api/java.base/java/security/KeyStore.html)
.
See [SslLoader](https://github.com/cashapp/misk/blob/master/wisp-ssl/src/main/kotlin/wisp/security/ssl/SslLoader.kt)
for a complete API.

This library is mostly only useful for other libraries that want to
use [ResourceLoader](https://github.com/cashapp/misk/tree/master/wisp-resource-loader) under the
hood.

## Usage

```kotlin
val sslLoader: SslLoader = SslLoader(ResourceLoader.SYSTEM)

val certStore = sslLoader.loadCertStore(
  "classpath:/ssl/client_cert_key_combo.pem",
  SslLoader.Companion.FORMAT_PEM,
  "password"
)
```
