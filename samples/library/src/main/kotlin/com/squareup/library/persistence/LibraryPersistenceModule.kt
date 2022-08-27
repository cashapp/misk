package com.squareup.library.persistence

import com.google.inject.Provides
import com.squareup.library.LibraryConfig
import com.squareup.library.db.LibraryDatabase
import com.squareup.sqldelight.sqlite.driver.JdbcDriver
import java.sql.Connection
import javax.inject.Provider
import javax.inject.Singleton
import javax.sql.DataSource
import misk.inject.KAbstractModule
import misk.jdbc.JdbcModule

class LibraryPersistenceModule(
  private val config: LibraryConfig
) : KAbstractModule() {
  override fun configure() {
    install(
      JdbcModule(
        LibraryDb::class,
        config.data_source_clusters.values.single().writer
      )
    )
  }

  @Provides
  @Singleton
  fun provideRedwoodDatabase(
    @LibraryDb dataSource: Provider<DataSource>
  ): LibraryDatabase {
    val driver = object : JdbcDriver() {
      override fun getConnection(): Connection {
        val connection = dataSource.get().connection
        connection.autoCommit = true
        return connection
      }

      override fun closeConnection(connection: Connection) {
        connection.close()
      }
    }
    return LibraryDatabase(
      driver = driver,
      booksAdapter = booksAdapter,
    )
  }
}
