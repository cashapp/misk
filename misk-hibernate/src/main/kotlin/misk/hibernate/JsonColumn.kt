package misk.hibernate

/**
 * Put this on a column field to get Hibernate to persist it as JSON using Moshi and the injector's
 * configured JSON adapters.
 *
 * By default, the configured adapters support Kotlin's basic types, as well as [List]s, [Set]s, and
 * [Map]s of supported types. Hibernate will raise an exception during initialization if there are
 * no adapters for the field type. You can add support for custom types by calling
 * [misk.inject.KAbstractModule.install] with [misk.moshi.MoshiAdapterModule] as part of your module
 * initialization:
 *
 * ```
 * install(MoshiAdapterModule(MyCustomClass.JsonAdapter))
 * ```
 *
 * The configured adapters do _not_ support [Any]. To get around this, define a custom type for
 * the field:
 *
 * ```
 * class MyCustomClass(
 *    private val map: Map<String, Any?>
 * ) : Map<String, Any?> by map {
 *   object JsonAdapter {
 *     private val moshiAdapter =
 *         Moshi.Builder().build().adapter(MyCustomClass::class.java)!!
 *
 *     @FromJson
 *     fun fromJson(reader: JsonReader): MyCustomClass? =
 *         moshiAdapter.fromJson(reader)
 *
 *     @ToJson
 *     fun toJson(writer: JsonWriter, value: MyCustomClass?): Unit =
 *         moshiAdapter.toJson(writer, value)
 *   }
 * }
 * ```
 *
 * It is an error to put this annotation on a mutable field. We don't defensively copy these when
 * we read them out of the database.
 */
@Target(AnnotationTarget.FIELD)
annotation class JsonColumn
