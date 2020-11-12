package misk.web

import com.google.common.base.Preconditions
import com.squareup.wire.Message
import com.squareup.wire.WireEnum
import com.squareup.wire.WireField
import misk.web.metadata.Field
import misk.web.metadata.Type
import okio.ByteString
import java.util.LinkedList
import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.full.superclasses

/**
 * Provides a mapping from field name to Type definition given a KType.
 * Useful for processes that want to have a schema definition of a type.
 * For example: used by the WebActions admin dashboard tab to show a statically typed form
 * containing request fields for developers to fill out.
 * Currently only supports Wire request type messages;
 * non-Wire messages return an empty mapping.
 */
class RequestTypes {
  fun calculateTypes(requestType: KType?): Map<String, Type> {
    // Type maps can only be calculated for wire messages
    if (requestType == null) {
      return mapOf()
    }

    val requestClass = requestType.classifier as KClass<*>
    if (!requestClass.superclasses.contains(Message::class)) {
      return mapOf()
    }

    val typesMap = mutableMapOf<String, Type>()
    val stack = LinkedList<KClass<*>>()
    stack.push(requestClass)

    while (stack.isNotEmpty()) {
      val klass = stack.pop()

      // No need to re-process a given type.
      // This acts as the visited set of our type graph traversal.
      if (typesMap.containsKey(klass.qualifiedName!!)) {
        continue
      }

      val fields = mutableListOf<Field>()

      for (property in klass.declaredMemberProperties) {
        // Use the WireField annotation to identify fields of our proto.
        if (property.annotations.any { it is WireField }) {
          val fieldName = property.name
          handleField(property.returnType, fieldName, fields, stack)
        }
      }

      typesMap.put(klass.qualifiedName!!, Type(fields.toList()))
    }

    return typesMap
  }

  private fun handleField(
    fieldType: KType,
    fieldName: String,
    fields: MutableList<Field>,
    stack: LinkedList<KClass<*>>,
    repeated: Boolean = false
  ) {

    val fieldClass = fieldType.classifier as KClass<*>
    val maybePrimitiveType = maybeCreatePrimitiveField(fieldClass, fieldName, repeated)
    when {
      maybePrimitiveType != null -> fields.add(maybePrimitiveType)
      fieldClass == List::class -> {
        val fieldClassParameters = fieldType.arguments
        Preconditions.checkState(
            fieldClassParameters.size == 1,
            "Encountered Wire-generated List without 1 type parameter: %s",
            fieldType)
        val listType = fieldClassParameters[0].type!!
        handleField(listType, fieldName, fields, stack, true)
      }
      fieldClass == Map::class -> {
        // TODO: Support maps
        fields.add(Field(fieldName, fieldClass.qualifiedName!!, repeated))
      }
      else -> {
        fields.add(Field(fieldName, fieldClass.qualifiedName!!, repeated))
        stack.push(fieldClass)
      }
    }
  }

  companion object {
    /**
     * Create misk-web [Field]s for primitives and enum types.
     * Returns null if the type cannot be mapped.
     */
    fun maybeCreatePrimitiveField(
      fieldClass: KClass<*>,
      fieldName: String,
      repeated: Boolean
    ): Field? {
      return when {
        fieldClass == String::class -> Field(fieldName, String::class.simpleName!!, repeated)
        fieldClass == ByteString::class -> Field(fieldName, ByteString::class.simpleName!!, repeated)
        fieldClass == Char::class -> Field(fieldName, Char::class.simpleName!!, repeated)
        fieldClass == Byte::class -> Field(fieldName, Byte::class.simpleName!!, repeated)
        fieldClass == Short::class -> Field(fieldName, Short::class.simpleName!!, repeated)
        fieldClass == Int::class -> Field(fieldName, Int::class.simpleName!!, repeated)
        fieldClass == Long::class -> Field(fieldName, Long::class.simpleName!!, repeated)
        fieldClass == Double::class -> Field(fieldName, Double::class.simpleName!!, repeated)
        fieldClass == Boolean::class -> Field(fieldName, Boolean::class.simpleName!!, repeated)
        fieldClass == Enum::class -> createEnumField(fieldClass, fieldName, repeated)
        fieldClass.superclasses.contains(WireEnum::class) -> {
          createEnumField(fieldClass, fieldName, repeated)
        }
        else -> null
      }
    }

    /**
     * Adds a field with a type that has the class name and enum values embedded
     * Example: "Enum<app.cash.backfila.BackfillType,ISOLATED,PARALLEL>"
     */
    private fun createEnumField(
      fieldClass: KClass<*>,
      fieldName: String,
      repeated: Boolean
    ): Field {
      val enumValues =
          (fieldClass.members.find { it.name == "values" }?.call() as Array<*>).map { (it as Enum<*>).name }
      return Field(fieldName, "Enum<${fieldClass.qualifiedName!!},${enumValues.joinToString(",")}>",
          repeated)
    }
  }
}
