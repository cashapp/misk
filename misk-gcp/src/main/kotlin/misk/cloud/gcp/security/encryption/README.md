# Envelope Encryption with Cloud KMS

The `EnvelopeEncryptionModule` provides an `EnvelopeEncryptor` which uses Tink and Cloud KMS to
encrypt arbitrary data using [envelope encryption](https://cloud.google.com/kms/docs/envelope-encryption).

## Configuring the module
A typical configuration for the module looks like so:
```yaml
envelope_encryptor:
  project_id: some-gcp-project-id
  kek:
    location: some-location
    key_ring: some-key-ring-name
    key_name: some-key-name
  credentials: filesystem:/etc/secrets/service/some-gcp-credentials.json 
```
, where `credentials` is a path to a Misk secret.

Configuration is only required for production and staging (i.e. "real") environments.
Other environments use an in-memory encryption method.

Install the `EnvelopeEncryptionModule` appropriately in your service, like so:
```kotlin
install(EnvelopeEncryptionModule(config.envelope_encryptor, serviceBuilder.deployment))
```

## Using the encryptor
You need only to inject the `EnvelopeEncryptor` and use its methods.
Usage is identical in the testing environment.
```kotlin
class SomeClass @Inject internal constructor(
  private val envelopeEncryptor: EnvelopeEncryptor
) {
  fun someFunc() {
    envelopeEncryptor.encrypt(SOME_BYTE_ARRAY)
  }
}
```

## Further notes
Currently, only encryption is supported and not decryption.

Data encryption keys are only generated using `AES256_GCM`.