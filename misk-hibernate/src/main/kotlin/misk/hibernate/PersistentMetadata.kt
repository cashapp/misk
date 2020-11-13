package misk.hibernate

import com.google.common.collect.ImmutableSet
import org.hibernate.SessionFactory
import org.hibernate.metamodel.spi.MetamodelImplementor
import org.hibernate.persister.entity.AbstractEntityPersister
import kotlin.reflect.KClass

internal class PersistenceMetadata constructor(private val sessionFactory: SessionFactory) {

  fun <T : DbEntity<T>> getTableName(entityType: KClass<out DbEntity<T>>): String {
    return hibernateMetadataForClass(entityType).tableName
  }

  fun <T : DbEntity<T>> getColumnNames(
    entityType: KClass<out DbEntity<T>>
  ): ImmutableSet<String> {
    val result = ImmutableSet.builder<String>()

    val classMetadata = hibernateMetadataForClass(entityType)
    for (column in classMetadata.identifierColumnNames) {
      result.add(column)
    }
    val propertyNames = classMetadata.propertyNames
    for (i in propertyNames.indices) {
      result.add(*classMetadata.getPropertyColumnNames(i))
    }

    return result.build()
  }

  private fun <T : DbEntity<T>> hibernateMetadataForClass(
    entityType: KClass<out DbEntity<T>>
  ): AbstractEntityPersister {
    val metaModel = sessionFactory.metamodel as MetamodelImplementor
    val entityPersister = metaModel.entityPersister(entityType.java)
    check(entityPersister != null) {
      "${entityType.qualifiedName} does not map to a known entity type"
    }
    check(entityPersister is AbstractEntityPersister) {
      "${entityType.qualifiedName} does not map to a persistent class"
    }
    return entityPersister
  }
}
