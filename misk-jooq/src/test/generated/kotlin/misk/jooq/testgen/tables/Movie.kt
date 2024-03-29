/*
 * This file is generated by jOOQ.
 */
package misk.jooq.testgen.tables


import java.time.LocalDateTime

import kotlin.collections.List

import misk.jooq.testgen.Jooq
import misk.jooq.testgen.keys.KEY_MOVIE_PRIMARY
import misk.jooq.testgen.tables.records.MovieRecord

import org.jooq.Field
import org.jooq.ForeignKey
import org.jooq.Identity
import org.jooq.Name
import org.jooq.Record
import org.jooq.Row6
import org.jooq.Schema
import org.jooq.Table
import org.jooq.TableField
import org.jooq.TableOptions
import org.jooq.UniqueKey
import org.jooq.impl.DSL
import org.jooq.impl.Internal
import org.jooq.impl.SQLDataType
import org.jooq.impl.TableImpl


/**
 * This class is generated by jOOQ.
 */
@Suppress("UNCHECKED_CAST")
open class Movie(
    alias: Name,
    child: Table<out Record>?,
    path: ForeignKey<out Record, MovieRecord>?,
    aliased: Table<MovieRecord>?,
    parameters: Array<Field<*>?>?
): TableImpl<MovieRecord>(
    alias,
    Jooq.JOOQ,
    child,
    path,
    aliased,
    parameters,
    DSL.comment(""),
    TableOptions.table()
) {
    companion object {

        /**
         * The reference instance of <code>jooq.movie</code>
         */
        val MOVIE = Movie()
    }

    /**
     * The class holding records for this type
     */
    override fun getRecordType(): Class<MovieRecord> = MovieRecord::class.java

    /**
     * The column <code>jooq.movie.id</code>.
     */
    val ID: TableField<MovieRecord, Long?> = createField(DSL.name("id"), SQLDataType.BIGINT.nullable(false).identity(true), this, "")

    /**
     * The column <code>jooq.movie.name</code>.
     */
    val NAME: TableField<MovieRecord, String?> = createField(DSL.name("name"), SQLDataType.VARCHAR(191).nullable(false), this, "")

    /**
     * The column <code>jooq.movie.genre</code>.
     */
    val GENRE: TableField<MovieRecord, String?> = createField(DSL.name("genre"), SQLDataType.VARCHAR(191).nullable(false), this, "")

    /**
     * The column <code>jooq.movie.version</code>.
     */
    val VERSION: TableField<MovieRecord, Int?> = createField(DSL.name("version"), SQLDataType.INTEGER.nullable(false), this, "")

    /**
     * The column <code>jooq.movie.created_at</code>.
     */
    val CREATED_AT: TableField<MovieRecord, LocalDateTime?> = createField(DSL.name("created_at"), SQLDataType.LOCALDATETIME(3).nullable(false).defaultValue(DSL.field("CURRENT_TIMESTAMP(3)", SQLDataType.LOCALDATETIME)), this, "")

    /**
     * The column <code>jooq.movie.updated_at</code>.
     */
    val UPDATED_AT: TableField<MovieRecord, LocalDateTime?> = createField(DSL.name("updated_at"), SQLDataType.LOCALDATETIME(3).nullable(false).defaultValue(DSL.field("CURRENT_TIMESTAMP(3)", SQLDataType.LOCALDATETIME)), this, "")

    private constructor(alias: Name, aliased: Table<MovieRecord>?): this(alias, null, null, aliased, null)
    private constructor(alias: Name, aliased: Table<MovieRecord>?, parameters: Array<Field<*>?>?): this(alias, null, null, aliased, parameters)

    /**
     * Create an aliased <code>jooq.movie</code> table reference
     */
    constructor(alias: String): this(DSL.name(alias))

    /**
     * Create an aliased <code>jooq.movie</code> table reference
     */
    constructor(alias: Name): this(alias, null)

    /**
     * Create a <code>jooq.movie</code> table reference
     */
    constructor(): this(DSL.name("movie"), null)

    constructor(child: Table<out Record>, key: ForeignKey<out Record, MovieRecord>): this(Internal.createPathAlias(child, key), child, key, MOVIE, null)
    override fun getSchema(): Schema = Jooq.JOOQ
    override fun getIdentity(): Identity<MovieRecord, Long?> = super.getIdentity() as Identity<MovieRecord, Long?>
    override fun getPrimaryKey(): UniqueKey<MovieRecord> = KEY_MOVIE_PRIMARY
    override fun getKeys(): List<UniqueKey<MovieRecord>> = listOf(KEY_MOVIE_PRIMARY)
    override fun getRecordVersion(): TableField<MovieRecord, Int?> = VERSION
    override fun `as`(alias: String): Movie = Movie(DSL.name(alias), this)
    override fun `as`(alias: Name): Movie = Movie(alias, this)

    /**
     * Rename this table
     */
    override fun rename(name: String): Movie = Movie(DSL.name(name), null)

    /**
     * Rename this table
     */
    override fun rename(name: Name): Movie = Movie(name, null)

    // -------------------------------------------------------------------------
    // Row6 type methods
    // -------------------------------------------------------------------------
    override fun fieldsRow(): Row6<Long?, String?, String?, Int?, LocalDateTime?, LocalDateTime?> = super.fieldsRow() as Row6<Long?, String?, String?, Int?, LocalDateTime?, LocalDateTime?>
}
