package misk.hibernate

/**
 * [VerifiedColumn] is an annotation used to mark a column is used to store Message Authentication Codes.
 * Adding this annotation to a field will cause Hibernate to *store the HMAC of the data being set*.
 *
 * The [keyName] variable specifies the name of the key that'll be used when storing the HMAC.
 *
 * To use this annotation, install [misk.crypto.CryptoModule] and add a key of type [misk.crypto.KeyType.MAC].
 * Example:
 * In your <app>-common.yaml configuration file:
 * ```
 * crypto:
 *   keys:
 *     - key_name: "columnVerificationKeyName"
 * ```
 * Then, add a table in your database with 3 columns:
 * - my_data: the column where you'd liek to persist your authenticated data
 * - my_data_salt: this column will be used internally to generate and store a salt value
 *   used in coomupting the data's MAC.
 * - my_data_hmac: this column stores the HMAC for (my_data + my_data_salt)
 * ```
 * CREATE TABLE my_table(
 *   id BIGINT NOT NULL PRIMARY KEY AUTO_INCREMENT,
 *   my_data VARCHAR(50),
 *   my_data_salt VARBINARY(36),
 *   my_data_hmac VARBINARY(100),
 *
 *   CONSTRAINT my_data_salt_uniq UNIQUE (my_data_salt)
 * )
 * ```
 * When bninding a Hibernate entity to your table, specify the following annotation for your verified column:
 * ```
 * @VerifiedColumn(keyName = "columnVerificationKeyName")
 * @Columns(columns = [
 *   Column(name = "my_data"),
 *   Column(name = "my_data_salt"),
 *   Column(name = "my_data_hmac")
 * ])
 * var myData: String
 * ```
 * *Notes*:
 * - The order of the column names in the entity class matter.
 *   The data is always first, and the data's hmac is second.
 * - This annotation only supports verifying [String]s.
 *   Use `VARCHAR` and `VARBINARY` when defining your table.
 * - It's important to include the CONSTRAINT over the "salt" column.
 *   Without it, verified data cannot be modified, but can still be duplicated/inserted to other rows in the table
 * - Using this annotation will help make sure the data stored with it is authentic and will throw
 *   an exception if the data does not match the HMAC.
 * - Lastly, in case of a failed MAC verification, an error level message will be logged as well
 *   with the relevant column name and details.
 */
@Target(AnnotationTarget.FIELD)
annotation class VerifiedColumn(val keyName: String)