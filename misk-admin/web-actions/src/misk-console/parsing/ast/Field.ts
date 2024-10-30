import AstNode from '@misk-console/parsing/ast/AstNode';

import JsonValue from '@misk-console/parsing/ast/JsonValue';
import Unexpected from '@misk-console/parsing/ast/Unexpected';
import {
  MiskFieldDefinition,
  MiskObjectTypes,
} from '@misk-console/api/responseTypes';
import StrLiteral from '@misk-console/parsing/ast/StrLiteral';
import Obj from '@misk-console/parsing/ast/Obj';
import MiskType from '@misk-console/api/MiskType';

export default class Field extends AstNode {
  name: StrLiteral;
  value: JsonValue | null;

  colonIndex: number | null;
  cursorInValuePosition: boolean = false;
  parent?: Obj;
  definition?: MiskFieldDefinition;

  constructor(
    name: StrLiteral,
    colonIndex: number | null,
    value?: JsonValue | Unexpected | null,
  ) {
    super();
    this.colonIndex = colonIndex;
    this.name = name;
    if (value instanceof JsonValue) {
      this.value = value ?? null;
    } else if (value instanceof Unexpected) {
      this.unexpected = [value];
      this.value = null;
    } else {
      this.value = null;
    }

    if (this.value) {
      this.value.parent = this;
    }
  }

  onEvalCursor(index: number) {
    if (this.colonIndex !== null && this.end !== undefined) {
      this.cursorInValuePosition =
        index >= this.colonIndex && index <= this.end;
    }
  }

  childNodes(): AstNode[] {
    return this.value ? [this.value] : [];
  }

  render(): string {
    return `"${this.name.value}": ${this.value?.render() ?? 'null'}`;
  }

  applyTypes(fieldDefinition: MiskFieldDefinition, types: MiskObjectTypes) {
    this.definition = fieldDefinition;
    if (this.value) {
      this.value.applyTypes(MiskType.fromFieldDef(fieldDefinition), types);
    }
  }
}
