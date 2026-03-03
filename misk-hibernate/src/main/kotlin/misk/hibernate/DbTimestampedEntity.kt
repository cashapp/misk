package misk.hibernate

import java.time.Instant

/**
 * This complements [DbEntity] for entities that keep updated_at and created_at timestamps.
 *
 * Misk uses the application's clock time to set [created_at] and [updated_at]. We prefer the
 * application's (ie. JVM's) clock over the database's clock because only the application's clock
 * can be faked in tests.
 *
 * To use timestamped entities first add two columns in your `CREATE TABLE` statement. You may also
 * add an optional index on `updated_at`:
 *
 * ```
 * CREATE TABLE movies(
 *   id bigint NOT NULL PRIMARY KEY AUTO_INCREMENT,
 *   created_at timestamp(3) NOT NULL DEFAULT NOW(3),
 *   updated_at timestamp(3) NOT NULL DEFAULT NOW(3) ON UPDATE NOW(3),
 *   ...
 *   KEY `idx_updated_at` (`updated_at`)
 * );
 * ```
 *
 * The above SQL uses `timestamp(3)` and `NOW(3)` to get millisecond precision which interoperates
 * nicely with Java. Although we declare `DEFAULT NOW(3)` and `ON UPDATE NOW(3)`, these don't apply
 * to writes from Hibernate because it always provides a value explicitly.
 *
 * Next implement this interface and add the following declarations to your entity class:
 *
 * ```
 * @Entity
 * @Table(name = "movies")
 * class DbMovie() : DbEntity<DbMovie>, DbTimestampedEntity {
 *
 *   ...
 *
 *   @Column
 *   override lateinit var updated_at: Instant
 *
 *   @Column
 *   override lateinit var created_at: Instant
 *
 *   ...
 *
 * }
 * ```
 *
 * The timestamp listener will automatically populate these values on save and update.
 * (Incidentally, the `@Columns` are not marked `nullable = false` because the timestamp listener is
 * triggered _after_ the nullability check.)
 */
interface DbTimestampedEntity {
  var created_at: Instant
  var updated_at: Instant
}
