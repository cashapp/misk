import AstNode from '@web-actions/parsing/ast/AstNode';

import JsonValue from '@web-actions/parsing/ast/JsonValue';
import Unexpected from '@web-actions/parsing/ast/Unexpected';
import {
  MiskFieldDefinition,
  MiskObjectTypes,
} from '@web-actions/api/responseTypes';
import StrLiteral from '@web-actions/parsing/ast/StrLiteral';
import Obj from '@web-actions/parsing/ast/Obj';
import MiskType from '@web-actions/api/MiskType';

export default class Field extends AstNode {
  name: StrLiteral;
  value: JsonValue | Unexpected | null;

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
    this.value = value ?? null;

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
