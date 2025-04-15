package misk.web

import com.google.inject.TypeLiteral
import com.squareup.wire.Message
import com.squareup.wire.ProtoAdapter
import com.squareup.wire.WireEnum
import com.squareup.wire.WireField
import okio.ByteString
import java.lang.reflect.ParameterizedType
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.util.LinkedList
import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.full.isSubclassOf
import kotlin.reflect.full.superclasses
import kotlin.reflect.jvm.javaField

/**
 * Provides a mapping from field name to Type definition given a KType.
 * Useful for processes that want to have a schema definition of a type.
 * For example: used by the WebActions admin dashboard tab to show a statically typed form
 * containing request fields for developers to fill out.
 * Currently only supports Wire request type messages;
 * non-Wire messages return an empty mapping.
 */
class MiskWebFormBuilder {
  fun calculateTypes(requestType: KType?): Map<String, Type> {
    // Type maps can only be calculated for wire messages
    if (requestType == null) {
      return mapOf()
    }

    val requestClass = requestType.classifier as KClass<*>
    if (Message::class !in requestClass.superclasses) {
      return mapOf()
    }

    val typesMap = mutableMapOf<String, Type>()
    val stack = LinkedList<KClass<*>>()
    stack.push(requestClass)

    while (stack.isNotEmpty()) {
      val clazz = stack.pop()

      // No need to re-process a given type.
      // This acts as the visited set of our type graph traversal.
      if (typesMap.containsKey(clazz.java.canonicalName!!)) {
        continue
      }

      val fields = mutableListOf<Field>()

      for (property in clazz.declaredMemberProperties) {
        val field = property.javaField
        // Use the WireField annotation to identify fields of our proto.
        if (field?.annotations?.any { it is WireField } == true) {
          handleField(
            fieldType = TypeLiteral.get(field.genericType),
            fieldName = field.name,
            fields = fields,
            stack = stack,
            annotations = property.annotations.filter { it !is WireField }
          )
        }
      }

      typesMap[clazz.java.canonicalName!!] = Type(fields.toList())
    }

    return typesMap
  }

  private fun handleField(
    fieldType: TypeLiteral<*>,
    fieldName: String,
    fields: MutableList<Field>,
    stack: LinkedList<KClass<*>>,
    repeated: Boolean = false,
    annotations: List<Annotation>,
  ) {
    val fieldClass = fieldType.rawType
    val maybePrimitiveType = maybeCreatePrimitiveField(fieldClass, fieldName, repeated, annotations)
    when {
      maybePrimitiveType != null -> fields.add(maybePrimitiveType)
      fieldClass == List::class.java -> {
        val fieldClassParameters = (fieldType.type as ParameterizedType).actualTypeArguments
        check(fieldClassParameters.size == 1) {
          "Encountered Wire-generated List without 1 type parameter: $fieldType"
        }
        val listType = fieldClassParameters[0]
        handleField(TypeLiteral.get(listType), fieldName, fields, stack, true, annotations)
      }
      fieldClass == Map::class.java -> {
        val fieldClassArguments = (fieldType.type as ParameterizedType).actualTypeArguments
        check(fieldClassArguments.size == 2) {
          "Encountered Wire-generated Map without 2 type parameters: $fieldType"
        }
        // key type can never be a Message so we skip it
        val valueType = fieldClassArguments[1]
        handleField(TypeLiteral.get(valueType), fieldName, fields, stack, true, annotations)
      }
      else -> {
        fields.add(
          Field(
            name = fieldName,
            type = fieldClass.canonicalName!!,
            repeated = repeated,
            annotations = annotations.toStrings(),
            protobufType = fieldClass.kotlin.getProtobufType(),
          )
        )
        stack.push(fieldClass.kotlin)
      }
    }
  }

  companion object {
    /**
     * Create misk-web [Field]s for primitives and enum types.
     * Returns null if the type cannot be mapped.
     */
    fun maybeCreatePrimitiveField(
      fieldClass: Class<*>,
      fieldName: String,
      repeated: Boolean,
      annotations: List<Annotation> = emptyList(),
    ): Field? {
      return when {
        fieldClass == String::class.java ->
          Field(fieldName, String::class.simpleName!!, repeated, annotations.toStrings())
        fieldClass == ByteString::class.java ->
          Field(fieldName, ByteString::class.simpleName!!, repeated, annotations.toStrings())
        fieldClass == Char::class.javaObjectType -> Field(
          fieldName,
          Char::class.simpleName!!,
          repeated,
          annotations.toStrings(),
        )
        fieldClass == Byte::class.javaObjectType -> Field(
          fieldName,
          Byte::class.simpleName!!,
          repeated,
          annotations.toStrings(),
        )
        fieldClass == Short::class.javaObjectType -> Field(
          fieldName,
          Short::class.simpleName!!,
          repeated,
          annotations.toStrings(),
        )
        fieldClass == Int::class.javaObjectType -> Field(
          fieldName,
          Int::class.simpleName!!,
          repeated,
          annotations.toStrings(),
        )
        fieldClass == Long::class.javaObjectType || fieldClass == Long::class.javaPrimitiveType -> Field(
          fieldName,
          Long::class.simpleName!!,
          repeated,
          annotations.toStrings(),
        )
        fieldClass == Double::class.javaObjectType -> Field(
          fieldName,
          Double::class.simpleName!!,
          repeated,
          annotations.toStrings(),
        )
        fieldClass == Boolean::class.javaObjectType -> Field(
          fieldName,
          Boolean::class.simpleName!!,
          repeated,
          annotations.toStrings(),
        )
        fieldClass == Enum::class.java -> createEnumField(fieldClass, fieldName, repeated, annotations)
        fieldClass == Instant::class.java -> Field(fieldName, Instant::class.simpleName!!, repeated, annotations.toStrings())
        fieldClass == Duration::class.java -> Field(
          fieldName,
          Duration::class.simpleName!!,
          repeated,
          annotations.toStrings(),
        )
        fieldClass == LocalDate::class.java -> Field(
          fieldName,
          LocalDate::class.simpleName!!,
          repeated,
          annotations.toStrings(),
        )
        WireEnum::class.java.isAssignableFrom(fieldClass) -> {
          createEnumField(fieldClass, fieldName, repeated, annotations)
        }
        else -> null
      }
    }

    /**
     * Adds a field with a type that has the class name and enum values embedded
     * Example: "Enum<app.cash.backfila.BackfillType,ISOLATED,PARALLEL>"
     */
    fun createEnumField(
      fieldClass: Class<*>,
      fieldName: String,
      repeated: Boolean,
      annotations: List<Annotation> = emptyList(),
    ): Field = createSyntheticEnumField(
      fieldClassName = fieldClass.canonicalName,
      fieldName = fieldName,
      enumValues = fieldClass.enumConstants.map { (it as Enum<*>).name },
      repeated = repeated,
      annotations = annotations,
    )

    /**
     * Adds a field with a type that has the class name and enum values embedded
     * Example: "Enum<app.cash.backfila.BackfillType,ISOLATED,PARALLEL>"
     */
    fun createSyntheticEnumField(
      fieldClassName: String,
      fieldName: String,
      enumValues: List<String>,
      repeated: Boolean,
      annotations: List<Annotation> = listOf()
    ) = Field(
      fieldName,
      "Enum<$fieldClassName,${enumValues.joinToString(",")}>",
      repeated,
      annotations.toStrings()
    )

    private fun List<Annotation>.toStrings(): List<String> = this.map { it.toString() }

    private fun KClass<*>.getProtobufType(): String? {
      return when {
        isSubclassOf(Message::class) -> ProtoAdapter.get(this.java)
        else -> null
      }?.typeUrl?.split("/")?.lastOrNull()
    }
  }

  /** Akin to a Proto Message, a Type has a list of fields */
  data class Type(val fields: List<Field>)

  /**
   * Akin to a Proto field, a field can be of primitive or another Message type,
   * and can be repeated to become a list.
   *
   * Enums are encoded to contain their values within their Type definition as
   * opposed to a unique Type.
   */
  data class Field @JvmOverloads constructor(
    val name: String,
    val type: String,
    val repeated: Boolean,
    val annotations: List<String> = listOf(),
    val protobufType: String? = null,
  )

}
