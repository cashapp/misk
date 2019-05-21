package misk.hibernate

/**
 * This annotation is used to get Hibernate ti encrypt a field before being persisted to the database.
 *
 * The [keyName] string is used to specify the name of the key used to encrypt and decrypt the value.
 *
 * *This annotation uses deterministic encryption*: encrypting the same plaintext will produce the same ciphertext.
 * This is weaker than non-deterministic encryption, but makes searching for encrypted values possible.
 * If searching for ciphertexts is not something your use case requires, use [SecretColumn] instead.
 *
 * INstall [misk.crypto.CryptoModule] to configure the key to use here.
 * Example:
 * ```
 * crypto:
 *   keys:
 *     - key_name: "secretKey"
 *     - key_type: DAEAD
 * ```
 * Then, in an entity class:
 * ```
 * @SelectableSecretColumn(keyName = "secretKey")
 * @Column(name = "my_secret_column")
 * var mySecretColumn: ByteArray
 * ```
 * Add a migration file to create the corresponding table:
 * ```
 * CREATE TABLE my_table(
 *   id BIGINT NOT NULL PRIMARY KEY AUTO_INCREMENT,
 *   my_secret_column VARBINARY(250)
 * ```
 */
@Target(AnnotationTarget.FIELD)
annotation class SelectableSecretColumn(val keyName: String)