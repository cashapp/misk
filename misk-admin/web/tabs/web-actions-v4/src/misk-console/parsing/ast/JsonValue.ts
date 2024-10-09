import AstNode from "@misk-console/parsing/ast/AstNode"
import MiskType from "@misk-console/api/MiskType"
import { MiskObjectTypes } from "@misk-console/api/responseTypes"

export default abstract class JsonValue extends AstNode {
  abstract applyTypes(type: MiskType, types: MiskObjectTypes): void

  as<T>(): T {
    return this as unknown as T
  }
}
