package misk.web.metadata.database

interface DatabaseQueryFunctionMetadata {
  /** Function simple name */
  val name: String
  /** String Type that identifies the parameters signature for the function */
  val parametersTypeName: String
}