package misk.hibernate

/**
 * [SecretColumn] is an annotation used to get Hibernate to encrypt a field before writing it
 * to the database.
 * The [keyName] string is used to specify the name of the key to be used to encrypt and decrypt the value.
 * Install [misk.crypto.CryptoModule] to configure the keys the app uses.
 * Example:
 * In app-common.yaml:
 * ```
 * crypto:
 *   keys:
 *     - key_name: "secretColumnKey"
 * ```
 * Then, in an entity class:
 * ```
 * @Column
 * @SecretColumn(keyName = "secretColumnKey")
 * var secret: String
 * ```
 * A Column annotated with [SecretColumn] has the following limitations:
 * - It must be declared as `VARBINARY()` in its respective MySQL table. For example:
 * ```
 * CREATE TABLE my_table(
 *   id BIGINT NOT NULL AUTO_INCREMENT,
 *   secret VARBINARY(500)
 * ```
 * - It cannot be a part of an index
 * - It cannot be annotates with any other custom column annotations like [ProtoColumn] or [JsonColumn].
 *
 * *Note*: the resulting ciphertext that is persisted in the database may be much larger in size
 * than the original plaintext because it also contains some metadata.
 * Please make sure to allocate enough space when defining the column using `VARBINARY()`.
 */
@Target(AnnotationTarget.FIELD)
annotation class SecretColumn(val keyName: String)

