package misk.hibernate.actions

import misk.hibernate.DbActor
import misk.hibernate.DbCharacter
import misk.hibernate.DbMovie
import misk.hibernate.HibernateEntityModule
import misk.hibernate.Movies
import misk.hibernate.MoviesTestModule
import misk.hibernate.OperatorsMovieQuery
import misk.inject.KAbstractModule
import misk.jdbc.DataSourceType
import misk.security.authz.AccessAnnotationEntry
import javax.inject.Qualifier

class HibernateDatabaseQueryTestingModule : KAbstractModule() {
  override fun configure() {
    install(HibernateWebActionTestingModule())
    install(MoviesTestModule(type = DataSourceType.MYSQL,
    entitiesModule = object :
      HibernateEntityModule(Movies::class) {
      override fun configureHibernate() {
        installHibernateAdminDashboardWebActions()

        addEntities(DbActor::class)
        addEntityWithDynamicQuery<DbMovie, DynamicMovieQueryAccess>()
        addEntityWithDynamicQuery<DbCharacter, DynamicMovieQueryAccess>()
        addEntityWithStaticQuery<DbMovie, OperatorsMovieQuery, OperatorsMovieQueryAccess>()
      }
    }))

    multibind<AccessAnnotationEntry>().toInstance(DYNAMIC_MOVIE_QUERY_ACCESS_ENTRY)
    multibind<AccessAnnotationEntry>().toInstance(OPERATORS_MOVIE_QUERY_ACCESS_ENTRY)
  }

  companion object {
    val DYNAMIC_MOVIE_QUERY_ACCESS_ENTRY = AccessAnnotationEntry<DynamicMovieQueryAccess>(
      capabilities = listOf(
        "dynamic_movie_query"
      )
    )
    val OPERATORS_MOVIE_QUERY_ACCESS_ENTRY = AccessAnnotationEntry<OperatorsMovieQueryAccess>(
      capabilities = listOf(
        "operators_movie_query"
      )
    )
  }
}

@Qualifier
@Target(AnnotationTarget.FIELD, AnnotationTarget.FUNCTION)
annotation class DynamicMovieQueryAccess

@Qualifier
@Target(AnnotationTarget.FIELD, AnnotationTarget.FUNCTION)
annotation class OperatorsMovieQueryAccess
