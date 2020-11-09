export const enum TypescriptBaseTypes {
  "any" = "any",
  "boolean" = "boolean",
  "enum" = "enum",
  "null" = "null",
  "number" = "number",
  "string" = "string"
}

export const enum ServerTypes {
  "Boolean" = "Boolean",
  "Byte" = "Byte",
  "ByteString" = "ByteString",
  "Char" = "Char",
  "Double" = "Double",
  "Enum" = "Enum",
  "Float" = "Float",
  "Int" = "Int",
  "JSON" = "JSON",
  "Long" = "Long",
  "Short" = "Short",
  "String" = "String"
}

export interface IBaseFieldTypes {
  [serverType: string]: TypescriptBaseTypes
}

export const BaseFieldTypes: IBaseFieldTypes = {
  [ServerTypes.Boolean]: TypescriptBaseTypes.boolean,
  [ServerTypes.Short]: TypescriptBaseTypes.number,
  [ServerTypes.Int]: TypescriptBaseTypes.number,
  [ServerTypes.JSON]: TypescriptBaseTypes.string,
  [ServerTypes.Long]: TypescriptBaseTypes.number,
  [ServerTypes.ByteString]: TypescriptBaseTypes.string,
  [ServerTypes.String]: TypescriptBaseTypes.string,
  [ServerTypes.Enum]: TypescriptBaseTypes.enum
}

export interface IFieldTypeMetadata {
  name: string
  repeated: boolean
  type: IBaseFieldTypes | any
}

export interface IQueryParameters {
  [type: string]: {
    fields: IFieldTypeMetadata[]
  }
}

export interface IDatabaseQueryMetadataAPI {
  allowedCapabilities: string[]
  allowedServices: string[]
  accessAnnotation: string
  table: string
  entityClass: string
  queryClass: string
  constraints?: IQueryParameters
  orders?: IQueryParameters
  selects?: IQueryParameters
}
