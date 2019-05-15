package misk.hibernate

/**
 * [SecretColumn] is an annotation used to get Hibernate to encrypt a field before writing it to the database.
 *
 * The [keyName] string is used to specify the name of the key to be used to encrypt and decrypt the value.
 *
 * Install [misk.crypto.CryptoModule] to configure the keys the app uses.
 * Example:
 * In app-common.yaml:
 * ```
 * crypto:
 *   keys:
 *     - key_name: "secretColumnKey"
 *     - key_type: AEAD
 * ```
 * Then, in an entity class:
 * ```
 * @SecretColumn(keyName = "secretColumnKey")
 * @Columns([Column(name = "secret"), Column(name = "secret_aad")])
 * var secret: ByteArray
 * ```
 * A Column annotated with [SecretColumn] has the following limitations:
 * - It must be declared as `VARBINARY()` in its respective MySQL table. For example:
 * ```
 * CREATE TABLE my_table(
 *   id BIGINT NOT NULL PRIMARY KEY AUTO_INCREMENT,
 *   secret VARBINARY(500),
 *   secret_aad VARBINARY(36),
 *
 *   CONSTRAINT UNIQUE secret_aad
 *   id BIGINT NOT NULL PRIMARY KEY AUTO_INCREMENT,
 *   secret VARBINARY(500)
 * ```
 * - It cannot be annotates with any other custom column annotations like [ProtoColumn] or [JsonColumn].
 * - It is not searchable. The encryption algorithm used by this annotation is non-deterministic,
 *   which means that every piece of data encrypted will result is a different
 *   ciphertext stored in the database.
 *
 * Security properties:
 * - Data encrypted using this annotation is authenticated and cannot be duplicated/modified
 *   outside the service's scope.
 * - Authentication is provided by the a accompanying "columnName_aad" column.
 *   This column must have a unique value, otherwise, an attacker could copy the encrypted value
 *   with its "_aad" data to create more records.
 *
 * *Note*:
 *  The resulting ciphertext that is persisted in the database may be much larger in size than
 *  the original plaintext because it also contains some metadata. Make sure to allocate
 *  enough space when defining the column using `VARBINARY()`.
 *
 */
@Target(AnnotationTarget.FIELD)
annotation class SecretColumn(val keyName: String)

