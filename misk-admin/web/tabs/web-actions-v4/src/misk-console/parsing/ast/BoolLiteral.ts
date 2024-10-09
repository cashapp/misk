import MiskType from "@misk-console/api/MiskType"
import JsonValue from "@misk-console/parsing/ast/JsonValue"
import { MiskObjectTypes } from "@misk-console/api/responseTypes"

export default class BoolLiteral extends JsonValue {
  value?: string
  type?: MiskType

  constructor(value: string) {
    super()
    this.value = value
  }

  applyTypes(type: MiskType, types: MiskObjectTypes) {
    this.type = type
  }

  render(): string {
    return this.value!
  }
}
