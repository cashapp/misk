package misk.hibernate.encryption

/**
 * Marker annotation for database entity functions that can generate additional state for the associated data for
 * field-level encryption. Can only be used with columns of encrypted field type [EncryptedFieldType.NonDeterministic].
 *
 *   [forColumn]: Which column field (name of the Kotlin entity field itself, not the backing column name) to add AAD
 *   to; the column must be annotated with [EncryptedField].
 *
 * This feature can be used to add additional per-row data that must be provided when a value is decrypted. One can
 * provide data from the current execution context (i.e. customer token of the user the request is being processed for)
 * or tie data to other fields of the entity.
 *
 * Note: Ensure that the context keys and values do not include characters '=' and '|' as they are reserved.
 *
 * Example context generation Hibernate method:
 *
 *    @Entity
 *    @Table(name = "customers")
 *    class DbCustomer : DbUnsharded<DbCustomer> {
 *
 *      ...........
 *
 *      @Column(nullable=false)
 *      @EncryptedField("address_key", type=EncryptedFieldType.NonDeterministic)
 *      var address: String? = null
 *
 *      ...........
 *
 *      @EncryptedFieldContext("address")
 *      fun addressContext(): Map<String, String> {
 *        val context = mutableMapOf("name" to "$name")
 *        if (::id.isInitialized) {
 *          context["id"] = id.toString()
 *        }
 *        return context
 *      }
 *
 *    }
 *
 * The above will cryptographically tie the fields 'name' and 'id' to the address ciphertext. This implies that the
 * referenced fields will not change; decryption will fail if they do.
 *
 */

@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION)
annotation class EncryptedFieldContext(val forColumn: String)
