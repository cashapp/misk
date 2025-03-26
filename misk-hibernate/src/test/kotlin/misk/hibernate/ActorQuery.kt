package misk.hibernate

interface ActorQuery : Query<DbActor> {
  @Constraint(path = "id", operator = Operator.EQ)
  fun id(id: Id<DbActor>): ActorQuery
}
