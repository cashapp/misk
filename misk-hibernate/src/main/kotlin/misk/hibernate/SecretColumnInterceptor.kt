package misk.hibernate

import org.hibernate.EmptyInterceptor
import org.hibernate.type.CustomType
import org.hibernate.type.Type
import java.io.Serializable
import java.util.Properties

class SecretColumnInterceptor : EmptyInterceptor() {

  override fun onSave(
    entity: Any,
    id: Serializable?,
    state: Array<out Any>?,
    propertyNames: Array<out String>,
    types: Array<out Type>
  ): Boolean {
    val hasSecretColumn = types.find { it.name == SecretColumnWithAadType::class.java.name }
    if (hasSecretColumn != null) {
      addAad(entity, propertyNames, types)
    }
    return super.onSave(entity, id, state, propertyNames, types)
  }

  override fun onLoad(
    entity: Any,
    id: Serializable?,
    state: Array<out Any>?,
    propertyNames: Array<out String>,
    types: Array<out Type>
  ): Boolean {
    val hasSecretColumn = types.find { it.name == SecretColumnWithAadType::class.java.name }
    if (hasSecretColumn != null) {
      addAad(entity, propertyNames, types)
    }
    return super.onLoad(entity, id, state, propertyNames, types)
  }

  fun addAad(entity: Any, propertyNames: Array<out String>, types: Array<out Type>) {
    val secretTypes = types.filter { it.name == SecretColumnWithAadType::class.java.name }
    secretTypes.forEach { type ->
      val index = types.indexOf(type)
      val propertyName = propertyNames[index]
      // this could be other fields in the
      val keyName = entity::class.java.getDeclaredField(propertyName)
          .getAnnotation(SecretColumnWithAad::class.java).keyName
      ((type as CustomType).userType as SecretColumnWithAadType)
          .setParameterValues(Properties())
    }
  }
}