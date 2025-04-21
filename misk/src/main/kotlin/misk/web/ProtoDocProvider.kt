package misk.web

/**
 * Given a fully qualified protobuf type name returns a URL to type documentation.
 * Default [NO_OP] is bound in MiskWebModule via OptionalBinder and can be overridden with:
 * ```
 *   OptionalBinder.newOptionalBinder(binder(), ProtoDocumentationProvider::class.java)
 *       .setBinding()
 *       .toInstance(ProtoDocumentationProvider { type -> "https://my-internal-doc-hosting/$type" })
 * ```
 *
 */
fun interface ProtoDocumentationProvider {
  fun get(qualifiedTypeName: String?): String?
}
