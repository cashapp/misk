package misk.hibernate

/**
 * [EncryptedColumn] is an annotation used to get Hibernate to encrypt a field before writing it
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
 * @EncryptedColumn(keyName = "secretColumnKey")
 * var secret: String
 * ```
 * Hibernate fields annotated with [EncryptedColumn] must be declared as `VARBINARY()` or `BINARY`
 * in their respective MySQL table. For example:
 * ```
 * CREATE TABLE my_table(
 *   id BIGINT NOT NULL AUTO_INCREMENT,
 *   secret VARBINARY(200)
 * _
 * ```
 */
@Target(AnnotationTarget.FIELD)
annotation class EncryptedColumn(val keyName: String)