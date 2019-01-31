package misk.hibernate

class EntityGroups {
  fun entityGroupId(entity: Any): Id<*>? {
    return when (entity) {
      is DbRoot<*> -> entity.id
      is DbChild<*, *> -> entity.rootId
      else -> null
    }
  }
}