package misk.hibernate.encryption

import misk.hibernate.encryption.EncryptedFieldType.Indexable
import misk.hibernate.encryption.EncryptedFieldType.NonDeterministic
import misk.hibernate.TransformedType

/**
 * [EncryptedFieldType] determines the type of an encrypted field.
 *
 * In brief, use [Indexable] if you ever might need to write queries based on encrypted values (such as an email
 * address, or a phone number) and use [NonDeterministic] if you're just associating data that doesn't need to be queries
 * such as a home address or a transaction description.
 *
 */
enum class EncryptedFieldType {
  /**
   * [Indexable] makes the column queryable for exact matches. This implies deterministic encryption: encrypted
   * identical plaintext will create identical ciphertext. This is marginally less secure than [NonDeterministic], but
   * enables this column to be matched using equality.
   */
  Indexable,

  /**
   * [NonDeterministic] uses a nondeterministic encryption algorithm which can not be queried. This is marginally more
   * secure, but more importantly permits the use of @[EncryptedFieldContext] functions.
   */
  NonDeterministic
}

/**
 * [EncryptedField] is an annotation used to mark entity fields as encrypted. It takes two arguments:
 *
 *  * [keyName] - The name of the key that should encrypt the values in the annotated columns. The key must be
 *     created via sqm and added to the config yaml. See example-crypto for examples.
 *  * [type] - The type of the encrypted column. See [EncryptedFieldType] for more details.
 *
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FIELD)
@TransformedType(transformer = EncryptFieldTransformer::class, targetType = ByteArray::class)
annotation class EncryptedField(val keyName: String, val type: EncryptedFieldType = Indexable)
