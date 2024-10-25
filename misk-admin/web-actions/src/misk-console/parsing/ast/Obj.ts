import AstNode from "@misk-console/parsing/ast/AstNode"
import {
  MiskObjectType,
  MiskFieldDefinition,
  MiskObjectTypes
} from "@misk-console/api/responseTypes"

import Field from "@misk-console/parsing/ast/Field"
import Unexpected from "@misk-console/parsing/ast/Unexpected"
import { associateBy, expectFromRecord } from "@misk-console/utils/common"
import JsonValue from "@misk-console/parsing/ast/JsonValue"
import MiskType from "@misk-console/api/MiskType"

export default class Obj extends JsonValue {
  fields: Field[]
  type?: MiskObjectType

  constructor(fields: Field[], unexpected: Unexpected[]) {
    super()
    this.fields = fields
    for (const field of fields) {
      field.parent = this
    }
    this.unexpected = unexpected
  }

  childNodes(): AstNode[] {
    return this.fields
  }

  render(): string {
    return `{${this.fields.map(it => it.render()).join(", ")}}`
  }

  applyTypes(type: MiskType, types: MiskObjectTypes) {
    const objType = expectFromRecord(types, type.type)
    this.type = objType
    const fieldDefinitions = associateBy(objType.fields, it => it.name)
    for (const field of this.fields) {
      const fieldDefinition = fieldDefinitions[field.name.value ?? ""]
      if (fieldDefinition) {
        field.applyTypes(fieldDefinition, types)
      }
    }
  }
}
