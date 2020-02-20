package misk.hibernate.encryption

import com.google.common.cache.CacheBuilder
import com.google.common.cache.CacheLoader
import java.lang.reflect.Field
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton
import javax.persistence.Table
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.functions
import misk.crypto.AeadKeyManager
import misk.crypto.FieldLevelEncryptionModule
import misk.crypto.FieldLevelEncryptionPacket
import org.hibernate.HibernateException
import org.hibernate.event.spi.AbstractPreDatabaseOperationEvent
import org.hibernate.event.spi.PostInsertEvent
import org.hibernate.event.spi.PostInsertEventListener
import org.hibernate.event.spi.PostLoadEvent
import org.hibernate.event.spi.PostLoadEventListener
import org.hibernate.event.spi.PostUpdateEvent
import org.hibernate.event.spi.PostUpdateEventListener
import org.hibernate.event.spi.PreInsertEvent
import org.hibernate.event.spi.PreInsertEventListener
import org.hibernate.event.spi.PreUpdateEvent
import org.hibernate.event.spi.PreUpdateEventListener
import org.hibernate.persister.entity.EntityPersister
import java.lang.reflect.AccessibleObject

typealias ContextT = Map<String, String>

@Singleton
class FieldEncryptHooks @Inject constructor() :
        PreInsertEventListener,
        PreUpdateEventListener,
        PostLoadEventListener,
        PostInsertEventListener,
        PostUpdateEventListener {

  private val contextFunctionsMapCache = CacheBuilder.newBuilder()
          .build(object : CacheLoader<KClass<*>, Map<String, (Any) -> ContextT>>() {
            override fun load(key: KClass<*>): Map<String, (Any) -> ContextT> =
                    key.functions.mapNotNull { method ->
                      val ann = method.findAnnotation<EncryptedFieldContext>() ?: return@mapNotNull null
                      @Suppress("UNCHECKED_CAST")
                      method as? KFunction<ContextT> ?: return@mapNotNull null
                      ann.forColumn to { e: Any -> method.call(e) }
                    }.toMap()
          })

  @Inject
  lateinit var aeadKeyManager: AeadKeyManager

  // http://anshuiitk.blogspot.com/2010/11/hibernate-pre-database-opertaion-event.html
  private fun packetWithContext(ctx: ContextT) =
          FieldLevelEncryptionPacket.Builder()
                  .also { builder ->
                    // Add the global values
                    FieldLevelEncryptionModule.CommonContextKeys.forEach { key ->
                      builder.addContextEntryWithValueFromEnv(key)
                    }
                    // Add the per-entity values
                    ctx.forEach { (k, v) ->
                      builder.addContextEntry(k, v)
                    }
                  }
                  .build()

  private fun encodeValueForField(value: ByteArray, field: Field) = when (field.type.kotlin) {
    String::class -> Base64.getEncoder().encodeToString(value)
    ByteArray::class -> value
    else -> throw HibernateException("Field type ${field.type} not supported")
  }

  private fun decodeValueForField(value: Any, field: Field) = when (field.type.kotlin) {
    String::class -> Base64.getDecoder().decode(value as String)
    ByteArray::class -> value as ByteArray
    else -> throw HibernateException("Field type ${field.type} not supported")
  }

  private fun onPre(event: AbstractPreDatabaseOperationEvent, state: Array<Any>): Boolean {
    transformFields(event.entity) { field, fieldContext, envContextWithKey ->

      val keyName = envContextWithKey["key_name"]
              ?: throw NoSuchElementException("key missing from field context")

      // XXX(yivnitskiy): Might make sense to include the key as well
      val envContext = envContextWithKey - "key_name"

      val packet = packetWithContext(fieldContext)

      val fieldContents = field.get(event.entity) ?: return@transformFields null
      val contents = EncryptFieldTransformer.serialize(fieldContents)
      val aad = packet.getAeadAssociatedData(envContext)
      val encrypted = aeadKeyManager[keyName].encrypt(contents, aad)

      val encryptedPacket = packet.serializeForStorage(encrypted, envContext)

      val encodedContents = encodeValueForField(encryptedPacket, field) // Base64.getEncoder().encodeToString(encryptedPacket)
      setValue(state, event.persister.entityMetamodel.propertyNames, field.name, encodedContents)
      encodedContents
    }

    return false
  }

  override fun onPostLoad(event: PostLoadEvent) = decryptFields(event.entity)

  override fun onPreUpdate(event: PreUpdateEvent) = onPre(event, event.state)

  override fun onPreInsert(event: PreInsertEvent) = onPre(event, event.state)

  /**
   * XXX(yivnitskiy): This might be better done by storing the plaintext in a cache (or a common base class) instead of
   * decrypting it back when after insertion. This is generally required because the ciphertext is substituted prior to
   * persisting the entity, but if the entity is used again after the fact, we want the original plaintext to be
   * accessible.
   */
  override fun onPostInsert(event: PostInsertEvent) = decryptFields(event.entity)

  override fun onPostUpdate(event: PostUpdateEvent) = decryptFields(event.entity)

  override fun requiresPostCommitHanding(persister: EntityPersister?) = true

  private fun decryptFields(entity: Any) {
    transformFields(entity) { field, _ /*fieldContext*/, envContextWithKey ->

      val keyName = envContextWithKey["key_name"]
              ?: throw NoSuchElementException("key missing from field context")

      // XXX(yivnitskiy): Might make sense to include the key as well
      val envContext = envContextWithKey - "key_name"

      val fieldContents = field.get(entity) ?: return@transformFields null

      val decodedContents = decodeValueForField(fieldContents, field) // Base64.getDecoder().decode(fieldContents as String)

      // TODO(yivnitskiy): Currently, fieldContext is not used here but is completely embedded in the packet
      val packet = FieldLevelEncryptionPacket.fromByteArray(decodedContents) // , envContext)

      val aad = packet.getAeadAssociatedData(envContext)
      val decryptedContents = aeadKeyManager[keyName].decrypt(packet.payload, aad)

      EncryptFieldTransformer.deserialize(decryptedContents, field.type.kotlin)
    }
  }

  private fun transformFields(entity: Any, f: (Field, ContextT, ContextT) -> Any?) {
    val contextFuncs = contextFunctionsMapCache[entity::class]

    val tableName = entity.javaClass.getAnnotation(Table::class.java).name

    // XXX(yivnitskiy) Not sure why, but the KClass's fields do not contain the
    // annotations we're looking for, so using the java class
    for (field in entity.javaClass.declaredFields) {
      val encAnnotation = field.getAnnotation(EncryptedField::class.java) ?: continue

      if (encAnnotation.type != EncryptedFieldType.NonDeterministic) {
        // indexable columns are entirely handled by FieldEncryptTransformer
        if (contextFuncs.containsKey(field.name))
          throw HibernateException("Can not assign a @EncryptedFieldContext method to an indexable field")
        continue
      }

      try {
        AccessibleObject.setAccessible(arrayOf(field), true)
      } catch (e: SecurityException) {
        throw HibernateException("Can not access EncryptedField property (${field.name})")
      }

      val userContext = contextFuncs.getOrElse(field.name) { { _ -> mapOf() } }.invoke(entity)

      val columnName = getColumnName(field)
      val entityContext = mapOf(
              "key_name" to encAnnotation.keyName,
              FieldLevelEncryptionPacket.ContextKey.TABLE_NAME.name to tableName,
              FieldLevelEncryptionPacket.ContextKey.COLUMN_NAME.name to columnName
      )

      val newContents = f(field, userContext, entityContext)
      field.set(entity, newContents)
    }
  }

  private fun setValue(currentState: Array<Any>, propertyNames: Array<String>, propertyToSet: String, value: Any) {
    val index = propertyNames.indexOf(propertyToSet)
    if (index < 0) throw HibernateException("Bad property name ($propertyToSet)")
    currentState[index] = value
  }
}
