package misk.hibernate

import com.google.common.collect.ImmutableMultimap
import com.google.common.collect.Multimap
import org.hibernate.boot.Metadata
import org.hibernate.mapping.Component
import org.hibernate.mapping.PersistentClass
import org.hibernate.mapping.Property
import org.hibernate.mapping.SimpleValue
import java.lang.reflect.Field
import java.util.Properties

/** Returns all properties (IDs, joined columns, regular columns) of this persistent class. */
private val PersistentClass.allProperties: List<Property>
  get() {
    val result = mutableListOf<Property>()

    identifierProperty?.let {
      result.add(it)
    }

    @Suppress("UNCHECKED_CAST") // This Hibernate method returns raw types!
    val i = propertyIterator as MutableIterator<Property>
    while (i.hasNext()) {
      result.add(i.next())
    }

    return result
  }

/** All properties of all entities. */
internal val Metadata.allProperties: Multimap<Class<*>, Property>
  get() {
    val result = ImmutableMultimap.builder<Class<*>, Property>()
    for (entityBinding in entityBindings) {
      for (property in entityBinding.allProperties) {
        val value = property.value
        if (value is Component) {
          for (subProperty in value.propertyIterator) {
            if (subProperty is Property) {
              result.put(Class.forName(value.componentClass.name), subProperty)
            }
          }
        } else {
          result.put(Class.forName(entityBinding.className), property)
        }
      }
    }
    return result.build()
  }

internal fun field(entityClass: Class<*>, property: Property): Field {
  try {
    return entityClass.getDeclaredField(property.name)
  } catch (e: NoSuchFieldException) {
    val superclass = entityClass.superclass
    if (superclass != null) {
      return field(superclass, property)
    }
    throw IllegalStateException("expected a field for ${property.name} in ${entityClass.name}", e)
  }
}

internal fun Properties.setField(name: String, field: Field) {
  setProperty("${name}DeclaringClass", field.declaringClass.name)
  setProperty("${name}Name", field.name)
}

internal fun Properties.getField(name: String): Field? {
  val declaringClassName = getProperty("${name}DeclaringClass") ?: return null
  val fieldName = getProperty("${name}Name") ?: return null

  val entityClass = Class.forName(declaringClassName)
  return entityClass.getDeclaredField(fieldName)
}

internal fun SimpleValue.setTypeParameter(key: String, value: String) {
  if (typeParameters == null) {
    typeParameters = Properties()
  }
  typeParameters.setProperty(key, value)
}

internal fun SimpleValue.setTypeParameter(name: String, field: Field) {
  if (typeParameters == null) {
    typeParameters = Properties()
  }
  typeParameters.setField(name, field)
}

internal fun SimpleValue.setTypeParameter(key: String, value: Any) {
  if (typeParameters == null) {
    typeParameters = Properties()
  }
  typeParameters[key] = value
}
