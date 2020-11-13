This module provides Misk with the ability to store and manage cryptographic key material in a
safe and easy way.

How it works?
-----
This module reads a configuration key and uses it to initialize and bind Tink primitives (`Aead`, `Mac`).
- Each primitive is also given a `@Named` annotation to make it easy to inject where needed.
- Each primitive must have a serialized and *encrypted* `KeysetHandle` 
file associated with it in order to be initialized.

In order to initialize the module, the app must have a configured `GcpKmsClient` (Google),
or an `AwsKmsClient` (Amazon) client in order to have access to the KMS.

Setup
-----
In one of your app's module files:
```$kotlin
class MyAppModule : KAbstractModule {
  override fun configure() {
    install(AwsKmsClientModule()) // will provide an AWS client with default credentials
    install(CryptoModule(cryptoConfig))
  }
}
```

Create a new key
-----
Keys managed by this module must be encrypted by a KMS.
The first step to generating a key is to use Google's 
[tinkey](https://github.com/google/tink/blob/master/docs/TINKEY.md) tool to generate and encrypt a new key.
```
tinkey create-keyset --key-template AES256-GCM --master-key-uri aws-kms://arn:kms:<region>:<account-id>:key/<key-id> --out myKey.json --credentials path/to/aws-credentials.json
```
Then, to specify a new `Aead` key called "myKey", add the following in your app's configuration file:
```$yaml
crypto:
  kms_uri: "aws-kms://arn:kms:<region>:<account-id>:key/<key-id>"
  keys:
    - key_name: "my_payment_token_key"
      encrypted_kek: [key encrypted using the same KMS used by the app encoded in base64] 
``` 
Using a key
-----
To use your newly created key:
```$kotlin
class PaymentTokenGenerator @Inject constructor(
  @Named("my_payment_token_key") lateinit var tokenCipher: Aead
) {
  
  fun encryptToken(token: String): ByteString {
    return tokenCipher.encrypt(token.encodeUtf8())
  }
  
  fun decryptToken(encryptedToken: ByteString): String {
    // using the key manager to get the right cipher by name
    return KeyManager["my_payment_token_key"]
        .decrypt(encryptedToken)
        .toString()
  }
}
```
