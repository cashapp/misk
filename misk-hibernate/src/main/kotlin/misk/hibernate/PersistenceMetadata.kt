package misk.hibernate

import com.google.common.collect.ImmutableSet
import com.google.inject.ImplementedBy

/** Persistence related metadata  */
@ImplementedBy(PersistenceMetadataImpl::class)
interface PersistenceMetadata {
  /** Gets the table name for the given class  */
  fun <T: DbEntity<T>> getTableName(entityType: T): String

  /** Returns all of the columns of `entityType`.  */
  fun <T: DbEntity<T>> getColumnNames(entityType: T): ImmutableSet<String>

  /**
   * Gets the column names for the given class and property name. Multiple names
   * can be returned since single properties can be mapped to multiple columns.
   */
  fun <T: DbEntity<T>> getColumnNames(entityType: T, propertyName: String): Array<String>
}
