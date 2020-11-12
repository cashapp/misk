package misk.web.metadata

interface FunctionMetadata {
  /** Function simple name */
  val name: String
  /** String Type that identifies the parameters signature for the function */
  val parametersType: String
}