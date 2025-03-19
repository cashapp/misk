import AstNode from '@web-actions/parsing/ast/AstNode';
import MiskType from '@web-actions/api/MiskType';
import { MiskObjectTypes } from '@web-actions/api/responseTypes';

export default abstract class JsonValue extends AstNode {
  abstract applyTypes(type: MiskType, types: MiskObjectTypes): void;

  as<T>(): T {
    return this as unknown as T;
  }
}
