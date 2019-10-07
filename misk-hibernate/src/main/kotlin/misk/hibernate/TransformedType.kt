package misk.hibernate

import java.io.Serializable
import kotlin.reflect.KClass




// TransformedType is a meta-annotation. Use it to create arbitrary column transformers
//
// TBD DOCS ON APPLICATION IN QUERYING

@Target(AnnotationTarget.ANNOTATION_CLASS)
annotation class TransformedType(val transformer: KClass<out Transformer>,
                                 val targetType: KClass<*>)



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


