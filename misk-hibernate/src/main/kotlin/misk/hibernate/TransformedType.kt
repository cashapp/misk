package misk.hibernate

import java.io.Serializable
import kotlin.reflect.KClass

/**
 * [TransformedType] is a meta-annotation for assigning arbitrary transformers to entity fields. Use it to annotate
 * attributes that will mark a field to be transformed.
 *
 * [transformer] is a KClass instance of a class that implements assemble and disassemble methods. Assemble is called
 *    when a value is read from the table, or from cache. Disassemble is called when a value is about to be written to
 *    a table or cache.
 *
 * [targetType] specifies a KClass instance of the type that the field should have when disassembled. It is used to
 *    determine the backing SQL type.
 *
 * The methods of [Transformer] additionally take a field representing the annotated field and a TransformerContext,
 * which contains limited entity state, including the name of the table and the column, the arguments the annotation
 * received.
 *
 * Note!: Wrapping basic numberic types may not behave as one would expect, especially when querying. This is partly due
 * to Hibernate's inlining of primitive values.
 *
 *
 * For example, one can define a transformer that appends a value to a string prior to storage, and removes it after
 * retrieval. First, define the Transformer itself
 *
 * class AppendTransformer(val context: TransformerContext) {
 *   fun assemble(owner: Any?, value: Serializable): Any =
 *     (value as String).removeSuffix(context.arguments["suffix"] as String))
 *
 *   fun disassemble(value: Any): Serializable =
 *    "${value as String}${context.arguments["suffix"] as String}"
 * }
 *
 *
 *
 * Then, create the entity annotation:
 *
 * @Target(AnnotationTarget.FIELD)
 * @TransformedType(transformer = AppendTransformer::class, targetType = String::class)
 * annotation class SuffixedString(val suffix: String)
 *
 *
 *
 * Then, annotate an entity field:
 *
 * @Entity
 * @Table(name="table_name")
 * class DbEntity : DbUnsharded(DbEntity) {
 *    // ...
 *
 *    @Column("some_column")
 *    @SuffixedString(suffix="_suffix")
 *    var suffixedString : String = ""
 *
 *    // ...
 * }
 *
 *
 */
@Target(AnnotationTarget.ANNOTATION_CLASS)
annotation class TransformedType(
  val transformer: KClass<out Transformer>,
  val targetType: KClass<*>
)

data class TransformerContext(
  val tableName: String,
  val columnName: String,
  var arguments: Map<String, *>,
  var field: KClass<*>
)

abstract class Transformer(val context: TransformerContext) {

  // assemble() is called with a value after being read from a db
  abstract fun assemble(owner: Any?, value: Serializable): Any

  // dissassemble() is called  with a value prior being stored in a db
  abstract fun disassemble(value: Any): Serializable
}
