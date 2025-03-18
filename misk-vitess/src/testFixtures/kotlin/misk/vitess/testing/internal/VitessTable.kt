package misk.vitess.testing.internal

internal data class VitessTable(val tableName: String, val type: VitessTableType)

/**
 * We currently only support two types of tables for typing: sequence and standard. If the need arises, we may want to
 * extend this in the future to support other tables types such as lookup and reference tables.
 */
enum class VitessTableType {
  SEQUENCE,
  REFERENCE,
  STANDARD,
}
