import { OrderedSet } from "immutable"

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

// TODO rename to IFormTypes since it's defining the form types
export interface IActionTypes {
  [type: string]: {
    fields: IFieldTypeMetadata[]
  }
}

export interface IFieldTypeMetadata {
  name: string
  repeated: boolean
  type: IBaseFieldTypes | any
}

export interface ITypesFieldMetadata {
  idParent: string
  idChildren: OrderedSet<string>
  id: string
  name: string
  repeated: boolean
  serverType: ServerTypes | null
  typescriptType: TypescriptBaseTypes | null
  dirtyInput: boolean
}
