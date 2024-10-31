import AstNode from '@web-actions/parsing/ast/AstNode';

import JsonValue from '@web-actions/parsing/ast/JsonValue';
import Unexpected from '@web-actions/parsing/ast/Unexpected';
import MiskType from '@web-actions/api/MiskType';
import { MiskObjectTypes } from '@web-actions/api/responseTypes';

export default class Arr extends JsonValue {
  values: JsonValue[];
  type?: MiskType;

  constructor(values: JsonValue[], unexpected: Unexpected[]) {
    super();
    this.values = values;
    this.unexpected = unexpected;

    for (const value of values) {
      value.parent = this;
    }
  }

  childNodes(): AstNode[] {
    return this.values;
  }

  render(): string {
    return `[${this.values.map((it) => it.render()).join(', ')}]`;
  }

  applyTypes(type: MiskType, types: MiskObjectTypes) {
    this.type = type;
    for (const value of this.values) {
      value.applyTypes(type.arrayType(), types);
    }
  }
}
