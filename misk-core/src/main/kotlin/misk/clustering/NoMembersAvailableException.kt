package misk.clustering

/**
 * Thrown if the cluster does not have any members available.
 */
class NoMembersAvailableException(val resourceId: String) :
  Exception("no members available for $resourceId")
