package misk.hibernate

import com.google.common.collect.ImmutableSet
import javax.inject.Inject
import org.hibernate.SessionFactory
import org.hibernate.persister.entity.AbstractEntityPersister

import com.google.common.base.Preconditions.checkState

internal class PersistenceMetadataImpl @Inject constructor(
  private val sessionFactory: SessionFactory
) : PersistenceMetadata {

  override fun <T: DbEntity<T>> getTableName(entityType: T): String {
    return hibernateMetadataForClass(entityType).tableName
  }

  override fun <T: DbEntity<T>> getColumnNames(
    entityType: T
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

  override fun <T: DbEntity<T>> getColumnNames(
    entityType: T,
    propertyName: String
  ): Array<String> {
    return hibernateMetadataForClass(entityType).getPropertyColumnNames(propertyName)
  }

  private fun <T: DbEntity<T>> hibernateMetadataForClass(
    entityType: T
  ): AbstractEntityPersister {
    val hibernateMetadata = sessionFactory.getClassMetadata(entityType::class.qualifiedName)

    checkState(hibernateMetadata != null,
        "${entityType::class.qualifiedName} does not map to a known entity type"
    )

    checkState(hibernateMetadata is AbstractEntityPersister,
        "${entityType::class.qualifiedName} does not map to a persistent class"
    )

    return hibernateMetadata as AbstractEntityPersister
  }
}
